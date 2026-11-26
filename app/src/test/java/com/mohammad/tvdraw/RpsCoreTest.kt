package com.mohammad.tvdraw

import org.junit.Assert.*
import org.junit.Test

class RpsCoreTest {
    private val R = RpsCore.ROCK; private val P = RpsCore.PAPER; private val S = RpsCore.SCISSORS

    @Test fun eachHandBeatsExactlyOneOther() {
        assertTrue(RpsCore.beats(P, R)); assertTrue(RpsCore.beats(S, P)); assertTrue(RpsCore.beats(R, S))
        assertFalse(RpsCore.beats(R, P)); assertFalse(RpsCore.beats(P, S)); assertFalse(RpsCore.beats(S, R))
        for (h in 0..2) { assertFalse("a hand does not beat itself", RpsCore.beats(h, h)); assertTrue("a hand never shown loses", RpsCore.beats(h, RpsCore.NONE)) }
        assertFalse(RpsCore.beats(RpsCore.NONE, R)); assertFalse(RpsCore.beats(RpsCore.NONE, RpsCore.NONE))
    }

    @Test fun aHandEarnsAPointForEveryHandItBeats() {
        val c = RpsCore(); c.join("a", "Sara"); c.join("b", "Omar"); c.join("c", "Lina"); c.rounds = 3; c.begin("TV")
        assertNull("three players need no TV", c.players[RpsCore.TV])
        assertTrue(c.pick("a", R)); assertFalse("one hand a round", c.pick("a", P)); assertFalse(c.allPicked)
        c.pick("b", S); c.pick("c", S); assertTrue(c.allPicked); c.reveal()
        assertEquals(2, c.players["a"]!!.gained); assertEquals(2, c.players["a"]!!.score); assertEquals(0, c.players["b"]!!.gained); assertEquals(1, c.players["b"]!!.lost)
        assertTrue(c.next()); assertEquals(RpsCore.NONE, c.players["a"]!!.pick)
        // all three hands on the table: everyone beats one and loses to one
        c.pick("a", R); c.pick("b", P); c.pick("c", S); c.reveal()
        assertTrue(c.players.values.all { it.gained == 1 && it.lost == 1 }); assertEquals(3, c.players["a"]!!.score)
        assertTrue(c.next())
        // someone who did not choose in time loses to those who did
        c.pick("a", P); c.reveal(); assertEquals(2, c.players["a"]!!.gained); assertEquals(1, c.players["b"]!!.lost); assertEquals(0, c.players["b"]!!.gained)
        assertFalse("three rounds were asked for", c.next())
        assertEquals(listOf("a"), c.winners().map { it.cid }); assertEquals(2, c.place(c.players["b"]!!))
    }

    @Test fun aLonePlayerPlaysTheTvWhoseHandIsChosenBeforeTheirs() {
        var tvWins = 0; var meWins = 0; var even = 0
        for (seed in 0L until 60L) { val c = RpsCore(java.util.Random(seed)); c.join("a", "Sara"); c.rounds = 1; c.begin("TV")
            val tv = c.players[RpsCore.TV]!!; assertTrue(tv.robot); assertEquals("TV", tv.name); assertEquals("hidden until the reveal", RpsCore.NONE, tv.pick)
            assertFalse(c.pick(RpsCore.TV, R)); c.pick("a", R); assertTrue("the TV is never waited for", c.allPicked); c.reveal()
            assertTrue(tv.pick in 0..2)
            val me = c.players["a"]!!; if (me.gained > 0) meWins++ else if (me.lost > 0) tvWins++ else even++ }
        assertTrue("a fair TV wins, loses and draws: $meWins/$tvWins/$even", meWins > 8 && tvWins > 8 && even > 8)
        // a second player joining sends the TV away
        val c = RpsCore(); c.join("a", "Sara"); c.begin("TV"); c.join("b", "Omar"); c.begin("TV"); assertNull(c.players[RpsCore.TV]); assertEquals(2, c.players.size)
    }

    @Test fun sixPlayersAtMostAndATieHasNoSingleWinner() {
        val c = RpsCore(); for (i in 1..6) assertTrue(c.join("p$i", "P$i")); assertFalse(c.join("p7", "P7")); assertTrue("the same phone again is the same player", c.join("p1", "New"))
        assertEquals("New", c.players["p1"]!!.name)
        val t = RpsCore(); t.join("a", "A"); t.join("b", "B"); t.rounds = 1; t.begin("TV"); t.pick("a", R); t.pick("b", R); t.reveal()
        assertTrue("nobody scored", t.winners().isEmpty())
    }
}
