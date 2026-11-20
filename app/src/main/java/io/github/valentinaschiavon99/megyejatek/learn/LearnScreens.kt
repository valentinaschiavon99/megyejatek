package io.github.valentinaschiavon99.megyejatek.learn

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ChevronRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.data.map.County
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.map.CountyVisual
import io.github.valentinaschiavon99.megyejatek.map.HungaryMap
import io.github.valentinaschiavon99.megyejatek.map.MapLink
import java.text.Collator
import java.text.NumberFormat
import java.util.Locale

enum class InfoPage { Megye, Faq, Sources }

private val hungarian: Locale = Locale.forLanguageTag("hu")
private fun formatInt(n: Int): String = NumberFormat.getIntegerInstance(hungarian).format(n)
private fun County.displayName(): String = if (info.isCapital) name else "$name megye"

/** Waits for the map data, then shows [content]. */
@Composable
private fun WithData(viewModel: LearnViewModel, content: @Composable (MapData) -> Unit) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    val d = data
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        content(d)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearnScaffold(title: String, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            },
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

// ---- home ---------------------------------------------------------------------------------

@Composable
fun LearnHomeScreen(
    onCounties: () -> Unit,
    onNeighbours: () -> Unit,
    onCities: () -> Unit,
    onInfo: (InfoPage) -> Unit,
    viewModel: LearnViewModel = hiltViewModel(),
) {
    val data by viewModel.data.collectAsStateWithLifecycle()
    LearnScaffold(stringResource(R.string.learn_title), onBack = null) {
        LazyColumn(Modifier.fillMaxSize().testTag("learn-home")) {
            item { Entry(R.string.learn_counties, stringResource(R.string.learn_counties_desc), onCounties) }
            item { Entry(R.string.learn_neighbours, stringResource(R.string.learn_neighbours_desc), onNeighbours) }
            item {
                Entry(R.string.learn_cities, stringResource(R.string.learn_cities_desc, data?.cities?.size ?: 0), onCities)
            }
            item { Entry(R.string.learn_megye, stringResource(R.string.learn_megye_desc)) { onInfo(InfoPage.Megye) } }
            item { Entry(R.string.learn_faq, stringResource(R.string.learn_faq_desc)) { onInfo(InfoPage.Faq) } }
            item { Entry(R.string.learn_sources, stringResource(R.string.learn_sources_desc)) { onInfo(InfoPage.Sources) } }
        }
    }
}

@Composable
private fun Entry(@StringRes title: Int, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title), style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

// ---- counties -----------------------------------------------------------------------------

@Composable
fun CountyListScreen(onBack: () -> Unit, onCounty: (String) -> Unit, viewModel: LearnViewModel = hiltViewModel()) {
    LearnScaffold(stringResource(R.string.learn_counties), onBack) {
        WithData(viewModel) { data ->
            val collator = remember { Collator.getInstance(hungarian) }
            val sorted = remember(data) {
                data.counties.sortedWith(compareBy<County> { it.info.isCapital }.thenComparator { a, b -> collator.compare(a.name, b.name) })
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(sorted, key = { it.id }) { c ->
                    val seat = c.info.seatCityId?.let { data.cityById[it]?.name }.orEmpty()
                    ListItem(
                        headlineContent = { Text(c.displayName(), fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                (if (c.info.isCapital) stringResource(R.string.capital) else stringResource(R.string.county_seat, seat)) +
                                    " · " + stringResource(R.string.county_summary, c.info.neighbours.size, c.info.cityIds.size),
                            )
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Outlined.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { onCounty(c.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CountyDetailScreen(
    countyId: String,
    onBack: () -> Unit,
    onCounty: (String) -> Unit,
    viewModel: LearnViewModel = hiltViewModel(),
) {
    WithData(viewModel) { data ->
        val c = data.countyById[countyId] ?: return@WithData
        LearnScaffold(c.displayName(), onBack) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                HungaryMap(
                    data = data,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    countyStates = c.info.neighbours.associateWith { CountyVisual.Hinted } + (c.id to CountyVisual.Highlighted),
                    showCities = true,
                    visibleCityIds = c.info.cityIds.toSet() + listOfNotNull(c.info.seatCityId),
                    showLabels = false,
                    interactive = false,
                )
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val seat = c.info.seatCityId?.let { data.cityById[it] }
                    if (c.info.isCapital) {
                        Text(stringResource(R.string.budapest_note), style = MaterialTheme.typography.bodyMedium)
                    } else if (seat != null) {
                        Text(stringResource(R.string.county_seat, seat.name), style = MaterialTheme.typography.titleMedium)
                    }
                    if (c.id == "HU-PE") {
                        Text(stringResource(R.string.pest_note), style = MaterialTheme.typography.bodyMedium)
                    }

                    Text(stringResource(R.string.county_neighbours), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (n in c.info.neighbours.mapNotNull { data.countyById[it] }) {
                            AssistChip(onClick = { onCounty(n.id) }, label = { Text(n.name) })
                        }
                    }

                    Text(stringResource(R.string.county_cities), style = MaterialTheme.typography.titleSmall)
                    val cities = c.info.cityIds.mapNotNull { data.cityById[it] }
                    if (cities.isEmpty()) {
                        Text(stringResource(R.string.county_no_cities), style = MaterialTheme.typography.bodyMedium)
                    }
                    for (city in cities) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                city.name + if (city.isSeat) " ★" else "",
                                modifier = Modifier.weight(1f),
                                fontWeight = if (city.isSeat) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                stringResource(R.string.city_population, formatInt(city.population)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- neighbours ---------------------------------------------------------------------------

@Composable
fun NeighboursScreen(onBack: () -> Unit, onCounty: (String) -> Unit, viewModel: LearnViewModel = hiltViewModel()) {
    LearnScaffold(stringResource(R.string.learn_neighbours), onBack) {
        WithData(viewModel) { data ->
            val links = remember(data) {
                data.counties.flatMap { a ->
                    a.info.neighbours.filter { it > a.id }.mapNotNull { id -> data.countyById[id] }.map { b ->
                        MapLink(a.info.label[0], a.info.label[1], b.info.label[0], b.info.label[1])
                    }
                }
            }
            Column(Modifier.fillMaxSize()) {
                HungaryMap(
                    data = data,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    showLabels = false,
                    links = links,
                    onCountyTap = onCounty,
                )
                Text(
                    stringResource(R.string.neighbours_count, links.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(data.counties.sortedBy { it.info.westToEast }, key = { it.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                Text(c.info.neighbours.mapNotNull { data.countyById[it]?.name }.joinToString(", "))
                            },
                            modifier = Modifier.clickable { onCounty(c.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ---- cities -------------------------------------------------------------------------------

@Composable
fun CitiesScreen(onBack: () -> Unit, viewModel: LearnViewModel = hiltViewModel()) {
    LearnScaffold(stringResource(R.string.learn_cities), onBack) {
        WithData(viewModel) { data ->
            var filter by rememberSaveable { mutableStateOf<String?>(null) }
            val ranked = remember(data) { data.cities.sortedByDescending { it.population } }
            val shown = ranked.filter { filter == null || it.countyId == filter }
            Column(Modifier.fillMaxSize()) {
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Box(Modifier.width(8.dp)) }
                    item {
                        FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text(stringResource(R.string.cities_all)) })
                    }
                    items(data.counties.sortedBy { it.info.westToEast }, key = { it.id }) { c ->
                        FilterChip(selected = filter == c.id, onClick = { filter = c.id }, label = { Text(c.name) })
                    }
                    item { Box(Modifier.width(8.dp)) }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f).testTag("cities-list")) {
                    itemsIndexed(shown, key = { _, c -> c.id }) { _, city ->
                        val rank = ranked.indexOf(city) + 1
                        ListItem(
                            leadingContent = { Text("$rank.", style = MaterialTheme.typography.titleMedium) },
                            headlineContent = {
                                Text(city.name + if (city.isSeat) " ★" else "", fontWeight = if (city.isSeat) FontWeight.Bold else FontWeight.Normal)
                            },
                            supportingContent = { Text(data.countyById[city.countyId]?.displayName().orEmpty()) },
                            trailingContent = { Text(stringResource(R.string.city_population, formatInt(city.population))) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ---- info pages ---------------------------------------------------------------------------

@Composable
fun InfoScreen(page: InfoPage, onBack: () -> Unit) {
    val title = when (page) {
        InfoPage.Megye -> R.string.learn_megye
        InfoPage.Faq -> R.string.learn_faq
        InfoPage.Sources -> R.string.learn_sources
    }
    LearnScaffold(stringResource(title), onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (page) {
                InfoPage.Megye -> listOf(R.string.megye_p1, R.string.megye_p2, R.string.megye_p3, R.string.megye_p4, R.string.megye_p5)
                    .forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
                InfoPage.Faq -> listOf(
                    R.string.faq_q1 to R.string.faq_a1,
                    R.string.faq_q2 to R.string.faq_a2,
                    R.string.faq_q3 to R.string.faq_a3,
                    R.string.faq_q4 to R.string.faq_a4,
                    R.string.faq_q5 to R.string.faq_a5,
                    R.string.faq_q6 to R.string.faq_a6,
                ).forEach { (q, a) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(q), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(a), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                InfoPage.Sources -> listOf(R.string.sources_osm, R.string.sources_ksh, R.string.sources_ne, R.string.sources_code, R.string.sources_note)
                    .forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
