package io.lawallet.cardactivation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.lawallet.cardactivation.R

// ── lawallet.io palette ──────────────────────────────────────────────────────
val Teal = Color(0xFF00836D)
val TealMid = Color(0xFF00A085)
val TealBright = Color(0xFF00C7A0)
val NearBlack = Color(0xFF0A0A0A)
val Surface1 = Color(0xFF171717)
val Surface2 = Color(0xFF262626)
val TextPrimary = Color(0xFFF5F5F5)
val TextSecondary = Color(0xFFA3A3A3)

private val LaWalletColors =
    darkColorScheme(
        primary = TealBright,
        onPrimary = NearBlack,
        secondary = TealBright,
        onSecondary = NearBlack,
        tertiary = TealMid,
        background = NearBlack,
        onBackground = TextPrimary,
        surface = Surface1,
        onSurface = TextPrimary,
        surfaceVariant = Surface2,
        onSurfaceVariant = TextSecondary,
        outline = Surface2,
        error = Color(0xFFEF4444),
    )

// Inter (lawallet.io's typeface) — variable font, weight axis per FontWeight.
val Inter =
    FontFamily(
        Font(R.font.inter_variable, FontWeight.Light),
        Font(R.font.inter_variable, FontWeight.Normal),
        Font(R.font.inter_variable, FontWeight.Medium),
        Font(R.font.inter_variable, FontWeight.SemiBold),
        Font(R.font.inter_variable, FontWeight.Bold),
    )

private fun interTypography(): Typography {
    val b = Typography()
    return b.copy(
        displayLarge = b.displayLarge.copy(fontFamily = Inter),
        displayMedium = b.displayMedium.copy(fontFamily = Inter),
        displaySmall = b.displaySmall.copy(fontFamily = Inter),
        headlineLarge = b.headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
        headlineMedium = b.headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Bold),
        headlineSmall = b.headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        titleLarge = b.titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
        titleMedium = b.titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        titleSmall = b.titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        bodyLarge = b.bodyLarge.copy(fontFamily = Inter),
        bodyMedium = b.bodyMedium.copy(fontFamily = Inter),
        bodySmall = b.bodySmall.copy(fontFamily = Inter),
        labelLarge = b.labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Medium),
        labelMedium = b.labelMedium.copy(fontFamily = Inter),
        labelSmall = b.labelSmall.copy(fontFamily = Inter),
    )
}

private val LaWalletShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(18.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LaWalletColors,
        typography = interTypography(),
        shapes = LaWalletShapes,
        content = content,
    )
}
