package io.github.valentinaschiavon99.megyejatek.core.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object PolygonMath {

    /** Even-odd ray casting. Points exactly on an edge may land on either side. */
    fun pointInRing(lon: Double, lat: Double, ring: Ring): Boolean {
        val xs = ring.lons
        val ys = ring.lats
        var inside = false
        var j = ring.size - 1
        for (i in 0 until ring.size) {
            val yi = ys[i]
            val yj = ys[j]
            if ((yi > lat) != (yj > lat)) {
                val xCross = xs[i] + (lat - yi) * (xs[j] - xs[i]) / (yj - yi)
                if (lon < xCross) inside = !inside
            }
            j = i
        }
        return inside
    }

    fun pointInPolygon(lon: Double, lat: Double, polygon: GeoPolygon): Boolean =
        pointInRing(lon, lat, polygon.outer) && polygon.holes.none { pointInRing(lon, lat, it) }

    fun pointInShape(lon: Double, lat: Double, shape: GeoShape): Boolean =
        shape.bounds.contains(lon, lat) && shape.polygons.any { pointInPolygon(lon, lat, it) }

    /** Great-circle distance in km (haversine). */
    fun distanceKm(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
