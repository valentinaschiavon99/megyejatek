package io.github.valentinaschiavon99.megyejatek.core.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette: ink blue + warm paper + amber accent. Green and red are reserved
// for right/wrong answers, so neither is used as a brand colour.
internal val Ink = Color(0xFF1F3A5F)
internal val InkLight = Color(0xFF9EC1EE)
internal val Paper = Color(0xFFF8F6F1)
internal val PaperDark = Color(0xFF14171C)
internal val Amber = Color(0xFFE8A33D)
internal val AmberDark = Color(0xFFF3BE6A)

/** Colours with a fixed meaning on the map. */
data class MapColors(
    val land: Color,
    val countyBorder: Color,
    val nationalBorder: Color,
    val water: Color,
    val highlighted: Color,
    val correct: Color,
    val wrong: Color,
    val hinted: Color,
    val label: Color,
)

internal val LightMapColors = MapColors(
    land = Color(0xFFEDE6D6),
    countyBorder = Color(0xFF8C8577),
    nationalBorder = Color(0xFF3B3833),
    water = Color(0xFF5B9BD5),
    highlighted = Color(0xFFF2C14E),
    correct = Color(0xFF3FA34D),
    wrong = Color(0xFFD64545),
    hinted = Color(0xFF7E6BC4),
    label = Color(0xFF2B2924),
)

internal val DarkMapColors = MapColors(
    land = Color(0xFF2A2D33),
    countyBorder = Color(0xFF6D6A63),
    nationalBorder = Color(0xFFD9D4C7),
    water = Color(0xFF4F86B8),
    highlighted = Color(0xFFC99A2E),
    correct = Color(0xFF4CAF5C),
    wrong = Color(0xFFE05A5A),
    hinted = Color(0xFF9C8BE0),
    label = Color(0xFFEDE8DC),
)
