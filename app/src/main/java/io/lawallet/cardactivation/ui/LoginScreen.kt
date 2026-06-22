package io.lawallet.cardactivation.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.lawallet.cardactivation.R
import io.lawallet.cardactivation.data.AuthStore
import io.lawallet.cardactivation.ui.theme.BrandButton
import io.lawallet.cardactivation.ui.theme.GhostButton
import io.lawallet.cardactivation.ui.theme.GradientBackground
import io.lawallet.cardactivation.ui.theme.TextPrimary
import io.lawallet.cardactivation.ui.theme.TextSecondary

@Composable
fun LoginScreen() {
    var showManual by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(token: String) {
        error =
            when (val r = AuthStore.login(token.trim())) {
                AuthStore.LoginResult.Ok -> null // state flips → app shows the flow
                is AuthStore.LoginResult.Fail ->
                    when (r.reason) {
                        "expired" -> "That token expired — ask for a new one."
                        else -> "That QR isn't a valid device token."
                    }
            }
    }

    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { submit(it) }
        }

    GradientBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.lawallet_logo),
                contentDescription = "LaWallet",
                modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1044.89f / 187.5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Card activation",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Ask your admin for a device token (Settings → Device tokens → Generate) and scan the QR.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))

            BrandButton(
                text = "SCAN TOKEN",
                icon = Icons.Filled.QrCodeScanner,
                onClick = {
                    error = null
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setBeepEnabled(false)
                            setOrientationLocked(false) // portrait is forced by the activity
                            setCaptureActivity(QrCaptureActivity::class.java)
                            setPrompt("Scan the device token")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            if (showManual) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("Device token (JWT)") },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                BrandButton(
                    text = "Use token",
                    onClick = { submit(manual) },
                    enabled = manual.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                GhostButton(
                    text = "Paste token manually",
                    onClick = { showManual = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}
