package io.lawallet.cardactivation.pos

import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import java.nio.charset.Charset

/**
 * Extracts the card URL from a tapped NDEF tag. Handles URI records (how
 * boltcards write `lnurlw://…`) and Text records (what the old wrapper parsed).
 */
object CardNfc {
    fun readUrl(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        val message =
            ndef.cachedNdefMessage
                ?: runCatching {
                    ndef.connect()
                    ndef.ndefMessage
                }.also { runCatching { ndef.close() } }.getOrNull()
                ?: return null

        // Prefer the LaWallet lnurl record wherever it sits in the message;
        // otherwise fall back to the first decodable record (so a blank /
        // non-LaWallet tag is still surfaced and handled as such upstream).
        var fallback: String? = null
        for (record in message.records) {
            val decoded = decode(record)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (decoded.contains("/api/cards/") || decoded.startsWith("lnurlw://", ignoreCase = true)) {
                return decoded
            }
            if (fallback == null) fallback = decoded
        }
        return fallback
    }

    private fun decode(record: NdefRecord): String? {
        // Standard URI record (boltcards) — Android assembles prefix + body.
        record.toUri()?.let { return it.toString() }

        if (record.tnf == NdefRecord.TNF_ABSOLUTE_URI) {
            return String(record.payload, Charsets.UTF_8)
        }
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI)) {
            return decodeUri(record.payload)
        }
        if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            return decodeText(record.payload)
        }
        return runCatching { String(record.payload, Charsets.UTF_8) }.getOrNull()
    }

    // NFC Forum URI Record Type Definition prefix table.
    private val URI_PREFIXES =
        arrayOf(
            "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
            "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
            "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://",
            "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://",
            "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
            "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
        )

    private fun decodeUri(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val prefix = URI_PREFIXES.getOrElse(payload[0].toInt() and 0xFF) { "" }
        return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }

    private fun decodeText(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val encoding = if (status and 0x80 == 0) Charsets.UTF_8 else Charset.forName("UTF-16")
        val langLen = status and 0x3F
        return String(payload, 1 + langLen, payload.size - 1 - langLen, encoding)
    }
}
