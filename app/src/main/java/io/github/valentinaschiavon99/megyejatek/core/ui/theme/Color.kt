package io.github.valentinaschiavon99.megyejatek.core.ui.theme

import androidx.compose.ui.graphics.Color

// Palette: deep ink blue, warm map paper and a paprika-gold accent. Green and red are kept
// for right and wrong answers only, so neither is used as a brand colour.
internal val Ink = Color(0xFF1B2A44)
internal val InkSoft = Color(0xFF2C4166)
internal val InkLight = Color(0xFFA9C4EC)
internal val Paper = Color(0xFFF7F1E4)
internal val PaperDeep = Color(0xFFEFE5D0)
internal val PaperDark = Color(0xFF11161F)
internal val SurfaceDark = Color(0xFF19202B)
internal val Amber = Color(0xFFE09A2D)
internal val AmberDark = Color(0xFFF2B85C)
internal val Plum = Color(0xFF7A5BC2)

/** Colours with a fixed meaning on the map. */
data class MapColors(
    val land: Color,
    val countyBorder: Color,
    val nationalBorder: Color,
    val water: Color,
    val shadow: Color,
    val highlighted: Color,
    val correct: Color,
    val wrong: Color,
    val hinted: Color,
    val label: Color,
)

internal val LightMapColors = MapColors(
    land = Color(0xFFF3E9D2),
    countyBorder = Color(0xFFB3A587),
    nationalBorder = Color(0xFF2E2A24),
    water = Color(0xFF4F95D6),
    shadow = Color(0x33452F10),
    highlighted = Color(0xFFF3BE45),
    correct = Color(0xFF3FA65A),
    wrong = Color(0xFFDB4B4B),
    hinted = Color(0xFF8A6FD6),
    label = Color(0xFF2B2924),
)

internal val DarkMapColors = MapColors(
    land = Color(0xFF2A313D),
    countyBorder = Color(0xFF59606C),
    nationalBorder = Color(0xFFE7E1D3),
    water = Color(0xFF4E8BC4),
    shadow = Color(0x66000000),
    highlighted = Color(0xFFD9A433),
    correct = Color(0xFF4FB86A),
    wrong = Color(0xFFE56060),
    hinted = Color(0xFF9F89E8),
    label = Color(0xFFEDE8DC),
)
