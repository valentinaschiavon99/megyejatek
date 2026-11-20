@file:OptIn(ExperimentalTextApi::class)

package io.github.valentinaschiavon99.megyejatek.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.valentinaschiavon99.megyejatek.R

/*
 * Two bundled variable fonts (SIL Open Font License, see licenses/):
 * Fraunces, a characterful serif for titles and prompts, and Manrope for everything else.
 * Both cover all Hungarian letters (ő, ű, …).
 */

private fun fraunces(weight: Int) = Font(
    R.font.fraunces,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.Setting("opsz", 72f), // display optical size
        FontVariation.Setting("SOFT", 50f),
    ),
)

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Display = FontFamily(fraunces(400), fraunces(600), fraunces(700), fraunces(800))
val Body = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private val base = Typography()

private fun TextStyle.display(weight: FontWeight = FontWeight.SemiBold) = copy(fontFamily = Display, fontWeight = weight)
private fun TextStyle.body(weight: FontWeight? = null) = copy(fontFamily = Body, fontWeight = weight ?: fontWeight)

internal val AppTypography = Typography(
    displayLarge = base.displayLarge.display(FontWeight.Bold),
    displayMedium = base.displayMedium.display(FontWeight.Bold),
    displaySmall = base.displaySmall.display(FontWeight.Bold),
    headlineLarge = base.headlineLarge.display(),
    headlineMedium = base.headlineMedium.display(),
    headlineSmall = base.headlineSmall.display(),
    titleLarge = base.titleLarge.display().copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleMedium = base.titleMedium.body(FontWeight.Bold),
    titleSmall = base.titleSmall.body(FontWeight.Bold),
    bodyLarge = base.bodyLarge.body(),
    bodyMedium = base.bodyMedium.body(),
    bodySmall = base.bodySmall.body(),
    // Tabular figures so the timer and the score don't jitter while they change.
    labelLarge = base.labelLarge.body(FontWeight.Bold).copy(fontFeatureSettings = "tnum"),
    labelMedium = base.labelMedium.body(FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"),
    labelSmall = base.labelSmall.body(FontWeight.SemiBold),
)
