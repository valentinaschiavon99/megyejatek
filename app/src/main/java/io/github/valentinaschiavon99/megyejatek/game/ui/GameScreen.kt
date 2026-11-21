package io.github.valentinaschiavon99.megyejatek.game.ui

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.security.InputSanitizer
import io.github.valentinaschiavon99.megyejatek.core.ui.components.AnimatedNumber
import io.github.valentinaschiavon99.megyejatek.core.ui.components.Confetti
import io.github.valentinaschiavon99.megyejatek.core.ui.components.IconBadge
import io.github.valentinaschiavon99.megyejatek.core.ui.components.Lives
import io.github.valentinaschiavon99.megyejatek.core.ui.components.MultiplierBadge
import io.github.valentinaschiavon99.megyejatek.core.ui.components.PointsPopup
import io.github.valentinaschiavon99.megyejatek.core.ui.components.SlidingText
import io.github.valentinaschiavon99.megyejatek.core.ui.components.StarRating
import io.github.valentinaschiavon99.megyejatek.core.ui.components.StatPill
import io.github.valentinaschiavon99.megyejatek.core.ui.components.icon
import io.github.valentinaschiavon99.megyejatek.core.ui.components.label
import io.github.valentinaschiavon99.megyejatek.game.engine.GameMode
import io.github.valentinaschiavon99.megyejatek.game.engine.PromptKind
import io.github.valentinaschiavon99.megyejatek.game.engine.Question
import io.github.valentinaschiavon99.megyejatek.game.engine.RoundState
import io.github.valentinaschiavon99.megyejatek.map.HungaryMap

@Composable
fun GameScreen(
    mode: GameMode,
    modePicker: @Composable () -> Unit = {},
    viewModel: GameViewModel = hiltViewModel(key = "game-${mode.key}"),
) {
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
        modePicker = modePicker,
    )
}

/** Stateless so it can be driven directly from UI tests and screenshot tests. */
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
    modePicker: @Composable () -> Unit = {},
) {
    val data = state.data
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val round = state.round
    val playing = state.phase == Phase.Playing && round != null
    val cityMode = round?.current?.kind == PromptKind.FindCity || state.mode == GameMode.Varosok
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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
            if (state.phase == Phase.Idle && !landscape) {
                StartCard(state.mode, state.best, onNewGame, Modifier.align(Alignment.Center).padding(20.dp))
            }
        }
    }

    if (landscape) {
        // Phone on its side: everything to read and press on the left, the map at full height on the right.
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modePicker()
                GameHeader(state, blindMap, onBlindMapChange)
                if (playing) {
                    PromptCard(data, state, round!!, onHint, onGiveUp, compact = true)
                } else if (state.phase == Phase.Idle) {
                    StartCard(state.mode, state.best, onNewGame, Modifier.padding(horizontal = 12.dp), compact = true)
                }
            }
            map(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            modePicker()
            GameHeader(state, blindMap, onBlindMapChange)
            if (playing) PromptCard(data, state, round!!, onHint, onGiveUp, compact = false)
            map(Modifier.weight(1f))
        }
    }

    if (state.showNewGameDialog) {
        NewGameDialog(onStart = onDialogStart, onDismiss = onDialogDismiss)
    }
    if (state.phase == Phase.Finished && round != null) {
        ResultDialog(state, round, onNewGame = onResultNewGame, onClose = onResultClose)
    }
}

@Composable
private fun GameHeader(state: GameUiState, blindMap: Boolean, onBlindMapChange: (Boolean) -> Unit) {
    val round = state.round
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Pills merge their children, so test tags go on the pill itself.
            StatPill(Icons.Outlined.Timer, scheme.surfaceContainerHigh, scheme.onSurfaceVariant, Modifier.testTag("timer")) {
                Text(formatTime(state.elapsedMs), style = MaterialTheme.typography.labelLarge)
            }
            MultiplierBadge(
                state.multiplier,
                label = stringResource(R.string.game_multiplier, state.multiplier),
                modifier = Modifier.testTag("multiplier"),
            )
            Box(contentAlignment = Alignment.Center) {
                StatPill(Icons.Rounded.EmojiEvents, scheme.primary, scheme.onPrimary, Modifier.testTag("score")) {
                    AnimatedNumber(
                        value = round?.score ?: 0,
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimary,
                        suffix = " " + stringResource(R.string.points_short),
                    )
                }
                PointsPopup(state.award?.points, state.award?.id ?: 0L, Modifier.align(Alignment.TopCenter))
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconToggleButton(checked = blindMap, onCheckedChange = onBlindMapChange) {
                Icon(
                    if (blindMap) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(R.string.blind_map),
                )
            }
        }
        if (round != null && state.phase != Phase.Idle) {
            val target = (round.index.toFloat() / round.total.coerceAtLeast(1)).coerceIn(0f, 1f)
            val progress by animateFloatAsState(target, label = "progress")
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp),
                color = scheme.secondary,
                trackColor = scheme.surfaceContainerHigh,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

/** The small line above the big prompt, and the big prompt itself. */
@Composable
private fun promptTexts(data: MapData, round: RoundState, q: Question): Pair<String, String> {
    val progress = stringResource(R.string.game_progress, round.index + 1, round.total)
    val countyFormat = stringResource(R.string.county_display)
    fun countyName(id: String): String {
        val c = data.countyById[id] ?: return id
        return if (c.info.isCapital) c.name else countyFormat.format(c.name)
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
private fun PromptCard(
    data: MapData,
    state: GameUiState,
    round: RoundState,
    onHint: () -> Unit,
    onGiveUp: () -> Unit,
    compact: Boolean,
) {
    val q = round.current ?: return
    val mode = state.mode
    val lastGuess = state.lastGuess
    val (small, big) = promptTexts(data, round, q)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!compact) IconBadge(mode.icon, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(small, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SlidingText(big, MaterialTheme.typography.titleLarge, Modifier.testTag("prompt"))
                }
                Lives(
                    left = state.livesLeft,
                    max = state.maxLives,
                    label = stringResource(R.string.game_lives, state.livesLeft),
                    modifier = Modifier.align(Alignment.Top).testTag("lives"),
                )
            }
            if (lastGuess != null) {
                Text(
                    stringResource(
                        R.string.game_last_guess,
                        data.cityById[lastGuess.cityId]?.name.orEmpty(),
                        lastGuess.km.toInt(),
                        lastGuess.points,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("last-guess"),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onHint, enabled = state.hintsLeft > 0, modifier = Modifier.testTag("hint")) {
                    Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.game_hint))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        state.hintsLeft.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
                TextButton(onClick = onGiveUp) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.game_give_up))
                }
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
private fun StartCard(mode: GameMode, best: Int?, onNewGame: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Card(
        modifier.widthIn(max = 420.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            Modifier.padding(if (compact) 16.dp else 24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconBadge(mode.icon, size = if (compact) 52.dp else 72.dp)
            Text(mode.label(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("start-title"))
            Text(stringResource(mode.helpText()), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (!compact) {
                Text(
                    stringResource(R.string.game_help_common),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (best != null) {
                StatPill(Icons.Rounded.EmojiEvents, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text(
                        stringResource(R.string.game_best, best),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.game_new), style = MaterialTheme.typography.titleMedium)
            }
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
private fun ResultDialog(state: GameUiState, round: RoundState, onNewGame: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Box {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("result"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(
                            when {
                                round.gaveUp -> R.string.result_title_gave_up
                                round.outOfLives -> R.string.result_title_out_of_lives
                                else -> R.string.result_title_done
                            },
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    StarRating(state.stars)
                    AnimatedNumber(
                        value = round.score,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.points_long), style = MaterialTheme.typography.labelLarge)
                    if (state.newRecord) {
                        StatPill(Icons.Rounded.EmojiEvents, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary) {
                            Text(
                                stringResource(R.string.result_new_record),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondary,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    ResultRow(stringResource(R.string.result_label_time), formatTime(state.elapsedMs))
                    ResultRow(stringResource(R.string.result_label_correct), "${round.hits} / ${round.hitsAvailable}")
                    if (round.timeBonus > 0) ResultRow(stringResource(R.string.result_label_bonus), "+${round.timeBonus}")
                    ResultRow(stringResource(R.string.result_label_lives), "${state.livesLeft} / ${state.maxLives}")
                    if (round.hintsUsed > 0) ResultRow(stringResource(R.string.result_label_hints), round.hintsUsed.toString())
                    Spacer(Modifier.height(4.dp))
                    Button(onClick = onNewGame, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text(stringResource(R.string.game_new), style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.dialog_close)) }
                }
            }
            if (state.stars == 3 || state.newRecord) Confetti(Modifier.matchParentSize())
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** m:ss */
fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
