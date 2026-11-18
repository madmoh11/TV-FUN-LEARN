package com.mohammad.tvdraw

import org.junit.Assert.*
import org.junit.Test

class MoveCoreTest {
    private fun game(level: Int = 0, seed: Long = 7): MoveCore { val g = MoveCore(java.util.Random(seed)); g.level = level; g.join("a", "Sara"); g.begin(); return g }

    @Test fun cuesFallOnBeatsAfterALeadInAndFillTheRound() {
        for (level in 0..2) { val g = game(level); val beat = g.beatMs
            assertEquals(4 * beat, g.cues.first().at)
            assertTrue(g.cues.all { it.at % beat == 0L && it.kind in 0..3 }); assertTrue(g.cues.last().at < 60000)
            assertTrue(g.cues.zipWithNext().all { (a, b) -> b.at - a.at in beat..2 * beat })
            assertTrue("never three of the same in a row", g.cues.windowed(3).none { w -> w.all { it.kind == w[0].kind } }) }
        assertTrue(game(2).cues.size > game(0).cues.size)
    }
    @Test fun timingDecidesTheMark() {
        val g = game(); val c = g.cues[0]; val d = g.cues[1]; val p = g.players["a"]!!
        assertEquals(0, g.act("a", (c.kind + 1) % 4, c.at)); assertEquals("a wrong move costs nothing", 0, p.judgeSeq)
        assertEquals(0, g.act("a", c.kind, c.at - 400))
        assertEquals(MoveCore.PERFECT, g.act("a", c.kind, c.at + 120)); assertEquals(100, p.score)
        assertEquals("answered once only", 0, g.act("a", c.kind, c.at + 130))
        assertEquals(MoveCore.GOOD, g.act("a", d.kind, d.at - 300)); assertEquals(150, p.score); assertEquals(2, p.combo)
        assertEquals(0, g.act("zz", c.kind, c.at))
    }
    @Test fun missedCuesBreakTheComboAndStreaksMultiply() {
        val g = game(2); val p = g.players["a"]!!
        for (i in 0 until 10) assertEquals(MoveCore.PERFECT, g.act("a", g.cues[i].kind, g.cues[i].at))
        assertEquals(10, p.combo); assertEquals(2, g.multiplier(p)); assertEquals(1000, p.score)
        g.act("a", g.cues[10].kind, g.cues[10].at); assertEquals(1200, p.score)
        g.sweep(g.cues[11].at + 100); assertEquals("not late yet", 11, p.combo)
        g.sweep(g.cues[11].at + 321); assertEquals(0, p.combo); assertEquals(1, p.miss); assertEquals(11, p.best); assertEquals(MoveCore.MISS, p.lastJudge)
        g.sweep(g.cues[11].at + 322); assertEquals(1, p.miss)
        assertEquals(0, g.act("a", g.cues[11].kind, g.cues[11].at + 325).coerceAtMost(0))
    }
    @Test fun roundEndsAfterTheLastCueAndBestScoreWins() {
        val g = MoveCore(java.util.Random(3)); g.join("a", "Sara"); g.join("b", "Omar"); assertFalse(g.join("c", "x")); g.begin()
        assertFalse(g.over(g.cues.last().at)); assertTrue(g.over(g.cues.last().at + 1300)); assertTrue(g.winners().isEmpty())
        g.act("a", g.cues[0].kind, g.cues[0].at); g.act("b", g.cues[0].kind, g.cues[0].at + 200)
        assertEquals(listOf("Sara"), g.winners().map { it.name }); assertEquals(2, g.place(g.players["b"]!!))
        g.sweep(70000); assertEquals(g.cues.size - 1, g.players["a"]!!.miss)
        g.begin(); assertEquals(0, g.players["a"]!!.score); assertEquals(0, g.players["a"]!!.miss)
    }
}
