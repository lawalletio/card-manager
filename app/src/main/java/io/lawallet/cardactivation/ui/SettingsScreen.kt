package io.lawallet.cardactivation.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lawallet.cardactivation.data.AuthStore
import io.lawallet.cardactivation.data.Settings
import io.lawallet.cardactivation.ui.theme.GhostButton
import io.lawallet.cardactivation.ui.theme.TealBright
import io.lawallet.cardactivation.ui.theme.TextPrimary
import io.lawallet.cardactivation.ui.theme.TextSecondary

/** Settings overlay: session info, JWT expiration, auto-print toggle, log out, version. */
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onLogout: () -> Unit,
) {
    val autoPrint by Settings.autoPrint.collectAsState()
    val claims = AuthStore.claims
    val version = rememberAppVersion()
    val expText =
        remember(claims) {
            claims?.exp?.let {
                java.text.SimpleDateFormat("MMM d, yyyy · HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(it * 1000))
            } ?: "—"
        }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onClose() }
                        .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("Session") {
            Field("Instance", AuthStore.host())
            claims?.pubkey?.let { Field("Pubkey", shortenMiddle(it)) }
            if (!claims?.scopes.isNullOrEmpty()) Field("Scopes", claims!!.scopes.joinToString(", "))
            claims?.kind?.let { Field("Kind", it) }
            Field("Token expires", expText)
        }

        Spacer(Modifier.height(20.dp))

        Section("Preferences") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-print on tap", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Mint and print the activation QR automatically when a card is tapped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = autoPrint,
                    onCheckedChange = { Settings.setAutoPrint(it) },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TealBright,
                            checkedBorderColor = TealBright,
                            uncheckedThumbColor = Color(0xFF9A9A9A),
                            uncheckedTrackColor = Color(0xFF2A2A2A),
                            uncheckedBorderColor = Color(0xFF3A3A3A),
                        ),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        GhostButton("Log out", onLogout, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(
            version,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = TealBright,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161616))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun shortenMiddle(s: String): String =
    if (s.length > 24) s.take(12) + "…" + s.takeLast(8) else s

/** "v1.0.0 (1)" from the package manager — works regardless of BuildConfig. */
@Composable
fun rememberAppVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pi.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pi.versionCode.toLong()
                }
            "v${pi.versionName} ($code)"
        }.getOrDefault("")
    }
}
