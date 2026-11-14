package com.mohammad.tvdraw

/** How one player did in one finished game: math and words count correct answers in the learning games. */
class GameResult(val cid: String, val won: Boolean, val math: Int = 0, val words: Int = 0)

/**
 * The family hall of fame: points every named player has collected over all games, in total and this week.
 * Plain Kotlin with no Android code, so the rules can be tested on their own.
 */
class Board {
    class Entry(var name: String) {
        var points = 0; var wins = 0; var games = 0; var math = 0; var words = 0
        var weekPoints = 0; var weekMath = 0; var weekWords = 0
    }

    private val entries = LinkedHashMap<String, Entry>()       // keyed by the name in lower case
    var week = 0; private set

    val size get() = entries.size
    private fun key(name: String) = name.trim().lowercase()

    /** Starts a new week when the calendar week has changed: weekly numbers go back to zero, totals stay. */
    fun rollWeek(now: Int) {
        if (now == week) return
        week = now
        for (e in entries.values) { e.weekPoints = 0; e.weekMath = 0; e.weekWords = 0 }
    }

    /** Adds one finished game for a player. Players without a name are not recorded. Returns the points earned. */
    fun record(name: String, won: Boolean, math: Int, words: Int, weekNow: Int): Int {
        if (name.isBlank()) return 0
        rollWeek(weekNow)
        val e = entries.getOrPut(key(name)) { Entry(name.trim()) }
        e.name = name.trim()
        val earned = PLAY_POINTS + (if (won) WIN_POINTS else 0) + math.coerceAtLeast(0) + words.coerceAtLeast(0)
        e.points += earned; e.weekPoints += earned; e.games++; if (won) e.wins++
        e.math += math.coerceAtLeast(0); e.weekMath += math.coerceAtLeast(0); e.words += words.coerceAtLeast(0); e.weekWords += words.coerceAtLeast(0)
        return earned
    }

    fun top(n: Int = 8) = entries.values.sortedWith(compareByDescending<Entry> { it.points }.thenByDescending { it.wins }).take(n)
    /** This week's champions; null while nobody has scored in that field this week. */
    fun weekChampion() = entries.values.filter { it.weekPoints > 0 }.maxByOrNull { it.weekPoints }
    fun mathChampion() = entries.values.filter { it.weekMath > 0 }.maxByOrNull { it.weekMath }
    fun wordChampion() = entries.values.filter { it.weekWords > 0 }.maxByOrNull { it.weekWords }
    fun clear() { entries.clear() }

    /** One line per player, tab separated; the first line is the week. Names cannot contain tabs or line breaks. */
    fun save(): String = buildString {
        append(week).append('\n')
        for (e in entries.values) append(listOf(e.name, e.points, e.wins, e.games, e.math, e.words, e.weekPoints, e.weekMath, e.weekWords).joinToString("\t")).append('\n')
    }
    fun load(text: String) {
        entries.clear()
        val lines = text.split('\n').filter { it.isNotEmpty() }
        week = lines.firstOrNull()?.toIntOrNull() ?: 0
        for (l in lines.drop(1)) { val f = l.split('\t'); if (f.size < 9) continue
            val e = Entry(f[0]); val n = f.drop(1).map { it.toIntOrNull() ?: 0 }
            e.points = n[0]; e.wins = n[1]; e.games = n[2]; e.math = n[3]; e.words = n[4]; e.weekPoints = n[5]; e.weekMath = n[6]; e.weekWords = n[7]
            entries[key(e.name)] = e }
    }

    companion object {
        const val PLAY_POINTS = 2      // for finishing any game
        const val WIN_POINTS = 10      // extra for winning it
        fun cleanName(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('|', ' ').trim().take(14)
    }
}
