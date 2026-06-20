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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
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

    data class Qr(val card: CardDetail, val token: ActivationToken) : Flow

    data class Failed(val message: String) : Flow
}

@Composable
fun HomeScreen(
    cardUrls: SharedFlow<String>,
    nfcAvailable: Boolean,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var flow by remember { mutableStateOf<Flow>(Flow.Scanning) }
    val flowRef = rememberUpdatedState(flow)

    // A tapped card (real NFC) or a manually-entered url/id → fetch + show.
    fun handleCard(input: String) {
        if (flowRef.value !is Flow.Scanning) return
        flow = Flow.LoadingCard
        scope.launch {
            flow =
                try {
                    Flow.Detail(Api.getCard(input))
                } catch (e: Exception) {
                    Flow.Failed(e.message ?: "No se pudo leer la tarjeta.")
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
                    Flow.Failed(e.message ?: "No se pudo activar la tarjeta.")
                }
        }
    }

    LaunchedEffect(Unit) {
        cardUrls.collect { url -> handleCard(url) }
    }

    GradientBackground {
        when (val f = flow) {
            Flow.Scanning ->
                ScanningContent(
                    nfcAvailable = nfcAvailable,
                    onManual = { handleCard(it) },
                    onLogout = onLogout,
                )

            Flow.LoadingCard -> Loading("Leyendo tarjeta…")

            is Flow.Detail ->
                CardDetailContent(
                    card = f.card,
                    onActivate = { activate(f.card) },
                    onCancel = { flow = Flow.Scanning },
                )

            Flow.Activating -> Loading("Generando QR de activación…")

            is Flow.Qr ->
                QrPrintContent(
                    token = f.token,
                    onDone = { flow = Flow.Scanning },
                    onCancel = { flow = Flow.Scanning },
                )

            is Flow.Failed ->
                FailedContent(message = f.message, onBack = { flow = Flow.Scanning })
        }
    }
}

@Composable
private fun ScanningContent(
    nfcAvailable: Boolean,
    onManual: (String) -> Unit,
    onLogout: () -> Unit,
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
            Text(
                "Salir",
                style = MaterialTheme.typography.titleMedium,
                color = TealBright,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.5.dp, TealBright.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onLogout() }
                        .padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Acercá tu tarjeta", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                if (nfcAvailable) "Escaneando NFC…" else "NFC no disponible",
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
                    label = { Text("URL o id de tarjeta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                BrandButton(
                    text = "Buscar tarjeta",
                    onClick = { if (manual.isNotBlank()) onManual(manual) },
                    enabled = manual.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "Ingresar manualmente",
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

        Text("Estado", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(card.status, style = MaterialTheme.typography.titleLarge, color = TealBright)

        card.lightningAddress?.let { address ->
            Spacer(Modifier.height(18.dp))
            Text("Lightning address", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(2.dp))
            Text("⚡ $address", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }

        Spacer(Modifier.weight(1f))

        BrandButton("ACTIVAR", onActivate, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        Spacer(Modifier.height(12.dp))
        GhostButton("Cancelar", onCancel, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    }
}

@Composable
private fun QrPrintContent(
    token: ActivationToken,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var printing by remember { mutableStateOf(false) }
    val translationY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    // Render the paper at full width minus a 60px gutter on each side.
    val sideGutter = with(LocalDensity.current) { 60.toDp() }

    // Warm the printer while the operator reads the ticket, so the first tap prints
    // without any init latency.
    LaunchedEffect(Unit) { ZcsPrinter.prewarm() }

    Box(modifier = Modifier.fillMaxSize().padding(vertical = 24.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Imprimí para activar tu tarjeta",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(28.dp))
            PrintTicket(
                qrContent = token.qrPayload,
                caption = "Activá tu tarjeta",
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
                text = if (printing) "Imprimiendo…" else "IMPRIMIR",
                icon = Icons.Filled.Print,
                enabled = !printing,
                onClick = {
                    if (printing) return@BrandButton
                    printing = true
                    // Print right away on the printer's own IO scope — it starts
                    // the instant the button is tapped and isn't tied to (or
                    // cancelled by) the slide-up animation below.
                    ZcsPrinter.printActivationNow(token.qrPayload)
                    scope.launch {
                        coroutineScope {
                            launch {
                                translationY.animateTo(-1300f, tween(2696, easing = LinearEasing))
                            }
                            launch {
                                alpha.animateTo(
                                    0f,
                                    keyframes {
                                        durationMillis = 2696
                                        1f at 1883
                                        0f at 2696
                                    },
                                )
                            }
                        }
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton("Cancelar", onCancel, Modifier.fillMaxWidth(), enabled = !printing)
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
        BrandButton("Volver", onBack, Modifier.fillMaxWidth(0.7f))
    }
}
