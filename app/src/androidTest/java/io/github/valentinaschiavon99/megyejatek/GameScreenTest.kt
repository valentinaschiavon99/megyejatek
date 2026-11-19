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
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import io.github.valentinaschiavon99.megyejatek.game.engine.QuestionFactory
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundState
import io.github.valentinaschiavon99.megyejatek.game.ui.GameContent
import io.github.valentinaschiavon99.megyejatek.game.ui.GameUiState
import io.github.valentinaschiavon99.megyejatek.game.ui.Phase
import io.github.valentinaschiavon99.megyejatek.map.HungaryMapDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** One Compose test per game mode: prompt, header and a tap on the map. */
class GameScreenTest {
    @get:Rule val compose = createComposeRule()

    private val data: MapData by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        fun read(name: String) = assets.open("map/$name").bufferedReader().use { it.readText() }
        MapDataParser.parse(
            MapAssets(read("counties.geojson"), read("outline.geojson"), read("water.geojson"),
                read("counties.json"), read("cities.json")),
        )
    }

    private var tappedCounty: String? = null
    private var tappedLonLat: Pair<Double, Double>? = null
    private var hints = 0

    private fun show(mode: GameMode): RoundState {
        val round = RoundState(QuestionFactory.forMode(mode, data, Random(42)))
        compose.setContent {
            MegyejatekTheme {
                GameContent(
                    state = GameUiState(mode = mode, data = data, phase = Phase.Playing, round = round),
                    blindMap = false,
                    onBlindMapChange = {},
                    onNewGame = {},
                    onCountyTap = { tappedCounty = it },
                    onMapTap = { lon, lat -> tappedLonLat = lon to lat },
                    onHint = { hints++ },
                    onGiveUp = {},
                    onDialogStart = {},
                    onDialogDismiss = {},
                    onResultNewGame = {},
                    onResultClose = {},
                )
            }
        }
        compose.onNodeWithTag("multiplier").assertTextEquals("×1 pont")
        compose.onNodeWithTag("timer").assertTextEquals("0:00")
        return round
    }

    /** Taps a lon/lat on the map, projected exactly like HungaryMap does. */
    private fun tapAt(lon: Double, lat: Double) {
        val map = compose.onNodeWithTag("hungary-map")
        val size = map.fetchSemanticsNode().size
        val pad = with(compose.density) { HungaryMapDefaults.ContentPadding.toPx() }
        val p = Projection(data.bounds, size.width.toFloat(), size.height.toFloat(), pad)
        map.performTouchInput { click(Offset(p.x(lon), p.y(lat))) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun tapCounty(id: String) {
        val label = data.countyById.getValue(id).info.label
        tapAt(label[0], label[1])
    }

    @Test fun megyek() {
        val q = show(GameMode.Megyek).current!!
        val c = data.countyById.getValue(q.subject)
        compose.onNodeWithTag("prompt").assertTextEquals(if (c.info.isCapital) c.name else "${c.name} megye")
        compose.onNodeWithText("Súgás").performClick()
        assertEquals(1, hints)
        tapCounty(q.subject)
        assertEquals(q.subject, tappedCounty)
    }

    @Test fun szekhelyek() {
        val q = show(GameMode.Szekhelyek).current!!
        compose.onNodeWithTag("prompt").assertTextEquals(data.cityById.getValue(q.subject).name)
        tapCounty(q.hintIds.first())
        assertEquals(q.hintIds.first(), tappedCounty)
    }

    @Test fun szomszedok() {
        val q = show(GameMode.Szomszedok).current!!
        assertTrue(q.answers.isNotEmpty())
        val first = q.answers.first()
        tapCounty(first)
        assertEquals(first, tappedCounty)
    }

    @Test fun varosok() {
        val q = show(GameMode.Varosok).current!!
        val city = data.cityById.getValue(q.subject)
        compose.onNodeWithTag("prompt").assertTextEquals(city.name)
        tapAt(city.lon, city.lat)
        assertNotNull(tappedLonLat)
        val (lon, lat) = tappedLonLat!!
        assertTrue(abs(lon - city.lon) < 0.05 && abs(lat - city.lat) < 0.05)
        assertEquals(null, tappedCounty) // county taps are off in this mode
    }

    @Test fun felsorolas() {
        val q = show(GameMode.Felsorolas).current!!
        compose.onNodeWithTag("prompt").assertTextEquals("1/20")
        tapCounty(q.subject)
        assertEquals(q.subject, tappedCounty)
    }
}
