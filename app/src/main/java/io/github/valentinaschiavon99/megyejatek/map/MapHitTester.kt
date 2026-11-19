package io.github.valentinaschiavon99.megyejatek.map

import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.geo.PolygonMath
import io.github.valentinaschiavon99.megyejatek.core.geo.Projection

/** Pure hit-testing, shared by the map composable and the unit tests. */
class MapHitTester(private val data: MapData) {

    /** County under a content-space point (see [MapViewport]), or null outside Hungary. */
    fun countyAt(projection: Projection, contentX: Float, contentY: Float): String? {
        val lon = projection.lon(contentX)
        val lat = projection.lat(contentY)
        // Budapest sits inside a hole of Pest, so testing in any order is safe;
        // checking smaller areas first still avoids surprises with imperfect data.
        return data.counties
            .sortedBy { it.shape.bounds.let { b -> (b.maxLon - b.minLon) * (b.maxLat - b.minLat) } }
            .firstOrNull { PolygonMath.pointInShape(lon, lat, it.shape) }
            ?.id
    }

    /**
     * Nearest city to a screen point, if it is within [tolerancePx] on screen. The tolerance is in
     * screen pixels (from dp), so it stays the same size at any zoom level.
     */
    fun cityAt(
        projection: Projection,
        viewport: MapViewport,
        screenX: Float,
        screenY: Float,
        tolerancePx: Float,
        candidates: Collection<String>? = null,
    ): String? {
        var best: String? = null
        var bestD2 = tolerancePx * tolerancePx
        for (c in data.cities) {
            if (candidates != null && c.id !in candidates) continue
            val dx = viewport.toScreenX(projection.x(c.lon)) - screenX
            val dy = viewport.toScreenY(projection.y(c.lat)) - screenY
            val d2 = dx * dx + dy * dy
            if (d2 <= bestD2) {
                bestD2 = d2
                best = c.id
            }
        }
        return best
    }
}
