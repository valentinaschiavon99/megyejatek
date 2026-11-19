package io.github.valentinaschiavon99.megyejatek.game.engine

import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import kotlin.random.Random

enum class GameMode(val title: String, val implemented: Boolean, val key: String, val scored: Boolean = true) {
    Megyek("Megyék", true, "megyek"),
    Szekhelyek("Székhelyek", false, "szekhelyek"),
    Szomszedok("Szomszédok", false, "szomszedok"),
    Varosok("Városok", false, "varosok"),
    Felsorolas("Felsorolás", false, "felsorolas"),
    Attekintes("Áttekintés", true, "attekintes", scored = false),
}

object QuestionFactory {
    /** Megyék: every county and Budapest once, in random order. */
    fun megyek(data: MapData, random: Random = Random.Default): List<Question> =
        data.counties.shuffled(random).map { Question(id = it.id, prompt = it.name, answerId = it.id) }
}
