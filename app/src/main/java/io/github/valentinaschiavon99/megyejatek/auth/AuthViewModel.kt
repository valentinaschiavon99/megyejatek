package io.github.valentinaschiavon99.megyejatek.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthError
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthRepository
import io.github.valentinaschiavon99.megyejatek.core.auth.Player
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthResult
import io.github.valentinaschiavon99.megyejatek.core.security.CredentialPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { Login, Register }

data class AuthUiState(
    val mode: AuthMode = AuthMode.Login,
    val username: String = "",
    val password: String = "",
    val confirm: String = "",
    val busy: Boolean = false,
    val error: AuthError? = null,
    /** Shown after a confirm mismatch, which Firebase never sees. */
    val mismatch: Boolean = false,
) {
    val passwordProblems: Set<CredentialPolicy.PasswordRule>
        get() = CredentialPolicy.passwordProblems(password, username)

    val usernameProblem: CredentialPolicy.UsernameProblem?
        get() = if (username.isEmpty()) null else CredentialPolicy.usernameProblem(username.trim())

    val canSubmit: Boolean
        get() = !busy && when (mode) {
            AuthMode.Login -> username.isNotBlank() && password.isNotEmpty()
            AuthMode.Register -> CredentialPolicy.usernameProblem(username.trim()) == null &&
                passwordProblems.isEmpty() && confirm.isNotEmpty()
        }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    val player: StateFlow<Player?> = auth.player

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun setMode(mode: AuthMode) = _state.update {
        it.copy(mode = mode, password = "", confirm = "", error = null, mismatch = false)
    }

    fun setUsername(v: String) = _state.update {
        it.copy(username = v.take(CredentialPolicy.USERNAME_MAX + 5), error = null)
    }

    fun setPassword(v: String) = _state.update {
        it.copy(password = v.take(CredentialPolicy.PASSWORD_MAX), error = null, mismatch = false)
    }

    fun setConfirm(v: String) = _state.update {
        it.copy(confirm = v.take(CredentialPolicy.PASSWORD_MAX), mismatch = false)
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        if (s.mode == AuthMode.Register && s.password != s.confirm) {
            _state.update { it.copy(mismatch = true) }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = when (s.mode) {
                AuthMode.Login -> auth.login(s.username, s.password)
                AuthMode.Register -> auth.register(s.username, s.password)
            }
            // Passwords leave memory as soon as Firebase has answered.
            _state.update {
                when (result) {
                    is AuthResult.Success -> AuthUiState()
                    is AuthResult.Failure -> it.copy(busy = false, error = result.error, password = "", confirm = "")
                }
            }
        }
    }
}
