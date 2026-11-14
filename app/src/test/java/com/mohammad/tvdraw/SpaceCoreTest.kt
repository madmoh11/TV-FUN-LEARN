package com.mohammad.tvdraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceCoreTest {
    private fun race(players: Int = 2): SpaceCore {
        val g = SpaceCore(java.util.Random(7)); repeat(players) { g.join("p$it", "P$it", 0, 0) }; g.reset(); return g
    }
    private val slow = 4000; private val fast = 1000

    @Test fun eachCorrectAnswerIsOneStep() {
        val g = race(); g.hit("p0", slow, false); assertEquals(10f, g.racers["p0"]!!.progress, 0.001f)
        g.length = 20; g.reset(); g.hit("p0", slow, false); assertEquals(5f, g.racers["p0"]!!.progress, 0.001f)
    }

    @Test fun fastAnswerGetsTwoPercentMore() {
        val g = race(); g.hit("p0", fast, false); assertEquals(12f, g.racers["p0"]!!.progress, 0.001f)
        g.hit("p1", 2500, false); assertEquals("exactly 2.5 seconds is not fast", 10f, g.racers["p1"]!!.progress, 0.001f)
    }

    @Test fun threeInARowDoublesTheFourth() {
        val g = race(); val p = g.racers["p0"]!!
        repeat(3) { g.hit("p0", slow, false) }; assertEquals(30f, p.progress, 0.001f); assertTrue(p.turbo)
        g.hit("p0", slow, false); assertEquals(50f, p.progress, 0.001f); assertFalse("turbo is used up", p.turbo)
        g.hit("p0", slow, false); assertEquals(60f, p.progress, 0.001f)
    }

    @Test fun wrongAnswerLosesStreakAndTurbo() {
        val g = race(); val p = g.racers["p0"]!!
        repeat(3) { g.hit("p0", slow, false) }; g.miss("p0", 42)
        assertFalse(p.turbo); assertEquals(0, p.streak); assertEquals(30f, p.progress, 0.001f)
        g.hit("p0", slow, false); assertEquals(40f, p.progress, 0.001f)
        assertEquals(setOf(42), g.missed)
    }

    @Test fun goldenQuestionDoublesOnlyWhenFast() {
        val g = race(); g.hit("p0", slow, true); assertEquals(10f, g.racers["p0"]!!.progress, 0.001f)
        g.hit("p1", fast, true); assertEquals(22f, g.racers["p1"]!!.progress, 0.001f)
    }

    @Test fun firstToReachTheEndWins() {
        val g = race(); g.hit("p1", slow, false); repeat(8) { g.hit("p0", slow, false) }       // 8 answers are enough thanks to two turbos
        assertTrue(g.over); assertEquals("P0", g.winner!!.name); assertEquals(100f, g.winner!!.progress, 0.001f)
        g.hit("p1", slow, false); assertEquals("nothing moves after the finish", 10f, g.racers["p1"]!!.progress, 0.001f)
        assertEquals(listOf("P0", "P1"), g.ranking().map { it.name })
    }

    @Test fun roboJoinsALonePlayerAutomatically() {
        val solo = race(1); assertNotNull(solo.racers[SpaceCore.ROBOT])
        val two = race(2); assertNull(two.racers[SpaceCore.ROBOT])
        two.roboMode = SpaceCore.ALWAYS; two.reset(); assertNotNull(two.racers[SpaceCore.ROBOT])
        solo.roboMode = SpaceCore.NEVER; solo.reset(); assertNull(solo.racers[SpaceCore.ROBOT])
    }

    @Test fun roboRacesAtItsSpeedAndCanWin() {
        for ((speed, lo, hi) in listOf(Triple(0, 50f, 82f), Triple(1, 34f, 58f), Triple(2, 22f, 39f))) {
            val g = race(1); g.roboSpeed = speed; g.reset()
            var t = 0f; while (!g.over && t < 300f) { g.step(0.05f); t += 0.05f }
            assertTrue("speed $speed took $t s", g.over && t in lo..hi); assertTrue(g.winner!!.robot)
        }
    }

    @Test fun atMostSixPlayersPlusRobo() {
        val g = SpaceCore(); repeat(6) { assertTrue(g.join("p$it", "", 0, 0)) }; assertFalse(g.join("p6", "", 0, 0))
        g.roboMode = SpaceCore.ALWAYS; g.reset(); assertEquals(7, g.racers.size); assertEquals(6, g.humans)
    }
}
