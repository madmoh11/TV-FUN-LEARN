package com.mohammad.tvdraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TugCoreTest {
    private fun game(left: Int = 1, right: Int = 1): TugCore {
        val g = TugCore()
        repeat(left) { g.join("l$it", "L$it", 0) }; repeat(right) { g.join("r$it", "R$it", 1) }
        g.reset(); return g
    }

    @Test fun atMostSixPlayers() {
        val g = TugCore()
        repeat(6) { assertTrue(g.join("p$it", "", it % 2)) }
        assertFalse(g.join("p7", "", 0))
        assertTrue("a player already in can still change team", g.join("p0", "", 1))
        assertEquals(6, g.players.size); assertEquals(2, g.count(0)); assertEquals(4, g.count(1))
    }

    @Test fun correctAnswersPullTowardsTheTeam() {
        val g = game(); g.level = 2
        g.hit("l0", 3, 900); assertEquals(-3f, g.pos, 0.001f)
        g.hit("r0", 4, 900); assertEquals(1f, g.pos, 0.001f)
    }

    @Test fun pointsCannotExceedTheLevel() {
        val g = game(); g.level = 0
        g.hit("r0", 4, 500); assertEquals("easy level gives one point only", 1f, g.pos, 0.001f)
        g.level = 1; g.hit("r0", 4, 500); assertEquals(3f, g.pos, 0.001f)
    }

    @Test fun wrongAnswerSlipsTheRopeAndBreaksTheStreak() {
        val g = game()
        repeat(3) { g.hit("l0", 1, 500) }; assertEquals(3, g.players["l0"]!!.streak)
        g.miss("l0")
        assertEquals(-2.5f, g.pos, 0.001f); assertEquals(0, g.players["l0"]!!.streak); assertEquals(1, g.players["l0"]!!.wrong)
    }

    @Test fun wrongAnswersAloneNeverEndTheGame() {
        val g = game(); repeat(200) { g.miss("l0") }
        assertFalse(g.over); assertTrue(g.pos < TugCore.LIMIT)
    }

    @Test fun fiveInARowDoublesThePullForFiveSeconds() {
        val g = game()
        repeat(5) { g.hit("r0", 1, 500) }; assertEquals(5f, g.pos, 0.001f)
        assertTrue(g.players["r0"]!!.boost > 0f)
        g.hit("r0", 1, 500); assertEquals("boosted answer counts double", 7f, g.pos, 0.001f)
        g.step(5.1f); g.hit("r0", 1, 500); assertEquals("boost has run out", 8f, g.pos, 0.001f)
    }

    @Test fun knockoutEndsTheGameAtOnce() {
        val g = game(); g.level = 2
        repeat(4) { g.hit("l0", 4, 400) }          // 16, plus a doubled one from the streak below
        assertFalse(g.over)
        g.hit("l0", 4, 400)
        assertTrue(g.over); assertEquals(0, g.winner); assertEquals(TugCore.KNOCKOUT, g.reason)
        val pos = g.pos; g.hit("r0", 4, 400); assertEquals("nothing moves after the end", pos, g.pos, 0.001f)
    }

    @Test fun whenTimeRunsOutTheNearerSideWins() {
        val g = game(); g.duration = 60; g.reset()
        g.hit("r0", 1, 500); g.step(59f); assertFalse(g.over)
        g.step(1.5f)
        assertTrue(g.over); assertEquals(1, g.winner); assertEquals(TugCore.TIME, g.reason)
    }

    @Test fun exactTieGoesToSuddenDeath() {
        val g = game(); g.duration = 60; g.reset()
        g.hit("l0", 1, 500); g.hit("r0", 1, 500); g.step(61f)
        assertFalse(g.over); assertTrue(g.sudden)
        g.miss("l0"); assertFalse("a wrong answer does not decide sudden death", g.over)
        g.hit("l0", 1, 500)
        assertTrue(g.over); assertEquals(0, g.winner); assertEquals(TugCore.SUDDEN, g.reason)
    }

    @Test fun smallerTeamPullsHarder() {
        val g = game(left = 1, right = 3)
        g.hit("l0", 1, 500); assertEquals(-3f, g.pos, 0.001f)
        g.hit("r0", 1, 500); assertEquals(-2f, g.pos, 0.001f)
    }

    @Test fun getsHarderFollowsTheClock() {
        val g = game(); g.level = 3; g.duration = 90; g.reset()
        assertEquals(0, g.currentLevel()); g.step(31f); assertEquals(1, g.currentLevel()); g.step(30f); assertEquals(2, g.currentLevel())
    }

    @Test fun computerPlaysForAnEmptyTeam() {
        val g = game(left = 1, right = 0)
        var t = 0f; while (t < 10f) { g.step(0.05f); t += 0.05f }
        assertTrue("the computer should have pulled right: ${g.pos}", g.pos in 2f..4f)
        val idle = game(); idle.step(10f); assertEquals("no computer when both teams have players", 0f, idle.pos, 0.001f)
    }

    @Test fun statsNameTheBestPlayers() {
        val g = game(left = 2, right = 1)
        repeat(4) { g.hit("l0", 1, 2000) }; repeat(3) { g.hit("l1", 1, 700) }; g.hit("r0", 1, 100)
        assertEquals("L0", g.mostCorrect()!!.name)
        assertEquals("one quick answer is not enough to be the fastest", "L1", g.fastest()!!.name)
    }
}
