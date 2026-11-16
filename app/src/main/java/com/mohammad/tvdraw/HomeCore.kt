package com.mohammad.tvdraw

/**
 * The household side of the family tools: announcements that repeat every day at a set time,
 * and chores that earn points. Plain Kotlin with no Android code, so the rules can be tested on their own.
 */
class HomeCore {
    /** minute = minutes after midnight; lastDay = the day it last rang, so it rings once a day */
    class Alarm(val id: Int, val minute: Int, val text: String) { var lastDay = -1 }
    /** A chore is open again every new day; doneBy is who ticked it on doneDay. */
    class Chore(val id: Int, val points: Int, val text: String) { var doneDay = -1; var doneBy = "" }

    val alarms = ArrayList<Alarm>()
    val chores = ArrayList<Chore>()
    private var nextId = 1

    fun addAlarm(minute: Int, text: String): Alarm? {
        val t = clean(text, 40)
        if (minute !in 0..1439 || t.isEmpty() || alarms.size >= MAX_ALARMS) return null
        return Alarm(nextId++, minute, t).also { alarms.add(it); alarms.sortBy { a -> a.minute } }
    }
    fun delAlarm(id: Int) = alarms.removeAll { it.id == id }

    /** The alarms that should ring now. An alarm rings in its own minute or the one after, once a day. */
    fun due(minuteNow: Int, day: Int): List<Alarm> {
        val out = alarms.filter { it.lastDay != day && (minuteNow - it.minute) in 0..1 }
        out.forEach { it.lastDay = day }
        return out
    }

    fun addChore(points: Int, text: String): Chore? {
        val t = clean(text, 40)
        if (t.isEmpty() || chores.size >= MAX_CHORES) return null
        return Chore(nextId++, points.coerceIn(1, 50), t).also { chores.add(it) }
    }
    fun delChore(id: Int) = chores.removeAll { it.id == id }
    fun isDone(c: Chore, day: Int) = c.doneDay == day

    /** Ticks a chore for a named player. Returns it when this was the first tick today, so points are given once. */
    fun done(id: Int, name: String, day: Int): Chore? {
        val c = chores.firstOrNull { it.id == id } ?: return null
        if (name.isBlank() || c.doneDay == day) return null
        c.doneDay = day; c.doneBy = name.trim()
        return c
    }
    /** Takes today's tick back (a parent's "not yet"). Returns the chore so its points can be taken back too. */
    fun undo(id: Int, day: Int): Chore? {
        val c = chores.firstOrNull { it.id == id } ?: return null
        if (c.doneDay != day) return null
        c.doneDay = -1
        return c
    }

    /** One tab separated line per item. Texts cannot contain tabs or line breaks. */
    fun save(): String = buildString {
        append(nextId).append('\n')
        for (a in alarms) append(listOf("A", a.id, a.minute, a.lastDay, a.text).joinToString("\t")).append('\n')
        for (c in chores) append(listOf("C", c.id, c.points, c.doneDay, c.doneBy, c.text).joinToString("\t")).append('\n')
    }
    fun load(text: String) {
        alarms.clear(); chores.clear()
        val lines = text.split('\n').filter { it.isNotEmpty() }
        nextId = lines.firstOrNull()?.toIntOrNull() ?: 1
        for (l in lines.drop(1)) { val f = l.split('\t')
            try {
                if (f[0] == "A" && f.size >= 5) alarms.add(Alarm(f[1].toInt(), f[2].toInt(), f[4]).apply { lastDay = f[3].toInt() })
                else if (f[0] == "C" && f.size >= 6) chores.add(Chore(f[1].toInt(), f[2].toInt(), f[5]).apply { doneDay = f[3].toInt(); doneBy = f[4] })
            } catch (e: NumberFormatException) { }
        }
        nextId = maxOf(nextId, (alarms.map { it.id } + chores.map { it.id }).maxOrNull()?.plus(1) ?: 1)
    }

    companion object {
        const val MAX_ALARMS = 12; const val MAX_CHORES = 20
        fun clean(s: String, max: Int) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').replace('|', ' ').trim().take(max)
    }
}
