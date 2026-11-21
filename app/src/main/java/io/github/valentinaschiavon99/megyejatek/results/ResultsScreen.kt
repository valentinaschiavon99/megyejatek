package io.github.valentinaschiavon99.megyejatek.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.game.ui.formatTime

private data class ScoreRow(val name: String, val score: Int, val timeMs: Long, val correct: Int, val total: Int, val highlight: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(onSignIn: () -> Unit = {}, viewModel: ResultsViewModel = hiltViewModel()) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val personal by viewModel.personal.collectAsStateWithLifecycle()
    val global by viewModel.global.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        if (player != null) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.records_logged_in_as, player!!.username),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (viewModel.canLogout) {
                        TextButton(onClick = viewModel::logout) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                            Text(" " + stringResource(R.string.records_logout))
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (m in viewModel.modes) {
                FilterChip(selected = m == mode, onClick = { viewModel.selectMode(m) }, label = { Text(m.title) })
            }
        }

        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            Tab(selected = tab == RecordsTab.Mine, onClick = { viewModel.selectTab(RecordsTab.Mine) },
                text = { Text(stringResource(R.string.records_mine)) })
            Tab(selected = tab == RecordsTab.Global, onClick = { viewModel.selectTab(RecordsTab.Global) },
                text = { Text(stringResource(R.string.records_global)) })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                RecordsTab.Mine -> ScoreList(
                    personal.map { ScoreRow(it.playerName, it.score, it.timeMs, it.correct, it.total, highlight = false) },
                )
                RecordsTab.Global -> if (player == null && viewModel.canLogout) {
                    // Guests: the leaderboard is only readable with an account.
                    Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Message(R.string.results_guest_global)
                            Button(onClick = onSignIn) { Text(stringResource(R.string.auth_sign_in_cta)) }
                        }
                    }
                } else when (val g = global) {
                    GlobalState.Idle, GlobalState.Loading -> Centered { CircularProgressIndicator() }
                    GlobalState.NotAvailable -> Centered { Message(R.string.records_global_offline) }
                    GlobalState.Error -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Message(R.string.records_global_error)
                            TextButton(onClick = viewModel::loadGlobal) { Text(stringResource(R.string.records_retry)) }
                        }
                    }
                    is GlobalState.Loaded -> ScoreList(
                        g.scores.map { ScoreRow(it.username, it.score, it.timeMs, it.correct, it.total, it.isMine) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreList(rows: List<ScoreRow>) {
    if (rows.isEmpty()) {
        Centered { Message(R.string.records_empty) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(rows) { i, r ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(40.dp),
                    color = if (i < 3) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        r.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (r.highlight) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        stringResource(R.string.records_row_detail, formatTime(r.timeMs), r.correct, r.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(R.string.records_points, r.score), style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Message(res: Int) {
    Text(stringResource(res), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
