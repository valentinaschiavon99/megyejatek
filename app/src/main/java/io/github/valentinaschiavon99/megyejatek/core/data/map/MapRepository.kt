package io.github.valentinaschiavon99.megyejatek.core.data.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Loads assets/map/ once per process and keeps the parsed result. */
@Singleton
class MapRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    @Volatile private var cached: MapData? = null

    suspend fun mapData(): MapData = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.Default) {
            MapDataParser.parse(readAssets())
        }.also { cached = it }
    }

    private suspend fun readAssets(): MapAssets = withContext(Dispatchers.IO) {
        fun read(name: String) = context.assets.open("map/$name").bufferedReader().use { it.readText() }
        MapAssets(
            countiesGeoJson = read("counties.geojson"),
            outlineGeoJson = read("outline.geojson"),
            waterGeoJson = read("water.geojson"),
            countiesJson = read("counties.json"),
            citiesJson = read("cities.json"),
        )
    }
}
