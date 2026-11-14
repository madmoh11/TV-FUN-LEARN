package com.mohammad.tvdraw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardTest {
    @Test fun pointsForPlayingWinningAndCorrectAnswers() {
        val b = Board()
        assertEquals(2, b.record("Sara", false, 0, 0, 1))
        assertEquals(12, b.record("Sara", true, 0, 0, 1))
        assertEquals(19, b.record("Omar", true, 7, 0, 1))
        assertEquals(listOf("Omar" to 19, "Sara" to 14), b.top().map { it.name to it.points })
        assertEquals(1, b.top().first { it.name == "Sara" }.wins); assertEquals(2, b.top().first { it.name == "Sara" }.games)
    }

    @Test fun sameNameInAnyCaseIsOnePlayerAndNamelessIsIgnored() {
        val b = Board(); b.record("sara", false, 0, 0, 1); b.record(" SARA ", false, 0, 0, 1)
        assertEquals(0, b.record("  ", true, 5, 5, 1))
        assertEquals(1, b.size); assertEquals(4, b.top()[0].points)
    }

    @Test fun newWeekClearsWeeklyNumbersOnly() {
        val b = Board(); b.record("Sara", true, 4, 0, 202646); b.record("Omar", false, 0, 9, 202646)
        assertEquals("Sara", b.weekChampion()!!.name); assertEquals("Sara", b.mathChampion()!!.name); assertEquals("Omar", b.wordChampion()!!.name)
        b.rollWeek(202647)
        assertNull(b.weekChampion()); assertNull(b.mathChampion()); assertEquals(16, b.top()[0].points)
        b.record("Omar", false, 0, 1, 202647); assertEquals("Omar", b.weekChampion()!!.name); assertEquals(3, b.weekChampion()!!.weekPoints)
    }

    @Test fun survivesSavingAndLoading() {
        val b = Board(); b.record("سارة", true, 3, 5, 202646); b.record("Omar", false, 1, 0, 202646)
        val c = Board(); c.load(b.save())
        assertEquals(b.save(), c.save()); assertEquals(202646, c.week); assertEquals("سارة", c.top()[0].name); assertEquals(20, c.top()[0].points)
        val broken = Board(); broken.load("garbage\nx\ty"); assertEquals(0, broken.size)
    }

    @Test fun namesAreCleaned() { assertEquals("a b c", Board.cleanName(" a\tb|c ")); assertEquals(14, Board.cleanName("x".repeat(40)).length) }
}
