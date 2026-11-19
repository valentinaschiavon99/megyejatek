package io.github.valentinaschiavon99.megyejatek.game.engine

/** One question: show [prompt], the right answer is the county [answerId]. */
data class Question(val id: String, val prompt: String, val answerId: String)

enum class Outcome { Correct, Revealed, GivenUp }

/** What a single tap did, so the UI can flash, vibrate and move on. */
sealed interface TapResult {
    data class Correct(val answerId: String, val points: Int) : TapResult
    data class Wrong(val tappedId: String, val attemptsLeft: Int) : TapResult
    /** Out of attempts: the answer is shown and the game moves to the next question. */
    data class Revealed(val tappedId: String, val answerId: String) : TapResult
    /** Tap on something already answered, or the round is over. */
    data object Ignored : TapResult
}

/**
 * Immutable state of one round. All rules live in [RoundRules]; this class only holds data,
 * which keeps the scoring easy to unit-test.
 */
data class RoundState(
    val questions: List<Question>,
    val index: Int = 0,
    val score: Int = 0,
    val streak: Int = 0,
    val wrongOnCurrent: Int = 0,
    val outcomes: Map<String, Outcome> = emptyMap(),
    val hintsUsed: Int = 0,
    val mistakes: Int = 0,
    val timeBonus: Int = 0,
    val finished: Boolean = false,
    val gaveUp: Boolean = false,
) {
    val current: Question? get() = questions.getOrNull(index)?.takeUnless { finished }
    val total: Int get() = questions.size
    val correctCount: Int get() = outcomes.values.count { it == Outcome.Correct }
}

object RoundRules {

    fun multiplier(state: RoundState, config: ScoringConfig) = config.multiplierFor(state.streak)

    fun tap(state: RoundState, tappedId: String, config: ScoringConfig): Pair<RoundState, TapResult> {
        val q = state.current ?: return state to TapResult.Ignored
        // Tapping a county that was already found is not a mistake, just nothing.
        if (tappedId != q.answerId && state.questions.any { it.answerId == tappedId && it.id in state.outcomes }) {
            return state to TapResult.Ignored
        }
        if (tappedId == q.answerId) {
            val points = config.basePoints * config.multiplierFor(state.streak)
            val next = state.copy(
                score = state.score + points,
                streak = state.streak + 1,
                outcomes = state.outcomes + (q.id to Outcome.Correct),
            ).advance()
            return next to TapResult.Correct(q.answerId, points)
        }
        val wrong = state.wrongOnCurrent + 1
        val base = state.copy(streak = 0, mistakes = state.mistakes + 1)
        return if (wrong >= config.attemptsPerQuestion) {
            base.copy(outcomes = state.outcomes + (q.id to Outcome.Revealed)).advance() to
                TapResult.Revealed(tappedId, q.answerId)
        } else {
            base.copy(wrongOnCurrent = wrong) to TapResult.Wrong(tappedId, config.attemptsPerQuestion - wrong)
        }
    }

    /** Súgás: resets the multiplier and costs points. Returns the id to flash, or null. */
    fun hint(state: RoundState, config: ScoringConfig): Pair<RoundState, String?> {
        val q = state.current ?: return state to null
        return state.copy(
            streak = 0,
            score = (state.score - config.hintCost).coerceAtLeast(0),
            hintsUsed = state.hintsUsed + 1,
        ) to q.answerId
    }

    /** Feladom: ends the round; every unanswered question counts as given up, no time bonus. */
    fun giveUp(state: RoundState): RoundState {
        if (state.finished) return state
        val rest = state.questions.filter { it.id !in state.outcomes }.associate { it.id to Outcome.GivenUp }
        return state.copy(outcomes = state.outcomes + rest, finished = true, gaveUp = true)
    }

    /** Adds the time bonus once the last question is answered. */
    fun complete(state: RoundState, elapsedMs: Long, config: ScoringConfig): RoundState {
        if (!state.finished || state.gaveUp || state.timeBonus > 0) return state
        val bonus = config.timeBonus(state.total, elapsedMs)
        return state.copy(timeBonus = bonus, score = state.score + bonus)
    }

    private fun RoundState.advance(): RoundState {
        val nextIndex = index + 1
        return copy(index = nextIndex, wrongOnCurrent = 0, finished = nextIndex >= questions.size)
    }
}
