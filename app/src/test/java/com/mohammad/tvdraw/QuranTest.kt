package com.mohammad.tvdraw

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class QuranTest {
    private val q = Quran(File("src/main/assets/quran/quran-uthmani.txt").readText(), File("src/main/assets/quran/surahs.txt").readText())

    @Test fun theWholeTextIsThereAndUnchanged() {
        assertEquals(6236, q.size); assertEquals(114, q.surahs.size); assertEquals(6236, q.surahs.sumOf { it.ayahs })
        for (s in q.surahs) for (a in 1..s.ayahs) assertTrue("${s.number}:$a", q.words(s.number, a).isNotEmpty())
        // never compare with text typed here: the same word can be written with its marks in another order
        val lines = File("src/main/assets/quran/quran-uthmani.txt").readLines().filter { it.isNotEmpty() && it[0].isDigit() }.associate { it.substringBeforeLast('|') to it.substringAfterLast('|').trim() }
        fun bare(s: String) = s.filter { it in 'ء'..'ي' || it == 'ٱ' || it == ' ' }
        assertEquals(lines["1|1"], q.text(1, 1)); assertEquals(lines["1|1"], q.basmala); assertEquals(4, q.words(1, 1).size); assertEquals(7, q.surahs[0].ayahs)
        assertEquals(lines["9|1"], q.text(9, 1)); assertEquals(lines["2|255"], q.text(2, 255)); assertEquals(lines["112|2"], q.text(112, 2)); assertEquals(2, q.words(112, 2).size)
        for (s in q.surahs) if (q.hasBasmala(s.number)) { val full = lines["${s.number}|1"]!!.split(' ')
            assertEquals("surah ${s.number} opens with the basmala", bare(q.basmala), bare(full.take(4).joinToString(" ")))
            assertEquals("and only the basmala is set aside", full.drop(4), q.words(s.number, 1)); assertTrue(q.words(s.number, 1).isNotEmpty()) }
        assertEquals(4, q.words(112, 1).size); assertEquals(1, q.words(2, 1).size)
        val raw = File("src/main/assets/quran/quran-uthmani.txt").readText()
        assertTrue("the Tanzil copyright block stays in the file", raw.contains("Tanzil Quran Text") && raw.contains("tanzil.net"))
    }
    @Test fun juzAndPartsCoverEverythingOnce() {
        assertEquals(1, q.juzOf(1, 1)); assertEquals(1, q.juzOf(2, 141)); assertEquals(2, q.juzOf(2, 142)); assertEquals(30, q.juzOf(78, 1)); assertEquals(30, q.juzOf(114, 6)); assertEquals(29, q.juzOf(67, 1))
        assertEquals(6236, q.parts.sumOf { it.to - it.from + 1 }); assertTrue(q.parts.all { it.to - it.from + 1 in 1..Quran.PART })
        for (s in q.surahs) { val p = q.parts.filter { it.surah == s.number }; assertEquals(1, p.first().from); assertEquals(s.ayahs, p.last().to)
            assertTrue(p.zipWithNext().all { (a, b) -> b.from == a.to + 1 }) }
        assertEquals(1, q.parts.count { it.surah == 112 }); assertEquals("112:1-4", q.parts.first { it.surah == 112 }.id); assertNotNull(q.part("2:1-20"))
    }
    @Test fun hidingKeepsMarksAndTheFirstWord() {
        val w = q.words(2, 255); assertTrue(w.any { Quran.isMark(it) })
        assertTrue(Quran.shown(w, 0).all { it })
        val l3 = Quran.shown(w, 3); assertTrue(l3[0]); assertEquals(1 + w.count { Quran.isMark(it) }, l3.count { it })
        assertTrue(Quran.shown(w, 1).count { it } > Quran.shown(w, 2).count { it })
        assertEquals("٢٥٥", Quran.digits(255))
    }
    @Test fun goodRecitationsWaitLongerAndMistakesComeBackTomorrow() {
        val p = HifzPlan(); p.juz.add(30)
        assertEquals(1, p.record("112:1-4", 4, 0, 100).level); assertEquals(101, p.entry("112:1-4")!!.due)
        assertEquals(104, p.record("112:1-4", 4, 0, 101).due); assertEquals(111, p.record("112:1-4", 4, 0, 104).due)
        val e = p.record("112:1-4", 4, 2, 111); assertEquals(0, e.level); assertEquals(112, e.due)
        assertEquals("one slip in twenty ayahs is still good", 1, p.record("78:1-20", 20, 2, 111).level)
        repeat(10) { p.record("114:1-6", 6, 0, 200 + it) }; assertEquals(HifzPlan.WAIT.size, p.entry("114:1-6")!!.level)
    }
    @Test fun todaysPortionPutsDuePartsFirstThenNewOnesFromTheEnd() {
        val p = HifzPlan(); p.juz.add(30)
        val first = p.today(q.parts, 50); assertEquals(HifzPlan.DAILY, first.size); assertEquals(114, first[0].surah); assertTrue(first.all { it.juz == 30 })
        p.record("114:1-6", 6, 0, 50); p.record("113:1-5", 5, 3, 50)
        assertEquals(listOf(112, 111), p.today(q.parts, 50).take(2).map { it.surah })
        assertEquals(setOf(114, 113), p.today(q.parts, 51).take(2).map { it.surah }.toSet())
        assertEquals(2, p.doneToday(50)); assertEquals(1, p.strong(q.parts)); assertTrue(HifzPlan().today(q.parts, 50).isEmpty())
    }
    @Test fun streakCountsDaysInARowAndSurvivesSaving() {
        val p = HifzPlan(); p.juz.addAll(listOf(29, 30)); p.record("114:1-6", 6, 0, 10); p.record("113:1-5", 5, 0, 10); p.record("112:1-4", 4, 0, 11)
        assertEquals(2, p.streak); assertEquals(2, p.streakOn(12)); assertEquals(0, p.streakOn(13))
        val g = HifzPlan(); g.load(p.save()); assertEquals(p.save(), g.save()); assertEquals(sortedSetOf(29, 30), g.juz); assertEquals(2, g.streak)
        p.record("112:1-4", 4, 0, 14); assertEquals(1, p.streak); g.load("garbage"); assertTrue(g.juz.isEmpty())
    }
    @Test fun challengeQuestionsUseOnlyRealQuranText() {
        fun strip(s: String) = s.removePrefix(HifzQuiz.MORE + " ").removeSuffix(" " + HifzQuiz.MORE)
        fun inside(piece: String, ayah: String) = (" $ayah ").contains(" " + strip(piece) + " ")
        val all = q.surahs.flatMap { s -> (1..s.ayahs).map { q.text(s.number, it) } }
        for ((seed, parts) in listOf(1L to q.parts.filter { it.juz == 30 }, 2L to q.parts.filter { it.juz == 1 }, 3L to q.parts.filter { it.surah == 112 || it.surah == 113 }, 4L to listOf(Quran.Part(2, 255, 262, 3)))) {
            val qs = HifzQuiz.questions(q, parts, 15, java.util.Random(seed)); assertTrue("questions for seed $seed", qs.size >= 4)
            for (x in qs) { val f = x.item.key.split(":"); val s = f[1].toInt(); val a = f[2].toInt()
                assertEquals(4, x.options.size); assertEquals(4, x.options.map { HifzQuiz.bare(it) }.toSet().size); assertEquals(x.item.answer, x.options[x.right])
                assertTrue("the prompt is from ayah $s:$a", inside(x.item.prompt, q.text(s, a)))
                assertTrue("the answer follows in the Quran", inside(x.item.answer, if (f[0] == HifzQuiz.NEXT) q.text(s, a + 1) else q.text(s, a)))
                if (f[0] == HifzQuiz.COMPLETE) assertTrue((" " + q.text(s, a) + " ").contains(" " + strip(x.item.prompt) + " " + strip(x.item.answer).split(" ").first() + " "))
                for (o in x.options) assertTrue("every choice is real Quran text: $o", all.any { inside(o, it) })
                assertTrue(strip(x.item.prompt).isNotEmpty() && x.options.all { strip(it).split(" ").size <= 10 }) }
            assertTrue(qs.any { it.item.key.startsWith(HifzQuiz.NEXT) }); assertEquals(qs.size, qs.map { it.item.key }.toSet().size)
        }
        assertTrue(HifzQuiz.questions(q, listOf(Quran.Part(108, 1, 3, 30)), 5, java.util.Random(1)).isEmpty())
        val c = QuizCore(); c.join("a", "Khaled"); c.rounds = 5; assertTrue(c.beginWith(HifzQuiz.questions(q, q.parts.filter { it.juz == 30 }, 10, java.util.Random(9)))); assertEquals(5, c.questions.size); assertFalse(c.beginWith(emptyList()))
    }
}
