package io.github.valentinaschiavon99.megyejatek.game.engine

import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import kotlin.math.abs
import kotlin.random.Random

enum class GameMode(val title: String, val implemented: Boolean, val key: String, val scored: Boolean = true) {
    Megyek("Megyék", true, "megyek"),
    Szekhelyek("Székhelyek", true, "szekhelyek"),
    Szomszedok("Szomszédok", true, "szomszedok"),
    Varosok("Városok", true, "varosok"),
    Felsorolas("Felsorolás", true, "felsorolas"),
    Attekintes("Áttekintés", true, "attekintes", scored = false),
}

object QuestionFactory {
    const val NEIGHBOUR_QUESTIONS = 8
    const val CITY_QUESTIONS = 15
    /** Felsorolás: counties whose label points are this close in longitude may be tapped in either order. */
    const val WEST_EAST_TOLERANCE_DEG = 0.12

    fun forMode(mode: GameMode, data: MapData, random: Random = Random.Default): List<Question> = when (mode) {
        GameMode.Megyek -> megyek(data, random)
        GameMode.Szekhelyek -> szekhelyek(data, random)
        GameMode.Szomszedok -> szomszedok(data, random)
        GameMode.Varosok -> varosok(data, random)
        GameMode.Felsorolas -> felsorolas(data)
        GameMode.Attekintes -> emptyList()
    }

    /** Megyék: every county and Budapest once, in random order. */
    fun megyek(data: MapData, random: Random = Random.Default): List<Question> =
        data.counties.shuffled(random).map {
            Question(id = it.id, kind = PromptKind.FindCounty, subject = it.id, answers = setOf(it.id))
        }

    /**
     * Székhelyek: name a seat, tap its county. Budapest is Pest's seat but not part of it, so for
     * that question tapping Budapest also counts (it is where the city is).
     */
    fun szekhelyek(data: MapData, random: Random = Random.Default): List<Question> =
        data.counties.filter { !it.info.isCapital && it.info.seatCityId != null }
            .shuffled(random)
            .map { c ->
                val seat = c.info.seatCityId!!
                val seatCounty = data.cityById[seat]?.countyId
                val answers = if (seatCounty != null && seatCounty != c.id) setOf(c.id, seatCounty) else setOf(c.id)
                Question(id = c.id, kind = PromptKind.CountyOfSeat, subject = seat, answers = answers, hintIds = setOf(c.id))
            }

    /** Szomszédok: a highlighted county, tap all of its neighbours. */
    fun szomszedok(data: MapData, random: Random = Random.Default): List<Question> =
        data.counties.shuffled(random).take(NEIGHBOUR_QUESTIONS).map { c ->
            Question(
                id = c.id,
                kind = PromptKind.NeighboursOf,
                subject = c.id,
                answers = c.info.neighbours.toSet(),
                needAll = true,
            )
        }

    /** Városok: a city name, tap where it is; the hint shows its county. */
    fun varosok(data: MapData, random: Random = Random.Default): List<Question> =
        data.cities.shuffled(random).take(CITY_QUESTIONS).map { city ->
            Question(
                id = city.id,
                kind = PromptKind.FindCity,
                subject = city.id,
                answers = setOf(city.id),
                hintIds = setOf(city.countyId),
            )
        }

    /**
     * Felsorolás: all areas from west to east (by label point). Counties that are almost level
     * may be tapped in either order.
     */
    fun felsorolas(data: MapData): List<Question> {
        val ordered = data.counties.sortedBy { it.info.westToEast }
        return ordered.map { c ->
            val lon = c.info.label[0]
            val near = ordered.filter { abs(it.info.label[0] - lon) <= WEST_EAST_TOLERANCE_DEG }.map { it.id }
            Question(id = c.id, kind = PromptKind.NextWestToEast, subject = c.id, answers = near.toSet(), hintIds = setOf(c.id))
        }
    }
}
