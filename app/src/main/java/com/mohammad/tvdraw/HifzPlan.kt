package com.mohammad.tvdraw

/**
 * When each memorised part of the Quran should be recited again. A part recited well comes back after a longer
 * and longer wait; one with mistakes comes back the next day. Plain Kotlin, so the rules can be tested on their own.
 */
class HifzPlan {
    /** level = how many good recitations in a row; due = the day it should be recited again; day numbers count days. */
    class Entry(val part: String) { var level = 0; var due = 0; var last = 0; var mistakes = 0; var times = 0 }

    val juz = sortedSetOf<Int>()                          // the juz the child has memorised
    private val entries = LinkedHashMap<String, Entry>()
    var streak = 0; private set                           // days in a row with at least one recitation
    private var streakDay = 0

    fun entry(part: String) = entries[part]

    /** Records one recitation of a part. Up to one mistake in ten ayahs still counts as a good one. */
    fun record(part: String, ayahs: Int, mistakes: Int, day: Int): Entry {
        val e = entries.getOrPut(part) { Entry(part) }
        val good = mistakes * 10 <= ayahs
        e.level = if (good) minOf(e.level + 1, WAIT.size) else 0
        e.due = day + (if (good) WAIT[e.level - 1] else 1)
        e.last = day; e.mistakes = mistakes; e.times++
        if (day != streakDay) { streak = if (day == streakDay + 1) streak + 1 else 1; streakDay = day }
        return e
    }
    /** The streak as it stands today: it is lost when a whole day was skipped. */
    fun streakOn(day: Int) = if (day - streakDay <= 1) streak else 0

    /** Today's portion from the memorised juz: parts that are due first (the most overdue first), then parts never recited. */
    fun today(parts: List<Quran.Part>, day: Int, limit: Int = DAILY): List<Quran.Part> {
        val mine = parts.filter { it.juz in juz }
        val due = mine.filter { val e = entries[it.id]; e != null && e.due <= day }.sortedBy { entries[it.id]!!.due }
        val fresh = mine.filter { entries[it.id] == null }.asReversed()        // short surahs at the end of the mushaf first
        return (due + fresh).take(limit)
    }
    fun doneToday(day: Int) = entries.values.count { it.last == day }
    /** How many parts of the memorised juz have been recited well at least once. */
    fun strong(parts: List<Quran.Part>) = parts.count { it.juz in juz && (entries[it.id]?.level ?: 0) > 0 }

    fun save(): String = buildString {
        append(juz.joinToString(",")).append('\n').append(streak).append(' ').append(streakDay).append('\n')
        for (e in entries.values) append(listOf(e.part, e.level, e.due, e.last, e.mistakes, e.times).joinToString(" ")).append('\n')
    }
    fun load(text: String) {
        juz.clear(); entries.clear(); streak = 0; streakDay = 0
        val l = text.split('\n')
        l.getOrNull(0)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..30 }?.let { juz.addAll(it) }
        l.getOrNull(1)?.split(' ')?.let { streak = it.getOrNull(0)?.toIntOrNull() ?: 0; streakDay = it.getOrNull(1)?.toIntOrNull() ?: 0 }
        for (x in l.drop(2)) { val f = x.split(' '); if (f.size < 6) continue
            val e = Entry(f[0]); val n = f.drop(1).map { it.toIntOrNull() ?: 0 }
            e.level = n[0]; e.due = n[1]; e.last = n[2]; e.mistakes = n[3]; e.times = n[4]; entries[e.part] = e }
    }

    companion object {
        val WAIT = intArrayOf(1, 3, 7, 14, 30, 60)          // days until the next recitation, by level
        const val DAILY = 5
    }
}
