package io.lawallet.cardactivation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.lawallet.cardactivation.data.ActivationToken
import io.lawallet.cardactivation.data.Api
import io.lawallet.cardactivation.data.AuthStore
import io.lawallet.cardactivation.data.CardDetail
import io.lawallet.cardactivation.data.Settings
import io.lawallet.cardactivation.pos.ZcsPrinter
import io.lawallet.cardactivation.ui.components.FloatingCard
import io.lawallet.cardactivation.ui.components.LottieLoader
import io.lawallet.cardactivation.ui.components.NfcPulse
import io.lawallet.cardactivation.ui.components.PrintTicket
import io.lawallet.cardactivation.ui.theme.BrandButton
import io.lawallet.cardactivation.ui.theme.GhostButton
import io.lawallet.cardactivation.ui.theme.GradientBackground
import io.lawallet.cardactivation.ui.theme.TealBright
import io.lawallet.cardactivation.ui.theme.TextPrimary
import io.lawallet.cardactivation.ui.theme.TextSecondary
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

private const val FALLBACK_IMAGE =
    "https://veintiuno.lat/images/card-images/card2-40.png"

private sealed interface Flow {
    data object Scanning : Flow

    data object LoadingCard : Flow

    data class Detail(val card: CardDetail) : Flow

    data object Activating : Flow

    data class Qr(val card: CardDetail, val token: ActivationToken, val autoStart: Boolean = false) : Flow

    data class Failed(val message: String) : Flow

    data object BlankCard : Flow
}

@Composable
fun HomeScreen(
    cardUrls: SharedFlow<String>,
    blankCards: SharedFlow<Unit>,
    nfcAvailable: Boolean,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var flow by remember { mutableStateOf<Flow>(Flow.Scanning) }
    val flowRef = rememberUpdatedState(flow)
    var showSettings by remember { mutableStateOf(false) }

    // A tapped card (real NFC) or a manually-entered url/id → fetch + show.
    fun handleCard(input: String) {
        if (flowRef.value !is Flow.Scanning) return
        flow = Flow.LoadingCard
        scope.launch {
            flow =
                try {
                    val card = Api.getCard(input)
                    if (Settings.autoPrint.value) {
                        // Auto-print: skip the detail screen, mint + print right away.
                        flow = Flow.Activating
                        Flow.Qr(card, Api.mintActivation(card.id), autoStart = true)
                    } else {
                        Flow.Detail(card)
                    }
                } catch (e: Exception) {
                    Flow.Failed(e.message ?: "Couldn't read the card.")
                }
        }
    }

    fun activate(card: CardDetail) {
        flow = Flow.Activating
        scope.launch {
            flow =
                try {
                    Flow.Qr(card, Api.mintActivation(card.id))
                } catch (e: Exception) {
                    Flow.Failed(e.message ?: "Couldn't activate the card.")
                }
        }
    }

    LaunchedEffect(Unit) {
        cardUrls.collect { url -> handleCard(url) }
    }
    // A tapped tag with no LaWallet lnurl → prompt to install the card first.
    LaunchedEffect(Unit) {
        blankCards.collect {
            if (flowRef.value is Flow.Scanning) flow = Flow.BlankCard
        }
    }

    GradientBackground {
        if (showSettings) {
            SettingsScreen(onClose = { showSettings = false }, onLogout = onLogout)
            return@GradientBackground
        }
        when (val f = flow) {
            Flow.Scanning ->
                ScanningContent(
                    nfcAvailable = nfcAvailable,
                    onManual = { handleCard(it) },
                    onOpenSettings = { showSettings = true },
                )

            Flow.LoadingCard -> Loading("Getting card details...")

            is Flow.Detail ->
                CardDetailContent(
                    card = f.card,
                    onActivate = { activate(f.card) },
                    onCancel = { flow = Flow.Scanning },
                )

            Flow.Activating -> Loading("Generating activation QR…")

            is Flow.Qr ->
                QrPrintContent(
                    token = f.token,
                    autoStart = f.autoStart,
                    onDone = { flow = Flow.Scanning },
                    onCancel = { flow = Flow.Scanning },
                )

            is Flow.Failed ->
                FailedContent(message = f.message, onBack = { flow = Flow.Scanning })

            Flow.BlankCard ->
                BlankCardContent(onBack = { flow = Flow.Scanning })
        }
    }
}

@Composable
private fun ScanningContent(
    nfcAvailable: Boolean,
    onManual: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showManual by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(AuthStore.host(), style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, TealBright.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenSettings() }
                        .padding(12.dp),
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = TealBright,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Tap your card", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                if (nfcAvailable) "Scanning NFC…" else "NFC unavailable",
                style = MaterialTheme.typography.bodyMedium,
                color = TealBright,
            )
            Spacer(Modifier.height(40.dp))
            NfcPulse()
            Spacer(Modifier.height(40.dp))

            if (showManual) {
                OutlinedTextField(
                    value = manual,
                    onValueChange = { manual = it },
                    label = { Text("Card URL or id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                BrandButton(
                    text = "Find card",
                    onClick = { if (manual.isNotBlank()) onManual(manual) },
                    enabled = manual.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "Enter manually",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier =
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showManual = true },
                )
            }
        }

        Text(
            rememberAppVersion(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun Loading(text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LottieLoader()
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
    }
}

@Composable
private fun CardDetailContent(
    card: CardDetail,
    onActivate: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        FloatingCard(imageUrl = card.imageUrl ?: FALLBACK_IMAGE, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(48.dp))

        Text("Status", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(card.status, style = MaterialTheme.typography.titleLarge, color = TealBright)

        card.lightningAddress?.let { address ->
            Spacer(Modifier.height(18.dp))
            Text("Lightning address", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text("⚡ $address", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }

        Spacer(Modifier.weight(1f))

        BrandButton("ACTIVATE", onActivate, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        Spacer(Modifier.height(12.dp))
        GhostButton("Cancel", onCancel, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    }
}

@Composable
private fun QrPrintContent(
    token: ActivationToken,
    autoStart: Boolean,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var printing by remember { mutableStateOf(false) }
    val translationY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    // Render the paper at full width minus a 69px gutter on each side.
    val sideGutter = with(LocalDensity.current) { 69.toDp() }

    // Fire the physical print (immediately, on the printer's own IO scope) and run
    // the slide-up animation, then return to scanning.
    fun startPrint() {
        if (printing) return
        printing = true
        ZcsPrinter.printActivationNow(token.qrPayload)
        scope.launch {
            coroutineScope {
                launch {
                    translationY.animateTo(-1300f, tween(2060, easing = LinearEasing))
                }
                launch {
                    alpha.animateTo(
                        0f,
                        keyframes {
                            durationMillis = 2060
                            1f at 1439
                            0f at 2060
                        },
                    )
                }
            }
            onDone()
        }
    }

    // Warm the printer while the operator reads the ticket; if auto-print is on,
    // fire immediately so the tap alone prints the activation QR.
    LaunchedEffect(Unit) {
        ZcsPrinter.prewarm()
        if (autoStart) startPrint()
    }

    Box(modifier = Modifier.fillMaxSize().padding(vertical = 24.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Print to activate your card",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(28.dp))
            PrintTicket(
                qrContent = token.qrPayload,
                caption = "Activate your card",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sideGutter)
                        .graphicsLayer {
                            this.translationY = translationY.value
                            this.alpha = alpha.value
                        },
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandButton(
                text = if (printing) "Printing…" else "PRINT",
                icon = Icons.Filled.Print,
                enabled = !printing,
                onClick = { startPrint() },
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton("Cancel", onCancel, Modifier.fillMaxWidth(), enabled = !printing)
        }
    }
}

@Composable
private fun FailedContent(
    message: String,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        BrandButton("Back", onBack, Modifier.fillMaxWidth(0.7f))
    }
}

@Composable
private fun BlankCardContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Blank card",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "This card has no LaWallet data yet. Install it first with the " +
                "LaWallet Card Installer app — available on zapstore.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        BrandButton("Back", onBack, Modifier.fillMaxWidth(0.7f))
    }
}
