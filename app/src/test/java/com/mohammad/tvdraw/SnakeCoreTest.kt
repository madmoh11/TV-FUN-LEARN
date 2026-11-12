package com.mohammad.tvdraw

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SnakeCoreTest {
    private fun core(level: Int = 0, seed: Int = 1) = SnakeCore(1920f, 1080f, 1f, Random(seed)).also { it.level = level }
    private fun run(c: SnakeCore, seconds: Float, each: (Int) -> Unit = {}) { var i = 0; var t = 0f; while (t < seconds && !c.over) { each(i++); c.step(0.016f); t += 0.016f } }

    @Test fun straightLineWrapsForeverWithoutCrashing() {
        for (lv in 0..2) { val c = core(lv); c.reset(true, false); c.food.clear()
            var wraps = 0; var lastX = c.s[0].x
            run(c, 60f) { c.food.clear(); if (c.s[0].x < lastX - 500f) wraps++; lastX = c.s[0].x }
            assertFalse("level $lv crashed going straight", c.over)
            assertTrue("level $lv should have crossed the right edge several times, got $wraps", wraps >= 5)
            assertTrue(c.s[0].x in 0f..1920f && c.s[0].y in 0f..1080f) }
    }
    @Test fun wrapsOnAllFourEdges() {
        val dirs = listOf(Pair(1f, 0f), Pair(-1f, 0f), Pair(0f, 1f), Pair(0f, -1f))
        for ((dx, dy) in dirs) { val c = core(2); c.reset(true, false)
            run(c, 12f) { c.food.clear(); c.steer(0, dx, dy) }
            assertFalse("crashed heading $dx,$dy", c.over)
            for (i in c.s[0].px.indices) assertTrue(c.s[0].px[i] in 0f..1920f && c.s[0].py[i] in 0f..1080f) }
    }
    private fun hardLeft(c: SnakeCore) { val a = c.s[0].heading + 1.5f; c.steer(0, Math.cos(a.toDouble()).toFloat(), Math.sin(a.toDouble()).toFloat()) }
    @Test fun tightestCircleIsSafeWhileShortAndCrashesOnceLongerThanTheCircle() {
        for (lv in 0..2) {
            val c = core(lv); c.reset(true, false)
            val circumference = 2 * Math.PI.toFloat() * c.speed() / c.turnRate()
            assertTrue("starting body must be shorter than the tightest circle", c.s[0].len < circumference)
            run(c, 25f) { c.food.clear(); hardLeft(c) }                                  // full lock, as tight as the snake can turn
            assertFalse("level $lv: a short snake hit itself just by turning as hard as it can", c.over)
            // just under one lap of body is still safe; clearly more than a lap must bite
            val safe = core(lv); safe.reset(true, false); safe.s[0].len = circumference * 0.8f
            run(safe, 25f) { safe.food.clear(); hardLeft(safe) }
            assertFalse("level $lv: body shorter than its circle should not collide", safe.over)
            val d = core(lv); d.reset(true, false); d.s[0].len = circumference * 1.3f
            run(d, 25f) { d.food.clear(); hardLeft(d) }
            assertTrue("level $lv: a snake longer than its tightest circle must bite itself", d.over)
            assertEquals(SnakeCore.SELF, d.reason); assertTrue(d.crashed[0]); assertEquals(-1, d.winner) }
    }
    @Test fun wildStickWigglingNeverFoldsTheNeck() {
        for (lv in 0..2) for (seed in 1..20) { val rnd = Random(seed); val c = core(lv, seed); c.reset(true, false)
            run(c, 20f) { c.food.clear(); val a = rnd.nextFloat() * 6.283f; c.steer(0, Math.cos(a.toDouble()).toFloat(), Math.sin(a.toDouble()).toFloat()) }   // new random direction every frame
            assertFalse("level $lv seed $seed: starting-length snake hit itself from stick wiggling", c.over) }
    }
    @Test fun reversingTheStickMakesAUTurnNotACrash() {
        for (lv in 0..2) { val c = core(lv); c.reset(true, false)
            run(c, 30f) { c.food.clear(); if ((it / 90) % 2 == 0) c.steer(0, 1f, 0f) else c.steer(0, -1f, 0f) }
            assertFalse("level $lv: flipping direction caused a crash", c.over) }
    }
    @Test fun headIntoOtherBodyLosesAndTheOtherWins() {
        val c = core(0); c.reset(true, true)                       // 0 goes right along y=389, 1 goes left along y=691
        run(c, 20f) { c.food.clear(); c.steer(1, -0.2f, -1f) }      // 1 turns up into 0's path
        assertTrue(c.over); assertTrue("snake 1 should be the one that crashed", c.crashed[1]); assertFalse(c.crashed[0])
        assertEquals(0, c.winner); assertEquals(SnakeCore.HIT_OTHER, c.reason)
    }
    @Test fun headOnIsADraw() {
        val c = core(0); c.reset(true, true)
        c.s[1].y = c.s[0].y; for (i in c.s[1].py.indices) c.s[1].py[i] = c.s[0].y     // same lane, facing each other
        run(c, 20f) { c.food.clear() }
        assertTrue(c.over); assertEquals(SnakeCore.DRAW, c.winner); assertEquals(SnakeCore.HEAD_ON, c.reason)
    }
    private fun place(c: SnakeCore, side: Int, x: Float, y: Float, heading: Float) { val sn = c.s[side]
        sn.x = x; sn.y = y; sn.heading = heading; sn.px.clear(); sn.py.clear(); val n = (sn.len / c.step).toInt()
        for (i in n downTo 1) { sn.px.add(c.wrapX(x - Math.cos(heading.toDouble()).toFloat() * c.step * i)); sn.py.add(c.wrapY(y - Math.sin(heading.toDouble()).toFloat() * c.step * i)) } }
    @Test fun rammingAHeadFromTheSideIsOnlyTheRammersFault() {
        for (lv in 0..2) for (late in listOf(35f, 50f, 70f)) { val c = core(lv); c.reset(true, true)
            place(c, 0, 600f, 500f, 0f)                                   // A cruising right
            place(c, 1, 800f, 700f + late, -Math.PI.toFloat() / 2)        // B coming up from below, arriving just after A's head passes
            run(c, 5f) { c.food.clear() }
            assertTrue("level $lv late $late: should have collided", c.over)
            assertTrue("B drove into A", c.crashed[1]); assertFalse("A was hit from the side and must not lose (level $lv, late $late)", c.crashed[0])
            assertEquals(0, c.winner); assertEquals(SnakeCore.HIT_OTHER, c.reason) }
    }
    @Test fun crossingAtTheSameInstantIsADraw() {
        val c = core(1); c.reset(true, true)
        place(c, 0, 600f, 500f, 0f); place(c, 1, 800f, 700f, -Math.PI.toFloat() / 2)     // both reach (800,500) together
        run(c, 5f) { c.food.clear() }
        assertTrue(c.over); assertEquals(SnakeCore.DRAW, c.winner); assertEquals(SnakeCore.HEAD_ON, c.reason)
    }
    @Test fun followingBehindAnotherSnakeIsSafeUntilYouTouchIt() {
        val c = core(0); c.reset(true, true)
        place(c, 0, 900f, 500f, 0f); place(c, 1, 900f - c.s[0].len - 200f, 500f, 0f)     // B trails A in the same lane, same speed
        run(c, 20f) { c.food.clear() }
        assertFalse("same speed, same lane, a gap between them: nobody should crash", c.over)
    }

    @Test fun eatingGrowsAndScoresAndFoodExpires() {
        val c = core(0); c.reset(true, false); c.food.clear()
        c.food.add(Pellet(c.s[0].x + 200f, c.s[0].y, false, 0f, 99f))
        val len0 = c.s[0].len
        run(c, 1.5f)
        assertEquals(1, c.s[0].score); assertEquals(len0 + 55f, c.s[0].len, 0.01f); assertEquals(1, c.eats[0])
        val d = core(2); d.reset(true, false); d.food.clear(); d.food.add(Pellet(100f, 1000f, false, 0f, 1f))
        run(d, 0.5f); assertTrue(d.food.any { it.x == 100f }); run(d, 1f); assertFalse("food should vanish when its time is up", d.food.any { it.x == 100f })
        val e = core(0); e.reset(true, false); run(e, 3f); assertTrue("food should keep appearing", e.food.size in 1..e.foodCount())
        for (f in e.food) assertTrue(f.x in 0f..1920f && f.y in 0f..1080f)
    }
    @Test fun bodyLengthAlwaysMatchesScore() {
        val c = core(1); c.reset(true, false)
        run(c, 40f) { val t = it * 0.016f; c.steer(0, Math.cos(t * 0.7).toFloat(), Math.sin(t * 0.7).toFloat()) }
        assertEquals(260f + 55f * c.s[0].score, c.s[0].len, 0.01f)
        assertTrue(Math.abs(c.s[0].px.size - (c.s[0].len / c.step).toInt()) <= 40)   // body may still be growing into its new length
    }
    @Test fun trailPointsAreEvenlySpacedAtEverySpeed() {
        for (lv in 0..2) for (dt in listOf(0.008f, 0.016f, 0.033f, 0.05f)) { val c = core(lv); c.reset(true, false)
            var t = 0f; while (t < 6f) { c.food.clear(); c.step(dt); t += dt }
            val sn = c.s[0]; assertEquals("level $lv dt $dt: number of body points", (sn.len / c.step).toInt(), sn.px.size)
            for (i in 1 until sn.px.size) assertEquals("level $lv dt $dt: gap between body points", c.step, c.dist(sn.px[i - 1], sn.py[i - 1], sn.px[i], sn.py[i]), 0.05f)
            assertTrue(c.dist(sn.x, sn.y, sn.px.last(), sn.py.last()) <= c.step + 0.05f) }
    }
    @Test fun brushingAlongsideABodyThenTurningInCrashes() {
        val c = core(0); c.reset(true, true)
        place(c, 0, 1200f, 500f, 0f); place(c, 1, 1000f, 580f, 0f)          // B runs parallel, 80px below A's body, level with its middle
        run(c, 1f) { c.food.clear() }; assertFalse("running parallel with a gap is safe", c.over)
        run(c, 5f) { c.food.clear(); c.steer(1, 0.8f, -0.6f) }              // then B veers up into A
        assertTrue(c.over); assertTrue(c.crashed[1]); assertFalse(c.crashed[0]); assertEquals(0, c.winner)
    }

    @Test fun timeUpLongerSnakeWins() {
        val c = core(0); c.reset(true, true); c.s[1].score = 5; c.s[0].score = 3; c.timeUp()
        assertEquals(1, c.winner); assertEquals(SnakeCore.TIME, c.reason)
        val d = core(0); d.reset(true, true); d.timeUp(); assertEquals(SnakeCore.DRAW, d.winner)
        val e = core(0); e.reset(true, false); e.timeUp(); assertEquals(-1, e.winner)
    }
    @Test fun randomPlayNeverBreaks() {
        var games = 0; var crashes = 0
        for (seed in 1..60) for (lv in 0..2) {
            val rnd = Random(seed * 31 + lv); val c = SnakeCore(1920f, 1080f, 1f, Random(seed)); c.level = lv; c.reset(true, seed % 2 == 0)
            var ax = 1f; var ay = 0f; var bx = -1f; var by = 0f; var t = 0f
            while (t < 90f && !c.over) {
                if (rnd.nextFloat() < 0.03f) { val a = rnd.nextFloat() * 6.283f; ax = Math.cos(a.toDouble()).toFloat(); ay = Math.sin(a.toDouble()).toFloat() }
                if (rnd.nextFloat() < 0.03f) { val a = rnd.nextFloat() * 6.283f; bx = Math.cos(a.toDouble()).toFloat(); by = Math.sin(a.toDouble()).toFloat() }
                c.steer(0, ax, ay); c.steer(1, bx, by)
                val dt = if (rnd.nextFloat() < 0.05f) 0.05f else 0.016f          // occasional slow frame
                c.step(dt); t += dt
                for (sn in c.s) { assertFalse(sn.x.isNaN() || sn.y.isNaN() || sn.heading.isNaN()); assertTrue(sn.x in 0f..1920f && sn.y in 0f..1080f) }
            }
            games++
            if (c.over) { crashes++
                val two = c.s[0].playing && c.s[1].playing
                if (!two) { assertEquals(-1, c.winner); assertEquals(SnakeCore.SELF, c.reason) }
                else when (c.winner) { 0 -> assertTrue(c.crashed[1] && !c.crashed[0]); 1 -> assertTrue(c.crashed[0] && !c.crashed[1]); else -> assertTrue(c.crashed[0] && c.crashed[1]) }
                // whoever crashed really is touching a body right now
                for (sn in c.s) if (c.crashed[sn.side]) { var near = 1e9f
                    for (o in c.s) if (o.playing) for (i in o.px.indices) { if (o === sn && i >= o.px.size - 12) continue; near = minOf(near, c.dist(sn.x, sn.y, o.px[i], o.py[i])) }
                    if (two) near = minOf(near, c.dist(c.s[0].x, c.s[0].y, c.s[1].x, c.s[1].y))
                    assertTrue("crash reported with nothing within reach ($near)", near < c.r * 2.1f) } }
        }
        println("SNAKE fuzz: $games games, $crashes ended in a crash")
    }
}
