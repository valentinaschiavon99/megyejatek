package io.github.valentinaschiavon99.megyejatek.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val base = Typography()

/** Default Material type scale; label text uses tabular figures so the timer and score don't jitter. */
internal val AppTypography = base.copy(
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontFeatureSettings = "tnum"),
)
