package io.lawallet.cardactivation.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Local app preferences, persisted across launches. */
object Settings {
    private const val PREFS = "lawallet_settings"
    private const val KEY_AUTOPRINT = "auto_print"

    private lateinit var prefs: SharedPreferences

    private val _autoPrint = MutableStateFlow(false)

    /** When true, tapping a card mints + prints the activation QR automatically. */
    val autoPrint: StateFlow<Boolean> = _autoPrint

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _autoPrint.value = prefs.getBoolean(KEY_AUTOPRINT, false)
    }

    fun setAutoPrint(value: Boolean) {
        _autoPrint.value = value
        prefs.edit().putBoolean(KEY_AUTOPRINT, value).apply()
    }
}
