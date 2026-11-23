package com.mohammad.tvdraw

/**
 * The text of the Quran (Tanzil, Uthmani script) and where its surahs and juz begin.
 * The words are never changed: they are only split on spaces, so they can be shown or hidden one by one.
 * Plain Kotlin with no Android code, so it can be tested on its own.
 */
class Quran(text: String, meta: String, clean: String = "") {
    class Surah(val number: Int, val ayahs: Int, val name: String, val english: String)
    /**
     * How a stretch of an ayah is said aloud: the words from..to of [words] (a word with the pause marks after it) and the
     * plain spelling a listener would write for them. Mostly one word each; "يَٰٓأَيُّهَا" is said as two, "يا" and "أيها".
     * whole is set for the opening letters of a surah, which are said by their names: "الم" as "الف لام ميم".
     */
    class Spoken(val from: Int, val to: Int, val say: List<String>, val whole: String?)
    /** A stretch of one surah small enough to recite in one go. */
    class Part(val surah: Int, val from: Int, val to: Int, val juz: Int) { val id get() = "$surah:$from-$to" }

    val surahs: List<Surah>
    private val juzStart: List<IntArray>
    private val lines = HashMap<Int, String>()
    private val plain = HashMap<Int, String>()                   // the same ayahs in everyday spelling (Tanzil simple clean)

    init {
        val s = ArrayList<Surah>(); val j = ArrayList<IntArray>()
        for (l in meta.split('\n')) { val f = l.trim().split('|'); if (f.size < 4) continue
            if (f[0] == "J") j.add(intArrayOf(f[2].toInt(), f[3].toInt())) else s.add(Surah(f[0].toInt(), f[1].toInt(), f[2], f[3])) }
        surahs = s; juzStart = j
        for (l in text.split('\n')) { if (l.isEmpty() || !l[0].isDigit()) continue
            val a = l.indexOf('|'); val b = l.indexOf('|', a + 1); if (a < 0 || b < 0) continue
            lines[key(l.substring(0, a).toInt(), l.substring(a + 1, b).toInt())] = l.substring(b + 1).trim() }
        for (l in clean.split('\n')) { if (l.isEmpty() || !l[0].isDigit()) continue
            val a = l.indexOf('|'); val b = l.indexOf('|', a + 1); if (a < 0 || b < 0) continue
            plain[key(l.substring(0, a).toInt(), l.substring(a + 1, b).toInt())] = l.substring(b + 1).trim() }
    }
    private fun key(surah: Int, ayah: Int) = surah * 1000 + ayah
    val size get() = lines.size
    fun surah(n: Int) = surahs.getOrNull(n - 1)

    /** The basmala that opens a surah stands before its first ayah; it is not part of that ayah, except in al-Fatiha. */
    fun hasBasmala(surah: Int) = surah != 1 && surah != 9
    val basmala get() = lines[key(1, 1)] ?: ""

    /** The words of one ayah, without the opening basmala. Pause marks are words of their own here. */
    fun words(surah: Int, ayah: Int): List<String> {
        val w = (lines[key(surah, ayah)] ?: return emptyList()).split(' ').filter { it.isNotEmpty() }
        return if (ayah == 1 && hasBasmala(surah)) w.drop(4) else w
    }
    fun text(surah: Int, ayah: Int) = words(surah, ayah).joinToString(" ")

    /**
     * The words of an ayah paired with how they are said, for following a recitation by ear. The mushaf spelling and the
     * everyday spelling do not always split into the same words, so the two are lined up by their consonants.
     */
    fun spoken(surah: Int, ayah: Int): List<Spoken> {
        val w = words(surah, ayah); val idx = w.indices.filter { !isMark(w[it]) }
        var c = (plain[key(surah, ayah)] ?: "").split(' ').filter { it.isNotEmpty() && !isMark(it) }
        if (ayah == 1 && hasBasmala(surah)) c = c.drop(4)
        val n = idx.size; val m = c.size
        val groups = ArrayList<IntArray>()                       // how many mushaf words go with how many plain ones
        if (m == 0) repeat(n) { groups.add(intArrayOf(1, 0)) }
        else if (n == m) repeat(n) { groups.add(intArrayOf(1, 1)) }
        else {
            val moves = arrayOf(intArrayOf(1, 1), intArrayOf(1, 2), intArrayOf(1, 3), intArrayOf(2, 1))
            val cost = Array(n + 1) { IntArray(m + 1) { Int.MAX_VALUE } }; val via = Array(n + 1) { arrayOfNulls<IntArray>(m + 1) }; cost[0][0] = 0
            for (i in 0..n) for (j in 0..m) { if (cost[i][j] == Int.MAX_VALUE) continue
                for (mv in moves) { val a = i + mv[0]; val b = j + mv[1]; if (a > n || b > m) continue
                    val d = cost[i][j] + 2 * Arabic.distance(Arabic.bones((i until a).joinToString("") { w[idx[it]] }), Arabic.bones(c.subList(j, b).joinToString(""))) + (if (mv[0] == 1 && mv[1] == 1) 0 else 1)
                    if (d < cost[a][b]) { cost[a][b] = d; via[a][b] = mv } } }
            var i = n; var j = m
            while ((i > 0 || j > 0) && via[i][j] != null) { val mv = via[i][j]!!; groups.add(0, mv); i -= mv[0]; j -= mv[1] }
            if (i != 0 || j != 0) { groups.clear(); repeat(n) { groups.add(intArrayOf(1, if (it < m) 1 else 0)) } }      // could not be lined up: word for word
        }
        val out = ArrayList<Spoken>(); var i = 0; var j = 0
        for (g in groups) { val from = if (i == 0) 0 else idx[i]; val to = if (i + g[0] < n) idx[i + g[0]] - 1 else w.size - 1
            val say = if (g[1] == 0) (i until i + g[0]).map { Arabic.norm(w[idx[it]]) } else c.subList(j, j + g[1]).map { Arabic.norm(it) }
            val opening = i == 0 && g[1] == 1 && (ayah == 1 || (surah == 42 && ayah == 2)) && Arabic.norm(c[j]) in OPENINGS && (ayah == 2 || surah in OPENING_SURAHS)
            out.add(if (opening) Spoken(from, to, say[0].map { LETTER_NAMES[it] ?: it.toString() }, say[0]) else Spoken(from, to, say, null))
            i += g[0]; j += g[1] }
        return out
    }

    /** Which juz an ayah is in, 1 to 30. */
    fun juzOf(surah: Int, ayah: Int): Int {
        var n = 1
        for (i in juzStart.indices) { val s = juzStart[i]; if (surah > s[0] || (surah == s[0] && ayah >= s[1])) n = i + 1 else break }
        return n
    }

    /** Every surah cut into parts of at most PART ayahs, as even as possible. A part belongs to the juz it starts in. */
    val parts: List<Part> by lazy {
        val out = ArrayList<Part>()
        for (s in surahs) { val n = (s.ayahs + PART - 1) / PART; var from = 1
            for (i in 0 until n) { val len = (s.ayahs - from + 1 + (n - i) - 1) / (n - i); out.add(Part(s.number, from, from + len - 1, juzOf(s.number, from))); from += len } }
        out
    }
    fun part(id: String) = parts.firstOrNull { it.id == id }

    companion object {
        const val PART = 20
        /** The surahs that open with letters said by their names, and those openings in plain spelling. */
        val OPENING_SURAHS = setOf(2, 3, 7, 10, 11, 12, 13, 14, 15, 19, 20, 26, 27, 28, 29, 30, 31, 32, 36, 38, 40, 41, 42, 43, 44, 45, 46, 50, 68)
        val OPENINGS = setOf("الم", "المص", "الر", "المر", "كهيعص", "طه", "طسم", "طس", "يس", "ص", "حم", "عسق", "ق", "ن")
        val LETTER_NAMES = mapOf('ا' to "الف", 'ل' to "لام", 'م' to "ميم", 'ص' to "صاد", 'ر' to "را", 'ك' to "كاف", 'ه' to "ها", 'ي' to "يا", 'ع' to "عين", 'ط' to "طا", 'س' to "سين", 'ح' to "حا", 'ق' to "قاف", 'ن' to "نون")
        /** A pause or sajda mark and not a word: it has no letter in it. */
        fun isMark(word: String) = word.none { it in 'ء'..'ي' || it == 'ٱ' }
        /** Arabic-Indic digits, as an ayah number is written in a mushaf. */
        fun digits(n: Int) = n.toString().map { '٠' + (it - '0') }.joinToString("")
        /** Which words stay visible at a hiding level: 0 all, 1 two of three, 2 one of three, 3 only the first. Marks always stay. */
        fun shown(words: List<String>, level: Int): BooleanArray {
            var i = 0
            return BooleanArray(words.size) { k -> if (isMark(words[k])) true else { val n = i++
                when (level) { 0 -> true; 1 -> n % 3 != 2; 2 -> n % 3 == 0; else -> n == 0 } } }
        }
    }
}
