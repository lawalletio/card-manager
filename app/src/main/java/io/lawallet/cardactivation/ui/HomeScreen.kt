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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import io.lawallet.cardactivation.data.OwnerSummary
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
import java.util.Locale

private const val FALLBACK_IMAGE =
    "https://veintiuno.lat/images/card-images/card2-40.png"

/** What a minted QR does — drives the screen title, ticket caption and printed subtitle. */
private enum class QrPurpose(val title: String, val caption: String) {
    Activate("Print to activate your card", "Activate your card"),
    Recover("Print to recover your account", "Recover your account"),
}

/** Owner info for an owned card's detail screen. */
private sealed interface Owner {
    data class Known(val summary: OwnerSummary) : Owner

    /** Older backend without `/owner` (404): no balance, no RECOVER. */
    data object Unsupported : Owner

    /** The owner fetch failed: "Balance unavailable", RECOVER disabled. */
    data object Unavailable : Owner
}

private sealed interface Flow {
    data object Scanning : Flow

    data object LoadingCard : Flow

    /** [owner] is null for unowned cards. */
    data class Detail(val card: CardDetail, val owner: Owner? = null) : Flow

    data object Activating : Flow

    data object Recovering : Flow

    data class Qr(
        val card: CardDetail,
        val token: ActivationToken,
        val purpose: QrPurpose,
        val autoStart: Boolean = false,
    ) : Flow

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

    // A tapped card (real NFC) or a pasted tap URL → verify the tap, then fetch + show.
    // Like a payment, only a genuine, unspent SUN proof gets a card on screen.
    fun handleCard(input: String) {
        if (flowRef.value !is Flow.Scanning) return
        val id = Api.resolveCardId(input)
        val sun = Api.parseSun(input)
        if (id == null || sun == null) {
            flow = Flow.Failed("Not a card tap. Tap the card, or paste its full lnurlw:// URL.")
            return
        }
        flow = Flow.LoadingCard
        scope.launch {
            flow =
                try {
                    Api.verifyTap(id, sun)
                    val card = Api.getCard(id, sun)
                    if (card.owned) {
                        // Owned cards always stop on detail (owner + RECOVER), even with auto-print.
                        Flow.Detail(card, loadOwner(card.id))
                    } else if (Settings.autoPrint.value) {
                        // Auto-print: skip the detail screen, mint + print right away.
                        flow = Flow.Activating
                        Flow.Qr(card, Api.mintActivation(card.id), QrPurpose.Activate, autoStart = true)
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
                    Flow.Qr(card, Api.mintActivation(card.id), QrPurpose.Activate)
                } catch (e: Exception) {
                    Flow.Failed(e.message ?: "Couldn't activate the card.")
                }
        }
    }

    fun recover(card: CardDetail) {
        val sun = card.sun
        flow = Flow.Recovering
        scope.launch {
            flow =
                try {
                    Flow.Qr(card, Api.mintRecovery(card.id, sun), QrPurpose.Recover)
                } catch (e: Exception) {
                    Flow.Failed(e.message ?: "Couldn't start the recovery.")
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
                    owner = f.owner,
                    onActivate = { activate(f.card) },
                    onRecover = { recover(f.card) },
                    onCancel = { flow = Flow.Scanning },
                )

            Flow.Activating -> Loading("Generating activation QR…")

            Flow.Recovering -> Loading("Generating recovery QR…")

            is Flow.Qr ->
                QrPrintContent(
                    token = f.token,
                    purpose = f.purpose,
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
                    label = { Text("Card tap URL (lnurlw://…?p=…&c=…)") },
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

/** The owner summary, [Owner.Unsupported] on an older backend, or [Owner.Unavailable] on failure. */
private suspend fun loadOwner(cardId: String): Owner =
    try {
        Api.getOwner(cardId)?.let { Owner.Known(it) } ?: Owner.Unsupported
    } catch (e: Exception) {
        Owner.Unavailable
    }

private fun formatSats(sats: Long): String = String.format(Locale.US, "%,d %s", sats, if (sats == 1L) "sat" else "sats")

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
    owner: Owner?,
    onActivate: () -> Unit,
    onRecover: () -> Unit,
    onCancel: () -> Unit,
) {
    // RECOVER shows for owned cards on a backend that has `/owner`; when it can't run,
    // a short reason replaces the action.
    val canOfferRecover = owner != null && owner !is Owner.Unsupported
    val recoverBlocked: String? =
        when {
            owner is Owner.Known && !owner.summary.cardRecoveryEnabled -> "The owner turned off card recovery"
            card.disabled || (owner is Owner.Known && owner.summary.cardDisabled) -> "Card is paused by its owner"
            owner !is Owner.Known -> "Owner info unavailable"
            else -> null
        }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(if (owner != null) 8.dp else 24.dp))
        FloatingCard(imageUrl = card.imageUrl ?: FALLBACK_IMAGE, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(if (owner != null) 32.dp else 48.dp))

        // Scrolls only if the owner block outgrows the space above the buttons.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Status", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text(card.status, style = MaterialTheme.typography.titleLarge, color = TealBright)

            if (owner is Owner.Known) {
                OwnerBlock(owner.summary)
            } else {
                card.lightningAddress?.let { address ->
                    Spacer(Modifier.height(18.dp))
                    Text("Lightning address", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text("⚡ $address", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }
                if (owner is Owner.Unavailable) {
                    Spacer(Modifier.height(18.dp))
                    Text("Balance unavailable", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val buttonModifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        if (canOfferRecover) {
            recoverBlocked?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            BrandButton("RECOVER", onRecover, buttonModifier, enabled = recoverBlocked == null)
            Spacer(Modifier.height(12.dp))
            Row(buttonModifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GhostButton("ACTIVATE", onActivate, Modifier.weight(1f))
                GhostButton("Cancel", onCancel, Modifier.weight(1f))
            }
        } else {
            BrandButton("ACTIVATE", onActivate, buttonModifier)
            Spacer(Modifier.height(12.dp))
            GhostButton("Cancel", onCancel, buttonModifier)
        }
    }
}

/** The owner's lightning addresses (primary first, from the server) and wallet balance. */
@Composable
private fun OwnerBlock(owner: OwnerSummary) {
    val primary = owner.primaryAddress ?: owner.lightningAddresses.firstOrNull()
    val others = owner.lightningAddresses.filter { it != primary }

    Spacer(Modifier.height(18.dp))
    Text("Owner", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(Modifier.height(2.dp))
    Text(
        primary?.let { "⚡ $it" } ?: "No lightning address",
        style = MaterialTheme.typography.titleMedium,
        color = if (primary != null) TextPrimary else TextSecondary,
        textAlign = TextAlign.Center,
    )
    others.forEach { address ->
        Text(address, style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = TextAlign.Center)
    }

    Spacer(Modifier.height(18.dp))
    Text("Balance", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Spacer(Modifier.height(2.dp))
    val balance = owner.balanceSats
    if (balance != null) {
        Text(formatSats(balance), style = MaterialTheme.typography.titleLarge, color = TealBright)
    } else {
        Text("Balance unavailable", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun QrPrintContent(
    token: ActivationToken,
    purpose: QrPurpose,
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
        ZcsPrinter.printActivationNow(token.qrPayload, purpose.caption)
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
                purpose.title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(28.dp))
            PrintTicket(
                qrContent = token.qrPayload,
                caption = purpose.caption,
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
