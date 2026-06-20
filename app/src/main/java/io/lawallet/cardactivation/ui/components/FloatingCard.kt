package io.lawallet.cardactivation.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A large credit-card-shaped remote image (Coil) that floats and rotates in real
 * 3D perspective: rotationY sweeps ~ -16°..16° and rotationX ~ -8°..8° on
 * out-of-phase periods, with a meaningful cameraDistance for genuine
 * foreshortening, a vertical bob, a soft drop shadow, and a moving sheen.
 */
@Composable
fun FloatingCard(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    val aspect = 1.585f
    val cornerRadius = 18.dp

    val density = LocalDensity.current
    val transition = rememberInfiniteTransition(label = "floatingCard")

    val rotY by transition.animateFloat(
        initialValue = -16f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(animation = tween(4200), repeatMode = RepeatMode.Reverse),
        label = "rotY",
    )
    val rotX by transition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(animation = tween(3100), repeatMode = RepeatMode.Reverse),
        label = "rotX",
    )
    val bobDp by transition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(animation = tween(2600), repeatMode = RepeatMode.Reverse),
        label = "bob",
    )

    AsyncImage(
        model = imageUrl,
        contentDescription = "Card",
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .aspectRatio(aspect)
                .graphicsLayer {
                    rotationX = rotX
                    rotationY = rotY
                    translationY = with(density) { bobDp.dp.toPx() }
                    cameraDistance = 12f * density.density
                }
                .shadow(elevation = 28.dp, shape = RoundedCornerShape(cornerRadius), clip = false)
                .clip(RoundedCornerShape(cornerRadius))
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val h = size.height
                    val shiftX = (rotY / 16f) * w * 0.5f
                    val shiftY = (rotX / 8f) * h * 0.35f
                    val sheen =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.16f),
                                    Color.White.copy(alpha = 0.30f),
                                    Color.White.copy(alpha = 0.16f),
                                    Color.Transparent,
                                ),
                            start = Offset(x = -w * 0.3f + shiftX, y = -h * 0.3f + shiftY),
                            end = Offset(x = w * 1.3f + shiftX, y = h * 1.3f + shiftY),
                        )
                    drawRect(brush = sheen)
                },
    )
}
