package com.mohammad.tvdraw

/**
 * First letters and numbers for children of four and five: the Arabic letters, the English letters and the numbers
 * from one to ten, each with a word and a picture. "Learn" turns the cards one by one; "play" shows a picture and asks
 * which letter its word starts with (or how many things there are), with three big choices and no clock.
 * A wrong choice only goes dim so the child tries again. Plain Kotlin, so it can be tested on its own.
 */
class KidsCore(private val rnd: java.util.Random = java.util.Random()) {
    /** sign = the letter or digit; say = its name as it is said aloud; word and picture go with it; count is for numbers. */
    class Card(val sign: String, val say: String, val word: String, val picture: String, val count: Int = 0)
    class Question(val card: Card, val options: List<String>, val right: Int, val picture: String)

    var track = AR; private set
    var index = 0; private set
    var stars = 0; private set
    var asked = 0; private set
    var question: Question? = null; private set
    /** Choices already tried and found wrong in this question. */
    val tried = HashSet<Int>()
    val cards get() = TRACKS[track]
    val card get() = cards[index]
    val finished get() = asked >= ROUND && question == null

    fun open(t: Int) { track = t.coerceIn(0, TRACKS.size - 1); index = 0; stars = 0; asked = 0; question = null; tried.clear() }
    fun go(i: Int) { index = ((i % cards.size) + cards.size) % cards.size }
    fun next() = go(index + 1)
    fun prev() = go(index - 1)

    /** Starts a round of questions. */
    fun play() { stars = 0; asked = 0; ask() }
    private fun ask() {
        tried.clear()
        if (asked >= ROUND) { question = null; return }
        val last = question?.card
        val c = cards.filter { it !== last }.let { it[rnd.nextInt(it.size)] }
        val wrong = cards.filter { it.sign != c.sign }.shuffled(rnd).take(2).map { it.sign }
        val opts = (wrong + c.sign).shuffled(rnd)
        question = Question(c, opts, opts.indexOf(c.sign), if (track == NUM) THINGS[rnd.nextInt(THINGS.size)] else c.picture)
        index = cards.indexOf(c); asked++
    }
    /** RIGHT, WRONG, or NOTHING when that choice cannot be made now. A first-try right answer earns a star. */
    fun answer(i: Int): Int {
        val q = question ?: return NOTHING
        if (i !in q.options.indices || i in tried) return NOTHING
        if (i != q.right) { tried.add(i); return WRONG }
        if (tried.isEmpty()) stars++
        return RIGHT
    }
    /** After a right answer: the next question, or the end of the round. */
    fun advance() { ask() }

    companion object {
        const val AR = 0; const val EN = 1; const val NUM = 2
        const val ROUND = 8; const val RIGHT = 1; const val WRONG = 2; const val NOTHING = 0
        /** Things to count in the number questions. */
        val THINGS = listOf("🍎", "⭐", "🐟", "🎈", "🚗", "🌸")
        // Every picture is an emoji from before 2019, so it also shows on TV boxes with an old Android.
        val ARABIC = listOf(
            Card("أ", "ألف", "أسد", "🦁"), Card("ب", "باء", "بطة", "🦆"), Card("ت", "تاء", "تفاحة", "🍎"), Card("ث", "ثاء", "ثعلب", "🦊"),
            Card("ج", "جيم", "جمل", "🐫"), Card("ح", "حاء", "حصان", "🐴"), Card("خ", "خاء", "خروف", "🐑"), Card("د", "دال", "دب", "🐻"),
            Card("ذ", "ذال", "ذرة", "🌽"), Card("ر", "راء", "رز", "🍚"), Card("ز", "زاي", "زرافة", "🦒"), Card("س", "سين", "سمكة", "🐟"),
            Card("ش", "شين", "شمس", "☀️"), Card("ص", "صاد", "صاروخ", "🚀"), Card("ض", "ضاد", "ضفدع", "🐸"), Card("ط", "طاء", "طائرة", "✈️"),
            Card("ظ", "ظاء", "ظرف", "✉️"), Card("ع", "عين", "عنب", "🍇"), Card("غ", "غين", "غزال", "🦌"), Card("ف", "فاء", "فيل", "🐘"),
            Card("ق", "قاف", "قطة", "🐱"), Card("ك", "كاف", "كلب", "🐶"), Card("ل", "لام", "ليمون", "🍋"), Card("م", "ميم", "موز", "🍌"),
            Card("ن", "نون", "نحلة", "🐝"), Card("هـ", "هاء", "هدية", "🎁"), Card("و", "واو", "وردة", "🌹"), Card("ي", "ياء", "يد", "✋"))
        val ENGLISH = listOf(
            Card("A", "A", "Apple", "🍎"), Card("B", "B", "Ball", "⚽"), Card("C", "C", "Cat", "🐱"), Card("D", "D", "Dog", "🐶"),
            Card("E", "E", "Elephant", "🐘"), Card("F", "F", "Fish", "🐟"), Card("G", "G", "Grapes", "🍇"), Card("H", "H", "Horse", "🐴"),
            Card("I", "I", "Ice cream", "🍦"), Card("J", "J", "Jet", "✈️"), Card("K", "K", "Key", "🔑"), Card("L", "L", "Lion", "🦁"),
            Card("M", "M", "Moon", "🌙"), Card("N", "N", "Nose", "👃"), Card("O", "O", "Orange", "🍊"), Card("P", "P", "Pizza", "🍕"),
            Card("Q", "Q", "Queen", "👸"), Card("R", "R", "Rabbit", "🐰"), Card("S", "S", "Sun", "☀️"), Card("T", "T", "Tree", "🌳"),
            Card("U", "U", "Umbrella", "☂️"), Card("V", "V", "Violin", "🎻"), Card("W", "W", "Watermelon", "🍉"), Card("X", "X", "X-ray", "🦴"),
            Card("Y", "Y", "Yellow", "💛"), Card("Z", "Z", "Zebra", "🦓"))
        private val NUM_AR = listOf("واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة")
        private val NUM_EN = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")
        /** The numbers one to ten. say is the Arabic name and word the English one, so each language can be shown or spoken. */
        val NUMBERS = (1..10).map { Card(it.toString(), NUM_AR[it - 1], NUM_EN[it - 1], "🍎", it) }
        val TRACKS = listOf(ARABIC, ENGLISH, NUMBERS)
        /** A digit as it is written in Arabic books. */
        fun eastern(n: Int) = n.toString().map { '٠' + (it - '0') }.joinToString("")
    }
}
