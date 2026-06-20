package io.lawallet.cardactivation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Centered NFC glyph with 3 concentric "radar" rings that expand outward and
 * fade, plus a gentle scale pulse on the glyph. Signals "waiting for a tap".
 */
@Composable
fun NfcPulse(modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "nfcPulse")

    val rippleCount = 3
    val rippleDurationMs = 2400

    val phases =
        (0 until rippleCount).map { i ->
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = rippleDurationMs, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset =
                            StartOffset(offsetMillis = (rippleDurationMs / rippleCount) * i),
                    ),
                label = "ripple$i",
            )
        }

    val glyphScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "glyphScale",
    )

    Box(
        modifier =
            modifier
                .size(220.dp)
                .drawBehind {
                    val maxRadius = size.minDimension / 2f
                    val baseRadius = maxRadius * 0.28f
                    phases.forEach { phaseState ->
                        val progress = phaseState.value
                        val radius = baseRadius + (maxRadius - baseRadius) * progress
                        val alpha = (1f - progress).coerceIn(0f, 1f) * 0.55f
                        drawCircle(
                            color = ringColor,
                            radius = radius,
                            center = center,
                            alpha = alpha,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Nfc,
            contentDescription = "NFC",
            tint = ringColor,
            modifier =
                Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = glyphScale
                        scaleY = glyphScale
                    },
        )
    }
}
