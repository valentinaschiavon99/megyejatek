package io.github.valentinaschiavon99.megyejatek.core.geo

/** A closed ring of lon/lat points (first point not repeated at the end). */
class Ring(val lons: DoubleArray, val lats: DoubleArray) {
    init {
        require(lons.size == lats.size) { "lon/lat arrays differ in length" }
    }
    val size: Int get() = lons.size
}

/** First ring is the outer boundary, the rest are holes. */
class GeoPolygon(val rings: List<Ring>) {
    val outer: Ring get() = rings.first()
    val holes: List<Ring> get() = rings.drop(1)
}

/** One feature's area: a Polygon or MultiPolygon. */
class GeoShape(val polygons: List<GeoPolygon>) {
    val bounds: GeoBounds by lazy { GeoBounds.of(polygons.flatMap { it.rings }) }
}

/** An open polyline (river). */
class GeoLine(val lons: DoubleArray, val lats: DoubleArray)

data class GeoBounds(val minLon: Double, val minLat: Double, val maxLon: Double, val maxLat: Double) {
    val centerLat: Double get() = (minLat + maxLat) / 2

    fun union(other: GeoBounds) = GeoBounds(
        minOf(minLon, other.minLon), minOf(minLat, other.minLat),
        maxOf(maxLon, other.maxLon), maxOf(maxLat, other.maxLat),
    )

    fun contains(lon: Double, lat: Double) = lon in minLon..maxLon && lat in minLat..maxLat

    companion object {
        fun of(rings: List<Ring>): GeoBounds {
            var minLon = Double.POSITIVE_INFINITY
            var minLat = Double.POSITIVE_INFINITY
            var maxLon = Double.NEGATIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            for (r in rings) for (i in 0 until r.size) {
                minLon = minOf(minLon, r.lons[i]); maxLon = maxOf(maxLon, r.lons[i])
                minLat = minOf(minLat, r.lats[i]); maxLat = maxOf(maxLat, r.lats[i])
            }
            return GeoBounds(minLon, minLat, maxLon, maxLat)
        }
    }
}
