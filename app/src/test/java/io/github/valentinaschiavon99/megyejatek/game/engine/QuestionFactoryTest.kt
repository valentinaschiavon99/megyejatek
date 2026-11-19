package io.github.valentinaschiavon99.megyejatek.game.engine

import io.github.valentinaschiavon99.megyejatek.core.data.map.MapAssets
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import io.github.valentinaschiavon99.megyejatek.core.data.map.MapDataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Builds every mode's questions from the bundled map data. */
class QuestionFactoryTest {

    private val data: MapData by lazy {
        val dir = File("src/main/assets/map")
        fun read(name: String) = File(dir, name).readText()
        MapDataParser.parse(
            MapAssets(read("counties.geojson"), read("outline.geojson"), read("water.geojson"),
                read("counties.json"), read("cities.json")),
        )
    }

    @Test fun megyekAsksEveryAreaOnce() {
        val qs = QuestionFactory.megyek(data, Random(1))
        assertEquals(20, qs.size)
        assertEquals(data.counties.map { it.id }.toSet(), qs.map { it.subject }.toSet())
    }

    @Test fun szekhelyekSkipsBudapestAndAcceptsItForPest() {
        val qs = QuestionFactory.szekhelyek(data, Random(1))
        assertEquals(19, qs.size)
        val pest = qs.single { it.id == "HU-PE" }
        assertEquals("budapest", pest.subject)
        assertEquals(setOf("HU-PE", "HU-BU"), pest.answers)
        assertEquals(setOf("HU-PE"), pest.hintIds)
        qs.filter { it.id != "HU-PE" }.forEach { assertEquals(setOf(it.id), it.answers) }
    }

    @Test fun szomszedokUsesRealNeighbours() {
        val qs = QuestionFactory.szomszedok(data, Random(1))
        assertEquals(QuestionFactory.NEIGHBOUR_QUESTIONS, qs.size)
        for (q in qs) {
            assertTrue(q.needAll)
            assertEquals(data.countyById.getValue(q.subject).info.neighbours.toSet(), q.answers)
        }
    }

    @Test fun varosokHintsTheCounty() {
        val qs = QuestionFactory.varosok(data, Random(1))
        assertEquals(QuestionFactory.CITY_QUESTIONS, qs.size)
        for (q in qs) assertEquals(setOf(data.cityById.getValue(q.subject).countyId), q.hintIds)
    }

    @Test fun felsorolasGoesWestToEast() {
        val qs = QuestionFactory.felsorolas(data)
        assertEquals(20, qs.size)
        val lons = qs.map { data.countyById.getValue(it.subject).info.label[0] }
        assertEquals(lons.sorted(), lons)
        qs.forEach { assertTrue(it.subject in it.answers) }
        // A whole round played in the listed order must finish with every answer correct.
        var s = RoundState(qs)
        for (q in qs) s = RoundRules.tap(s, q.subject, ScoringConfig.Default).first
        assertTrue(s.finished)
        assertEquals(20, s.correctCount)
    }
}
