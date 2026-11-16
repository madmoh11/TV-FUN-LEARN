package com.mohammad.tvdraw

import org.junit.Assert.*
import org.junit.Test

class QuizCoreTest {
    private fun items(n: Int) = (1..n).map { QuizCore.Item("k$it", "p$it", "Answer $it") }

    @Test fun choiceQuestionsHaveFourDifferentOptionsWithTheRightOne() {
        val q = QuizCore(java.util.Random(3)); q.join("a", "Sara"); q.rounds = 10
        assertTrue(q.begin(items(30))); assertEquals(10, q.questions.size)
        assertEquals(10, q.questions.map { it.item.key }.toSet().size)
        for (x in q.questions) { assertEquals(4, x.options.toSet().size); assertEquals(x.item.answer, x.options[x.right]) }
        assertFalse("too few to make four choices", QuizCore().begin(items(3)))
    }
    @Test fun rightAnswersScoreMoreWhenFasterAndOnlyTheFirstAnswerCounts() {
        val q = QuizCore(java.util.Random(1)); q.join("a", "Sara"); q.join("b", "Omar"); q.join("c", "Lina"); q.begin(items(8))
        val r = q.current!!.right
        assertTrue(q.answer("a", "" + r, 0, 12000)); assertTrue(q.answer("b", "" + r, 12000, 12000)); assertTrue(q.answer("c", "" + (r + 1) % 4, 100, 12000))
        assertFalse(q.answer("c", "" + r, 200, 12000)); assertFalse(q.answer("zz", "" + r, 0, 12000))
        assertEquals(15, q.players["a"]!!.score); assertEquals(10, q.players["b"]!!.score); assertEquals(0, q.players["c"]!!.score)
        assertTrue(q.allAnswered); assertEquals(listOf("Sara"), q.winners().map { it.name }); assertEquals(3, q.place(q.players["c"]!!))
        assertTrue(q.next()); assertFalse(q.allAnswered); assertEquals(0, q.players["a"]!!.gained); assertEquals(15, q.players["a"]!!.score)
    }
    @Test fun spellingIgnoresCaseAndSpaces() {
        val q = QuizCore(); q.mode = QuizCore.SPELL; q.rounds = 3; q.join("a", "Sara"); q.join("b", "Omar")
        assertTrue(q.begin(listOf(QuizCore.Item("1", "قطة", "cat"))))
        assertTrue(q.current!!.options.isEmpty())
        q.answer("a", "  CAT ", 5000, 25000); q.answer("b", "kat", 100, 25000)
        assertTrue(q.players["a"]!!.correct); assertFalse(q.players["b"]!!.correct); assertEquals(1, q.players["a"]!!.rights)
        assertFalse("one word means one round", q.next())
    }
    @Test fun gameEndsAfterTheLastRoundAndATieHasTwoWinners() {
        val q = QuizCore(java.util.Random(5)); q.rounds = 2; q.join("a", "Sara"); q.join("b", "Omar"); q.begin(items(10))
        assertTrue(q.winners().isEmpty())
        q.answer("a", "" + q.current!!.right, 6000, 12000); assertTrue(q.next())
        q.answer("b", "" + q.current!!.right, 6000, 12000); assertFalse(q.next())
        assertEquals(2, q.winners().size)
        q.begin(items(10)); assertEquals(0, q.players["a"]!!.score); assertEquals(0, q.round)
    }
    @Test fun atMostSixPlayers() {
        val q = QuizCore(); repeat(6) { assertTrue(q.join("p$it", "n")) }; assertFalse(q.join("x", "n")); assertTrue(q.join("p0", "renamed"))
        q.leave("p1"); assertTrue(q.join("x", "n"))
    }
}
