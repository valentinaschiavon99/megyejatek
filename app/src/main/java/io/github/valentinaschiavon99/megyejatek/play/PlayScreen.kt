package io.github.valentinaschiavon99.megyejatek.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import io.github.valentinaschiavon99.megyejatek.game.ui.MegyekScreen

/** "Játék" tab: a row of game modes above the selected mode's screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen() {
    var mode by rememberSaveable { mutableStateOf(GameMode.Megyek) }
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = mode.ordinal, edgePadding = 8.dp) {
            GameMode.entries.forEach { m ->
                Tab(
                    selected = m == mode,
                    onClick = { mode = m },
                    enabled = m.implemented,
                    text = { Text(m.title) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (mode) {
                GameMode.Megyek -> MegyekScreen()
                GameMode.Attekintes -> OverviewScreen()
                else -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.mode_coming_soon), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
