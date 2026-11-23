package com.mohammad.tvdraw

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReciteTest {
    private val dir = "src/main/assets/quran/"
    private val q = Quran(File(dir + "quran-uthmani.txt").readText(), File(dir + "surahs.txt").readText(), File(dir + "quran-simple-clean.txt").readText())
    private fun rec(s: Int, a: Int, b: Int) = Recite((a..b).flatMap { n -> q.spoken(s, n).map { Recite.Item(n, it.from, it.to, it.say, it.whole) } })
    private var sid = 0
    /** One stretch of listening, heard the way a recogniser reports it: growing word by word, then final. */
    private fun Recite.say(text: String, alts: List<String> = emptyList()): Int { val w = text.split(' '); val id = ++sid; var f = 0
        for (n in 1..w.size) f = f or heard(id, false, 0, w.take(n)); return f or heard(id, true, 0, w, alts.map { it.split(' ') }) }
    private fun plain(s: Int, a: Int) = File(dir + "quran-simple-clean.txt").readLines().first { it.startsWith("$s|$a|") }.substringAfterLast('|').split(' ').filter { !Quran.isMark(it) }.joinToString(" ")

    @Test fun everyWordHasItsSpokenForm() {
        var joined = 0; var far = 0
        for (s in q.surahs) for (a in 1..s.ayahs) { val w = q.words(s.number, a); val sp = q.spoken(s.number, a)
            assertEquals("${s.number}:$a starts at its first word", 0, sp.first().from); assertEquals(w.size - 1, sp.last().to)
            assertTrue("${s.number}:$a leaves no word out", sp.zipWithNext().all { (x, y) -> y.from == x.to + 1 })
            for (x in sp) { assertTrue("${s.number}:$a", x.say.isNotEmpty() && x.say.all { it.isNotEmpty() }); assertFalse(Quran.isMark(w[x.from]) && x.from > 0)
                if (x.whole != null) continue
                if (x.say.size > 1) joined++
                if (Arabic.distance(Arabic.bones(w.subList(x.from, x.to + 1).joinToString("")), Arabic.bones(x.say.joinToString(""))) > 0) far++ } }
        assertEquals("words said as two or three, like the calling ya", 366, joined); assertTrue("only a handful of spellings differ in a consonant: $far", far in 1..110)
        assertEquals(listOf("يا", "ايها"), q.spoken(2, 21)[0].say); assertEquals(listOf("يا", "ابن", "ام"), q.spoken(20, 94).first { it.say.size == 3 }.say)
        assertEquals(listOf("الف", "لام", "ميم"), q.spoken(2, 1)[0].say); assertEquals("الم", q.spoken(2, 1)[0].whole); assertEquals(listOf("عين", "سين", "قاف"), q.spoken(42, 2)[0].say)
        assertEquals(29, q.surahs.count { q.spoken(it.number, 1)[0].whole != null }); assertNull(q.spoken(112, 1)[0].whole)
        assertTrue("the Tanzil copyright block stays in the file", File(dir + "quran-simple-clean.txt").readText().let { it.contains("Tanzil Quran Text") && it.contains("tanzil.net") })
    }
    @Test fun aWholeSurahRecitedRightIsWrittenWordByWord() {
        for (s in listOf(1, 36, 55, 67, 112, 114)) { val n = q.surah(s)!!.ayahs; val r = rec(s, 1, n)
            for (a in 1..n) { val f = r.say(plain(s, a).let { if (a == 1 && q.hasBasmala(s)) it.split(' ').drop(4).joinToString(" ") else it }); assertTrue("$s:$a", f and Recite.AYAH != 0); assertEquals(0, f and Recite.MISTAKE) }
            assertTrue("surah $s", r.done); assertEquals(0, r.mistakes) }
        // the whole Quran, each ayah on its own: nothing in the text trips the follower over
        for (s in q.surahs) for (a in 1..s.ayahs) { val r = rec(s.number, a, a); val t = plain(s.number, a).let { if (a == 1 && q.hasBasmala(s.number)) it.split(' ').drop(4).joinToString(" ") else it }
            r.heard(1, true, 0, t.split(' ')); assertTrue("${s.number}:$a", r.done && r.mistakes == 0) }
    }
    @Test fun aWrongWordWritesNothingUntilItIsSaidRight() {
        val r = rec(112, 1, 4)
        var f = r.say("قل هو الله الصمد"); assertTrue(f and Recite.MISTAKE != 0); assertTrue(r.pending); assertEquals(3, r.current); assertFalse(r.done)
        f = r.say("الله الصمد"); assertEquals("still waiting for the same word", 3, r.current); assertTrue(r.pending); assertEquals("one mistake, however often", 0, f and Recite.MISTAKE)
        f = r.say("احد"); assertEquals(4, r.current); assertFalse(r.pending); assertTrue(f and Recite.AYAH != 0); assertTrue(r.wrong[3]); assertEquals(1, r.mistakes)
        // a word left out
        val a = rec(1, 2, 2); a.say("الحمد رب العالمين"); assertEquals(1, a.current); assertTrue(a.pending)
        a.say("الحمد لله رب العالمين"); assertTrue("going back to the start of the ayah is a run-up, not more mistakes", a.done); assertEquals(1, a.mistakes)
    }
    @Test fun whatIsNotAMistake() {
        // the refuge and the basmala before the first word
        var r = rec(112, 1, 4); assertEquals(0, r.say("اعوذ بالله من الشيطان الرجيم بسم الله الرحمن الرحيم قل هو الله احد") and Recite.MISTAKE); assertEquals(4, r.current)
        r = rec(55, 1, 2); r.say("بسم الله الرحمن الرحيم الرحمن علم القران"); assertTrue(r.done); assertEquals(0, r.mistakes)
        r = rec(55, 1, 2); r.say("الرحمن علم القران"); assertTrue(r.done)
        r = rec(1, 1, 2); r.say("اعوذ بالله من الشيطان الرجيم"); r.say("بسم الله الرحمن الرحيم"); assertEquals(4, r.current); assertEquals(0, r.mistakes)
        r = rec(2, 255, 255); r.say("أعوذ بالله من الشيطان الرجيم"); r.say("الله لا اله الا هو"); assertEquals(5, r.current); assertEquals(0, r.mistakes)
        // a word put right in the same breath, and a word said again
        r = rec(1, 2, 2); assertEquals(0, r.say("الحمد لله ربي رب العالمين") and Recite.MISTAKE); assertTrue(r.done); assertEquals(0, r.mistakes)
        r = rec(1, 2, 3); r.say("الحمد لله رب"); r.say("لله رب العالمين الرحمن"); assertEquals(5, r.current); assertEquals(0, r.mistakes)
        // the last word of an unfinished stretch may still change
        r = rec(1, 2, 3); assertEquals(0, r.heard(90, false, 0, listOf("الحمد", "لله", "ربل")) and Recite.MISTAKE); assertEquals(2, r.current); assertFalse(r.pending)
        // the recogniser changes its mind: the mistake it caused is taken back
        r = rec(1, 2, 3); assertTrue(r.heard(91, false, 0, listOf("الحمد", "لله", "ربل", "عالمين", "الرحمن")) and Recite.MISTAKE != 0); assertTrue(r.pending)
        r.heard(91, false, 0, listOf("الحمد", "لله", "رب", "العالمين", "الرحمن")); assertFalse(r.pending); assertEquals(0, r.mistakes); assertEquals(5, r.current)
        // another way the recogniser heard the same words
        r = rec(112, 1, 1); r.say("قل هو الله احمد", listOf("قل هو الله أحد")); assertTrue(r.done); assertEquals(0, r.mistakes)
        // spelling habits: hamza, ta marbuta, alif maqsura, marks
        r = rec(1, 7, 7); r.say("صِرَاطَ الَّذِينَ انعمت عليهم غير المغضوب عليهم ولا الضالين"); assertTrue(r.done); assertEquals(0, r.mistakes)
        assertTrue(Arabic.same(Arabic.norm("آمَنُوا"), Arabic.norm("امنو"))); assertFalse(Arabic.same(Arabic.norm("الله"), Arabic.norm("إله"))); assertFalse(Arabic.same(Arabic.norm("يعلمون"), Arabic.norm("تعلمون"))); assertFalse(Arabic.same(Arabic.norm("قال"), Arabic.norm("قل")))
        assertTrue(Arabic.same(Arabic.norm("رحمة"), "رحمت")); assertFalse(Arabic.same(Arabic.norm("جعله"), "جعلت"))
    }
    @Test fun wordsHeardAsTwoOrAsOneAndOpeningLetters() {
        var r = rec(2, 21, 21); r.say("يا ايها الناس اعبدوا"); assertEquals(3, r.current)
        r = rec(2, 21, 21); r.say("ياايها الناس"); assertEquals(2, r.current); r = rec(2, 21, 21); r.say("يأيها الناس"); assertEquals(2, r.current)
        r = rec(2, 21, 21); r.say("يا الناس"); assertEquals("half a word reveals nothing", 0, r.current); assertTrue(r.pending)
        r = rec(2, 1, 2); r.say("الف لام ميم ذلك الكتاب"); assertEquals(3, r.current); assertEquals(0, r.mistakes)
        r = rec(2, 1, 2); r.say("بسم الله الرحمن الرحيم الم ذلك الكتاب"); assertEquals(3, r.current)
        r = rec(2, 1, 2); r.say("ألف لام ميم"); assertEquals(1, r.current); r = rec(2, 1, 2); r.say("ذلك الكتاب"); assertEquals("letters the recogniser cannot write are not held against the reciter", 3, r.current)
        r = rec(19, 1, 2); r.say("كاف هاء ياء عين صاد ذكر رحمة ربك"); assertEquals(4, r.current); assertEquals(0, r.mistakes)
    }
    @Test fun aGrownUpCanHelp() {
        val r = rec(112, 1, 2); r.say("قل هو الرحمن"); assertTrue(r.pending)
        r.reveal(); assertEquals(3, r.current); assertFalse(r.pending); assertTrue(r.wrong[2])
        r.say("احد"); r.say("الله الصمت"); assertTrue(r.pending); r.accept(); assertTrue(r.done); assertFalse(r.wrong[5]); assertEquals(1, r.mistakes)
        // long stretches arrive from the phone in windows
        val f = rec(1, 2, 4); val w = "الحمد لله رب العالمين الرحمن الرحيم مالك يوم الدين".split(' ')
        f.heard(5, false, 0, w.take(4)); f.heard(5, true, 3, w.drop(3)); assertTrue(f.done); assertEquals(0, f.mistakes)
    }
}
