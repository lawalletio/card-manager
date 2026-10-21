package io.lawallet.cardactivation.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** SUN proof from a tapped card's NDEF URL query (`p` = encrypted PICC data, `c` = CMAC). */
data class Sun(
    val p: String,
    val c: String,
)

/** Card detail for display (subset of the backend `Card`). */
data class CardDetail(
    val id: String,
    val imageUrl: String?,
    val designName: String?,
    val status: String,
    val lightningAddress: String?,
    val kind: String?,
    val owned: Boolean,
    val disabled: Boolean,
    /** The tap's SUN proof, already verified by the server. RECOVER spends it. */
    val sun: Sun,
)

/** Owner of a card, from `GET /api/cards/:id/owner`. Addresses are full `user@domain`. */
data class OwnerSummary(
    val owned: Boolean,
    val pubkey: String?,
    val primaryAddress: String?,
    val lightningAddresses: List<String>,
    val cards: Int,
    val remoteWallets: Int,
    val cardRecoveryEnabled: Boolean,
    val cardDisabled: Boolean,
    /** Null when the owner's wallet is unreachable. */
    val balanceSats: Long?,
)

/** Minted ONE_TIME activation (or recovery) token. */
data class ActivationToken(
    val qrPayload: String,
    val expiresAt: String?,
)

class ApiException(message: String, val code: Int = 0) : Exception(message)

/**
 * Authenticated LaWallet API (`Authorization: Bearer <jwt>` over `AuthStore.baseUrl`),
 * mirroring card-installer's flow: GET `/api/cards/:id` for the card data, then
 * POST `/api/cards/:id/activation-tokens` `{qrKind:"ONE_TIME"}` to mint the QR.
 * Owned cards also load `GET …/owner`, and RECOVER mints POST `…/recovery-tokens`
 * with the tap's SUN `{p, c}`.
 */
object Api {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val jsonMedia = "application/json".toMediaType()

    /** The server card id from a tapped lnurlw/https scan URL, or null if it isn't one. */
    fun resolveCardId(input: String): String? = Regex("/api/cards/([^/?]+)").find(input)?.groupValues?.get(1)

    /** The SUN `p`/`c` hex params of a tapped card URL, or null if either is missing. */
    fun parseSun(cardUrl: String): Sun? {
        fun param(name: String) = Regex("[?&]$name=([0-9A-Fa-f]+)").find(cardUrl)?.groupValues?.get(1)
        val p = param("p") ?: return null
        val c = param("c") ?: return null
        return Sun(p, c)
    }

    /**
     * Checks a tap is genuine and unspent (`POST …/verify-tap`), the way a payment
     * would: an edited or replayed URL fails here, so a card id alone never gets a
     * card on screen. Doesn't spend the tap. Throws with the server's reason.
     */
    suspend fun verifyTap(
        cardId: String,
        sun: Sun,
    ): Unit =
        withContext(Dispatchers.IO) {
            val body: RequestBody =
                JSONObject().put("p", sun.p).put("c", sun.c).toString().toRequestBody(jsonMedia)
            request(
                Request.Builder()
                    .url(AuthStore.baseUrl + "/api/cards/" + enc(cardId) + "/verify-tap")
                    .post(body),
                preferServerMessage = true,
            )
        }

    suspend fun getCard(
        id: String,
        sun: Sun,
    ): CardDetail =
        withContext(Dispatchers.IO) {
            val o = request(Request.Builder().url(AuthStore.baseUrl + "/api/cards/" + enc(id)).get())
            val design = o.optJSONObject("design")
            val username = o.optStr("username")
            val pubkey = o.optStr("pubkey")
            val assigned = username != null || pubkey != null
            val disabled = o.optBoolean("disabled")
            CardDetail(
                id = o.optString("id").ifBlank { id },
                imageUrl = design?.optString("imageUrl")?.ifBlank { null },
                designName = design?.optString("description")?.ifBlank { null },
                status =
                    when {
                        !assigned -> "Not activated"
                        disabled -> "Paused"
                        else -> "Activated"
                    },
                lightningAddress = username?.let { "$it@${AuthStore.host()}" },
                kind = o.optString("kind").ifBlank { null },
                owned = assigned,
                disabled = disabled,
                sun = sun,
            )
        }

    /** The card owner's summary, or null on an older backend without the endpoint (404). */
    suspend fun getOwner(cardId: String): OwnerSummary? =
        withContext(Dispatchers.IO) {
            val o =
                try {
                    request(Request.Builder().url(AuthStore.baseUrl + "/api/cards/" + enc(cardId) + "/owner").get())
                } catch (e: ApiException) {
                    if (e.code == 404) return@withContext null
                    throw e
                }
            OwnerSummary(
                owned = o.optBoolean("owned"),
                pubkey = o.optStr("pubkey"),
                primaryAddress = o.optStr("primaryAddress"),
                lightningAddresses = o.optJSONArray("lightningAddresses").strings(),
                cards = o.optInt("cards"),
                remoteWallets = o.optInt("remoteWallets"),
                cardRecoveryEnabled = o.optBoolean("cardRecoveryEnabled"),
                cardDisabled = o.optBoolean("cardDisabled"),
                balanceSats = if (o.isNull("balanceSats")) null else o.optLong("balanceSats"),
            )
        }

    suspend fun mintActivation(cardId: String): ActivationToken =
        withContext(Dispatchers.IO) {
            val body: RequestBody = JSONObject().put("qrKind", "ONE_TIME").toString().toRequestBody(jsonMedia)
            val o =
                request(
                    Request.Builder()
                        .url(AuthStore.baseUrl + "/api/cards/" + enc(cardId) + "/activation-tokens")
                        .post(body),
                )
            val qr = o.optString("qrPayload").ifBlank { null }
                ?: throw ApiException("Activation response missing qrPayload.")
            ActivationToken(qrPayload = qr, expiresAt = o.optString("expiresAt").ifBlank { null })
        }

    /**
     * Mints a ONE_TIME recovery QR (`…/wallet/activate/<id>?tag=recover`). Needs the
     * fresh tap's SUN; errors carry the server's reason (recovery off, tap reused…).
     */
    suspend fun mintRecovery(
        cardId: String,
        sun: Sun,
    ): ActivationToken =
        withContext(Dispatchers.IO) {
            val body: RequestBody =
                JSONObject().put("p", sun.p).put("c", sun.c).toString().toRequestBody(jsonMedia)
            val o =
                request(
                    Request.Builder()
                        .url(AuthStore.baseUrl + "/api/cards/" + enc(cardId) + "/recovery-tokens")
                        .post(body),
                    preferServerMessage = true,
                )
            val qr = o.optString("qrPayload").ifBlank { null }
                ?: throw ApiException("Recovery response missing qrPayload.")
            ActivationToken(qrPayload = qr, expiresAt = o.optString("expiresAt").ifBlank { null })
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Like `optString`, but JSON `null` / missing / blank → null (not the string "null"). */
    private fun JSONObject.optStr(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).ifBlank { null } }

    /** [preferServerMessage]: show the server's `message` for any error code, not our canned 404/409 text. */
    private fun request(
        builder: Request.Builder,
        preferServerMessage: Boolean = false,
    ): JSONObject {
        val token = AuthStore.jwt ?: throw ApiException("Not authenticated.")
        val req =
            builder
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (res.code == 401) {
                AuthStore.logout()
                throw ApiException("Session expired — scan a new token.", 401)
            }
            if (!res.isSuccessful) {
                // `{ success:false, error:{ message, code } }`; older routes send a top-level `message`.
                val serverMessage =
                    json?.optJSONObject("error")?.optStr("message")
                        ?: json?.optStr("message")
                val msg =
                    serverMessage?.takeIf { preferServerMessage }
                        ?: when (res.code) {
                            404 -> "Card not registered."
                            409 -> "Another QR was just issued — try again."
                            else -> serverMessage ?: "Error (HTTP ${res.code})."
                        }
                throw ApiException(msg, res.code)
            }
            return json ?: throw ApiException("Unexpected server response.")
        }
    }
}
