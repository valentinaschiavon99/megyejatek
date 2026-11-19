package io.github.valentinaschiavon99.megyejatek.map

/**
 * Zoom and pan of the map, as screen = content * scale + offset.
 * "Content" is the projected map at zoom 1, filling the canvas.
 */
data class MapViewport(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun toScreenX(contentX: Float) = contentX * scale + offsetX
    fun toScreenY(contentY: Float) = contentY * scale + offsetY
    fun toContentX(screenX: Float) = (screenX - offsetX) / scale
    fun toContentY(screenY: Float) = (screenY - offsetY) / scale

    /**
     * Applies one pinch/pan step. Zooming keeps the content under the fingers' [centroidX]/[centroidY]
     * fixed, and the result is clamped so the map can't be dragged off screen.
     */
    fun transformed(
        zoom: Float,
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float,
        width: Float,
        height: Float,
    ): MapViewport {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val k = newScale / scale
        val nx = (offsetX - centroidX) * k + centroidX + panX
        val ny = (offsetY - centroidY) * k + centroidY + panY
        return MapViewport(newScale, nx, ny).clamped(width, height)
    }

    /** At zoom 1 the map is centred; zoomed in, the content must still cover the canvas edges. */
    fun clamped(width: Float, height: Float): MapViewport {
        val minX = width - width * scale
        val minY = height - height * scale
        return copy(
            offsetX = offsetX.coerceIn(minOf(minX, 0f), 0f),
            offsetY = offsetY.coerceIn(minOf(minY, 0f), 0f),
        )
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 8f
        val Identity = MapViewport()
    }
}
