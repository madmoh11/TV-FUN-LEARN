package com.mohammad.tvdraw

import org.junit.Assert.*
import org.junit.Test

class HomeCoreTest {
    @Test fun alarmRingsOnceADayInItsMinute() {
        val h = HomeCore(); h.addAlarm(20 * 60, "Dinner")
        assertTrue(h.due(19 * 60 + 59, 1).isEmpty())
        assertEquals(listOf("Dinner"), h.due(20 * 60, 1).map { it.text })
        assertTrue("not twice the same day", h.due(20 * 60 + 1, 1).isEmpty())
        assertEquals(1, h.due(20 * 60 + 1, 2).size)          // a late check the next day still rings
        assertTrue("too late is skipped", h.due(20 * 60 + 5, 3).isEmpty())
    }
    @Test fun alarmsAreSortedLimitedAndCleaned() {
        val h = HomeCore()
        assertNull(h.addAlarm(1440, "x")); assertNull(h.addAlarm(10, "  "))
        h.addAlarm(600, "b|b\tb"); h.addAlarm(60, "a")
        assertEquals(listOf("a", "b b b"), h.alarms.map { it.text })
        repeat(20) { h.addAlarm(100 + it, "n") }
        assertEquals(HomeCore.MAX_ALARMS, h.alarms.size)
        assertTrue(h.delAlarm(h.alarms[0].id)); assertFalse(h.delAlarm(9999))
    }
    @Test fun choreGivesPointsOnceADayAndOpensAgainTomorrow() {
        val h = HomeCore(); val c = h.addChore(10, "Tidy your room")!!
        assertNull("nameless players cannot tick", h.done(c.id, " ", 5))
        assertEquals("Sara", h.done(c.id, "Sara", 5)!!.doneBy)
        assertNull("second tick the same day", h.done(c.id, "Omar", 5))
        assertTrue(h.isDone(c, 5)); assertFalse(h.isDone(c, 6))
        assertEquals("Omar", h.done(c.id, "Omar", 6)!!.doneBy)
        assertNotNull(h.undo(c.id, 6)); assertNull(h.undo(c.id, 6)); assertFalse(h.isDone(c, 6))
        assertEquals(50, h.addChore(999, "big")!!.points)
    }
    @Test fun saveAndLoadKeepEverything() {
        val h = HomeCore(); h.addAlarm(75, "Bus"); val c = h.addChore(20, "Homework")!!; h.done(c.id, "Sara", 9); h.due(75, 9)
        val g = HomeCore(); g.load(h.save())
        assertEquals(h.save(), g.save())
        assertTrue(g.isDone(g.chores[0], 9)); assertTrue(g.due(75, 9).isEmpty())
        assertTrue("new ids do not clash", g.addChore(5, "x")!!.id > c.id)
        g.load("garbage\nA\tx\ty"); assertTrue(g.alarms.isEmpty())
    }
    @Test fun bonusPointsOnTheBoard() {
        val b = Board(); assertFalse(b.bonus(" ", 10, 1)); assertTrue(b.bonus("Sara", 10, 1)); b.bonus("sara", -25, 1)
        assertEquals(0, b.top()[0].points); assertEquals(0, b.top()[0].games)
    }
}
