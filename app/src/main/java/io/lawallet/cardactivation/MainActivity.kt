package io.lawallet.cardactivation

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import io.lawallet.cardactivation.data.AuthStore
import io.lawallet.cardactivation.pos.CardNfc
import io.lawallet.cardactivation.pos.ZcsPrinter
import io.lawallet.cardactivation.ui.HomeScreen
import io.lawallet.cardactivation.ui.LoginScreen
import io.lawallet.cardactivation.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private val cardUrls = MutableSharedFlow<String>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_PHONE_STATE),
            REQ_PHONE_STATE,
        )
        ZcsPrinter.init()

        setContent {
            AppTheme {
                val auth by AuthStore.state.collectAsState()
                if (auth.loggedIn) {
                    HomeScreen(
                        cardUrls = cardUrls,
                        nfcAvailable = nfcAdapter?.isEnabled == true,
                        onLogout = { AuthStore.logout() },
                    )
                } else {
                    LoginScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent =
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getActivity(this, 0, intent, flags)
        nfcAdapter?.enableForegroundDispatch(this, pending, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
        tag?.let { CardNfc.readUrl(it)?.let(cardUrls::tryEmit) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PHONE_STATE) ZcsPrinter.init()
    }

    private companion object {
        const val REQ_PHONE_STATE = 1
    }
}
