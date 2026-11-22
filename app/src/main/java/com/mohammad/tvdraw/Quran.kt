package com.mohammad.tvdraw

/**
 * The text of the Quran (Tanzil, Uthmani script) and where its surahs and juz begin.
 * The words are never changed: they are only split on spaces, so they can be shown or hidden one by one.
 * Plain Kotlin with no Android code, so it can be tested on its own.
 */
class Quran(text: String, meta: String) {
    class Surah(val number: Int, val ayahs: Int, val name: String, val english: String)
    /** A stretch of one surah small enough to recite in one go. */
    class Part(val surah: Int, val from: Int, val to: Int, val juz: Int) { val id get() = "$surah:$from-$to" }

    val surahs: List<Surah>
    private val juzStart: List<IntArray>
    private val lines = HashMap<Int, String>()

    init {
        val s = ArrayList<Surah>(); val j = ArrayList<IntArray>()
        for (l in meta.split('\n')) { val f = l.trim().split('|'); if (f.size < 4) continue
            if (f[0] == "J") j.add(intArrayOf(f[2].toInt(), f[3].toInt())) else s.add(Surah(f[0].toInt(), f[1].toInt(), f[2], f[3])) }
        surahs = s; juzStart = j
        for (l in text.split('\n')) { if (l.isEmpty() || !l[0].isDigit()) continue
            val a = l.indexOf('|'); val b = l.indexOf('|', a + 1); if (a < 0 || b < 0) continue
            lines[key(l.substring(0, a).toInt(), l.substring(a + 1, b).toInt())] = l.substring(b + 1).trim() }
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
