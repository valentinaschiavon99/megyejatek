package io.github.valentinaschiavon99.megyejatek.core.data.map

import io.github.valentinaschiavon99.megyejatek.core.geo.GeoBounds
import io.github.valentinaschiavon99.megyejatek.core.geo.GeoLine
import io.github.valentinaschiavon99.megyejatek.core.geo.GeoShape
import kotlinx.serialization.Serializable

/** One entry of assets/map/counties.json. Budapest is included with type "capital". */
@Serializable
data class CountyInfo(
    val id: String,
    val name: String,
    val type: String,
    val seatCityId: String? = null,
    val neighbours: List<String> = emptyList(),
    val cityIds: List<String> = emptyList(),
    /** [lon, lat] of a point well inside the county, for its label. */
    val label: List<Double>,
    /** 1 = westernmost label point. */
    val westToEast: Int,
) {
    val isCapital: Boolean get() = type == "capital"
}

/** One entry of assets/map/cities.json. */
@Serializable
data class CityInfo(
    val id: String,
    val name: String,
    val countyId: String,
    val population: Int,
    val lon: Double,
    val lat: Double,
    val isSeat: Boolean = false,
)

class County(val info: CountyInfo, val shape: GeoShape) {
    val id: String get() = info.id
    val name: String get() = info.name
}

class Lake(val shape: GeoShape)

/** Everything the map and the game need, loaded once from assets. */
class MapData(
    val counties: List<County>,
    val outline: GeoShape,
    val rivers: List<GeoLine>,
    val lakes: List<Lake>,
    val cities: List<CityInfo>,
) {
    val bounds: GeoBounds = outline.bounds
    val countyById: Map<String, County> = counties.associateBy { it.id }
    val cityById: Map<String, CityInfo> = cities.associateBy { it.id }
}
