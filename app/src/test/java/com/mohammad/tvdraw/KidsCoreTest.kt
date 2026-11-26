package com.mohammad.tvdraw

import org.junit.Assert.*
import org.junit.Test

class KidsCoreTest {
    @Test fun everyLetterAndNumberHasItsWordAndPicture() {
        assertEquals(28, KidsCore.ARABIC.size); assertEquals(26, KidsCore.ENGLISH.size); assertEquals(10, KidsCore.NUMBERS.size)
        for (t in KidsCore.TRACKS) { assertEquals("no letter twice", t.size, t.map { it.sign }.toSet().size)
            for (c in t) assertTrue(c.sign, c.sign.isNotBlank() && c.say.isNotBlank() && c.word.isNotBlank() && c.picture.isNotBlank()) }
        // the word really starts with its letter
        for (c in KidsCore.ENGLISH) assertTrue(c.word, c.word.startsWith(c.sign))
        for (c in KidsCore.ARABIC) assertEquals(c.word, c.sign.first(), c.word.first())
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ", KidsCore.ENGLISH.joinToString("") { it.sign })
        assertEquals((1..10).toList(), KidsCore.NUMBERS.map { it.count }); assertEquals("١٠", KidsCore.eastern(10))
        // no picture newer than 2018, which an old TV box would show as an empty square
        for (t in KidsCore.TRACKS) for (c in t) for (cp in c.picture.codePoints().toArray()) assertTrue(c.word + " " + Integer.toHexString(cp), cp < 0x1F9B0 || cp in 0x1F9B4..0x1F9B4)
    }

    @Test fun theCardsTurnInACircle() {
        val c = KidsCore(); c.open(KidsCore.AR); assertEquals("أ", c.card.sign)
        c.next(); assertEquals("ب", c.card.sign); c.prev(); c.prev(); assertEquals("ي", c.card.sign); c.next(); assertEquals("أ", c.card.sign)
        c.open(KidsCore.EN); assertEquals("A", c.card.sign); c.open(99); assertEquals(KidsCore.NUM, c.track)
    }

    @Test fun aQuestionHasThreeChoicesAndAWrongOneOnlyGoesDim() {
        for (track in 0..2) for (seed in 0L until 40L) { val c = KidsCore(java.util.Random(seed)); c.open(track); c.play()
            var last: KidsCore.Card? = null
            for (n in 1..KidsCore.ROUND) { val q = c.question!!
                assertEquals(3, q.options.size); assertEquals(3, q.options.toSet().size); assertEquals(q.card.sign, q.options[q.right]); assertNotSame("not the same card twice running", last, q.card); last = q.card
                assertEquals(n, c.asked)
                val wrong = (0..2).first { it != q.right }
                if (n % 2 == 0) { assertEquals(KidsCore.WRONG, c.answer(wrong)); assertEquals("it cannot be pressed again", KidsCore.NOTHING, c.answer(wrong)); assertTrue(wrong in c.tried) }
                assertEquals(KidsCore.NOTHING, c.answer(7))
                assertEquals(KidsCore.RIGHT, c.answer(q.right)); c.advance() }
            assertNull(c.question); assertTrue(c.finished); assertEquals("a star only for a first try", KidsCore.ROUND / 2, c.stars) }
    }

    @Test fun countingShowsDifferentThings() {
        val seen = HashSet<String>()
        for (seed in 0L until 30L) { val c = KidsCore(java.util.Random(seed)); c.open(KidsCore.NUM); c.play(); val q = c.question!!
            assertTrue(q.picture in KidsCore.THINGS); assertTrue(q.card.count in 1..10); seen.add(q.picture) }
        assertTrue(seen.size >= 4)
    }
}
