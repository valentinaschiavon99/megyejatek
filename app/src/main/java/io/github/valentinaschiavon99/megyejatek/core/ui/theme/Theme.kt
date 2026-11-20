package io.github.valentinaschiavon99.megyejatek.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Color(0xFFDCE5F5),
    onPrimaryContainer = Ink,
    secondary = Amber,
    onSecondary = Color(0xFF2B1A00),
    secondaryContainer = Color(0xFFFBE3BC),
    onSecondaryContainer = Color(0xFF3D2700),
    tertiary = Plum,
    background = Paper,
    onBackground = Color(0xFF1E1B16),
    surface = Paper,
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = PaperDeep,
    onSurfaceVariant = Color(0xFF5C5547),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF6EC),
    surfaceContainer = Color(0xFFF2EBDC),
    surfaceContainerHigh = Color(0xFFECE3D1),
    surfaceContainerHighest = Color(0xFFE5DAC4),
    outline = Color(0xFF9A907E),
    outlineVariant = Color(0xFFD9CFBC),
)

private val DarkScheme = darkColorScheme(
    primary = InkLight,
    onPrimary = Color(0xFF0E1A2E),
    primaryContainer = InkSoft,
    onPrimaryContainer = Color(0xFFDCE5F5),
    secondary = AmberDark,
    onSecondary = Color(0xFF2B1A00),
    secondaryContainer = Color(0xFF5A3D0D),
    onSecondaryContainer = Color(0xFFFBE3BC),
    tertiary = Color(0xFFBBA6F0),
    background = PaperDark,
    surface = PaperDark,
    surfaceVariant = SurfaceDark,
    surfaceContainerLowest = Color(0xFF0C1017),
    surfaceContainerLow = Color(0xFF151B25),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = Color(0xFF212936),
    surfaceContainerHighest = Color(0xFF2A3341),
    outlineVariant = Color(0xFF3A4352),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val LocalMapColors = staticCompositionLocalOf { LightMapColors }

/**
 * App theme. Dynamic colour is deliberately off: the map relies on fixed meanings
 * (green = correct, red = wrong) that a wallpaper-derived palette could blur.
 */
@Composable
fun MegyejatekTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMapColors provides if (darkTheme) DarkMapColors else LightMapColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
