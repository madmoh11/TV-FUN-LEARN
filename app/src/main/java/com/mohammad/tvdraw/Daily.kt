package com.mohammad.tvdraw

import org.json.JSONObject

/** What the home screen reads aloud or shows page by page: surahs, the ruqyah verses and the daily adhkar. */
object Daily {
    /** One screen of text. surah/ayah say which recitation file goes with it; 0 means there is none. */
    class Page(val text: String, val surah: Int, val ayah: Int, val quran: Boolean, val count: Int = 1, val note: String = "", val label: String = "")

    private fun ayah(q: Quran, s: Int, a: Int) = q.text(s, a) + " \uFD3F" + Quran.digits(a) + "\uFD3E"

    /** A whole surah, one ayah to a page, opened by the basmala where the mushaf has one. */
    fun surah(q: Quran, n: Int): List<Page> {
        val s = q.surah(n) ?: return emptyList(); val out = ArrayList<Page>()
        if (q.hasBasmala(n)) out.add(Page(q.basmala, 1, 1, true, label = s.name))
        for (a in 1..s.ayahs) out.add(Page(ayah(q, n, a), n, a, true, label = s.name + "  •  " + a + " / " + s.ayahs))
        return out
    }

    /** Stretches of ayahs, each given as surah, first ayah, last ayah. A surah read from its start gets its basmala. */
    fun ranges(q: Quran, r: List<IntArray>): List<Page> {
        val out = ArrayList<Page>()
        for (x in r) { val s = q.surah(x[0]) ?: continue
            if (x[1] == 1 && q.hasBasmala(x[0])) out.add(Page(q.basmala, 1, 1, true, label = s.name))
            for (a in x[1]..x[2]) out.add(Page(ayah(q, x[0], a), x[0], a, true, label = s.name + "  •  " + a)) }
        return out
    }

    /** The verses most often recited as ruqyah: al-Fatiha, ayat al-Kursi, the end of al-Baqara and the last three surahs. */
    val RUQYAH = listOf(intArrayOf(1, 1, 7), intArrayOf(2, 255, 255), intArrayOf(2, 285, 286), intArrayOf(112, 1, 4), intArrayOf(113, 1, 5), intArrayOf(114, 1, 6))
    /** Surahs offered on the home screen with one press. */
    val SURAHS = intArrayOf(18, 2, 36, 67, 56, 55)

    /**
     * Morning (evening = false) or evening adhkar from the data file. A passage of the Quran is never stored there as
     * text: the file names the ayahs and the words come from the Tanzil text.
     */
    fun adhkar(json: String, q: Quran, evening: Boolean): List<Page> {
        val out = ArrayList<Page>()
        val items = JSONObject(json).getJSONArray("items")
        for (i in 0 until items.length()) { val o = items.getJSONObject(i); val type = o.getInt("type")
            if (type != 0 && type != (if (evening) 2 else 1)) continue
            val count = o.getInt("count"); val note = o.optString("fadl")
            val qr = o.optJSONArray("quran")
            if (qr == null) { out.add(Page(o.getString("text"), 0, 0, false, count, note)); continue }
            val sb = StringBuilder()
            for (j in 0 until qr.length()) { val x = qr.getJSONArray(j); val s = x.getInt(0)
                if (x.getInt(1) == 1 && q.hasBasmala(s)) sb.append(q.basmala).append('\n')
                for (a in x.getInt(1)..x.getInt(2)) sb.append(ayah(q, s, a)).append(' ') }
            out.add(Page(sb.toString().trim(), 0, 0, true, count, note, o.optString("text")))
        }
        return out
    }

    /** Apps that lead the row until the family arranges it: YouTube first. */
    val APPS_FIRST = listOf("com.google.android.youtube.tv", "com.google.android.youtube", "com.google.android.youtube.tvkids")

    /**
     * The order of the apps on the home screen. apps are package names with their titles; saved is the order the
     * family chose. Apps not arranged yet follow: YouTube, then the rest by title. Hidden apps are left out.
     */
    fun appOrder(apps: List<Pair<String, String>>, saved: List<String>, hidden: Set<String>): List<String> {
        val have = apps.map { it.first }.toSet()
        val placed = saved.filter { it in have }.distinct()
        val rest = apps.filter { it.first !in placed }.sortedWith(compareBy({ APPS_FIRST.indexOf(it.first).let { i -> if (i >= 0) i else if (it.first.contains("youtube", true) || it.second.contains("youtube", true)) 50 else 99 } }, { it.second.lowercase() })).map { it.first }
        return (placed + rest).filter { it !in hidden }
    }
    /** The shown order after one app is moved: how is "first", "earlier" or "later". */
    fun moveApp(shown: List<String>, pkg: String, how: String): List<String> {
        val l = shown.toMutableList(); val i = l.indexOf(pkg); if (i < 0) return shown
        val to = when (how) { "first" -> 0; "earlier" -> i - 1; "later" -> i + 1; else -> i }.coerceIn(0, l.size - 1)
        l.removeAt(i); l.add(to, pkg); return l
    }

    /** Which prayer comes next and how many minutes away it is. today holds the five times in minutes of the day. */
    fun next(now: Int, today: IntArray, tomorrowFajr: Int): IntArray {
        for (i in today.indices) if (today[i] > now) return intArrayOf(i, today[i] - now)
        return intArrayOf(0, tomorrowFajr + 1440 - now)
    }
}
