package io.github.valentinaschiavon99.megyejatek.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundRulesTest {
    private val config = ScoringConfig(
        basePoints = 100, streakPerStep = 2, maxMultiplier = 3, hintCost = 50,
        attemptsPerQuestion = 3, timeBonusPerSecond = 10, parSecondsPerQuestion = 5,
    )
    private val questions = listOf("A", "B", "C", "D", "E", "F").map { Question(it, "Q$it", it) }
    private fun fresh() = RoundState(questions)

    private fun RoundState.tap(id: String) = RoundRules.tap(this, id, config)

    @Test fun correctAnswersGrowTheMultiplier() {
        var s = fresh()
        val points = mutableListOf<Int>()
        for (id in listOf("A", "B", "C", "D", "E", "F")) {
            val (next, r) = s.tap(id)
            points += (r as TapResult.Correct).points
            s = next
        }
        // streak 0,1 -> x1; 2,3 -> x2; 4,5 -> x3 (capped at 3)
        assertEquals(listOf(100, 100, 200, 200, 300, 300), points)
        assertEquals(1200, s.score)
        assertTrue(s.finished)
        assertEquals(6, s.correctCount)
    }

    @Test fun multiplierIsCapped() {
        assertEquals(3, config.multiplierFor(100))
        assertEquals(1, config.multiplierFor(0))
    }

    @Test fun mistakeResetsMultiplierButKeepsQuestion() {
        var s = fresh().tap("A").first.tap("B").first // streak 2 -> x2
        assertEquals(2, RoundRules.multiplier(s, config))
        val (afterWrong, r) = s.tap("F")
        assertTrue(r is TapResult.Wrong)
        assertEquals(2, (r as TapResult.Wrong).attemptsLeft)
        assertEquals(1, RoundRules.multiplier(afterWrong, config))
        assertEquals("C", afterWrong.current?.answerId)
        s = afterWrong
        val (_, ok) = s.tap("C")
        assertEquals(100, (ok as TapResult.Correct).points)
    }

    @Test fun outOfAttemptsRevealsAndMovesOn() {
        var s = fresh()
        s = s.tap("B").first
        s = s.tap("C").first
        val (next, r) = s.tap("D")
        assertTrue(r is TapResult.Revealed)
        assertEquals("A", (r as TapResult.Revealed).answerId)
        assertEquals(Outcome.Revealed, next.outcomes["A"])
        assertEquals("B", next.current?.answerId)
        assertEquals(0, next.score)
        assertEquals(3, next.mistakes)
    }

    @Test fun tappingAnAlreadyFoundCountyIsIgnored() {
        val s = fresh().tap("A").first
        val (same, r) = s.tap("A")
        assertEquals(TapResult.Ignored, r)
        assertEquals(s, same)
    }

    @Test fun hintCostsPointsResetsStreakAndNeverGoesNegative() {
        var s = fresh().tap("A").first.tap("B").first // 200 points, streak 2
        val (afterHint, flash) = RoundRules.hint(s, config)
        assertEquals("C", flash)
        assertEquals(150, afterHint.score)
        assertEquals(0, afterHint.streak)
        assertEquals(1, afterHint.hintsUsed)
        s = fresh()
        assertEquals(0, RoundRules.hint(s, config).first.score)
    }

    @Test fun giveUpEndsRoundWithoutTimeBonus() {
        val s = RoundRules.giveUp(fresh().tap("A").first)
        assertTrue(s.finished && s.gaveUp)
        assertEquals(Outcome.Correct, s.outcomes["A"])
        assertEquals(5, s.outcomes.values.count { it == Outcome.GivenUp })
        assertEquals(s, RoundRules.complete(s, 1_000, config))
        assertEquals(null, s.current)
    }

    @Test fun timeBonusOnlyOnceAndOnlyWhenFinished() {
        var s = fresh()
        assertEquals(s, RoundRules.complete(s, 1_000, config)) // not finished
        for (id in listOf("A", "B", "C", "D", "E", "F")) s = s.tap(id).first
        val before = s.score
        // par = 6 × 5 s = 30 s; finished in 12 s -> 18 s spare × 10
        val done = RoundRules.complete(s, 12_000, config)
        assertEquals(180, done.timeBonus)
        assertEquals(before + 180, done.score)
        assertEquals(done, RoundRules.complete(done, 12_000, config))
    }

    @Test fun noTimeBonusWhenSlow() {
        assertEquals(0, config.timeBonus(questions = 6, elapsedMs = 60_000))
        assertFalse(config.timeBonus(questions = 6, elapsedMs = 0) == 0)
    }
}
