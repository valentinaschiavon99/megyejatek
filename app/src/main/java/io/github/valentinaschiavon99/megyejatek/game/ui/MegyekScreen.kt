package io.github.valentinaschiavon99.megyejatek.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundState
import io.github.valentinaschiavon99.megyejatek.map.HungaryMap

@Composable
fun MegyekScreen(viewModel: GameViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val blind by viewModel.blindMap.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            haptics.performHapticFeedback(
                when (e) {
                    GameEvent.Correct, GameEvent.Finished -> HapticFeedbackType.Confirm
                    GameEvent.Wrong -> HapticFeedbackType.Reject
                    GameEvent.Hint -> HapticFeedbackType.LongPress
                },
            )
        }
    }

    MegyekContent(
        state = state,
        blindMap = blind,
        onBlindMapChange = viewModel::setBlindMap,
        onNewGame = viewModel::onNewGameClick,
        onCountyTap = viewModel::onCountyTap,
        onHint = viewModel::onHint,
        onGiveUp = viewModel::onGiveUp,
        onDialogStart = viewModel::onDialogStart,
        onDialogDismiss = viewModel::onDialogDismiss,
        onResultNewGame = {
            viewModel.onCloseResult()
            viewModel.onNewGameClick()
        },
        onResultClose = viewModel::onCloseResult,
    )
}

/** Stateless so it can be driven directly from UI tests. */
@Composable
fun MegyekContent(
    state: GameUiState,
    blindMap: Boolean,
    onBlindMapChange: (Boolean) -> Unit,
    onNewGame: () -> Unit,
    onCountyTap: (String) -> Unit,
    onHint: () -> Unit,
    onGiveUp: () -> Unit,
    onDialogStart: (String) -> Unit,
    onDialogDismiss: () -> Unit,
    onResultNewGame: () -> Unit,
    onResultClose: () -> Unit,
) {
    val data = state.data
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val round = state.round

    Column(Modifier.fillMaxSize()) {
        GameHeader(
            elapsedMs = state.elapsedMs,
            multiplier = state.multiplier,
            score = round?.score ?: 0,
            blindMap = blindMap,
            onBlindMapChange = onBlindMapChange,
        )
        if (state.phase == Phase.Playing && round != null) {
            PromptBar(data, round, onHint, onGiveUp)
        }
        Box(Modifier.weight(1f)) {
            HungaryMap(
                data = data,
                countyStates = state.countyStates,
                blindMap = blindMap,
                onCountyTap = onCountyTap,
            )
            if (state.phase == Phase.Idle) {
                StartCard(onNewGame, Modifier.align(Alignment.Center))
            }
        }
    }

    if (state.showNewGameDialog) {
        NewGameDialog(onStart = onDialogStart, onDismiss = onDialogDismiss)
    }
    if (state.phase == Phase.Finished && round != null) {
        ResultDialog(round, state.elapsedMs, onNewGame = onResultNewGame, onClose = onResultClose)
    }
}

@Composable
private fun GameHeader(
    elapsedMs: Long,
    multiplier: Int,
    score: Int,
    blindMap: Boolean,
    onBlindMapChange: (Boolean) -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(formatTime(elapsedMs), style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("timer"))
            Text(
                stringResource(R.string.game_multiplier, multiplier),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag("multiplier"),
            )
            Text(
                stringResource(R.string.game_score, score),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("score"),
            )
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.blind_map), style = MaterialTheme.typography.labelMedium)
            Switch(checked = blindMap, onCheckedChange = onBlindMapChange)
        }
    }
}

@Composable
private fun PromptBar(data: MapData, round: RoundState, onHint: () -> Unit, onGiveUp: () -> Unit) {
    val q = round.current ?: return
    val county = data.countyById[q.answerId]
    val name = if (county?.info?.isCapital == true) q.prompt else stringResource(R.string.game_county_name, q.prompt)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.game_find) + " " +
                    stringResource(R.string.game_progress, round.index + 1, round.total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("prompt"))
        }
        OutlinedButton(onClick = onHint) {
            Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.game_hint))
        }
        TextButton(onClick = onGiveUp) {
            Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.game_give_up))
        }
    }
}

@Composable
private fun StartCard(onNewGame: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.padding(24.dp).widthIn(max = 420.dp)) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.game_megyek_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.game_megyek_help),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onNewGame) { Text(stringResource(R.string.game_new)) }
        }
    }
}

@Composable
private fun NewGameDialog(onStart: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.game_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_who_plays))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text(stringResource(R.string.dialog_nickname)) },
                    singleLine = true,
                    modifier = Modifier.testTag("name-field"),
                )
            }
        },
        confirmButton = { Button(onClick = { onStart(name) }) { Text(stringResource(R.string.dialog_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_close)) } },
    )
}

@Composable
private fun ResultDialog(round: RoundState, elapsedMs: Long, onNewGame: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(stringResource(if (round.gaveUp) R.string.result_title_gave_up else R.string.result_title_done))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.result_score, round.score), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.result_time, formatTime(elapsedMs)))
                Text(stringResource(R.string.result_correct, round.correctCount, round.total))
                if (round.timeBonus > 0) Text(stringResource(R.string.result_time_bonus, round.timeBonus))
                if (round.hintsUsed > 0) Text(stringResource(R.string.result_hints, round.hintsUsed))
            }
        },
        confirmButton = { Button(onClick = onNewGame) { Text(stringResource(R.string.game_new)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.dialog_close)) } },
    )
}

/** m:ss */
fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
