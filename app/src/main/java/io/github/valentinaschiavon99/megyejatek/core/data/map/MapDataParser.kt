package io.github.valentinaschiavon99.megyejatek.core.data.map

import io.github.valentinaschiavon99.megyejatek.core.geo.GeoLine
import io.github.valentinaschiavon99.megyejatek.core.geo.GeoPolygon
import io.github.valentinaschiavon99.megyejatek.core.geo.GeoShape
import io.github.valentinaschiavon99.megyejatek.core.geo.Ring
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Raw file contents of assets/map/, so parsing can be unit-tested without Android. */
class MapAssets(
    val countiesGeoJson: String,
    val outlineGeoJson: String,
    val waterGeoJson: String,
    val countiesJson: String,
    val citiesJson: String,
)

object MapDataParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(assets: MapAssets): MapData {
        val infos = json.decodeFromString<List<CountyInfo>>(assets.countiesJson).associateBy { it.id }

        val counties = features(assets.countiesGeoJson).mapNotNull { f ->
            val id = f.properties["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val info = infos[id] ?: error("counties.geojson has $id but counties.json does not")
            County(info, shape(f.geometry))
        }.sortedBy { it.info.westToEast }
        check(counties.size == infos.size) { "counties.json and counties.geojson disagree" }

        val outline = GeoShape(features(assets.outlineGeoJson).flatMap { shape(it.geometry).polygons })

        val rivers = mutableListOf<GeoLine>()
        val lakes = mutableListOf<Lake>()
        for (f in features(assets.waterGeoJson)) {
            when (f.properties["kind"]?.jsonPrimitive?.content) {
                "lake" -> lakes += Lake(shape(f.geometry))
                else -> rivers += lines(f.geometry)
            }
        }

        val cities = json.decodeFromString<List<CityInfo>>(assets.citiesJson)
        return MapData(counties, outline, rivers, lakes, cities)
    }

    private class Feature(val properties: JsonObject, val geometry: JsonObject)

    private fun features(geoJson: String): List<Feature> {
        val root = json.parseToJsonElement(geoJson).jsonObject
        return root["features"]!!.jsonArray.mapNotNull { el ->
            val obj = el.jsonObject
            val geometry = obj["geometry"] as? JsonObject ?: return@mapNotNull null
            Feature(obj["properties"] as? JsonObject ?: JsonObject(emptyMap()), geometry)
        }
    }

    internal fun shape(geometry: JsonObject): GeoShape {
        val coords = geometry["coordinates"]!!.jsonArray
        return when (val type = geometry["type"]!!.jsonPrimitive.content) {
            "Polygon" -> GeoShape(listOf(polygon(coords)))
            "MultiPolygon" -> GeoShape(coords.map { polygon(it.jsonArray) })
            else -> error("expected a polygon, got $type")
        }
    }

    private fun lines(geometry: JsonObject): List<GeoLine> {
        val coords = geometry["coordinates"]!!.jsonArray
        return when (val type = geometry["type"]!!.jsonPrimitive.content) {
            "LineString" -> listOf(line(coords))
            "MultiLineString" -> coords.map { line(it.jsonArray) }
            else -> error("expected a line, got $type")
        }
    }

    private fun polygon(rings: JsonArray) = GeoPolygon(rings.map { ring(it.jsonArray) })

    private fun ring(points: JsonArray): Ring {
        // GeoJSON repeats the first point at the end; drop it.
        val n = if (points.size > 1 && points.first() == points.last()) points.size - 1 else points.size
        val lons = DoubleArray(n)
        val lats = DoubleArray(n)
        for (i in 0 until n) {
            val p = points[i].jsonArray
            lons[i] = p[0].num()
            lats[i] = p[1].num()
        }
        return Ring(lons, lats)
    }

    private fun line(points: JsonArray): GeoLine {
        val lons = DoubleArray(points.size)
        val lats = DoubleArray(points.size)
        points.forEachIndexed { i, el ->
            val p = el.jsonArray
            lons[i] = p[0].num()
            lats[i] = p[1].num()
        }
        return GeoLine(lons, lats)
    }

    private fun JsonElement.num(): Double = jsonPrimitive.double
}
