package io.github.valentinaschiavon99.megyejatek.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class ProjectionTest {
    private val hungary = GeoBounds(16.11, 45.74, 22.90, 48.59)

    @Test fun roundTrip() {
        val p = Projection(hungary, 1080f, 720f, padding = 24f)
        for ((lon, lat) in listOf(19.04 to 47.50, 16.11 to 45.74, 22.90 to 48.59, 20.15 to 46.25)) {
            assertEquals(lon, p.lon(p.x(lon)), 1e-4)
            assertEquals(lat, p.lat(p.y(lat)), 1e-4)
        }
    }

    @Test fun northIsUpEastIsRight() {
        val p = Projection(hungary, 1000f, 1000f)
        assertTrue(p.y(48.0) < p.y(46.0))
        assertTrue(p.x(22.0) > p.x(17.0))
    }

    @Test fun fitsInsidePaddingAndCentres() {
        val w = 1000f; val h = 1000f; val pad = 20f
        val p = Projection(hungary, w, h, pad)
        val left = p.x(hungary.minLon); val right = p.x(hungary.maxLon)
        val top = p.y(hungary.maxLat); val bottom = p.y(hungary.minLat)
        assertTrue(left >= pad - 0.01f && right <= w - pad + 0.01f)
        assertTrue(top >= pad - 0.01f && bottom <= h - pad + 0.01f)
        // Hungary is wider than tall, so in a square box it touches the side paddings…
        assertEquals(pad, left, 0.01f)
        assertEquals(w - pad, right, 0.01f)
        // …and is centred vertically.
        assertEquals(h / 2, (top + bottom) / 2, 0.01f)
    }

    @Test fun cosineCorrectionKeepsRealProportions() {
        val p = Projection(hungary, 2000f, 2000f)
        val oneDegLon = p.x(20.0) - p.x(19.0)
        val oneDegLat = p.y(47.0) - p.y(48.0)
        assertEquals(cos(Math.toRadians(hungary.centerLat)), (oneDegLon / oneDegLat).toDouble(), 1e-4)
    }
}
