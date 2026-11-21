package io.github.valentinaschiavon99.megyejatek

import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import io.github.valentinaschiavon99.megyejatek.core.data.prefs.Settings
import io.github.valentinaschiavon99.megyejatek.core.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.map
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.valentinaschiavon99.megyejatek.auth.AuthScreen
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthRepository
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MegyejatekTheme
import io.github.valentinaschiavon99.megyejatek.core.update.ReleaseInfo
import io.github.valentinaschiavon99.megyejatek.core.update.UpdateChecker
import io.github.valentinaschiavon99.megyejatek.core.update.UpdateStatus
import io.github.valentinaschiavon99.megyejatek.intro.IntroScreen
import io.github.valentinaschiavon99.megyejatek.navigation.AppNavHost
import javax.inject.Inject

private enum class TopScreen { Intro, Auth, App }

private const val UPDATE_CHECK_INTERVAL_MS = 5 * 60 * 1000L

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var updates: UpdateChecker
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MegyejatekTheme {
                // Saveable, so rotating the phone doesn't replay the intro.
                var showIntro by rememberSaveable { mutableStateOf(true) }
                val player by auth.player.collectAsStateWithLifecycle(initialValue = auth.currentPlayer)
                // Null while DataStore loads (the intro covers it).
                val onboardedFlow = remember { settings.settings.map<Settings, Boolean?> { it.onboarded } }
                val onboarded by onboardedFlow.collectAsStateWithLifecycle(initialValue = null)
                val scope = rememberCoroutineScope()
                LaunchedEffect(player) { if (player != null) settings.setOnboarded() }
                val screen = when {
                    showIntro || onboarded == null -> TopScreen.Intro
                    // The account screen comes only on the very first start and can be skipped ("vendégként");
                    // without Firebase (offline build) there are no accounts at all.
                    auth.isAvailable && player == null && onboarded == false -> TopScreen.Auth
                    else -> TopScreen.App
                }
                Crossfade(targetState = screen, animationSpec = tween(400), label = "top") { s ->
                    when (s) {
                        TopScreen.Intro -> IntroScreen(onFinished = { showIntro = false })
                        TopScreen.Auth -> AuthScreen(onSkip = { scope.launch { settings.setOnboarded() } }, skipAsGuest = true)
                        TopScreen.App -> AppNavHost()
                    }
                }

                // Every time the app comes to the foreground: offer a newer build from GitHub, if any.
                // Checks run at most every few minutes; "Később" hides it until the next check.
                var available by remember {
                    mutableStateOf((updates.lastStatus as? UpdateStatus.Available)?.release)
                }
                // Saveable, so rotating the phone neither re-checks nor brings back a dismissed dialog.
                var dismissed by rememberSaveable { mutableStateOf(false) }
                var lastCheck by rememberSaveable { mutableLongStateOf(0L) }
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    val now = SystemClock.elapsedRealtime()
                    if (lastCheck == 0L || now - lastCheck > UPDATE_CHECK_INTERVAL_MS) {
                        lastCheck = now
                        scope.launch {
                            (updates.check() as? UpdateStatus.Available)?.let {
                                available = it.release
                                dismissed = false
                            }
                        }
                    }
                }
                val release = available
                if (release != null && !dismissed && !showIntro) {
                    val uri = LocalUriHandler.current
                    AlertDialog(
                        onDismissRequest = { dismissed = true },
                        title = { Text(stringResource(R.string.update_dialog_title)) },
                        text = { Text(stringResource(R.string.update_dialog_text, release.versionName)) },
                        confirmButton = {
                            Button(onClick = {
                                dismissed = true
                                if (UpdateChecker.isTrustedApkUrl(release.apkUrl)) uri.openUri(release.apkUrl)
                            }) { Text(stringResource(R.string.settings_download)) }
                        },
                        dismissButton = { TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.update_later)) } },
                    )
                }
            }
        }
    }
}
