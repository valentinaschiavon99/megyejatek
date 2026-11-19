package io.github.valentinaschiavon99.megyejatek.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolygonMathTest {

    private fun ring(vararg pts: Pair<Double, Double>) =
        Ring(pts.map { it.first }.toDoubleArray(), pts.map { it.second }.toDoubleArray())

    private val square = ring(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 0.0 to 10.0)
    private val hole = ring(4.0 to 4.0, 6.0 to 4.0, 6.0 to 6.0, 4.0 to 6.0)

    @Test fun insideAndOutsideSquare() {
        assertTrue(PolygonMath.pointInRing(5.0, 5.0, square))
        assertTrue(PolygonMath.pointInRing(0.1, 9.9, square))
        assertFalse(PolygonMath.pointInRing(-0.1, 5.0, square))
        assertFalse(PolygonMath.pointInRing(5.0, 10.1, square))
        assertFalse(PolygonMath.pointInRing(20.0, 20.0, square))
    }

    @Test fun concaveRing() {
        // A "U": the notch between the arms is outside.
        val u = ring(0.0 to 0.0, 9.0 to 0.0, 9.0 to 9.0, 6.0 to 9.0, 6.0 to 3.0, 3.0 to 3.0, 3.0 to 9.0, 0.0 to 9.0)
        assertTrue(PolygonMath.pointInRing(1.5, 6.0, u))
        assertTrue(PolygonMath.pointInRing(7.5, 6.0, u))
        assertFalse(PolygonMath.pointInRing(4.5, 6.0, u))
        assertTrue(PolygonMath.pointInRing(4.5, 1.5, u))
    }

    @Test fun holesAreOutside() {
        val withHole = GeoPolygon(listOf(square, hole))
        assertFalse(PolygonMath.pointInPolygon(5.0, 5.0, withHole))
        assertTrue(PolygonMath.pointInPolygon(2.0, 2.0, withHole))
    }

    @Test fun multiPolygonAnyPart() {
        val far = ring(20.0 to 20.0, 22.0 to 20.0, 22.0 to 22.0, 20.0 to 22.0)
        val shape = GeoShape(listOf(GeoPolygon(listOf(square)), GeoPolygon(listOf(far))))
        assertTrue(PolygonMath.pointInShape(21.0, 21.0, shape))
        assertTrue(PolygonMath.pointInShape(1.0, 1.0, shape))
        assertFalse(PolygonMath.pointInShape(15.0, 15.0, shape))
    }

    @Test fun rayThroughVertexCountsOnce() {
        val diamond = ring(5.0 to 0.0, 10.0 to 5.0, 5.0 to 10.0, 0.0 to 5.0)
        assertTrue(PolygonMath.pointInRing(5.0, 5.0, diamond))
        assertFalse(PolygonMath.pointInRing(-1.0, 5.0, diamond))
        assertFalse(PolygonMath.pointInRing(11.0, 5.0, diamond))
    }

    @Test fun budapestToDebrecenDistance() {
        // Roughly 194 km as the crow flies.
        val d = PolygonMath.distanceKm(19.0402, 47.4979, 21.6273, 47.5316)
        assertEquals(194.0, d, 4.0)
    }
}
