package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar

/**
 * Learning the Quran by heart on the TV. "Listen" shows one ayah, plays its recitation and hides more and more of
 * its words; "test" hides the text while the child recites and a grown-up marks each ayah on the phone; "read" listens
 * through the phone and writes each word on the TV as it is recited, stopping at a wrong word until it is said right.
 * Recited parts come back for revision after a growing number of days.
 */
class Hifz(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    companion object { const val OFF = 0; const val LISTEN = 1; const val TEST = 2; const val RESULT = 3; const val READ = 4; const val PAGE_WORDS = 36
        const val GONE_MS = 600000L
        val RECITERS = arrayOf("Husary_Muallim_128kbps", "Husary_64kbps")
        fun url(reciter: Int, surah: Int, ayah: Int) = "https://everyayah.com/data/" + RECITERS[reciter.coerceIn(0, RECITERS.size - 1)] + "/" + String.format(java.util.Locale.US, "%03d%03d.mp3", surah, ayah) }
    var sfx: Sfx? = null
    /** Plays one recording from the internet and calls back when it has ended (or could not be played). */
    var play: ((String, () -> Unit) -> Unit)? = null
    var stop: (() -> Unit)? = null
    private val sp = view.context.getSharedPreferences("family", 0)
    val quran: Quran by lazy {
        fun read(n: String) = try { view.context.assets.open("quran/$n").use { it.readBytes().toString(Charsets.UTF_8) } } catch (e: Throwable) { "" }
        Quran(read("quran-uthmani.txt"), read("surahs.txt"), read("quran-simple-clean.txt"))
    }
    private val font: Typeface by lazy { try { Typeface.createFromAsset(view.context.assets, "fonts/AmiriQuran.ttf") } catch (e: Throwable) { Typeface.DEFAULT } }

    var who = sp.getString("hifz_who", "") ?: ""; private set          // whose memorising is being followed
    private var plan = loadPlan()
    private fun loadPlan() = HifzPlan().apply { load(sp.getString("hifz:" + who.lowercase(), "") ?: "") }
    private fun store() = sp.edit().putString("hifz:" + who.lowercase(), plan.save()).putString("hifz_who", who).apply()
    private fun today() = Calendar.getInstance().let { (it.timeInMillis + it.timeZone.getOffset(it.timeInMillis)) / 86400000L }.toInt()

    private var state = OFF
    private var surah = 1; private var from = 1; private var to = 1; private var cur = 1
    private var hide = 0; private var auto = false; private var reciter = 0
    private var marks = IntArray(0)                                    // test: 0 not yet, 1 right, 2 wrong
    private var partId = ""; private var playing = false; private var token = 0
    private var touched = 0L; private var stateAt = 0L; private var wrongAyah = 0; private var wrongAt = 0L
    private var result: HifzPlan.Entry? = null
    private var rec: Recite? = null                                    // read: follows the recitation word by word
    private var pages: List<IntRange> = emptyList()                    // read: the ayahs shown together on the TV
    val active get() = state != OFF
    /** Why the last recitation could not be played, or empty. Shown on the TV and the phone. */
    @Volatile var audioError = ""
    private var sound = true                                          // false when a phone plays the recitation itself
    private var quizParts: List<Quran.Part>? = null
    /** What the Quran challenge asks about: a chosen stretch, else the memorised juz, else the last juz. */
    fun quizScope(): List<Quran.Part> = quizParts ?: quran.parts.filter { it.juz in plan.juz }.ifEmpty { quran.parts.filter { it.juz == 30 } }

    fun stopNow() { state = OFF; token++; playing = false; stop?.invoke() }
    private fun enc(t: String) = URLEncoder.encode(t, "UTF-8")

    private fun recite() {
        if (state != LISTEN) return
        val mine = ++token
        if (!sound) { stop?.invoke(); playing = false; view.invalidate(); return }
        playing = true
        val ayah = { play?.invoke(url(reciter, surah, cur)) { if (mine == token) { playing = false; ended() } } ?: run { playing = false } }
        if (cur == 1 && quran.hasBasmala(surah)) play?.invoke(url(reciter, 1, 1)) { if (mine == token) ayah() } ?: run { playing = false } else ayah()
        view.invalidate()
    }
    /** In automatic mode the child gets time to repeat the ayah, then the next one follows. */
    private fun ended() {
        view.invalidate()
        if (!auto || state != LISTEN) return
        val mine = token; val pause = 1500L + 650L * quran.words(surah, cur).count { !Quran.isMark(it) }
        view.postDelayed({ if (mine == token && state == LISTEN && auto) { if (cur < to) { cur++; recite() } else auto = false; view.invalidate() } }, pause)
    }
    private fun open(mode: Int, s: Int, a: Int, b: Int, id: String) {
        val su = quran.surah(s) ?: return
        token++; stop?.invoke(); playing = false
        surah = s; from = a.coerceIn(1, su.ayahs); to = b.coerceIn(from, su.ayahs); cur = from; partId = id
        marks = IntArray(to - from + 1); result = null; wrongAyah = 0
        state = mode; stateAt = SystemClock.uptimeMillis()
        rec = null
        if (mode == READ) {
            rec = Recite((from..to).flatMap { a -> quran.spoken(s, a).map { Recite.Item(a, it.from, it.to, it.say, it.whole) } })
            val pg = ArrayList<IntRange>(); var start = from; var count = 0
            for (a in from..to) { val n = quran.words(s, a).size; if (a > start && count + n > PAGE_WORDS) { pg.add(start until a); start = a; count = 0 }; count += n }
            pg.add(start..to); pages = pg
        }
        if (mode == LISTEN) recite() else { hide = 0; auto = false }
    }
    /** After something was heard or a grown-up helped: sounds, the ayah that is waited for, and the end. */
    private fun followed(flags: Int, now: Long) {
        val r = rec ?: return
        if (flags and Recite.MISTAKE != 0) sfx?.play("thump", 0.5f, true)
        if (r.done) { for (a in from..to) marks[a - from] = if (r.items.indices.any { r.items[it].ayah == a && r.wrong[it] }) 2 else 1; cur = to; finish(now) }
        else { cur = r.items[r.current].ayah; if (flags and Recite.AYAH != 0) sfx?.play("blip", 0.5f, true) }
    }

    /**
     * "hz who NAME", "hz juz N 0|1", "hz listen S FROM TO", "hz test S FROM TO", "hz read S FROM TO", "hz part ID [listen|read]" (a part of today's portion, tested by a grown-up unless said otherwise),
     * "hz heard SESSION FINAL OFFSET WORDS [OTHER|OTHER]" (what the phone's speech recogniser hears), "hz reveal" (show the word, a mistake), "hz ok" (the word was said right),
     * "hz next", "hz prev", "hz go AYAH", "hz hide LEVEL", "hz say", "hz auto 0|1", "hz reciter n", "hz sound 0|1" (off when a phone plays the recitation), "hz mark 1|0", "hz quiz juz" or "hz quiz S FROM TO" (what the challenge asks about), "hz close"
     */
    fun command(pp: List<String>, cid: String) = synchronized(this) {
        val now = SystemClock.uptimeMillis(); touched = now
        when (pp[1]) {
            "who" -> { val n = Board.cleanName(URLDecoder.decode(pp.getOrElse(2) { "" }, "UTF-8")); if (n != who) { who = n; plan = loadPlan(); store() } }
            "juz" -> { val n = pp[2].toInt(); if (n in 1..30) { if (pp.getOrElse(3) { "1" } == "1") plan.juz.add(n) else plan.juz.remove(n); store() } }
            "listen" -> open(LISTEN, pp[2].toInt(), pp[3].toInt(), pp[4].toInt(), "")
            "test" -> open(TEST, pp[2].toInt(), pp[3].toInt(), pp[4].toInt(), "")
            "read" -> open(READ, pp[2].toInt(), pp[3].toInt(), pp[4].toInt(), "")
            "part" -> quran.part(pp[2])?.let { open(when (pp.getOrElse(3) { "test" }) { "listen" -> LISTEN; "read" -> READ; else -> TEST }, it.surah, it.from, it.to, it.id) }
            "heard" -> if (state == READ) rec?.let { r -> fun list(t: String) = URLDecoder.decode(t, "UTF-8").split(' ').filter { it.isNotEmpty() }
                followed(r.heard(pp[2].toInt(), pp[3] == "1", pp[4].toInt(), list(pp.getOrElse(5) { "" }), pp.getOrElse(6) { "" }.split("%7C", "|").filter { it.isNotEmpty() }.map { list(it) }), now) }
            "reveal" -> if (state == READ) rec?.let { followed(it.reveal(), now) }
            "ok" -> if (state == READ) rec?.let { followed(it.accept(), now) }
            "next" -> if (state == LISTEN && cur < to) { cur++; recite() }
            "prev" -> if (state == LISTEN && cur > from) { cur--; recite() }
            "go" -> if (state == LISTEN) { cur = pp[2].toInt().coerceIn(from, to); recite() }
            "hide" -> if (state == LISTEN) hide = pp[2].toInt().coerceIn(0, 3)
            "say" -> recite()
            "sound" -> { sound = pp[2] == "1"; if (!sound) { token++; stop?.invoke(); playing = false; auto = false; audioError = "" } }
            "auto" -> if (state == LISTEN) { auto = pp[2] == "1"; if (auto && !playing) recite() }
            "reciter" -> { reciter = pp[2].toInt().coerceIn(0, RECITERS.size - 1); if (state == LISTEN) recite() }
            "mark" -> if (state == TEST) { val ok = pp[2] == "1"
                marks[cur - from] = if (ok) 1 else 2
                if (ok) sfx?.play("blip", 0.6f, true) else { wrongAyah = cur; wrongAt = now; sfx?.play("thump", 0.5f, true) }
                if (cur < to) cur++ else finish(now) }
            "quiz" -> { quizParts = if (pp.getOrElse(2) { "juz" } == "juz") null else quran.surah(pp[2].toInt())?.let { su ->
                    val a = pp[3].toInt().coerceIn(1, su.ayahs); listOf(Quran.Part(su.number, a, pp[4].toInt().coerceIn(a, su.ayahs), 0)) }
                stopNow() }
            "close" -> stopNow()
        }
        view.invalidate()
    }
    private fun finish(now: Long) {
        val wrong = marks.count { it == 2 }
        // only a whole part counts for the revision plan; a hand-picked stretch is practice
        val id = partId.ifEmpty { quran.parts.firstOrNull { it.surah == surah && it.from == from && it.to == to }?.id ?: "" }
        result = if (id.isEmpty()) null else plan.record(id, to - from + 1, wrong, today()).also { store() }
        state = RESULT; stateAt = now; sfx?.play("chime", 1f, true)
    }

    /**
     * First line: state|surah|from|to|current|hide|auto|reciter|mistakes|playing|streak|strong parts|parts in my juz|done today|name
     * then "X|text of the current ayah" (in read: only the words recited so far), "W|a mistake waits|mistakes|words done|words|last heard" in read, "J|memorised juz", "R|mistakes|ayahs|level|days until next" after a test,
     * and one "D|part|surah|from|to|level|days overdue (or -999 when new)" for each part of today's portion.
     */
    fun status(): String = synchronized(this) {
        val day = today(); val sb = StringBuilder()
        val mine = quran.parts.count { it.juz in plan.juz }
        sb.append(state).append('|').append(surah).append('|').append(from).append('|').append(to).append('|').append(cur).append('|').append(hide).append('|').append(if (auto) 1 else 0).append('|').append(reciter)
            .append('|').append(marks.count { it == 2 }).append('|').append(if (playing) 1 else 0).append('|').append(plan.streakOn(day)).append('|').append(plan.strong(quran.parts)).append('|').append(mine)
            .append('|').append(plan.doneToday(day)).append('|').append(enc(who)).append('\n')
        if (state == LISTEN || state == TEST) sb.append("X|").append(enc(quran.text(surah, cur))).append('\n')
        rec?.let { r -> if (state == READ) { sb.append("X|").append(enc(masked(r, cur).toString())).append('\n')
            sb.append("W|").append(if (r.pending) 1 else 0).append('|').append(r.mistakes).append('|').append(r.current).append('|').append(r.items.size).append('|').append(enc(r.lastHeard)).append('\n') } }
        sb.append("J|").append(plan.juz.joinToString(",")).append('\n')
        if (audioError.isNotEmpty() && state == LISTEN) sb.append("E|").append(enc(audioError)).append('\n')
        sb.append("S|").append(if (sound) 1 else 0).append('\n')
        result?.let { if (state == RESULT) sb.append("R|").append(it.mistakes).append('|').append(to - from + 1).append('|').append(it.level).append('|').append(it.due - day).append('\n') }
        for (p in plan.today(quran.parts, day)) { val e = plan.entry(p.id)
            sb.append("D|").append(p.id).append('|').append(p.surah).append('|').append(p.from).append('|').append(p.to).append('|').append(e?.level ?: 0).append('|').append(if (e == null) -999 else day - e.due).append('\n') }
        sb.toString()
    }

    // ---- drawing ----
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = false) = Ui.text(cv, view, t, x, y, size, color, outline)
    /** Quran text set in lines inside a box, as large as fits. */
    private fun para(cv: Canvas, t: CharSequence, cx: Float, top: Float, width: Float, maxH: Float, size: Float, color: Int) {
        var sz = size; var lay: StaticLayout
        while (true) { tp.typeface = font; tp.textSize = sz; tp.color = color
            lay = StaticLayout.Builder.obtain(t, 0, t.length, tp, width.toInt()).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 0.8f).build()
            if (lay.height <= maxH || sz <= 26f) break
            sz *= 0.9f }
        cv.save(); cv.translate(cx - width / 2, top + maxOf(0f, (maxH - lay.height) / 2)); lay.draw(cv); cv.restore()
    }
    /** The ayah number between ornate brackets, as it closes an ayah in a mushaf. */
    private fun mark(ayah: Int) = "﴿" + Quran.digits(ayah) + "﴾"
    private fun ayahText(ayah: Int, level: Int): String {
        val w = quran.words(surah, ayah); val keep = Quran.shown(w, level)
        return w.mapIndexed { i, x -> if (keep[i]) x else "ـــــ" }.joinToString(" ") + " " + mark(ayah)
    }

    /** One ayah as it stands while it is recited: the words said so far, a line for each word still to come, and its number. */
    private fun masked(r: Recite, ayah: Int, colors: Boolean = false): CharSequence {
        val w = quran.words(surah, ayah); val sb = android.text.SpannableStringBuilder()
        fun add(t: String, color: Int) { val a = sb.length; sb.append(t); if (colors) sb.setSpan(android.text.style.ForegroundColorSpan(color), a, sb.length, 0); sb.append(' ') }
        for (i in r.items.indices) { val it = r.items[i]; if (it.ayah != ayah) continue
            val word = w.subList(it.from, it.to + 1).joinToString(" ")
            if (i < r.current) add(word, if (r.wrong[i]) Color.rgb(255, 170, 150) else Color.WHITE)
            else add("ـ".repeat(w[it.from].count { c -> c in 'ء'..'ي' || c == 'ٱ' }.coerceIn(4, 8)), if (i > r.current) Color.argb(95, 255, 255, 255) else if (r.pending) Color.rgb(255, 80, 70) else Ui.GOLD) }
        add(mark(ayah), Color.rgb(220, 200, 140)); return sb
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        if (now - touched > GONE_MS && !playing) { stopNow(); return false }
        synchronized(this) {
            val w = view.width.toFloat(); val h = view.height.toFloat(); val k = h / 1080f
            cv.drawColor(Color.rgb(14, 30, 26))
            p.style = Paint.Style.STROKE; p.strokeWidth = 6f * k; p.color = Ui.GOLD; cv.drawRoundRect(RectF(36f * k, 36f * k, w - 36f * k, h - 36f * k), 30f * k, 30f * k, p)
            p.strokeWidth = 2f * k; cv.drawRoundRect(RectF(52f * k, 52f * k, w - 52f * k, h - 52f * k), 22f * k, 22f * k, p); p.style = Paint.Style.FILL
            val su = quran.surah(surah)
            tp.typeface = font; tp.textAlign = Paint.Align.CENTER; tp.textSize = 76f * k; tp.color = Ui.GOLD
            cv.drawText("سُورَةُ " + (su?.name ?: ""), w / 2, 160f * k, tp); tp.textAlign = Paint.Align.LEFT
            if (who.isNotEmpty()) text(cv, who, w - 190f * k, 130f * k, 38f * k, Color.WHITE)
            val n = to - from + 1
            when (state) {
                LISTEN -> {
                    if (cur == 1 && quran.hasBasmala(surah)) para(cv, quran.basmala, w / 2, 190f * k, w * 0.7f, 110f * k, 60f * k, Color.argb(255, 220, 200, 140))
                    para(cv, ayahText(cur, hide), w / 2, 290f * k, w * 0.9f, h * 0.55f, 108f * k, Color.WHITE)
                    val label = intArrayOf(R.string.hz_hide0, R.string.hz_hide1, R.string.hz_hide2, R.string.hz_hide3)[hide]
                    text(cv, (if (playing) "🔊  " else "") + s(label) + (if (auto) "   •   " + s(R.string.hz_auto) else ""), w / 2, h - 150f * k, 38f * k, Color.argb(255, 190, 215, 200))
                    text(cv, s(R.string.hz_ayah_of, cur - from + 1, n), w / 2, h - 90f * k, 34f * k, Ui.GOLD)
                    if (audioError.isNotEmpty()) { text(cv, s(R.string.hz_no_sound), w / 2, h - 232f * k, 34f * k, Color.rgb(255, 150, 130))
                        text(cv, audioError, w / 2, h - 196f * k, 24f * k, Color.argb(200, 255, 150, 130)) }
                }
                READ -> rec?.let { r ->
                    val page = pages.firstOrNull { cur in it } ?: (cur..cur)
                    if (page.first == 1 && quran.hasBasmala(surah)) para(cv, quran.basmala, w / 2, 190f * k, w * 0.7f, 110f * k, 60f * k, Color.argb(255, 220, 200, 140))
                    else text(cv, s(R.string.hz_read), w / 2, 250f * k, 40f * k, Color.argb(255, 190, 215, 200))
                    val sb = android.text.SpannableStringBuilder(); for (a in page) sb.append(masked(r, a, true))
                    para(cv, sb, w / 2, 300f * k, w * 0.9f, h * 0.5f, 100f * k, Color.WHITE)
                    if (r.pending) {                                               // a wrong word: nothing is written until the right one is said
                        p.style = Paint.Style.STROKE; p.strokeWidth = 10f * k; p.color = Color.rgb(255, 80, 70); cv.drawRoundRect(RectF(36f * k, 36f * k, w - 36f * k, h - 36f * k), 30f * k, 30f * k, p); p.style = Paint.Style.FILL
                        p.color = Color.rgb(179, 38, 30); cv.drawRoundRect(RectF(w / 2 - 520f * k, h - 222f * k, w / 2 + 520f * k, h - 132f * k), 45f * k, 45f * k, p)
                        text(cv, s(R.string.hz_read_wrong), w / 2, h - 162f * k, 46f * k, Color.WHITE) }
                    text(cv, s(R.string.hz_ayah_of, cur - from + 1, n) + (if (r.mistakes > 0) "   •   " + s(R.string.hz_read_mistakes, r.mistakes) else ""), w / 2, h - 90f * k, 34f * k, Ui.GOLD)
                }
                TEST -> {
                    text(cv, s(R.string.hz_test), w / 2, 260f * k, 54f * k, Color.WHITE, true)
                    tp.typeface = font; tp.textAlign = Paint.Align.CENTER; tp.textSize = 190f * k; tp.color = Ui.GOLD
                    cv.drawText(mark(cur), w / 2, h * 0.52f, tp); tp.textAlign = Paint.Align.LEFT
                    text(cv, s(R.string.hz_recite, cur), w / 2, h * 0.52f + 130f * k, 44f * k, Color.argb(255, 190, 215, 200))
                    if (wrongAyah != 0 && now - wrongAt < 9000) {                    // the ayah that went wrong, shown for a moment so it can be put right
                        para(cv, ayahText(wrongAyah, 0), w / 2, h * 0.7f, w * 0.84f, 150f * k, 50f * k, Color.rgb(255, 170, 150)); view.postInvalidateDelayed(9000 - (now - wrongAt) + 30) }
                    val step = minOf(64f * k, (w - 240f * k) / n); val left = w / 2 - step * n / 2                     // one dot for each ayah, read from the right
                    for (i in 0 until n) { p.color = when (marks[i]) { 1 -> Color.rgb(52, 199, 89); 2 -> Color.rgb(255, 80, 70); else -> Color.argb(120, 255, 255, 255) }
                        cv.drawCircle(left + step * (n - 1 - i) + step / 2, h - 110f * k, minOf(18f * k, step * 0.36f) * (if (i == cur - from) 1.35f else 1f), p) }
                }
                RESULT -> {
                    val wrong = marks.count { it == 2 }; val good = wrong * 10 <= n
                    if (good) Ui.confetti(cv, view, now - stateAt)
                    text(cv, if (wrong == 0) s(R.string.hz_perfect) else if (good) s(R.string.hz_good) else s(R.string.hz_again), w / 2, h * 0.4f, 100f * k, if (good) Ui.GOLD else Color.WHITE, true)
                    text(cv, s(R.string.hz_result, n - wrong, n), w / 2, h * 0.52f, 58f * k, Color.WHITE)
                    result?.let { text(cv, if (it.due - today() <= 1) s(R.string.hz_tomorrow) else s(R.string.hz_next_in, it.due - today()), w / 2, h * 0.62f, 46f * k, Color.argb(255, 190, 215, 200)) }
                    val st = plan.streakOn(today()); if (st > 1) text(cv, "🔥 " + s(R.string.hz_streak, st), w / 2, h * 0.74f, 50f * k, Ui.GOLD)
                }
            }
        }
        return state == RESULT && now - stateAt < 9000
    }
}
