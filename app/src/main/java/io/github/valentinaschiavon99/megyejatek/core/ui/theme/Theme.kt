package io.github.valentinaschiavon99.megyejatek.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = Amber,
    background = Paper,
    surface = Paper,
)

private val DarkScheme = darkColorScheme(
    primary = InkLight,
    onPrimary = PaperDark,
    secondary = AmberDark,
    background = PaperDark,
    surface = PaperDark,
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
            content = content,
        )
    }
}

object MegyejatekThemeExt {
    val mapColors: MapColors
        @Composable @ReadOnlyComposable get() = LocalMapColors.current
}
