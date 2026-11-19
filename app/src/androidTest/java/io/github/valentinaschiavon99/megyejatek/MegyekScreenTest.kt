package io.github.valentinaschiavon99.megyejatek

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapAssets
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapDataParser
import io.github.valentinaschiavon99.megyejatek.core.geo.Projection
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MegyejatekTheme
import io.github.valentinaschiavon99.megyejatek.game.engine.Question
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundState
import io.github.valentinaschiavon99.megyejatek.game.ui.GameUiState
import io.github.valentinaschiavon99.megyejatek.game.ui.MegyekContent
import io.github.valentinaschiavon99.megyejatek.game.ui.Phase
import io.github.valentinaschiavon99.megyejatek.map.HungaryMapDefaults
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MegyekScreenTest {
    @get:Rule val compose = createComposeRule()

    private val data: MapData by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        fun read(name: String) = assets.open("map/$name").bufferedReader().use { it.readText() }
        MapDataParser.parse(
            MapAssets(read("counties.geojson"), read("outline.geojson"), read("water.geojson"),
                read("counties.json"), read("cities.json")),
        )
    }

    @Test
    fun promptHeaderAndTapOnTheAskedCounty() {
        val baranya = data.countyById.getValue("HU-BA")
        val round = RoundState(listOf(Question("HU-BA", baranya.name, "HU-BA")))
        var tapped: String? = null
        var hints = 0
        var gaveUp = 0

        compose.setContent {
            MegyejatekTheme {
                MegyekContent(
                    state = GameUiState(data = data, phase = Phase.Playing, round = round, multiplier = 1),
                    blindMap = false,
                    onBlindMapChange = {},
                    onNewGame = {},
                    onCountyTap = { tapped = it },
                    onHint = { hints++ },
                    onGiveUp = { gaveUp++ },
                    onDialogStart = {},
                    onDialogDismiss = {},
                    onResultNewGame = {},
                    onResultClose = {},
                )
            }
        }

        compose.onNodeWithTag("prompt").assertTextEquals("Baranya megye")
        compose.onNodeWithTag("multiplier").assertTextEquals("×1 pont")
        compose.onNodeWithTag("timer").assertTextEquals("0:00")

        compose.onNodeWithText("Súgás").performClick()
        compose.onNodeWithText("Feladom").performClick()
        assertEquals(1, hints)
        assertEquals(1, gaveUp)

        // Tap Baranya's label point, projected exactly like HungaryMap does.
        val map = compose.onNodeWithTag("hungary-map")
        val size = map.fetchSemanticsNode().size
        val pad = with(compose.density) { HungaryMapDefaults.ContentPadding.toPx() }
        val projection = Projection(data.bounds, size.width.toFloat(), size.height.toFloat(), pad)
        map.performTouchInput {
            click(Offset(projection.x(baranya.info.label[0]), projection.y(baranya.info.label[1])))
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals("HU-BA", tapped)
    }
}
