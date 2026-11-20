package io.github.valentinaschiavon99.megyejatek.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.ui.components.icon
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import io.github.valentinaschiavon99.megyejatek.game.ui.GameScreen

/**
 * "Játék" tab. The mode chips are handed to the mode's screen as a slot, so in portrait they sit above
 * the game and in landscape in the side panel, leaving the whole height to the map.
 */
@Composable
fun PlayScreen() {
    var mode by rememberSaveable { mutableStateOf(GameMode.Megyek) }
    val picker: @Composable () -> Unit = { ModeChips(mode, onSelect = { mode = it }) }
    Box(Modifier.fillMaxSize()) {
        when {
            mode == GameMode.Attekintes -> OverviewScreen(modePicker = picker)
            mode.implemented -> GameScreen(mode, modePicker = picker)
            else -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.mode_coming_soon), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun ModeChips(selected: GameMode, onSelect: (GameMode) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(selected) { list.animateScrollToItem(selected.ordinal.coerceAtLeast(0)) }
    LazyRow(
        modifier.fillMaxWidth().testTag("mode-chips"),
        state = list,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(GameMode.entries, key = { it.key }) { m ->
            FilterChip(
                selected = m == selected,
                onClick = { onSelect(m) },
                enabled = m.implemented,
                label = { Text(m.title, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(m.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}
