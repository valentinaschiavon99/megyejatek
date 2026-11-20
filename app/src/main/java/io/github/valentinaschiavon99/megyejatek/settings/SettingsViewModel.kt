package io.github.valentinaschiavon99.megyejatek.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthRepository
import io.github.valentinaschiavon99.megyejatek.core.auth.Player
import io.github.valentinaschiavon99.megyejatek.core.data.prefs.SettingsRepository
import io.github.valentinaschiavon99.megyejatek.core.data.scores.ScoreDao
import io.github.valentinaschiavon99.megyejatek.core.update.UpdateChecker
import io.github.valentinaschiavon99.megyejatek.core.update.UpdateStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class Done(val status: UpdateStatus) : UpdateUi
}

sealed interface DeleteUi {
    data object Idle : DeleteUi
    data object Asking : DeleteUi
    data object Working : DeleteUi
    data object WrongPassword : DeleteUi
    data object Failed : DeleteUi
    data object Deleted : DeleteUi
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settings: SettingsRepository,
    private val scoreDao: ScoreDao,
    private val updates: UpdateChecker,
) : ViewModel() {

    val accountsAvailable: Boolean get() = auth.isAvailable
    val versionName: String get() = updates.currentVersionName
    val versionSha: String get() = updates.currentSha

    val player: StateFlow<Player?> = auth.player
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), auth.currentPlayer)

    val blindMap: StateFlow<Boolean> = settings.settings.map { it.blindMap }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _update = MutableStateFlow<UpdateUi>(UpdateUi.Idle)
    val update: StateFlow<UpdateUi> = _update.asStateFlow()

    private val _delete = MutableStateFlow<DeleteUi>(DeleteUi.Idle)
    val delete: StateFlow<DeleteUi> = _delete.asStateFlow()

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        if (_update.value == UpdateUi.Checking) return
        _update.value = UpdateUi.Checking
        viewModelScope.launch { _update.value = UpdateUi.Done(updates.check()) }
    }

    fun setBlindMap(enabled: Boolean) {
        viewModelScope.launch { settings.setBlindMap(enabled) }
    }

    fun logout() = auth.logout()

    fun askDelete() {
        _delete.value = DeleteUi.Asking
    }

    fun cancelDelete() {
        _delete.value = DeleteUi.Idle
    }

    fun confirmDelete(password: String) {
        if (password.isEmpty()) return
        _delete.value = DeleteUi.Working
        viewModelScope.launch {
            auth.deleteAccount(password).fold(
                onSuccess = { uid ->
                    scoreDao.deleteForUser(uid)
                    _delete.value = DeleteUi.Deleted
                },
                onFailure = { e ->
                    _delete.value = if (e is AuthRepository.WrongPasswordException) DeleteUi.WrongPassword else DeleteUi.Failed
                },
            )
        }
    }
}
