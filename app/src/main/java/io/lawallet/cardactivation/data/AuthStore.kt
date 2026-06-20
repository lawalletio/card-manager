package io.lawallet.cardactivation.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the device-token JWT + the instance base URL, persisted across launches.
 * Mirrors card-installer's LaWallet provider: the QR is a bare JWT whose
 * optional `apiUrl` claim becomes the base URL (else a default), and every API
 * call sends `Authorization: Bearer <jwt>`.
 */
object AuthStore {
    private const val PREFS = "lawallet_auth"
    private const val KEY_JWT = "device_token"
    private const val KEY_BASE = "base_url"
    private const val DEFAULT_BASE = "https://beta.lawallet.io"

    private lateinit var prefs: SharedPreferences

    data class State(val loggedIn: Boolean, val baseUrl: String)

    private val _state = MutableStateFlow(State(false, DEFAULT_BASE))
    val state: StateFlow<State> = _state

    @Volatile
    var jwt: String? = null
        private set

    @Volatile
    var baseUrl: String = DEFAULT_BASE
        private set

    @Volatile
    var claims: JwtClaims? = null
        private set

    sealed interface LoginResult {
        data object Ok : LoginResult

        data class Fail(val reason: String) : LoginResult
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        baseUrl = prefs.getString(KEY_BASE, null) ?: DEFAULT_BASE
        val stored = prefs.getString(KEY_JWT, null)
        if (stored != null) {
            val decoded = Jwt.decode(stored)
            if (decoded != null && !Jwt.isExpired(decoded)) {
                jwt = stored
                claims = decoded
            } else {
                prefs.edit().remove(KEY_JWT).apply()
            }
        }
        emit()
    }

    fun login(token: String): LoginResult {
        if (!Jwt.looksLikeJwt(token)) return LoginResult.Fail("invalid")
        val decoded = Jwt.decode(token) ?: return LoginResult.Fail("invalid")
        if (Jwt.isExpired(decoded)) return LoginResult.Fail("expired")

        decoded.apiUrl?.let {
            baseUrl = normalizeBaseUrl(it)
            prefs.edit().putString(KEY_BASE, baseUrl).apply()
        }
        jwt = token
        claims = decoded
        prefs.edit().putString(KEY_JWT, token).apply()
        emit()
        return LoginResult.Ok
    }

    fun logout() {
        jwt = null
        claims = null
        prefs.edit().remove(KEY_JWT).apply()
        emit()
    }

    val isLoggedIn: Boolean
        get() = jwt != null && claims?.let { !Jwt.isExpired(it) } == true

    /** Instance host for display / lightning-address domain. */
    fun host(): String =
        runCatching { java.net.URI(baseUrl).host }.getOrNull() ?: baseUrl.removePrefix("https://")

    private fun emit() {
        _state.value = State(isLoggedIn, baseUrl)
    }
}
