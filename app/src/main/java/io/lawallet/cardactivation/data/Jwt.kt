package io.lawallet.cardactivation.data

import android.util.Base64
import org.json.JSONObject

/** Public claims of a LaWallet device token (decoded, not verified). */
data class JwtClaims(
    val apiUrl: String?,
    val scopes: List<String>,
    val pubkey: String?,
    val kind: String?,
    val exp: Long?, // seconds since epoch
)

object Jwt {
    fun looksLikeJwt(token: String): Boolean {
        val parts = token.trim().split(".")
        return parts.size == 3 && parts.all { it.isNotEmpty() }
    }

    /** base64url-decodes the payload; returns null if not a parseable JWT. */
    fun decode(token: String): JwtClaims? =
        try {
            val parts = token.trim().split(".")
            if (parts.size != 3) {
                null
            } else {
                val seg = parts[1]
                val padded = seg + "=".repeat((4 - seg.length % 4) % 4)
                val bytes = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
                val o = JSONObject(String(bytes, Charsets.UTF_8))
                JwtClaims(
                    apiUrl = o.optString("apiUrl").ifBlank { null },
                    scopes =
                        o.optJSONArray("scopes")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                    pubkey = o.optString("pubkey").ifBlank { null },
                    kind = o.optString("kind").ifBlank { null },
                    exp = if (o.has("exp")) o.optLong("exp") else null,
                )
            }
        } catch (t: Throwable) {
            null
        }

    /** True when `exp` has passed (with a 30s skew). No exp ⇒ not expired. */
    fun isExpired(
        claims: JwtClaims,
        skewSec: Long = 30,
    ): Boolean {
        val exp = claims.exp ?: return false
        return exp * 1000 <= System.currentTimeMillis() + skewSec * 1000
    }
}

/** Normalizes a base URL (https default, strip trailing slashes). */
fun normalizeBaseUrl(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    return withScheme.trimEnd('/')
}
