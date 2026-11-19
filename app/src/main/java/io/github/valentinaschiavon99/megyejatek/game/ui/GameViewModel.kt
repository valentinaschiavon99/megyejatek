package io.github.valentinaschiavon99.megyejatek.game.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapRepository
import io.github.valentinaschiavon99.megyejatek.core.data.prefs.SettingsRepository
import io.github.valentinaschiavon99.megyejatek.game.engine.Outcome
import io.github.valentinaschiavon99.megyejatek.game.engine.QuestionFactory
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundRules
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundState
import io.github.valentinaschiavon99.megyejatek.game.engine.ScoringConfig
import io.github.valentinaschiavon99.megyejatek.game.engine.TapResult
import io.github.valentinaschiavon99.megyejatek.map.CountyVisual
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

data class GameUiState(
    val data: MapData? = null,
    val phase: Phase = Phase.Idle,
    val round: RoundState? = null,
    val elapsedMs: Long = 0,
    val multiplier: Int = 1,
    /** Temporary colours (wrong flash, hint flash) drawn over the round's permanent ones. */
    val flashes: Map<String, CountyVisual> = emptyMap(),
    val showNewGameDialog: Boolean = false,
    val playerName: String? = null,
) {
    val countyStates: Map<String, CountyVisual>
        get() {
            val r = round ?: return flashes
            val permanent = buildMap {
                for (q in r.questions) {
                    when (r.outcomes[q.id]) {
                        Outcome.Correct -> put(q.answerId, CountyVisual.Correct)
                        Outcome.Revealed, Outcome.GivenUp -> put(q.answerId, CountyVisual.Hinted)
                        null -> Unit
                    }
                }
            }
            return permanent + flashes
        }
}

/** Runs the "Megyék" round: name a county, the player taps it. */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val config = ScoringConfig.Default

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    val blindMap: StateFlow<Boolean> = settings.settings.map { it.blindMap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var startedAt = 0L
    private var timerJob: Job? = null
    private val flashJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            val data = mapRepository.mapData()
            val name = settings.settings.first().playerName
            _state.update { it.copy(data = data, playerName = name) }
        }
    }

    /** "Új játék": asks for a name only the first time. */
    fun onNewGameClick() {
        if (_state.value.playerName == null) _state.update { it.copy(showNewGameDialog = true) }
        else start()
    }

    fun onDialogDismiss() = _state.update { it.copy(showNewGameDialog = false) }

    fun onDialogStart(name: String) {
        val clean = name.trim().ifEmpty { DEFAULT_NAME }
        viewModelScope.launch { settings.setPlayerName(clean) }
        _state.update { it.copy(playerName = clean, showNewGameDialog = false) }
        start()
    }

    private fun start() {
        val data = _state.value.data ?: return
        flashJobs.values.forEach { it.cancel() }
        flashJobs.clear()
        _state.update {
            it.copy(
                phase = Phase.Playing,
                round = RoundState(QuestionFactory.megyek(data)),
                elapsedMs = 0,
                multiplier = 1,
                flashes = emptyMap(),
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
        if (s.phase != Phase.Playing) return
        val (next, result) = RoundRules.tap(round, countyId, config)
        when (result) {
            is TapResult.Correct -> {
                _events.tryEmit(GameEvent.Correct)
                clearFlash(result.answerId) // a running hint flash must not hide the green
            }
            is TapResult.Wrong -> {
                _events.tryEmit(GameEvent.Wrong)
                flash(result.tappedId, CountyVisual.Wrong, 600)
            }
            is TapResult.Revealed -> {
                _events.tryEmit(GameEvent.Wrong)
                flash(result.tappedId, CountyVisual.Wrong, 600)
            }
            TapResult.Ignored -> return
        }
        applyRound(next)
    }

    fun onHint() {
        val round = _state.value.round ?: return
        if (_state.value.phase != Phase.Playing) return
        val (next, target) = RoundRules.hint(round, config)
        if (target != null) {
            _events.tryEmit(GameEvent.Hint)
            flash(target, CountyVisual.Hinted, 1_200)
        }
        applyRound(next)
    }

    fun onGiveUp() {
        val round = _state.value.round ?: return
        if (_state.value.phase != Phase.Playing) return
        applyRound(RoundRules.giveUp(round))
    }

    /** Back to the start screen after the result dialog. */
    fun onCloseResult() = _state.update { it.copy(phase = Phase.Idle, round = null, flashes = emptyMap(), elapsedMs = 0) }

    private fun applyRound(next: RoundState) {
        var round = next
        if (round.finished) {
            timerJob?.cancel()
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            round = RoundRules.complete(round, elapsed, config)
            _state.update { it.copy(elapsedMs = elapsed) }
            _events.tryEmit(GameEvent.Finished)
        }
        _state.update {
            it.copy(
                round = round,
                multiplier = RoundRules.multiplier(round, config),
                phase = if (round.finished) Phase.Finished else Phase.Playing,
            )
        }
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

    fun setBlindMap(enabled: Boolean) {
        viewModelScope.launch { settings.setBlindMap(enabled) }
    }

    companion object {
        const val DEFAULT_NAME = "Játékos"
    }
}
