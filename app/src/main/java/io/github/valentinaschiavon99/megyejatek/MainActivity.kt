package io.github.valentinaschiavon99.megyejatek

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.valentinaschiavon99.megyejatek.auth.AuthScreen
import io.github.valentinaschiavon99.megyejatek.core.auth.AuthRepository
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MegyejatekTheme
import io.github.valentinaschiavon99.megyejatek.intro.IntroScreen
import io.github.valentinaschiavon99.megyejatek.navigation.AppNavHost
import javax.inject.Inject

private enum class TopScreen { Intro, Auth, App }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: AuthRepository

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
            }
        }
    }
}
