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
import dagger.hilt.android.AndroidEntryPoint
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MegyejatekTheme
import io.github.valentinaschiavon99.megyejatek.intro.IntroScreen
import io.github.valentinaschiavon99.megyejatek.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MegyejatekTheme {
                // Saveable, so rotating the phone doesn't replay the intro.
                var showIntro by rememberSaveable { mutableStateOf(true) }
                Crossfade(targetState = showIntro, animationSpec = tween(400), label = "intro") { intro ->
                    if (intro) IntroScreen(onFinished = { showIntro = false }) else AppNavHost()
                }
            }
        }
    }
}
