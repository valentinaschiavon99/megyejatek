package io.github.valentinaschiavon99.megyejatek.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthError
import io.github.valentinaschiavon99.megyejatek.core.security.CredentialPolicy
import io.github.valentinaschiavon99.megyejatek.core.security.CredentialPolicy.PasswordRule
import io.github.valentinaschiavon99.megyejatek.core.security.CredentialPolicy.UsernameProblem
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.LocalMapColors

@Composable
fun AuthScreen(
    /** First run: "Játék vendégként". Later, from Beállítások or the leaderboard: back to the app. */
    onSkip: () -> Unit,
    skipAsGuest: Boolean,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthContent(
        state = state,
        onMode = viewModel::setMode,
        onUsername = viewModel::setUsername,
        onPassword = viewModel::setPassword,
        onConfirm = viewModel::setConfirm,
        onSubmit = viewModel::submit,
        onSkip = onSkip,
        skipAsGuest = skipAsGuest,
    )
}

@Composable
fun AuthContent(
    state: AuthUiState,
    onMode: (AuthMode) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit = {},
    skipAsGuest: Boolean = true,
) {
    val register = state.mode == AuthMode.Register
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.auth_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AuthMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = state.mode == m,
                            onClick = { onMode(m) },
                            shape = SegmentedButtonDefaults.itemShape(i, AuthMode.entries.size),
                        ) {
                            Text(stringResource(if (m == AuthMode.Login) R.string.auth_login else R.string.auth_register))
                        }
                    }
                }

                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsername,
                    label = { Text(stringResource(R.string.auth_username)) },
                    singleLine = true,
                    isError = register && state.usernameProblem != null,
                    supportingText = {
                        if (register) {
                            Text(stringResource(state.usernameProblem?.message() ?: R.string.auth_username_help))
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("auth-username"),
                )

                PasswordField(
                    value = state.password,
                    onValueChange = onPassword,
                    label = R.string.auth_password,
                    imeAction = if (register) ImeAction.Next else ImeAction.Done,
                    onDone = onSubmit,
                    tag = "auth-password",
                )

                if (register) {
                    StrengthBar(CredentialPolicy.strength(state.password, state.username))
                    RuleList(state.passwordProblems, state.password.isNotEmpty())
                    PasswordField(
                        value = state.confirm,
                        onValueChange = onConfirm,
                        label = R.string.auth_confirm,
                        imeAction = ImeAction.Done,
                        onDone = onSubmit,
                        tag = "auth-confirm",
                        isError = state.mismatch,
                    )
                }

                val error = when {
                    state.mismatch -> R.string.auth_error_mismatch
                    else -> state.error?.message()
                }
                if (error != null) {
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("auth-error"))
                }

                Button(
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    modifier = Modifier.fillMaxWidth().testTag("auth-submit"),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(if (register) R.string.auth_register_action else R.string.auth_login_action))
                    }
                }

                OutlinedButton(
                    onClick = onSkip,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("auth-skip"),
                ) {
                    Text(stringResource(if (skipAsGuest) R.string.auth_play_as_guest else R.string.auth_not_now))
                }

                Text(
                    stringResource(R.string.auth_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    imeAction: ImeAction,
    onDone: () -> Unit,
    tag: String,
    isError: Boolean = false,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun StrengthBar(strength: Int) {
    val colors = LocalMapColors.current
    val color: Color = when (strength) {
        0, 1 -> colors.wrong
        2 -> colors.highlighted
        else -> colors.correct
    }
    val label = when (strength) {
        0 -> R.string.auth_strength_0
        1 -> R.string.auth_strength_1
        2 -> R.string.auth_strength_2
        3 -> R.string.auth_strength_3
        else -> R.string.auth_strength_4
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { (strength + 1) / 5f },
            color = color,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun RuleList(problems: Set<PasswordRule>, started: Boolean) {
    val colors = LocalMapColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (rule in PasswordRule.entries) {
            val ok = started && rule !in problems
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (ok) colors.correct else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(stringResource(rule.message()), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@StringRes
private fun PasswordRule.message(): Int = when (this) {
    PasswordRule.Length -> R.string.rule_length
    PasswordRule.Lowercase -> R.string.rule_lower
    PasswordRule.Uppercase -> R.string.rule_upper
    PasswordRule.Digit -> R.string.rule_digit
    PasswordRule.Symbol -> R.string.rule_symbol
    PasswordRule.NotUsername -> R.string.rule_not_username
    PasswordRule.NotCommon -> R.string.rule_not_common
}

@StringRes
private fun UsernameProblem.message(): Int = when (this) {
    UsernameProblem.TooShort -> R.string.username_too_short
    UsernameProblem.TooLong -> R.string.username_too_long
    UsernameProblem.MustStartWithLetter -> R.string.username_start_letter
    UsernameProblem.InvalidCharacters -> R.string.username_chars
}

@StringRes
private fun AuthError.message(): Int = when (this) {
    AuthError.InvalidUsername -> R.string.auth_error_username
    AuthError.WeakPassword -> R.string.auth_error_weak
    AuthError.UsernameTaken -> R.string.auth_error_taken
    AuthError.WrongCredentials -> R.string.auth_error_credentials
    AuthError.TooManyAttempts -> R.string.auth_error_too_many
    AuthError.Network -> R.string.auth_error_network
    AuthError.Unavailable -> R.string.auth_error_unavailable
    AuthError.Unknown -> R.string.auth_error_unknown
}
