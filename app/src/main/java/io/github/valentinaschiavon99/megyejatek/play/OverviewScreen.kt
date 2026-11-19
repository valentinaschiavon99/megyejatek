package io.github.valentinaschiavon99.megyejatek.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.valentinaschiavon99.megyejatek.R
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.map.CountyVisual
import io.github.valentinaschiavon99.megyejatek.map.HungaryMap
import java.text.NumberFormat
import java.util.Locale

@Composable
fun OverviewScreen(viewModel: OverviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val blind by viewModel.blindMap.collectAsStateWithLifecycle()
    val data = state.data

    when {
        state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.map_load_error, state.error!!), color = MaterialTheme.colorScheme.error)
        }
        data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> Column(Modifier.fillMaxSize()) {
            InfoBar(data, state.selectedCountyId, state.selectedCityId, blind, viewModel::setBlindMap)
            HungaryMap(
                data = data,
                modifier = Modifier.weight(1f),
                countyStates = overviewStates(data, state.selectedCountyId),
                cityStates = state.selectedCityId?.let { mapOf(it to CountyVisual.Correct) }.orEmpty(),
                showCities = true,
                blindMap = blind,
                onCountyTap = viewModel::onCountyTap,
                onCityTap = viewModel::onCityTap,
            )
        }
    }
}

@Composable
private fun InfoBar(
    data: MapData,
    countyId: String?,
    cityId: String?,
    blind: Boolean,
    onBlindChange: (Boolean) -> Unit,
) {
    val county = countyId?.let { data.countyById[it] }
    val city = cityId?.let { data.cityById[it] }
    val numbers = NumberFormat.getIntegerInstance(Locale.forLanguageTag("hu"))
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when {
                        city != null -> city.name
                        county != null -> county.name
                        else -> stringResource(R.string.map_tap_hint)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                val detail = when {
                    city != null -> stringResource(
                        R.string.map_city_detail,
                        numbers.format(city.population),
                        data.countyById[city.countyId]?.name.orEmpty(),
                    )
                    county != null -> county.info.seatCityId?.let { data.cityById[it]?.name }
                        ?.let { stringResource(R.string.map_seat, it) }
                    else -> null
                }
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (city == null && county != null) {
                    val neighbours = county.info.neighbours.mapNotNull { data.countyById[it]?.name }.joinToString(", ")
                    Text(
                        stringResource(R.string.map_neighbours, neighbours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val cities = county.info.cityIds.mapNotNull { data.cityById[it]?.name }.joinToString(", ")
                    if (cities.isNotEmpty()) {
                        Text(
                            stringResource(R.string.map_cities, cities),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(stringResource(R.string.blind_map), style = MaterialTheme.typography.labelLarge)
            Switch(checked = blind, onCheckedChange = onBlindChange)
        }
    }
}

/** Selected county in yellow, its neighbours in the hint colour. */
private fun overviewStates(data: MapData, selectedId: String?): Map<String, CountyVisual> {
    val county = selectedId?.let { data.countyById[it] } ?: return emptyMap()
    return county.info.neighbours.associateWith { CountyVisual.Hinted } + (county.id to CountyVisual.Highlighted)
}
