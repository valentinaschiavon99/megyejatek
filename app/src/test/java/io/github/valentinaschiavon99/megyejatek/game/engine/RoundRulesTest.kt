package io.github.valentinaschiavon99.megyejatek.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundRulesTest {
    private val config = ScoringConfig(
        basePoints = 100, streakPerStep = 2, maxMultiplier = 3, hintCost = 50,
        attemptsPerQuestion = 3, timeBonusPerSecond = 10, parSecondsPerAnswer = 5,
        placeFullKm = 5.0, placeZeroKm = 55.0, placeGoodKm = 15.0,
    )

    private fun county(id: String) = Question(id, PromptKind.FindCounty, id, setOf(id))
    private val ids = listOf("A", "B", "C", "D", "E", "F")
    private fun megyek() = RoundState(ids.map(::county))
    private fun RoundState.tap(id: String) = RoundRules.tap(this, id, config)

    // ---- Megyék --------------------------------------------------------------------------

    @Test fun correctAnswersGrowTheMultiplier() {
        var s = megyek()
        val points = mutableListOf<Int>()
        for (id in ids) {
            val (next, r) = s.tap(id)
            points += (r as TapResult.Correct).points
            s = next
        }
        // streak 0,1 -> x1; 2,3 -> x2; 4,5 -> x3 (capped at 3)
        assertEquals(listOf(100, 100, 200, 200, 300, 300), points)
        assertEquals(1200, s.score)
        assertTrue(s.finished)
        assertEquals(6, s.correctCount)
        assertEquals(6, s.hits)
        assertEquals(6, s.hitsAvailable)
    }

    @Test fun multiplierIsCapped() {
        assertEquals(3, config.multiplierFor(100))
        assertEquals(1, config.multiplierFor(0))
    }

    @Test fun mistakeResetsMultiplierButKeepsQuestion() {
        val s = megyek().tap("A").first.tap("B").first // streak 2 -> x2
        assertEquals(2, RoundRules.multiplier(s, config))
        val (afterWrong, r) = s.tap("F")
        assertEquals(2, (r as TapResult.Wrong).attemptsLeft)
        assertEquals(1, RoundRules.multiplier(afterWrong, config))
        assertEquals("C", afterWrong.current?.subject)
        assertEquals(100, (afterWrong.tap("C").second as TapResult.Correct).points)
    }

    @Test fun outOfAttemptsRevealsAndMovesOn() {
        val s = megyek().tap("B").first.tap("C").first
        val (next, r) = s.tap("D")
        assertEquals(setOf("A"), (r as TapResult.Revealed).missing)
        assertEquals(Outcome.Revealed, next.outcomes["A"])
        assertEquals("B", next.current?.subject)
        assertEquals(0, next.score)
        assertEquals(3, next.mistakes)
    }

    @Test fun tappingAnAlreadyFoundCountyIsIgnored() {
        val s = megyek().tap("A").first
        val (same, r) = s.tap("A")
        assertEquals(TapResult.Ignored, r)
        assertEquals(s, same)
    }

    @Test fun hintCostsPointsResetsStreakAndNeverGoesNegative() {
        val s = megyek().tap("A").first.tap("B").first // 200 points, streak 2
        val (afterHint, flash) = RoundRules.hint(s, config)
        assertEquals(setOf("C"), flash)
        assertEquals(150, afterHint.score)
        assertEquals(0, afterHint.streak)
        assertEquals(1, afterHint.hintsUsed)
        assertEquals(0, RoundRules.hint(megyek(), config).first.score)
    }

    @Test fun giveUpEndsRoundWithoutTimeBonus() {
        val s = RoundRules.giveUp(megyek().tap("A").first)
        assertTrue(s.finished && s.gaveUp)
        assertEquals(Outcome.Correct, s.outcomes["A"])
        assertEquals(5, s.outcomes.values.count { it == Outcome.GivenUp })
        assertEquals(s, RoundRules.complete(s, 1_000, config))
        assertEquals(null, s.current)
    }

    @Test fun timeBonusOnlyOnceAndOnlyWhenFinished() {
        var s = megyek()
        assertEquals(s, RoundRules.complete(s, 1_000, config)) // not finished
        for (id in ids) s = s.tap(id).first
        val before = s.score
        // par = 6 answers × 5 s = 30 s; finished in 12 s -> 18 s spare × 10
        val done = RoundRules.complete(s, 12_000, config)
        assertEquals(180, done.timeBonus)
        assertEquals(before + 180, done.score)
        assertEquals(done, RoundRules.complete(done, 12_000, config))
        assertEquals(0, config.timeBonus(answers = 6, elapsedMs = 60_000))
    }

    // ---- Székhelyek ----------------------------------------------------------------------

    @Test fun seatQuestionAcceptsEveryListedAnswer() {
        // Pest's seat is Budapest: both Pest and Budapest count, the hint shows Pest.
        val q = Question("PE", PromptKind.CountyOfSeat, "budapest", setOf("PE", "BU"), hintIds = setOf("PE"))
        val (s, r) = RoundState(listOf(q, county("X"))).tap("BU")
        assertTrue((r as TapResult.Correct).questionDone)
        assertEquals(Outcome.Correct, s.outcomes["PE"])
        assertEquals(setOf("PE"), RoundRules.hint(RoundState(listOf(q)), config).second)
    }

    // ---- Szomszédok ----------------------------------------------------------------------

    private fun neighbours() = RoundState(
        listOf(
            Question("N1", PromptKind.NeighboursOf, "P", setOf("A", "B", "C"), needAll = true),
            Question("N2", PromptKind.NeighboursOf, "A", setOf("P", "B"), needAll = true),
        ),
    )

    @Test fun neighboursNeedAllAnswersAndPayEach() {
        var s = neighbours()
        val r1 = s.tap("A"); s = r1.first
        assertFalse((r1.second as TapResult.Correct).questionDone)
        assertEquals("N1", s.current?.id)
        assertEquals(TapResult.Ignored, s.tap("A").second) // already found in this question
        assertEquals(TapResult.Ignored, s.tap("P").second) // the highlighted county itself
        s = s.tap("B").first
        val r3 = s.tap("C"); s = r3.first
        assertTrue((r3.second as TapResult.Correct).questionDone)
        assertEquals("N2", s.current?.id)
        assertEquals(3, s.hits)
        assertEquals(5, s.hitsAvailable)
        // Answers of earlier neighbour questions can be answers again.
        assertTrue(s.tap("B").second is TapResult.Correct)
    }

    @Test fun neighbourRevealShowsOnlyTheMissingOnes() {
        var s = neighbours().tap("A").first
        s = s.tap("X").first.tap("Y").first
        val r = s.tap("Z").second as TapResult.Revealed
        assertEquals(setOf("B", "C"), r.missing)
        val (_, hint) = RoundRules.hint(neighbours().tap("A").first, config)
        assertEquals(setOf("B", "C"), hint)
    }

    // ---- Városok -------------------------------------------------------------------------

    private fun cities() = RoundState(
        listOf("x", "y", "z").map { Question(it, PromptKind.FindCity, it, setOf(it), hintIds = setOf("county-$it")) },
    )

    @Test fun cityGuessScoresByDistance() {
        assertEquals(100, config.placePoints(3.0))
        assertEquals(50, config.placePoints(30.0))
        assertEquals(0, config.placePoints(80.0))

        var s = cities()
        val (s1, r1) = RoundRules.guess(s, 3.0, config); s = s1
        assertEquals(TapResult.Guess(3.0, 100, good = true), r1)
        val (s2, r2) = RoundRules.guess(s, 30.0, config); s = s2
        // second good-streak step would be x1 still (streakPerStep = 2), far guess resets it
        assertEquals(50, (r2 as TapResult.Guess).points)
        assertFalse(r2.good)
        assertEquals(0, s.streak)
        assertEquals(Outcome.Revealed, s.outcomes["y"])
        val (s3, r3) = RoundRules.guess(s, 200.0, config)
        assertEquals(0, (r3 as TapResult.Guess).points)
        assertTrue(s3.finished)
        assertEquals(2, s3.hits) // two guesses earned points
        assertEquals(TapResult.Ignored, RoundRules.tap(cities(), "x", config).second) // county taps don't count
        assertEquals(setOf("county-x"), RoundRules.hint(cities(), config).second)
    }

    // ---- Felsorolás ----------------------------------------------------------------------

    private fun westToEast() = RoundState(
        listOf(
            Question("W", PromptKind.NextWestToEast, "W", setOf("W"), hintIds = setOf("W")),
            Question("M1", PromptKind.NextWestToEast, "M1", setOf("M1", "M2"), hintIds = setOf("M1")),
            Question("M2", PromptKind.NextWestToEast, "M2", setOf("M1", "M2"), hintIds = setOf("M2")),
            Question("E", PromptKind.NextWestToEast, "E", setOf("E"), hintIds = setOf("E")),
        ),
    )

    @Test fun westToEastInOrder() {
        var s = westToEast()
        for (id in listOf("W", "M1", "M2", "E")) s = s.tap(id).first
        assertTrue(s.finished)
        assertEquals(4, s.correctCount)
    }

    @Test fun nearlyLevelCountiesMayBeSwapped() {
        var s = westToEast().tap("W").first
        val (s1, r1) = s.tap("M2") // M2 is level with M1: accepted, and moved forward
        assertTrue(r1 is TapResult.Correct)
        s = s1
        assertEquals("M1", s.current?.subject)
        assertEquals(TapResult.Ignored, s.tap("M2").second) // already listed
        assertTrue(s.tap("E").second is TapResult.Wrong) // E is not next yet
        s = s.tap("M1").first.tap("E").first
        assertTrue(s.finished)
        assertEquals(setOf("W", "M2", "M1", "E"), s.outcomes.filterValues { it == Outcome.Correct }.keys)
    }
}
