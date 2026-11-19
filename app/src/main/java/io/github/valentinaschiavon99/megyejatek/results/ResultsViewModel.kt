package io.github.valentinaschiavon99.megyejatek.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthRepository
import io.github.valentinaschiavon99.megyejatek.core.auth.Player
import io.github.valentinaschiavon99.megyejatek.core.data.scores.GlobalScore
import io.github.valentinaschiavon99.megyejatek.core.data.scores.ScoreEntity
import io.github.valentinaschiavon99.megyejatek.core.data.scores.ScoresRepository
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordsTab { Mine, Global }

sealed interface GlobalState {
    data object Idle : GlobalState
    data object Loading : GlobalState
    data object NotAvailable : GlobalState
    data object Error : GlobalState
    data class Loaded(val scores: List<GlobalScore>) : GlobalState
}

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val scores: ScoresRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    val modes: List<GameMode> = GameMode.entries.filter { it.scored && it.implemented }

    private val _mode = MutableStateFlow(modes.first())
    val mode: StateFlow<GameMode> = _mode.asStateFlow()

    private val _tab = MutableStateFlow(RecordsTab.Mine)
    val tab: StateFlow<RecordsTab> = _tab.asStateFlow()

    private val _global = MutableStateFlow<GlobalState>(GlobalState.Idle)
    val global: StateFlow<GlobalState> = _global.asStateFlow()

    val player: StateFlow<Player?> = auth.player
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), auth.currentPlayer)

    /** Re-queried when the mode or the signed-in player changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val personal: StateFlow<List<ScoreEntity>> = combine(_mode, player) { m, _ -> m }
        .flatMapLatest { scores.personalBest(it.key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectMode(m: GameMode) {
        _mode.value = m
        if (_tab.value == RecordsTab.Global) loadGlobal()
    }

    fun selectTab(t: RecordsTab) {
        _tab.value = t
        if (t == RecordsTab.Global) loadGlobal()
    }

    fun loadGlobal() {
        if (!auth.isAvailable || auth.currentPlayer == null) {
            _global.value = GlobalState.NotAvailable
            return
        }
        _global.value = GlobalState.Loading
        viewModelScope.launch {
            _global.update {
                runCatching { scores.globalTop(_mode.value.key) }
                    .fold({ GlobalState.Loaded(it) }, { GlobalState.Error })
            }
        }
    }

    val canLogout: Boolean get() = auth.isAvailable

    fun logout() = auth.logout()
}
