package io.lawallet.cardactivation.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Brand teal gradient (≈135° #00836D → #00A085 → #00C7A0). */
val TealGradient =
    Brush.linearGradient(colors = listOf(Teal, TealMid, TealBright))

/** Near-black canvas with a soft teal glow at the top — the lawallet.io feel. */
@Composable
fun GradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(NearBlack)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Teal.copy(alpha = 0.22f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.16f),
                            radius = size.width * 0.95f,
                        ),
                    )
                },
    ) {
        content()
    }
}

/** Primary CTA — teal gradient fill with a teal glow. */
@Composable
fun BrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier =
            modifier
                .height(60.dp)
                .then(
                    if (enabled) {
                        Modifier.shadow(20.dp, shape, spotColor = Teal, ambientColor = Teal)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(if (enabled) TealGradient else SolidColor(Surface2))
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val tint = if (enabled) TextPrimary else TextSecondary
            icon?.let { Icon(it, contentDescription = null, tint = tint) }
            Text(
                text,
                color = tint,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Secondary action — ghost/outline. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier =
            modifier
                .height(54.dp)
                .clip(shape)
                .border(1.dp, Surface2, shape)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextSecondary, style = MaterialTheme.typography.titleMedium)
    }
}
