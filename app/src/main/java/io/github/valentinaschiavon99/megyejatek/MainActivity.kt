package io.github.valentinaschiavon99.megyejatek

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.valentinaschiavon99.megyejatek.core.ui.theme.MegyejatekTheme
import io.github.valentinaschiavon99.megyejatek.navigation.AppNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MegyejatekTheme {
                AppNavHost()
            }
        }
    }
}
