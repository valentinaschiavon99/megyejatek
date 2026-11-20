package io.github.valentinaschiavon99.megyejatek

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapAssets
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapDataParser
import io.github.valentinaschiavon99.megyejatek.core.geo.PolygonMath
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MegyejatekTheme
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import io.github.valentinaschiavon99.megyejatek.game.engine.QuestionFactory
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundRules
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundState
import io.github.valentinaschiavon99.megyejatek.game.engine.ScoringConfig
import io.github.valentinaschiavon99.megyejatek.game.ui.GameContent
import io.github.valentinaschiavon99.megyejatek.game.ui.GameUiState
import io.github.valentinaschiavon99.megyejatek.game.ui.LastGuess
import io.github.valentinaschiavon99.megyejatek.game.ui.Phase
import io.github.valentinaschiavon99.megyejatek.map.MapGuess
import io.github.valentinaschiavon99.megyejatek.play.ModeChips
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

/**
 * Renders the main screens in fixed, good-looking states and stores PNGs through the Test Storage
 * service. CI collects them from the emulator and publishes them for the README.
 */
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val config = ScoringConfig()

    private val data: MapData by lazy {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        fun read(name: String) = assets.open("map/$name").bufferedReader().use { it.readText() }
        MapDataParser.parse(
            MapAssets(read("counties.geojson"), read("outline.geojson"), read("water.geojson"),
                read("counties.json"), read("cities.json")),
        )
    }

    /** A round of [mode] where the first [correct] questions were answered right. */
    private fun played(mode: GameMode, correct: Int): RoundState {
        var round = RoundState(QuestionFactory.forMode(mode, data, Random(7)))
        repeat(correct) {
            val q = round.current ?: return round
            for (answer in q.answers) {
                if (round.current?.id != q.id) break
                round = RoundRules.tap(round, answer, config).first
            }
        }
        return round
    }

    private fun content(state: GameUiState, landscape: Boolean = false, dark: Boolean = false) {
        compose.setContent {
            Frame(landscape, dark) {
                GameContent(
                    state = state,
                    blindMap = false,
                    onBlindMapChange = {},
                    onNewGame = {},
                    onCountyTap = {},
                    onMapTap = { _, _ -> },
                    onHint = {},
                    onGiveUp = {},
                    onDialogStart = {},
                    onDialogDismiss = {},
                    onResultNewGame = {},
                    onResultClose = {},
                    modePicker = { ModeChips(state.mode, onSelect = {}) },
                )
            }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Frame(landscape: Boolean, dark: Boolean, body: @Composable () -> Unit) {
        val inner: @Composable () -> Unit = {
            MegyejatekTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize().testTag(SHOT), color = MaterialTheme.colorScheme.background) { body() }
            }
        }
        if (!landscape) {
            inner()
            return
        }
        // A phone on its side, whatever the emulator's own orientation.
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(840.dp, 390.dp))) {
            val base = LocalConfiguration.current
            val rotated = Configuration(base).apply { orientation = Configuration.ORIENTATION_LANDSCAPE }
            CompositionLocalProvider(LocalConfiguration provides rotated) { Box(Modifier.fillMaxSize()) { inner() } }
        }
    }

    private fun save(name: String, bitmap: Bitmap) {
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String) = save(name, compose.onNodeWithTag(SHOT).captureToImage().asAndroidBitmap())

    @Test
    fun start() {
        content(GameUiState(mode = GameMode.Megyek, data = data, phase = Phase.Idle, best = 2_340))
        shot("01-start")
    }

    @Test
    fun playingCounties() {
        val round = played(GameMode.Megyek, correct = 8)
        content(
            GameUiState(
                mode = GameMode.Megyek, data = data, phase = Phase.Playing, round = round,
                elapsedMs = 41_000, multiplier = RoundRules.multiplier(round, config),
            ),
        )
        shot("02-counties")
    }

    @Test
    fun playingCities() {
        var round = RoundState(QuestionFactory.forMode(GameMode.Varosok, data, Random(3)))
        round = RoundRules.guess(round, 4.0, config).first
        round = RoundRules.guess(round, 9.0, config).first
        val previous = data.cityById.getValue(round.questions[1].subject)
        val guess = MapGuess(previous.lon + 0.08, previous.lat - 0.05, previous.lon, previous.lat)
        content(
            GameUiState(
                mode = GameMode.Varosok, data = data, phase = Phase.Playing, round = round, elapsedMs = 23_000,
                multiplier = RoundRules.multiplier(round, config),
                lastGuess = LastGuess(
                    previous.id, guess, PolygonMath.distanceKm(guess.tapLon, guess.tapLat, previous.lon, previous.lat), 180, true,
                ),
            ),
        )
        shot("03-cities")
    }

    @Test
    fun neighboursDark() {
        val round = played(GameMode.Szomszedok, correct = 3)
        content(
            GameUiState(mode = GameMode.Szomszedok, data = data, phase = Phase.Playing, round = round, elapsedMs = 64_000,
                multiplier = RoundRules.multiplier(round, config)),
            dark = true,
        )
        shot("04-neighbours-dark")
    }

    @Test
    fun landscape() {
        val round = played(GameMode.Megyek, correct = 12)
        content(
            GameUiState(
                mode = GameMode.Megyek, data = data, phase = Phase.Playing, round = round,
                elapsedMs = 58_000, multiplier = RoundRules.multiplier(round, config),
            ),
            landscape = true,
        )
        shot("05-landscape")
    }

    @Test
    fun result() {
        var round = played(GameMode.Megyek, correct = 20)
        round = RoundRules.complete(round, 71_000, config)
        content(
            GameUiState(
                mode = GameMode.Megyek, data = data, phase = Phase.Finished, round = round, elapsedMs = 71_000,
                best = 1_200, newRecord = true,
            ),
        )
        compose.mainClock.advanceTimeBy(1_600) // stars and score finish their entrance
        compose.waitForIdle()
        val dialog = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val screen = compose.onRoot().captureToImage().asAndroidBitmap()
        // The dialog lives in its own window: paint it over the dimmed screen for one picture.
        val out = screen.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(0x99000000.toInt())
        canvas.drawBitmap(dialog, (out.width - dialog.width) / 2f, (out.height - dialog.height) / 2f, null)
        save("06-result", out)
    }

    private companion object {
        const val SHOT = "shot"
    }
}
