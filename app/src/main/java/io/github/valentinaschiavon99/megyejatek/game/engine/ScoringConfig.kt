package io.github.valentinaschiavon99.megyejatek.game.engine

/** Every number that decides the score, in one place so it is easy to tune. */
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
    /** Time bonus: every second under [parSecondsPerQuestion] × questions is worth this much. */
    val timeBonusPerSecond: Int = 5,
    val parSecondsPerQuestion: Int = 6,
) {
    fun multiplierFor(streak: Int): Int = (1 + streak / streakPerStep).coerceAtMost(maxMultiplier)

    fun timeBonus(questions: Int, elapsedMs: Long): Int {
        val par = parSecondsPerQuestion.toLong() * questions
        val spare = par - elapsedMs / 1000
        return if (spare > 0) (spare * timeBonusPerSecond).toInt() else 0
    }

    companion object {
        val Default = ScoringConfig()
    }
}
