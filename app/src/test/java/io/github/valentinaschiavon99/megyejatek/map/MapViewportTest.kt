package io.github.valentinaschiavon99.megyejatek.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapViewportTest {
    private val w = 1000f
    private val h = 600f

    @Test fun zoomKeepsPointUnderFingersFixed() {
        val v = MapViewport.Identity.transformed(2f, 0f, 0f, 400f, 300f, w, h)
        assertEquals(2f, v.scale)
        assertEquals(400f, v.toScreenX(400f), 0.01f)
        assertEquals(300f, v.toScreenY(300f), 0.01f)
    }

    @Test fun scaleIsClamped() {
        assertEquals(MapViewport.MIN_SCALE, MapViewport.Identity.transformed(0.2f, 0f, 0f, 0f, 0f, w, h).scale)
        assertEquals(MapViewport.MAX_SCALE, MapViewport.Identity.transformed(50f, 0f, 0f, 0f, 0f, w, h).scale)
    }

    @Test fun cannotPanAtZoomOne() {
        val v = MapViewport.Identity.transformed(1f, 300f, -200f, 0f, 0f, w, h)
        assertEquals(0f, v.offsetX)
        assertEquals(0f, v.offsetY)
    }

    @Test fun panIsClampedToContentEdges() {
        val zoomed = MapViewport(scale = 2f)
        val v = zoomed.transformed(1f, -5000f, -5000f, 0f, 0f, w, h)
        assertEquals(w - w * 2f, v.offsetX)
        assertEquals(h - h * 2f, v.offsetY)
    }

    @Test fun screenContentRoundTrip() {
        val v = MapViewport(scale = 3f, offsetX = -120f, offsetY = -80f)
        assertEquals(123f, v.toContentX(v.toScreenX(123f)), 0.001f)
        assertEquals(45f, v.toContentY(v.toScreenY(45f)), 0.001f)
    }
}
