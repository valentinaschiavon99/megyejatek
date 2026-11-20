package io.github.valentinaschiavon99.megyejatek.game.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.security.InputSanitizer
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import io.github.valentinaschiavon99.megyejatek.game.engine.PromptKind
import io.github.valentinaschiavon99.megyejatek.game.engine.Question
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundState
import io.github.valentinaschiavon99.megyejatek.map.HungaryMap

@Composable
fun GameScreen(mode: GameMode, viewModel: GameViewModel = hiltViewModel(key = "game-${mode.key}")) {
    LaunchedEffect(mode) { viewModel.selectMode(mode) }
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

    GameContent(
        state = state,
        blindMap = blind,
        onBlindMapChange = viewModel::setBlindMap,
        onNewGame = viewModel::onNewGameClick,
        onCountyTap = viewModel::onCountyTap,
        onMapTap = viewModel::onMapTap,
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
fun GameContent(
    state: GameUiState,
    blindMap: Boolean,
    onBlindMapChange: (Boolean) -> Unit,
    onNewGame: () -> Unit,
    onCountyTap: (String) -> Unit,
    onMapTap: (Double, Double) -> Unit,
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
    val cityMode = round?.current?.kind == PromptKind.FindCity || state.mode == GameMode.Varosok

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val panel: @Composable () -> Unit = {
        GameHeader(
            elapsedMs = state.elapsedMs,
            multiplier = state.multiplier,
            score = round?.score ?: 0,
            blindMap = blindMap,
            onBlindMapChange = onBlindMapChange,
        )
        if (state.phase == Phase.Playing && round != null) {
            PromptBar(data, round, state.lastGuess, onHint, onGiveUp)
        }
    }
    val map: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            HungaryMap(
                data = data,
                countyStates = state.countyStates,
                blindMap = blindMap,
                showLabels = false,
                onCountyTap = if (cityMode) null else onCountyTap,
                onMapTap = if (cityMode) { lon, lat, _ -> onMapTap(lon, lat) } else null,
                guess = state.lastGuess?.guess,
            )
            if (state.phase == Phase.Idle) {
                StartCard(state.mode, onNewGame, Modifier.align(Alignment.Center))
            }
        }
    }

    if (landscape) {
        // Phone on its side: controls on the left, the map gets the full height on the right.
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(340.dp).fillMaxHeight().verticalScroll(rememberScrollState())) { panel() }
            map(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            panel()
            map(Modifier.weight(1f))
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

/** The small line above the big prompt, and the big prompt itself. */
@Composable
private fun promptTexts(data: MapData, round: RoundState, q: Question): Pair<String, String> {
    val progress = stringResource(R.string.game_progress, round.index + 1, round.total)
    fun countyName(id: String): String {
        val c = data.countyById[id] ?: return id
        return if (c.info.isCapital) c.name else "${c.name} megye"
    }
    return when (q.kind) {
        PromptKind.FindCounty ->
            stringResource(R.string.game_find) + " " + progress to countyName(q.subject)
        PromptKind.CountyOfSeat ->
            stringResource(R.string.game_seat_of) + " " + progress to (data.cityById[q.subject]?.name ?: q.subject)
        PromptKind.NeighboursOf -> stringResource(
            R.string.game_neighbours_of, round.foundInCurrent.size, q.answers.size,
        ) + " · " + progress to countyName(q.subject)
        PromptKind.FindCity ->
            stringResource(R.string.game_where_is) + " " + progress to (data.cityById[q.subject]?.name ?: q.subject)
        PromptKind.NextWestToEast ->
            stringResource(R.string.game_west_to_east) to progress
    }
}

@Composable
private fun PromptBar(
    data: MapData,
    round: RoundState,
    lastGuess: LastGuess?,
    onHint: () -> Unit,
    onGiveUp: () -> Unit,
) {
    val q = round.current ?: return
    val (small, big) = promptTexts(data, round, q)
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Text(
            small,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            big,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("prompt"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                lastGuess?.let {
                    stringResource(
                        R.string.game_last_guess,
                        data.cityById[it.cityId]?.name.orEmpty(),
                        it.km.toInt(),
                        it.points,
                    )
                }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag("last-guess"),
            )
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
}

@StringRes
private fun GameMode.helpText(): Int = when (this) {
    GameMode.Megyek -> R.string.game_help_megyek
    GameMode.Szekhelyek -> R.string.game_help_szekhelyek
    GameMode.Szomszedok -> R.string.game_help_szomszedok
    GameMode.Varosok -> R.string.game_help_varosok
    GameMode.Felsorolas -> R.string.game_help_felsorolas
    GameMode.Attekintes -> R.string.game_help_megyek
}

@Composable
private fun StartCard(mode: GameMode, onNewGame: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.padding(24.dp).widthIn(max = 420.dp)) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(mode.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(mode.helpText()),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.game_help_common),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onValueChange = { name = it.take(InputSanitizer.MAX_NICKNAME_LENGTH * 2) },
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
                Text(stringResource(R.string.result_correct, round.hits, round.hitsAvailable))
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
