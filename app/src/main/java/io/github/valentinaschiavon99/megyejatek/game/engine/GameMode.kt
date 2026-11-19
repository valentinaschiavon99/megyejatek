package io.github.valentinaschiavon99.megyejatek.game.engine

import io.github.valentinaschiavon99.megyejatek.core.data.map.MapData
import kotlin.random.Random

enum class GameMode(val title: String, val implemented: Boolean) {
    Megyek("Megyék", true),
    Szekhelyek("Székhelyek", false),
    Szomszedok("Szomszédok", false),
    Varosok("Városok", false),
    Felsorolas("Felsorolás", false),
    Attekintes("Áttekintés", true),
}

object QuestionFactory {
    /** Megyék: every county and Budapest once, in random order. */
    fun megyek(data: MapData, random: Random = Random.Default): List<Question> =
        data.counties.shuffled(random).map { Question(id = it.id, prompt = it.name, answerId = it.id) }
}
