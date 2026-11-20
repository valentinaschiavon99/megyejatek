package io.github.valentinaschiavon99.megyejatek.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The learning screens only read the bundled map data. */
@HiltViewModel
class LearnViewModel @Inject constructor(mapRepository: MapRepository) : ViewModel() {
    private val _data = MutableStateFlow<MapData?>(null)
    val data: StateFlow<MapData?> = _data.asStateFlow()

    init {
        viewModelScope.launch { _data.value = mapRepository.mapData() }
    }
}
