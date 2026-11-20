package io.github.valentinaschiavon99.megyejatek.game.engine

/** What the prompt asks; the UI turns it into Hungarian text. */
enum class PromptKind(
    /** Answers found in earlier questions stay found: tapping them again is not a mistake. */
    val oneShotAnswers: Boolean,
) {
    /** Megyék: tap the county called [Question.subject]. */
    FindCounty(true),
    /** Székhelyek: [Question.subject] is a seat (city id); tap its county. */
    CountyOfSeat(true),
    /** Szomszédok: [Question.subject] is highlighted; tap every neighbour. */
    NeighboursOf(false),
    /** Városok: [Question.subject] is a city id; tap where it is. */
    FindCity(false),
    /** Felsorolás: tap the next county from west to east. */
    NextWestToEast(true),
}

/**
 * One question.
 * @param answers ids that count as correct (counties, or the city for [PromptKind.FindCity])
 * @param needAll every answer must be found (Szomszédok); otherwise any one of them ends the question
 * @param hintIds what Súgás flashes; the answers by default
 */
data class Question(
    val id: String,
    val kind: PromptKind,
    val subject: String,
    val answers: Set<String>,
    val needAll: Boolean = false,
    val hintIds: Set<String> = answers,
) {
    /** How many correct taps this question is worth. */
    val hitsAvailable: Int get() = if (needAll) answers.size else 1
}

enum class Outcome { Correct, Revealed, GivenUp }

/** What a single tap did, so the UI can flash, vibrate and move on. */
sealed interface TapResult {
    data class Correct(val answerId: String, val points: Int, val questionDone: Boolean) : TapResult
    data class Wrong(val tappedId: String, val livesLeft: Int) : TapResult
    /** The last life is gone: the round ends and the answers of the current question are shown. */
    data class OutOfLives(val tappedId: String, val missing: Set<String>) : TapResult
    /** Városok: one guess per city, scored by distance. */
    data class Guess(val km: Double, val points: Int, val good: Boolean) : TapResult
    /** Tap on something already answered, on the question's own county, or the round is over. */
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
    /** Answers already found in the current question (Szomszédok). */
    val foundInCurrent: Set<String> = emptySet(),
    val outcomes: Map<String, Outcome> = emptyMap(),
    val hits: Int = 0,
    val hintsUsed: Int = 0,
    /** Wrong taps and far-off guesses in the whole round; each one costs a life. */
    val mistakes: Int = 0,
    val timeBonus: Int = 0,
    val finished: Boolean = false,
    val gaveUp: Boolean = false,
    /** Ended because every life was used up. */
    val outOfLives: Boolean = false,
) {
    fun livesLeft(config: ScoringConfig): Int = (config.livesPerRound - mistakes).coerceAtLeast(0)
    fun hintsLeft(config: ScoringConfig): Int = (config.hintsPerRound - hintsUsed).coerceAtLeast(0)

    val current: Question? get() = questions.getOrNull(index)?.takeUnless { finished }
    val total: Int get() = questions.size
    val correctCount: Int get() = outcomes.values.count { it == Outcome.Correct }
    /** Correct taps possible in the whole round; the denominator shown in results. */
    val hitsAvailable: Int get() = questions.sumOf { it.hitsAvailable }

    /** Ids answered in finished questions whose answers can't be asked again. */
    val settledAnswers: Set<String>
        get() = questions.take(index)
            .filter { it.kind.oneShotAnswers && outcomes[it.id] != null }
            .flatMapTo(mutableSetOf()) { q -> if (q.kind == PromptKind.NextWestToEast) setOf(q.subject) else q.answers }
}

object RoundRules {

    fun multiplier(state: RoundState, config: ScoringConfig) = config.multiplierFor(state.streak)

    fun tap(state: RoundState, tappedId: String, config: ScoringConfig): Pair<RoundState, TapResult> {
        val q = state.current ?: return state to TapResult.Ignored
        if (q.kind == PromptKind.FindCity) return state to TapResult.Ignored
        if (tappedId in state.foundInCurrent) return state to TapResult.Ignored
        if (q.kind == PromptKind.NeighboursOf && tappedId == q.subject) return state to TapResult.Ignored
        if (q.kind.oneShotAnswers && tappedId in state.settledAnswers) return state to TapResult.Ignored

        if (tappedId in q.answers) {
            val points = config.basePoints * config.multiplierFor(state.streak)
            val found = state.foundInCurrent + tappedId
            val done = !q.needAll || found.containsAll(q.answers)
            var next = state.copy(
                score = state.score + points,
                streak = state.streak + 1,
                hits = state.hits + 1,
                foundInCurrent = found,
            )
            if (q.kind == PromptKind.NextWestToEast && tappedId != q.subject) next = next.bringForward(tappedId)
            if (done) next = next.copy(outcomes = next.outcomes + (next.questions[next.index].id to Outcome.Correct)).advance()
            return next to TapResult.Correct(tappedId, points, done)
        }

        val next = state.copy(streak = 0, mistakes = state.mistakes + 1)
        return if (next.livesLeft(config) == 0) {
            val missing = if (q.needAll) q.answers - state.foundInCurrent else q.hintIds
            next.endOutOfLives() to TapResult.OutOfLives(tappedId, missing)
        } else {
            next to TapResult.Wrong(tappedId, next.livesLeft(config))
        }
    }

    /** Városok: one guess, [km] from the asked city. */
    fun guess(state: RoundState, km: Double, config: ScoringConfig): Pair<RoundState, TapResult> {
        val q = state.current ?: return state to TapResult.Ignored
        if (q.kind != PromptKind.FindCity) return state to TapResult.Ignored
        val good = km <= config.placeGoodKm
        val points = config.placePoints(km) * config.multiplierFor(state.streak)
        var next = state.copy(
            score = state.score + points,
            streak = if (good) state.streak + 1 else 0,
            hits = state.hits + if (points > 0) 1 else 0,
            mistakes = state.mistakes + if (good) 0 else 1,
            outcomes = state.outcomes + (q.id to if (good) Outcome.Correct else Outcome.Revealed),
        )
        next = if (next.livesLeft(config) == 0) next.endOutOfLives() else next.advance()
        return next to TapResult.Guess(km, points, good)
    }

    /** Súgás: resets the multiplier and costs points; once per round. Returns the ids to flash. */
    fun hint(state: RoundState, config: ScoringConfig): Pair<RoundState, Set<String>> {
        val q = state.current ?: return state to emptySet()
        if (state.hintsLeft(config) == 0) return state to emptySet()
        val ids = if (q.needAll) q.answers - state.foundInCurrent else q.hintIds
        return state.copy(
            streak = 0,
            score = (state.score - config.hintCost).coerceAtLeast(0),
            hintsUsed = state.hintsUsed + 1,
        ) to ids
    }

    /** Feladom: ends the round; every unanswered question counts as given up, no time bonus. */
    fun giveUp(state: RoundState): RoundState {
        if (state.finished) return state
        val rest = state.questions.filter { it.id !in state.outcomes }.associate { it.id to Outcome.GivenUp }
        return state.copy(outcomes = state.outcomes + rest, finished = true, gaveUp = true)
    }

    /** Adds the time bonus once the last question is answered. */
    fun complete(state: RoundState, elapsedMs: Long, config: ScoringConfig): RoundState {
        if (!state.finished || state.gaveUp || state.outOfLives || state.timeBonus > 0) return state
        val bonus = config.timeBonus(state.hitsAvailable, elapsedMs)
        return state.copy(timeBonus = bonus, score = state.score + bonus)
    }

    /** No lives left: the current question counts as missed, the rest as not reached. */
    private fun RoundState.endOutOfLives(): RoundState {
        val q = questions[index]
        val current = if (q.id in outcomes) emptyMap() else mapOf(q.id to Outcome.Revealed)
        val rest = questions.filter { it.id !in outcomes && it.id != q.id }.associate { it.id to Outcome.GivenUp }
        return copy(outcomes = outcomes + current + rest, foundInCurrent = emptySet(), finished = true, outOfLives = true)
    }

    private fun RoundState.advance(): RoundState {
        val nextIndex = index + 1
        return copy(
            index = nextIndex,
            foundInCurrent = emptySet(),
            finished = nextIndex >= questions.size,
        )
    }

    /** Felsorolás: a close-enough later county was tapped; swap it into the current slot. */
    private fun RoundState.bringForward(subject: String): RoundState {
        val j = questions.indexOfFirst { it.subject == subject }
        if (j <= index) return this
        val list = questions.toMutableList()
        val tmp = list[index]
        list[index] = list[j]
        list[j] = tmp
        return copy(questions = list)
    }
}
