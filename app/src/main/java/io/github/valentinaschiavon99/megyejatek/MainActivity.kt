package io.github.valentinaschiavon99.megyejatek

import android.os.Bundle
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
import androidx.compose.runtime.remember
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: AuthRepository
    @Inject lateinit var updates: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MegyejatekTheme {
                // Saveable, so rotating the phone doesn't replay the intro.
                var showIntro by rememberSaveable { mutableStateOf(true) }
                val player by auth.player.collectAsStateWithLifecycle(initialValue = auth.currentPlayer)
                val screen = when {
                    showIntro -> TopScreen.Intro
                    // Without Firebase (offline build) there are no accounts: straight to the game.
                    auth.isAvailable && player == null -> TopScreen.Auth
                    else -> TopScreen.App
                }
                Crossfade(targetState = screen, animationSpec = tween(400), label = "top") { s ->
                    when (s) {
                        TopScreen.Intro -> IntroScreen(onFinished = { showIntro = false })
                        TopScreen.Auth -> AuthScreen()
                        TopScreen.App -> AppNavHost()
                    }
                }

                // Once per launch: offer a newer build from GitHub, if there is one.
                var available by remember { mutableStateOf<ReleaseInfo?>(null) }
                var dismissed by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    (updates.check() as? UpdateStatus.Available)?.let { available = it.release }
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
