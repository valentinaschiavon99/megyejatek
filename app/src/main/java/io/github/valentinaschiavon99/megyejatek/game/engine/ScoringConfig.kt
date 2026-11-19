package io.github.valentinaschiavon99.megyejatek.game.engine

/**
 * Every number that decides the score, in one place so it is easy to tune.
 * firebase/firestore.rules caps uploaded scores with the defaults below (500 points per hit,
 * 30 bonus points per answer): keep the two in sync.
 */
data class ScoringConfig(
    /** Points for a correct answer, before the multiplier. */
    val basePoints: Int = 100,
    /** The multiplier grows by one after this many correct answers in a row. */
    val streakPerStep: Int = 3,
    val maxMultiplier: Int = 5,
    /** Subtracted for each Súgás (the score never goes below zero). */
    val hintCost: Int = 50,
    /** Wrong taps allowed on one question; after that the answer is shown and the game moves on. */
    val attemptsPerQuestion: Int = 3,
    /** Time bonus: every second under [parSecondsPerAnswer] × answers is worth this much. */
    val timeBonusPerSecond: Int = 5,
    val parSecondsPerAnswer: Int = 6,
    /** Városok: full points within this distance of the city… */
    val placeFullKm: Double = 5.0,
    /** …nothing from this distance on, linear in between. */
    val placeZeroKm: Double = 75.0,
    /** A guess this close counts as a hit for the multiplier. */
    val placeGoodKm: Double = 15.0,
) {
    fun multiplierFor(streak: Int): Int = (1 + streak / streakPerStep).coerceAtMost(maxMultiplier)

    fun timeBonus(answers: Int, elapsedMs: Long): Int {
        val par = parSecondsPerAnswer.toLong() * answers
        val spare = par - elapsedMs / 1000
        return if (spare > 0) (spare * timeBonusPerSecond).toInt() else 0
    }

    /** Base points (before the multiplier) for a Városok guess [km] away from the city. */
    fun placePoints(km: Double): Int = when {
        km <= placeFullKm -> basePoints
        km >= placeZeroKm -> 0
        else -> (basePoints * (placeZeroKm - km) / (placeZeroKm - placeFullKm)).toInt()
    }

    companion object {
        val Default = ScoringConfig()
    }
}
