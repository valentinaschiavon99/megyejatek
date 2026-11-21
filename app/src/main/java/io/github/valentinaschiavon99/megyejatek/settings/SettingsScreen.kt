package io.github.valentinaschiavon99.megyejatek.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.update.UpdateChecker
import io.github.valentinaschiavon99.megyejatek.core.update.UpdateStatus

@Composable
fun SettingsScreen(onSignIn: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val player by viewModel.player.collectAsStateWithLifecycle()
    val blind by viewModel.blindMap.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val delete by viewModel.delete.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineSmall)

        // ---- app version and updates ----
        Section(stringResource(R.string.settings_app)) {
            Text(stringResource(R.string.settings_version, viewModel.versionName, viewModel.versionSha))
            when (val u = update) {
                UpdateUi.Idle, UpdateUi.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("  " + stringResource(R.string.settings_checking))
                }
                is UpdateUi.Done -> when (val st = u.status) {
                    UpdateStatus.UpToDate -> Text(stringResource(R.string.settings_up_to_date), modifier = Modifier.testTag("update-status"))
                    is UpdateStatus.Failed -> Text(
                        stringResource(R.string.settings_update_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("update-status"),
                    )
                    is UpdateStatus.Available -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.settings_update_available, st.release.versionName),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("update-status"),
                        )
                        Button(onClick = {
                            if (UpdateChecker.isTrustedApkUrl(st.release.apkUrl)) uri.openUri(st.release.apkUrl)
                        }) { Text(stringResource(R.string.settings_download)) }
                        Text(stringResource(R.string.settings_update_hint), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = viewModel::checkForUpdate, enabled = update != UpdateUi.Checking) {
                Text(stringResource(R.string.settings_check_update))
            }
        }

        // ---- display ----
        Section(stringResource(R.string.settings_display)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.blind_map))
                    Text(stringResource(R.string.settings_blind_desc), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = blind, onCheckedChange = viewModel::setBlindMap)
            }
        }

        // ---- account ----
        if (viewModel.accountsAvailable) {
            Section(stringResource(R.string.settings_account)) {
                val p = player
                if (p == null) {
                    Text(stringResource(R.string.settings_guest))
                    Button(onClick = onSignIn, modifier = Modifier.testTag("sign-in")) {
                        Text(stringResource(R.string.auth_sign_in_cta))
                    }
                } else {
                    Text(stringResource(R.string.records_logged_in_as, p.username))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::logout) { Text(stringResource(R.string.records_logout)) }
                        Button(
                            onClick = viewModel::askDelete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.testTag("delete-account"),
                        ) { Text(stringResource(R.string.settings_delete)) }
                    }
                }
            }
        }

        // ---- privacy ----
        Section(stringResource(R.string.settings_privacy)) {
            Text(stringResource(R.string.settings_privacy_desc), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { uri.openUri(PRIVACY_URL) }) { Text(stringResource(R.string.settings_privacy_open)) }
        }
    }

    when (delete) {
        DeleteUi.Asking, DeleteUi.Working, DeleteUi.WrongPassword, DeleteUi.Failed ->
            DeleteDialog(delete, onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
        DeleteUi.Deleted -> AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.settings_deleted_title)) },
            text = { Text(stringResource(R.string.settings_deleted_text)) },
            confirmButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.dialog_close)) } },
        )
        DeleteUi.Idle -> Unit
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun DeleteDialog(state: DeleteUi, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var password by remember { mutableStateOf("") }
    val working = state == DeleteUi.Working
    AlertDialog(
        onDismissRequest = { if (!working) onCancel() },
        title = { Text(stringResource(R.string.settings_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_delete_text))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !working,
                    modifier = Modifier.testTag("delete-password"),
                )
                when (state) {
                    DeleteUi.WrongPassword -> Text(stringResource(R.string.auth_error_credentials), color = MaterialTheme.colorScheme.error)
                    DeleteUi.Failed -> Text(stringResource(R.string.settings_delete_failed), color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = !working && password.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                if (working) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.settings_delete_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !working) { Text(stringResource(R.string.dialog_close)) } },
    )
}

private const val PRIVACY_URL = "https://github.com/valentinaschiavon99/megyejatek/blob/main/PRIVACY.md"
