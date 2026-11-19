package io.github.valentinaschiavon99.megyejatek.core.data.map

import io.github.valentinaschiavon99.megyejatek.core.geo.PolygonMath
import io.github.valentinaschiavon99.megyejatek.core.geo.Projection
import io.github.valentinaschiavon99.megyejatek.map.MapHitTester
import io.github.valentinaschiavon99.megyejatek.map.MapViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the files actually shipped in assets/map/. Unit tests run with the module dir as cwd. */
class BundledMapDataTest {

    private val data: MapData by lazy {
        val dir = File("src/main/assets/map")
        fun read(name: String) = File(dir, name).readText()
        MapDataParser.parse(
            MapAssets(
                countiesGeoJson = read("counties.geojson"),
                outlineGeoJson = read("outline.geojson"),
                waterGeoJson = read("water.geojson"),
                countiesJson = read("counties.json"),
                citiesJson = read("cities.json"),
            ),
        )
    }

    @Test fun twentyAreasWithBudapest() {
        assertEquals(20, data.counties.size)
        assertEquals(19, data.counties.count { !it.info.isCapital })
        assertTrue(data.countyById.getValue("HU-BU").info.isCapital)
    }

    @Test fun neighboursAreSymmetric() {
        for (c in data.counties) {
            assertTrue("${c.name} has no neighbours", c.info.neighbours.isNotEmpty())
            for (n in c.info.neighbours) {
                val other = data.countyById[n]
                assertNotNull("${c.id}: unknown neighbour $n", other)
                assertTrue("${c.id} -> $n is not mutual", c.id in other!!.info.neighbours)
            }
        }
    }

    @Test fun pestSeatIsBudapestButBudapestIsNotInPest() {
        val pest = data.countyById.getValue("HU-PE").info
        assertEquals("budapest", pest.seatCityId)
        assertTrue("budapest" !in pest.cityIds)
        assertEquals("HU-BU", data.cityById.getValue("budapest").countyId)
        assertTrue("HU-BU" in pest.neighbours)
    }

    @Test fun everySeatExistsAndLiesInsideItsCounty() {
        for (c in data.counties) {
            val seat = data.cityById[c.info.seatCityId]
            assertNotNull("${c.name}: missing seat", seat)
            if (c.id != "HU-PE") {
                assertTrue("${seat!!.name} outside ${c.name}", PolygonMath.pointInShape(seat.lon, seat.lat, c.shape))
            }
        }
    }

    @Test fun tappingEachLabelPointHitsThatCounty() {
        val projection = Projection(data.bounds, 1080f, 720f, 24f)
        val hit = MapHitTester(data)
        for (c in data.counties) {
            val x = projection.x(c.info.label[0])
            val y = projection.y(c.info.label[1])
            assertEquals(c.id, hit.countyAt(projection, x, y))
        }
        assertNull(hit.countyAt(projection, 1f, 1f))
    }

    @Test fun cityToleranceIsInScreenPixels() {
        val projection = Projection(data.bounds, 1080f, 720f, 24f)
        val hit = MapHitTester(data)
        val bp = data.cityById.getValue("budapest")
        for (vp in listOf(MapViewport.Identity, MapViewport(scale = 4f, offsetX = -1500f, offsetY = -900f))) {
            val sx = vp.toScreenX(projection.x(bp.lon))
            val sy = vp.toScreenY(projection.y(bp.lat))
            assertEquals("budapest", hit.cityAt(projection, vp, sx + 10f, sy, tolerancePx = 20f))
            assertNull(hit.cityAt(projection, vp, sx + 30f, sy, tolerancePx = 20f, candidates = setOf("budapest")))
        }
    }

    @Test fun westToEastIsAPermutation() {
        assertEquals((1..20).toList(), data.counties.map { it.info.westToEast }.sorted())
        val vas = data.countyById.getValue("HU-VA").info.westToEast
        val szabolcs = data.countyById.getValue("HU-SZ").info.westToEast
        assertTrue(vas < szabolcs)
    }
}
