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

    // Emitted when a tag is tapped but carries no LaWallet card lnurl (blank card).
    private val blankCards = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No edge-to-edge: content sits below the (green) status bar instead of
        // drawing behind it, so the bar stays a visible, non-overlaid margin.
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
                        blankCards = blankCards,
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
        tag?.let {
            val url = CardNfc.readUrl(it)
            // A LaWallet card's lnurl points at /api/cards/<id>/scan. Anything else
            // (no NDEF, or a non-LaWallet tag) is treated as a blank card.
            if (url != null && url.contains("/api/cards/")) {
                cardUrls.tryEmit(url)
            } else {
                blankCards.tryEmit(Unit)
            }
        }
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
