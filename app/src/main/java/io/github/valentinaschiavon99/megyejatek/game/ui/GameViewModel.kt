package io.github.valentinaschiavon99.megyejatek.game.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthRepository
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapRepository
import io.github.valentinaschiavon99.megyejatek.core.data.prefs.SettingsRepository
import io.github.valentinaschiavon99.megyejatek.core.data.scores.RoundResult
import io.github.valentinaschiavon99.megyejatek.core.data.scores.ScoresRepository
import io.github.valentinaschiavon99.megyejatek.core.geo.PolygonMath
import io.github.valentinaschiavon99.megyejatek.core.security.InputSanitizer
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import io.github.valentinaschiavon99.megyejatek.game.engine.Outcome
import io.github.valentinaschiavon99.megyejatek.game.engine.PromptKind
import io.github.valentinaschiavon99.megyejatek.game.engine.Question
import io.github.valentinaschiavon99.megyejatek.game.engine.QuestionFactory
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundRules
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundState
import io.github.valentinaschiavon99.megyejatek.game.engine.ScoringConfig
import io.github.valentinaschiavon99.megyejatek.game.engine.TapResult
import io.github.valentinaschiavon99.megyejatek.map.CountyVisual
import io.github.valentinaschiavon99.megyejatek.map.MapGuess
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Phase { Idle, Playing, Finished }

/** Short-lived feedback for the UI (haptics). */
enum class GameEvent { Correct, Wrong, Hint, Finished }

/** Points just earned; [id] changes every time so the "+N" animation replays. */
data class Award(val points: Int, val id: Long)

/** Result of the last Városok guess, shown on the map and under the prompt. */
data class LastGuess(val cityId: String, val guess: MapGuess, val km: Double, val points: Int, val good: Boolean)

data class GameUiState(
    val mode: GameMode = GameMode.Megyek,
    val data: MapData? = null,
    val phase: Phase = Phase.Idle,
    val round: RoundState? = null,
    val elapsedMs: Long = 0,
    val multiplier: Int = 1,
    /** Temporary colours (wrong flash, hint flash) drawn over the round's permanent ones. */
    val flashes: Map<String, CountyVisual> = emptyMap(),
    val lastGuess: LastGuess? = null,
    val showNewGameDialog: Boolean = false,
    val playerName: String? = null,
    val award: Award? = null,
    /** Best completed score in this mode on this phone, for the start card and "Új rekord!". */
    val best: Int? = null,
    val newRecord: Boolean = false,
    val maxLives: Int = ScoringConfig.Default.livesPerRound,
    val maxHints: Int = ScoringConfig.Default.hintsPerRound,
) {
    val livesLeft: Int get() = (maxLives - (round?.mistakes ?: 0)).coerceAtLeast(0)
    val hintsLeft: Int get() = (maxHints - (round?.hintsUsed ?: 0)).coerceAtLeast(0)

    /** 0–3 stars for the result screen, by the share of correct answers. */
    val stars: Int
        get() {
            val r = round ?: return 0
            if (r.gaveUp || r.outOfLives || r.hitsAvailable == 0) return 0
            val ratio = r.hits.toDouble() / r.hitsAvailable
            return when {
                ratio >= 0.95 && r.hintsUsed == 0 -> 3
                ratio >= 0.75 -> 2
                ratio >= 0.4 -> 1
                else -> 0
            }
        }

    val countyStates: Map<String, CountyVisual>
        get() {
            val r = round ?: return flashes
            val current = r.current
            val permanent = buildMap {
                if (current?.kind == PromptKind.NeighboursOf) {
                    put(current.subject, CountyVisual.Highlighted)
                    r.foundInCurrent.forEach { put(it, CountyVisual.Correct) }
                } else {
                    for (q in r.questions) {
                        if (!q.kind.oneShotAnswers) continue
                        val id = q.hintIds.first()
                        when (r.outcomes[q.id]) {
                            Outcome.Correct -> put(id, CountyVisual.Correct)
                            Outcome.Revealed, Outcome.GivenUp -> put(id, CountyVisual.Hinted)
                            null -> Unit
                        }
                    }
                }
            }
            return permanent + flashes
        }
}

/** Runs one round of any scored mode; the screen picks the mode with [selectMode]. */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val settings: SettingsRepository,
    private val auth: AuthRepository,
    private val scores: ScoresRepository,
) : ViewModel() {

    private val config = ScoringConfig.Default

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    val blindMap: StateFlow<Boolean> = settings.settings.map { it.blindMap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var startedAt = 0L
    private var awardIds = 0L
    private var bestJob: Job? = null
    private var timerJob: Job? = null
    private val flashJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            val data = mapRepository.mapData()
            val name = auth.currentPlayer?.username ?: settings.settings.first().playerName
            _state.update { it.copy(data = data, playerName = name) }
        }
    }

    /** Called once by the screen; a view model instance is kept per mode. */
    fun selectMode(mode: GameMode) {
        if (_state.value.mode != mode && _state.value.phase == Phase.Idle) _state.update { it.copy(mode = mode) }
        if (bestJob == null) {
            bestJob = viewModelScope.launch {
                scores.personalBest(_state.value.mode.key, limit = 1).collect { list ->
                    _state.update { it.copy(best = list.firstOrNull()?.score) }
                }
            }
        }
    }

    /**
     * "Új játék". With an account the player's username is used; without Firebase (offline build)
     * the "Ki játszik?" dialog asks for a nickname once.
     */
    fun onNewGameClick() {
        auth.currentPlayer?.let { p -> _state.update { it.copy(playerName = p.username) } }
        if (_state.value.playerName == null) _state.update { it.copy(showNewGameDialog = true) }
        else start()
    }

    fun onDialogDismiss() = _state.update { it.copy(showNewGameDialog = false) }

    fun onDialogStart(name: String) {
        val clean = InputSanitizer.nickname(name).ifEmpty { DEFAULT_NAME }
        viewModelScope.launch { settings.setPlayerName(clean) }
        _state.update { it.copy(playerName = clean, showNewGameDialog = false) }
        start()
    }

    private fun start() {
        val s = _state.value
        val data = s.data ?: return
        clearAllFlashes()
        _state.update {
            it.copy(
                phase = Phase.Playing,
                round = RoundState(QuestionFactory.forMode(s.mode, data)),
                elapsedMs = 0,
                multiplier = 1,
                flashes = emptyMap(),
                lastGuess = null,
                award = null,
                newRecord = false,
            )
        }
        startedAt = SystemClock.elapsedRealtime()
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt) }
                delay(200)
            }
        }
    }

    fun onCountyTap(countyId: String) {
        val s = _state.value
        val round = s.round ?: return
        if (s.phase != Phase.Playing || round.current?.kind == PromptKind.FindCity) return
        val (next, result) = RoundRules.tap(round, countyId, config)
        when (result) {
            is TapResult.Correct -> {
                _events.tryEmit(GameEvent.Correct)
                _state.update { it.copy(award = Award(result.points, ++awardIds)) }
                clearFlash(result.answerId) // a running hint flash must not hide the green
            }
            is TapResult.Wrong -> {
                _events.tryEmit(GameEvent.Wrong)
                flash(result.tappedId, CountyVisual.Wrong, 600)
            }
            is TapResult.OutOfLives -> {
                _events.tryEmit(GameEvent.Wrong)
                flash(result.tappedId, CountyVisual.Wrong, 600)
                // Neighbour answers are not kept on the map after the question, so show them briefly.
                if (round.current?.kind == PromptKind.NeighboursOf) {
                    result.missing.forEach { flash(it, CountyVisual.Hinted, 1_500) }
                }
            }
            is TapResult.Guess, TapResult.Ignored -> return
        }
        applyRound(next)
    }

    /** Városok: a tap anywhere on the map is a guess for the asked city. */
    fun onMapTap(lon: Double, lat: Double) {
        val s = _state.value
        val round = s.round ?: return
        val q: Question = round.current ?: return
        if (s.phase != Phase.Playing || q.kind != PromptKind.FindCity) return
        val city = s.data?.cityById?.get(q.subject) ?: return
        val km = PolygonMath.distanceKm(lon, lat, city.lon, city.lat)
        val (next, result) = RoundRules.guess(round, km, config)
        if (result !is TapResult.Guess) return
        _events.tryEmit(if (result.good) GameEvent.Correct else GameEvent.Wrong)
        clearAllFlashes()
        _state.update {
            it.copy(
                lastGuess = LastGuess(city.id, MapGuess(lon, lat, city.lon, city.lat), km, result.points, result.good),
                award = if (result.points > 0) Award(result.points, ++awardIds) else it.award,
            )
        }
        applyRound(next)
    }

    fun onHint() {
        val round = _state.value.round ?: return
        if (_state.value.phase != Phase.Playing || round.hintsLeft(config) == 0) return
        val (next, ids) = RoundRules.hint(round, config)
        if (ids.isNotEmpty()) {
            _events.tryEmit(GameEvent.Hint)
            ids.forEach { flash(it, CountyVisual.Hinted, 1_200) }
        }
        applyRound(next)
    }

    fun onGiveUp() {
        val round = _state.value.round ?: return
        if (_state.value.phase != Phase.Playing) return
        applyRound(RoundRules.giveUp(round))
    }

    /** Back to the start screen after the result dialog. */
    fun onCloseResult() {
        clearAllFlashes()
        _state.update { it.copy(phase = Phase.Idle, round = null, flashes = emptyMap(), elapsedMs = 0, lastGuess = null) }
    }

    private fun applyRound(next: RoundState) {
        var round = next
        if (round.finished) {
            timerJob?.cancel()
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            round = RoundRules.complete(round, elapsed, config)
            val previousBest = _state.value.best
            _state.update {
                it.copy(elapsedMs = elapsed, newRecord = !round.gaveUp && round.score > 0 && round.score > (previousBest ?: 0))
            }
            _events.tryEmit(GameEvent.Finished)
            saveResult(round, elapsed)
        }
        _state.update {
            it.copy(
                round = round,
                multiplier = RoundRules.multiplier(round, config),
                phase = if (round.finished) Phase.Finished else Phase.Playing,
            )
        }
    }

    private fun saveResult(round: RoundState, elapsedMs: Long) {
        val result = RoundResult(
            mode = _state.value.mode.key,
            score = round.score,
            timeMs = elapsedMs,
            correct = round.hits,
            total = round.hitsAvailable,
            hints = round.hintsUsed,
            gaveUp = round.gaveUp,
        )
        val name = _state.value.playerName ?: DEFAULT_NAME
        viewModelScope.launch { runCatching { scores.save(result, name) } }
    }

    private fun flash(countyId: String, visual: CountyVisual, durationMs: Long) {
        flashJobs[countyId]?.cancel()
        _state.update { it.copy(flashes = it.flashes + (countyId to visual)) }
        flashJobs[countyId] = viewModelScope.launch {
            delay(durationMs)
            _state.update { it.copy(flashes = it.flashes - countyId) }
            flashJobs.remove(countyId)
        }
    }

    private fun clearFlash(countyId: String) {
        flashJobs.remove(countyId)?.cancel()
        _state.update { it.copy(flashes = it.flashes - countyId) }
    }

    private fun clearAllFlashes() {
        flashJobs.values.forEach { it.cancel() }
        flashJobs.clear()
        _state.update { it.copy(flashes = emptyMap()) }
    }

    fun setBlindMap(enabled: Boolean) {
        viewModelScope.launch { settings.setBlindMap(enabled) }
    }

    companion object {
        const val DEFAULT_NAME = "Játékos"
    }
}
