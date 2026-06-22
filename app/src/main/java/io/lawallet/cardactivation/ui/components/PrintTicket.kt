package io.lawallet.cardactivation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.lawallet.cardactivation.util.cardDomain
import io.lawallet.cardactivation.ui.QrBitmap

/**
 * On-screen preview of the thermal slip that gets printed: a white "paper"
 * ticket with the LaWallet header, the QR, and a caption — mirrors the printed
 * output and is the element that animates up when printing.
 */
@Composable
fun PrintTicket(
    qrContent: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val qr = remember(qrContent) { QrBitmap.create(qrContent, 600).asImageBitmap() }
    val domain = remember(qrContent) { cardDomain(qrContent) }
    val ink = Color(0xFF0A0A0A)
    val paper = remember { TornPaperShape() }

    Surface(
        color = Color(0xFFFBFBF8),
        shape = paper, // no rounded corners; torn/cut top & bottom edges
        // No platform shadow: Android only defines elevation shadows for convex
        // outlines, and the dentated edge is concave. The bright paper on the
        // near-black screen + the slide-up animation carry the depth.
        shadowElevation = 0.dp,
        modifier = modifier, // width is set by the caller (full width − side gutters)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "LaWallet",
                color = ink,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
            Spacer(Modifier.height(12.dp))
            DashedDivider()
            Spacer(Modifier.height(18.dp))
            Image(
                bitmap = qr,
                contentDescription = "QR",
                modifier = Modifier.fillMaxWidth(0.91f).aspectRatio(1f),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                caption.uppercase(),
                color = ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            DashedDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                domain,
                color = Color(0xFF444444),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp)) // small free margin
        }
    }
}

@Composable
private fun DashedDivider() {
    Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
        drawLine(
            color = Color(0xFFCCCCCC),
            start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
            end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
        )
    }
}

/**
 * Receipt-style torn/cut paper outline: a row of small triangular teeth along
 * the top and bottom edges (peaks point outward), with straight, square sides —
 * emulating a strip of thermal paper cut off the roll.
 */
private class TornPaperShape(
    private val tooth: Dp = 12.dp, // base width of each triangle
    private val depth: Dp = 7.dp, // height of each triangle
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val tw = with(density) { tooth.toPx() }
        val th = with(density) { depth.toPx() }
        val path =
            Path().apply {
                // Top edge: teeth pointing up — peaks at y=0, valleys at y=th.
                moveTo(0f, th)
                var x = 0f
                while (x < size.width) {
                    val mid = (x + tw / 2f).coerceAtMost(size.width)
                    val end = (x + tw).coerceAtMost(size.width)
                    lineTo(mid, 0f)
                    lineTo(end, th)
                    x = end
                }
                // Straight right side down to the bottom valley line.
                lineTo(size.width, size.height - th)
                // Bottom edge: teeth pointing down — peaks at y=height.
                var xb = size.width
                while (xb > 0f) {
                    val mid = (xb - tw / 2f).coerceAtLeast(0f)
                    val end = (xb - tw).coerceAtLeast(0f)
                    lineTo(mid, size.height)
                    lineTo(end, size.height - th)
                    xb = end
                }
                // Straight left side back up to the start.
                close()
            }
        return Outline.Generic(path)
    }
}
