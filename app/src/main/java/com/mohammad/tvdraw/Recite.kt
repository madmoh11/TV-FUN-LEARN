package com.mohammad.tvdraw

/** Comparing Arabic words as they are heard, whatever marks and spelling habits they were written with. */
object Arabic {
    /** Letters only, with the forms of alif, hamza, ya and ta marbuta that sound alike written the same way. */
    fun norm(w: String): String { val sb = StringBuilder()
        for (c in w) when (c) { 'أ', 'إ', 'آ', 'ٱ', 'ا' -> sb.append('ا'); 'ى', 'ئ' -> sb.append('ي'); 'ة' -> sb.append('ه'); 'ؤ' -> sb.append('و'); 'ء', 'ـ' -> {}
            else -> if (c in 'ء'..'ي') sb.append(c) }
        return sb.toString() }
    /** Only the consonants. Used to line up two spellings of the same text, never to judge a reciter. */
    fun bones(w: String) = norm(w).filter { it != 'ا' && it != 'و' && it != 'ي' }
    /** A doubled letter written once or twice, and the silent alif after a plural waw, make no difference. */
    private fun loose(n: String): String { val sb = StringBuilder(); for (c in n) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        if (sb.length > 2 && sb.endsWith("وا")) sb.setLength(sb.length - 1); return sb.toString() }
    /** Whether a heard word is the expected word. Both are already through [norm]. Short words must match exactly. */
    fun same(heard: String, expected: String) = heard == expected || (heard.length >= 4 && expected.length >= 4 && loose(heard) == loose(expected)) ||
        (expected.endsWith('ت') && heard.length == expected.length && heard.endsWith('ه') && heard.regionMatches(0, expected, 0, expected.length - 1) && OPEN_TA.any { expected.endsWith(it) })
    /** Words the mushaf writes with an open ta where everyday spelling has a ta marbuta: "رحمت" for "رحمة". */
    private val OPEN_TA = listOf("رحمت", "نعمت", "امرات", "سنت", "شجرت", "جنت", "لعنت", "كلمت", "بقيت", "قرت", "فطرت", "معصيت", "ابنت")
    /** Letters a recogniser often writes for one another, written the same way: the heavy letters as their light twins. */
    private fun sound(n: String, wide: Boolean): String { val sb = StringBuilder()
        for (c in n) sb.append(when (c) { 'ص', 'ث' -> 'س'; 'ض' -> 'د'; 'ذ', 'ظ' -> 'ز'; 'ط' -> 'ت'; 'ق' -> if (wide) 'ك' else c; 'ح' -> if (wide) 'ه' else c; else -> c })
        return sb.toString() }
    /**
     * Whether a heard word is close enough to the expected one to be the recogniser's slip and not the reciter's.
     * ease 0 allows nothing; 1 forgives letters that sound alike; 2 also forgives one letter in a word of four or more
     * and two in a word of seven or more, which lets some look-alike words through.
     */
    fun near(heard: String, expected: String, ease: Int): Boolean {
        if (ease <= 0 || heard.isEmpty()) return false
        val a = sound(heard, ease >= 2); val b = sound(expected, ease >= 2)
        if (a == b || (a.length >= 4 && b.length >= 4 && loose(a) == loose(b))) return true
        if (ease < 2 || expected.length < 4) return false
        val d = distance(a, b)
        return d <= 1 || (expected.length >= 7 && d <= 2)
    }
    fun distance(a: String, b: String): Int { var p = IntArray(b.length + 1) { it }
        for (i in 1..a.length) { val q = IntArray(b.length + 1); q[0] = i
            for (j in 1..b.length) q[j] = minOf(p[j] + 1, q[j - 1] + 1, p[j - 1] + (if (a[i - 1] == b[j - 1]) 0 else 1)); p = q }
        return p[b.length] }
}

/**
 * Follows someone reciting from memory, word by word, from what a speech recogniser hears.
 * A word is revealed only when it is heard in its place. A wrong word reveals nothing: it raises a mistake that stays
 * until the right word is said. Going back a few words to take a run-up, and the opening "أعوذ بالله" and basmala,
 * are not mistakes. Plain Kotlin, so the rules can be tested on their own.
 */
class Recite(val items: List<Item>, var ease: Int = 1) {
    /** One word of the mushaf (words from..to of its ayah) and the plain words that are heard for it. */
    class Item(val ayah: Int, val from: Int, val to: Int, val say: List<String>, val whole: String?)
    companion object { const val ADVANCED = 1; const val MISTAKE = 2; const val AYAH = 4; const val DONE = 8; const val BACK = 12; const val HELPED = 16; const val TRIES = 3
        private val REFUGE = listOf("اعوذ", "بالله", "من", "الشيطان", "الرجيم"); private val BASMALA = listOf("بسم", "الله", "الرحمن", "الرحيم") }

    private val tok = ArrayList<String>(); private val itemOf = ArrayList<Int>(); private val first = IntArray(items.size)
    init { items.forEachIndexed { i, it -> first[i] = tok.size; for (t in it.say) { tok.add(t); itemOf.add(i) } } }
    private val opening = if (tok.isNotEmpty() && tok[0] == BASMALA[0]) REFUGE else REFUGE + BASMALA      // what may be said before the first word
    private var tp = 0                                           // the next plain word that has to be heard
    val wrong = BooleanArray(items.size)                         // words that needed more than one try, or help
    /** A wrong word was heard and the right one has not been said yet. */
    var pending = false; private set
    /** The item that is waited for; items before it are revealed. */
    val current get() = if (tp < tok.size) itemOf[tp] else items.size
    val done get() = tp >= tok.size
    val mistakes get() = wrong.count { it }
    /** The last words heard, as the recogniser wrote them. */
    var lastHeard = ""; private set

    private var session = -1; private val seen = ArrayList<String>(); private var consumed = 0; private var replayTo = 0
    private var back = -1; private var pre = 0; private var errAt = -1; private var errItem = -1
    private var misses = 0; private var missAt = -1; private var missItem = -1
    private fun ok(h: String, e: String) = Arabic.same(h, e) || Arabic.near(h, e, ease)
    /** Where a run-up may begin: the start of the ayah that is being recited, or of the one before it when a new ayah is about to start. */
    private fun runStart(): Int {
        if (tp <= 0) return 0
        val at = itemOf[minOf(tp, tok.size - 1)]; var i = at
        while (i > 0 && items[i - 1].ayah == items[at].ayah) i--
        if (first[i] >= tp && i > 0) { val prev = items[i - 1].ayah; i--; while (i > 0 && items[i - 1].ayah == prev) i-- }
        return first[i]
    }

    /** Tries one heard word (and the one after it) at the place that is waited for: the place after it and how many heard words it took, or null. */
    private fun match(h: String, h2: String?): IntArray? {
        if (tp >= tok.size) return null
        val e = tok[tp]; val it = itemOf[tp]; val opens = items[it].whole != null && tp == first[it]; val after = first[it] + items[it].say.size
        if (ok(h, e)) return intArrayOf(tp + 1, 1)
        if (opens && h == items[it].whole) return intArrayOf(after, 1)
        var cat = e                                              // two or three words heard as one: "ياايها"
        for (k in 1..2) { if (tp + k >= tok.size) break; cat += tok[tp + k]; if (ok(h, cat)) return intArrayOf(tp + k + 1, 1) }
        if (h2 != null && ok(h + h2, e)) return intArrayOf(tp + 1, 2)      // one word heard as two
        if (opens && after < tok.size && ok(h, tok[after])) return intArrayOf(after + 1, 1)      // opening letters the recogniser could not write
        return null
    }
    private fun advance(to: Int, at: Int): Int {
        val a = if (tp < tok.size) items[itemOf[tp]].ayah else -1
        if (pending && at <= errAt && errItem >= 0) wrong[errItem] = false          // the recogniser changed its mind about that word
        tp = to; pending = false; back = -1; pre = -1
        return ADVANCED or (if (tp >= tok.size) DONE or AYAH else if (items[itemOf[tp]].ayah != a) AYAH else 0)
    }

    /**
     * What the recogniser has heard so far in one stretch of listening: [words] start at word number [offset] of that
     * stretch, and are final or may still change. [alts] are other ways it may have heard the same stretch.
     * Returns ADVANCED, MISTAKE, AYAH (an ayah was finished) and DONE as flags.
     */
    fun heard(sessionId: Int, final: Boolean, offset: Int, words: List<String>, alts: List<List<String>> = emptyList()): Int {
        if (sessionId != session) { session = sessionId; seen.clear(); consumed = 0; replayTo = 0; back = -1; errAt = -1; missAt = -1; if (tp == 0) pre = 0 }
        if (words.isNotEmpty()) lastHeard = words.takeLast(6).joinToString(" ")
        val end = offset + words.size
        for (k in words.indices) { val a = offset + k; val n = Arabic.norm(words[k])
            if (a < seen.size) { if (seen[a] != n) { seen[a] = n; if (a < consumed) { replayTo = maxOf(replayTo, consumed); consumed = a } } }
            else { while (seen.size < a) seen.add(""); seen.add(n) } }
        while (seen.size > end) seen.removeAt(seen.size - 1)
        consumed = consumed.coerceIn(minOf(offset, end), end)
        var flags = 0; var i = consumed
        while (i < end && tp < tok.size) {
            val h = seen[i]; if (h.isEmpty()) { i++; continue }
            val h2 = if (i + 1 < end) seen[i + 1].ifEmpty { null } else null
            val last = i == end - 1
            if (tp == 0 && pre >= 0) {                           // before the first word: the refuge and the basmala are welcome
                val edge = pre == 0 || pre == REFUGE.size
                if (edge && match(h, h2) != null) pre = -1
                else if (pre < opening.size && Arabic.same(h, opening[pre])) { if (++pre >= opening.size) pre = -1; i++; continue }
                else if (pre == 0 && opening.size > REFUGE.size && Arabic.same(h, BASMALA[0])) { pre = REFUGE.size + 1; i++; continue }
                else if (edge) pre = -1
                else if (match(h, h2) != null) pre = -1
                else { if (last && !final) break; i++; continue }
            }
            if (back in 0 until tp) {                            // going over words already said, up to the word that is waited for
                if (ok(h, tok[back])) { if (++back >= tp) back = -1; i++; continue }
                val m0 = match(h, h2)
                if (m0 != null) { flags = flags or advance(m0[0], i); i += m0[1]; continue }      // jumped straight to the missing word
                if (last && !final) break
                if (++back >= tp) back = -1; i++; continue        // misheard while repeating: it is not judged
            }
            val m = match(h, h2)
            if (m != null) { flags = flags or advance(m[0], i); i += m[1]; continue }
            // a run-up starts at the word just said, or at an earlier one (back to the start of the ayah) when the word after it follows too
            val near = (tp - 1 downTo maxOf(0, minOf(tp - BACK, runStart()))).filter { ok(h, tok[it]) }
            val b = near.firstOrNull { it + 1 >= tp || (h2 != null && ok(h2, tok[it + 1])) }
            if (b != null) { back = if (b + 1 >= tp) -1 else b + 1; i++; continue }
            if (near.isNotEmpty() && h2 == null && !final) break
            if (i < replayTo) { i++; continue }                   // old words the recogniser rewrote
            if (!final && end - 1 - i < 2) break                 // too fresh to judge: it may still change
            if (h2 != null && match(h2, if (i + 2 < end) seen[i + 2].ifEmpty { null } else null) != null) { i++; continue }      // put right straight away, or a stray sound
            if (final && alts.any { a -> (i - 1..i + 1).any { j -> a.getOrNull(j - offset)?.let { ok(Arabic.norm(it), tok[tp]) } == true } }) { flags = flags or advance(tp + 1, i); i++; continue }
            if (!pending) flags = flags or MISTAKE
            errItem = itemOf[tp]; wrong[errItem] = true; pending = true; errAt = i; back = -1
            if (missItem != errItem) { missItem = errItem; misses = 0; missAt = -1 }
            if (i > missAt) { missAt = i; misses++ }              // a word the recogniser rewrote is one try, not two
            i++
            if (ease >= 2 && misses >= TRIES) {                   // stuck: the word is shown, it stays a mistake, and the recitation goes on
                val it = errItem; misses = 0; missItem = -1
                flags = flags or HELPED or advance(first[it] + items[it].say.size, Int.MAX_VALUE); wrong[it] = true }
        }
        consumed = i
        return flags
    }

    /** A grown-up shows the word: it counts as a mistake and the recitation goes on from the next word. */
    fun reveal(): Int { if (done) return 0; wrong[current] = true; return skip() }
    /** A grown-up heard the word said right although the recogniser did not. */
    fun accept(): Int { if (done) return 0; wrong[current] = false; return skip() }
    private fun skip(): Int { val it = current; errAt = -1; consumed = seen.size; replayTo = 0; return advance(first[it] + items[it].say.size, Int.MAX_VALUE) }
}
