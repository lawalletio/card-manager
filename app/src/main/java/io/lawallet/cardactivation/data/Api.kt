package io.lawallet.cardactivation.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Card detail for display (subset of the backend `Card`). */
data class CardDetail(
    val id: String,
    val imageUrl: String?,
    val designName: String?,
    val status: String,
    val lightningAddress: String?,
    val kind: String?,
)

/** Minted ONE_TIME activation token. */
data class ActivationToken(
    val qrPayload: String,
    val expiresAt: String?,
)

class ApiException(message: String, val code: Int = 0) : Exception(message)

/**
 * Authenticated LaWallet API (`Authorization: Bearer <jwt>` over `AuthStore.baseUrl`),
 * mirroring card-installer's flow: GET `/api/cards/:id` for the card data, then
 * POST `/api/cards/:id/activation-tokens` `{qrKind:"ONE_TIME"}` to mint the QR.
 */
object Api {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val jsonMedia = "application/json".toMediaType()

    /** Extracts the server card id from a tapped lnurlw/https scan URL, or
     * accepts a bare id when entered manually. */
    fun resolveCardId(input: String): String {
        Regex("/api/cards/([^/?]+)").find(input)?.let { return it.groupValues[1] }
        return input.trim().removePrefix("lnurlw://").removePrefix("https://").trim('/')
    }

    suspend fun getCard(cardUrlOrId: String): CardDetail =
        withContext(Dispatchers.IO) {
            val id = resolveCardId(cardUrlOrId)
            val o = request(Request.Builder().url(AuthStore.baseUrl + "/api/cards/" + enc(id)).get())
            val design = o.optJSONObject("design")
            val username = o.optString("username").ifBlank { null }
            val pubkey = o.optString("pubkey").ifBlank { null }
            val assigned = username != null || pubkey != null
            CardDetail(
                id = o.optString("id").ifBlank { id },
                imageUrl = design?.optString("imageUrl")?.ifBlank { null },
                designName = design?.optString("description")?.ifBlank { null },
                status = if (assigned) "Activada" else "Sin activar",
                lightningAddress = username?.let { "$it@${AuthStore.host()}" },
                kind = o.optString("kind").ifBlank { null },
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
                ?: throw ApiException("Respuesta de activación sin qrPayload.")
            ActivationToken(qrPayload = qr, expiresAt = o.optString("expiresAt").ifBlank { null })
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun request(builder: Request.Builder): JSONObject {
        val token = AuthStore.jwt ?: throw ApiException("No autenticado.")
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
                throw ApiException("Sesión expirada — escaneá un token nuevo.", 401)
            }
            if (!res.isSuccessful) {
                val msg =
                    when (res.code) {
                        404 -> "Tarjeta no registrada."
                        409 -> "Se acaba de emitir otro QR — reintentá."
                        else -> json?.optString("message")?.ifBlank { null } ?: "Error (HTTP ${res.code})."
                    }
                throw ApiException(msg, res.code)
            }
            return json ?: throw ApiException("Respuesta inesperada del servidor.")
        }
    }
}
