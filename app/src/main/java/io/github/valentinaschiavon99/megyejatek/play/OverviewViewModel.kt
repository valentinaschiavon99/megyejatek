package io.github.valentinaschiavon99.megyejatek.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapRepository
import io.github.valentinaschiavon99.megyejatek.core.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * "Áttekintés": free study mode. Tap a county or city to see its details.
 */
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val mapRepository: MapRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val data: MapData? = null,
        val selectedCountyId: String? = null,
        val selectedCityId: String? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val blindMap: StateFlow<Boolean> = settings.settings.map { it.blindMap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            runCatching { mapRepository.mapData() }
                .onSuccess { data -> _state.update { it.copy(data = data) } }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: e.toString()) } }
        }
    }

    fun onCountyTap(id: String) = _state.update { it.copy(selectedCountyId = id, selectedCityId = null) }

    fun onCityTap(id: String) = _state.update {
        it.copy(selectedCityId = id, selectedCountyId = it.data?.cityById?.get(id)?.countyId)
    }

    fun setBlindMap(enabled: Boolean) {
        viewModelScope.launch { settings.setBlindMap(enabled) }
    }
}
