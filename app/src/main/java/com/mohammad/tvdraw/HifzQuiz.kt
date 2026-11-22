package com.mohammad.tvdraw

/**
 * Questions for the Quran challenge: "which ayah comes next?" and "how does this ayah go on?".
 * Every piece of text shown, right or wrong, is an unbroken run of words from the Quran: the wrong choices are
 * taken from other ayahs, never made up. Plain Kotlin, so this can be tested on its own.
 */
object HifzQuiz {
    const val NEXT = "N"; const val COMPLETE = "C"
    const val MORE = "…"
    /** Letters only, to tell two pieces of text apart whatever their marks. */
    fun bare(s: String) = s.filter { it in 'ء'..'ي' || it == 'ٱ' }

    /** The words from real word number a up to (not including) b; pause marks inside come along, one at the very end is left off. */
    private fun cut(w: List<String>, a: Int, b: Int): List<String> {
        val out = ArrayList<String>(); var n = 0
        for (x in w) { val mark = Quran.isMark(x)
            if (mark) { if (n in (a + 1) until b) out.add(x) } else { if (n in a until b) out.add(x); n++ } }
        return out
    }
    private fun real(w: List<String>) = w.count { !Quran.isMark(it) }
    /** The opening of an ayah, as a choice. */
    fun head(w: List<String>, n: Int = 7) = cut(w, 0, n).joinToString(" ") + (if (real(w) > n) " $MORE" else "")
    /** The close of an ayah, as the thing to continue from. */
    fun tail(w: List<String>, n: Int = 12) = real(w).let { r -> (if (r > n) "$MORE " else "") + cut(w, maxOf(0, r - n), r).joinToString(" ") }
    /** Where a "how does it go on" question splits an ayah: the shown part and the part to pick. Null for a short ayah. */
    fun split(w: List<String>): Pair<String, String>? {
        val r = real(w); if (r < 6) return null
        val at = minOf(r / 2, 9); val end = minOf(r, at + 7)
        return Pair(cut(w, 0, at).joinToString(" ") + " $MORE", cut(w, at, end).joinToString(" ") + (if (end < r) " $MORE" else ""))
    }

    fun questions(q: Quran, parts: List<Quran.Part>, count: Int, rnd: java.util.Random): List<QuizCore.Question> {
        val ayahs = parts.flatMap { p -> (p.from..p.to).map { Pair(p.surah, it) } }.distinct()
        if (ayahs.size < 6) return emptyList()
        val scope = ayahs.toHashSet()
        val out = ArrayList<QuizCore.Question>()
        for ((i, sa) in ayahs.shuffled(rnd).withIndex()) {
            if (out.size >= count) break
            val (s, a) = sa
            // other ayahs to take wrong choices from: the nearest in the same surah first, then the rest of the scope
            val others = ayahs.filter { it != sa }.map { Pair(it, (if (it.first == s) 0 else 1000) + Math.abs(it.second - a) + rnd.nextInt(6)) }.sortedBy { it.second }.map { it.first }
            val wantNext = (out.size + i) % 2 == 0
            val made = (if (wantNext) next(q, s, a, scope, others) else null) ?: complete(q, s, a, others) ?: next(q, s, a, scope, others) ?: continue
            val opts = (made.third + made.second).shuffled(rnd)
            out.add(QuizCore.Question(QuizCore.Item(made.first + ":$s:$a", if (made.first == NEXT) tail(q.words(s, a)) else split(q.words(s, a))!!.first, made.second), opts, opts.indexOf(made.second)))
        }
        return out
    }
    private fun pick(answer: String, pool: Sequence<String>): List<String>? {
        val seen = hashSetOf(bare(answer)); val out = ArrayList<String>()
        for (c in pool) { if (seen.add(bare(c))) out.add(c); if (out.size == 3) return out }
        return null
    }
    private fun next(q: Quran, s: Int, a: Int, scope: Set<Pair<Int, Int>>, others: List<Pair<Int, Int>>): Triple<String, String, List<String>>? {
        if (Pair(s, a + 1) !in scope) return null
        val answer = head(q.words(s, a + 1))
        val wrong = pick(answer, others.asSequence().filter { !(it.first == s && it.second == a + 1) }.map { head(q.words(it.first, it.second)) }) ?: return null
        return Triple(NEXT, answer, wrong)
    }
    private fun complete(q: Quran, s: Int, a: Int, others: List<Pair<Int, Int>>): Triple<String, String, List<String>>? {
        val answer = split(q.words(s, a))?.second ?: return null
        val wrong = pick(answer, others.asSequence().mapNotNull { split(q.words(it.first, it.second))?.second }) ?: return null
        return Triple(COMPLETE, answer, wrong)
    }
}
