package io.github.valentinaschiavon99.megyejatek.core.geo

import kotlin.math.cos
import kotlin.math.min

/**
 * Equirectangular projection with a cos(latitude) correction, fitted and centred into a
 * width x height box with [padding] on every side. Accurate enough for a country the size
 * of Hungary, and affine, so a point is inside a projected polygon exactly when its
 * un-projected lon/lat is inside the original one.
 */
class Projection(
    val bounds: GeoBounds,
    val width: Float,
    val height: Float,
    padding: Float = 0f,
) {
    /** Horizontal shrink factor so a degree of longitude has its true length at the map's centre. */
    val lonFactor: Double = cos(Math.toRadians(bounds.centerLat))

    /** Pixels per (corrected) degree. */
    val scale: Double
    private val offsetX: Double
    private val offsetY: Double

    init {
        val spanX = (bounds.maxLon - bounds.minLon) * lonFactor
        val spanY = bounds.maxLat - bounds.minLat
        require(spanX > 0 && spanY > 0) { "empty bounds" }
        val availW = (width - 2 * padding).coerceAtLeast(1f)
        val availH = (height - 2 * padding).coerceAtLeast(1f)
        scale = min(availW / spanX, availH / spanY)
        offsetX = (width - spanX * scale) / 2
        offsetY = (height - spanY * scale) / 2
    }

    fun x(lon: Double): Float = ((lon - bounds.minLon) * lonFactor * scale + offsetX).toFloat()

    fun y(lat: Double): Float = ((bounds.maxLat - lat) * scale + offsetY).toFloat()

    fun lon(x: Float): Double = (x - offsetX) / scale / lonFactor + bounds.minLon

    fun lat(y: Float): Double = bounds.maxLat - (y - offsetY) / scale
}
