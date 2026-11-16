package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** The room quiz on the TV: flags, capitals and English spelling. One question for everybody, answered on the phones. */
class QuizGame(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    companion object { const val OFF = 0; const val LOBBY = 1; const val COUNT = 2; const val ASK = 3; const val REVEAL = 4; const val END = 5
        const val GONE_MS = 8000L; const val CHOICE_MS = 12000L; const val TYPE_MS = 25000L; const val REVEAL_MS = 4500L
        val COLORS = intArrayOf(0xFFD92D20.toInt(), 0xFF1F6FFF.toInt(), 0xFFE0A800.toInt(), 0xFF1F9D46.toInt())
        val TITLES = intArrayOf(R.string.quiz_flags, R.string.quiz_caps, R.string.quiz_spell) }
    var sfx: Sfx? = null
    var onEnd: ((List<GameResult>) -> Unit)? = null
    /** Extra points for right answers that the board does not count by itself: name, points. */
    var onBonus: ((String, Int) -> Unit)? = null
    /** Says an English word aloud on the TV, when the device has a speech engine. */
    var speak: ((String) -> Unit)? = null
    private val core = QuizCore()
    private var state = OFF
    private var stateAt = 0L; private var countShown = -1; private var said = 0
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val active get() = state != OFF

    private val arabic get() = Locale.getDefault().language == "ar"
    private class Country(val code: String, val name: String, val capital: String, val level: Int)
    private val countries: List<Country> by lazy {
        val out = ArrayList<Country>()
        try { val a = org.json.JSONArray(view.context.assets.open("data/countries.json").use { it.readBytes().toString(Charsets.UTF_8) })
            for (i in 0 until a.length()) { val o = a.getJSONObject(i)
                out.add(Country(o.getString("code"), o.getString(if (arabic) "ar" else "en"), o.getString(if (arabic) "cap_ar" else "cap_en"), o.getInt("level"))) } } catch (e: Throwable) { }
        out
    }
    /** Single English words with their Arabic meaning and difficulty 1..3. */
    private val words: List<Triple<String, String, Int>> by lazy {
        val out = ArrayList<Triple<String, String, Int>>()
        try { val a = org.json.JSONArray(view.context.assets.open("data/words_bank.json").use { it.readBytes().toString(Charsets.UTF_8) })
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); val en = o.getString("en")
                if (en.all { it.isLetter() }) out.add(Triple(en, o.getString("ar"), o.getInt("difficulty"))) } } catch (e: Throwable) { }
        out
    }
    private fun items(): List<QuizCore.Item> = when (core.mode) {
        QuizCore.FLAGS -> countries.filter { core.level > 0 || it.level == 1 }.map { QuizCore.Item(it.code, "", it.name) }
        QuizCore.CAPITALS -> countries.filter { core.level > 0 || it.level == 1 }.map { QuizCore.Item(it.code, it.name, it.capital) }
        else -> words.filter { it.third == core.level + 1 }.map { QuizCore.Item(it.first, it.second, it.first) }
    }
    private val flags = HashMap<String, Bitmap?>()
    private fun flag(code: String) = flags.getOrPut(code) { Ui.bitmap(view.context, "flags/$code.webp") }
    private fun limit() = if (core.typed) TYPE_MS else CHOICE_MS
    private fun enc(t: String) = URLEncoder.encode(t, "UTF-8")

    fun stopNow() { state = OFF; core.players.clear(); seen.clear() }

    /** state|mode|round|rounds|seconds left|my score|answered|right (-1 not shown yet)|players|my place at the end|level|in game|points just won|prompt|answer shown|word to say|options... */
    fun status(cid: String): String = synchronized(core) {
        val now = SystemClock.uptimeMillis(); seen[cid] = now
        val me = core.players[cid]; val q = core.current
        val shown = state == REVEAL || state == END
        val sb = StringBuilder()
        sb.append(state).append('|').append(core.mode).append('|').append(core.round + 1).append('|').append(core.rounds).append('|')
            .append(if (state == ASK) Math.ceil((limit() - (now - stateAt)) / 1000.0).toInt().coerceAtLeast(0) else 0).append('|')
            .append(me?.score ?: 0).append('|').append(if (me?.answered == true) 1 else 0).append('|').append(if (!shown || me == null) -1 else if (me.correct) 1 else 0).append('|')
            .append(core.players.size).append('|').append(if (state == END && me != null) core.place(me) else 0).append('|').append(core.level).append('|').append(if (me != null) 1 else 0).append('|')
            .append(me?.gained ?: 0).append('|')
        val asking = q != null && (state == ASK || state == REVEAL)
        sb.append(if (asking) enc(q!!.item.prompt) else "").append('|').append(if (asking && state == REVEAL) enc(q!!.item.answer) else "").append('|')
            .append(if (asking && core.typed) enc(q!!.item.answer) else "")
        if (asking) for (o in q!!.options) sb.append('|').append(enc(o))
        sb.toString()
    }

    /** "qz join MODE NAME", "qz leave", "qz start", "qz stop", "qz mode n", "qz rounds n", "qz level n", "qz ans ROUND VALUE" */
    fun command(pp: List<String>, cid: String) = synchronized(core) {
        val now = SystemClock.uptimeMillis(); seen[cid] = now
        val idle = state == LOBBY || state == END
        when (pp[1]) {
            "join" -> { val name = Board.cleanName(URLDecoder.decode(pp.getOrElse(3) { "" }, "UTF-8"))
                if (core.join(cid, name)) { if (state == OFF) { state = LOBBY; stateAt = now; core.mode = pp.getOrElse(2) { "0" }.toInt().coerceIn(0, 2) } } }
            "leave" -> { core.leave(cid); if (core.players.isEmpty()) stopNow() }
            "start" -> if (idle && core.players.isNotEmpty() && core.begin(items())) { state = COUNT; stateAt = now; countShown = -1 }
            "mode" -> if (idle) { core.mode = pp[2].toInt().coerceIn(0, 2); if (state == END) { state = LOBBY; stateAt = now } }
            "rounds" -> if (idle) core.rounds = pp[2].toInt().let { if (it == 5 || it == 15) it else 10 }
            "level" -> if (idle) core.level = pp[2].toInt().coerceIn(0, 2)
            "ans" -> if (state == ASK && pp[2].toInt() == core.round + 1 && core.answer(cid, URLDecoder.decode(pp.getOrElse(3) { "" }, "UTF-8"), now - stateAt, limit())) sfx?.play("blip", 0.5f)
            "stop" -> stopNow()
        }
        view.invalidate()
    }

    private fun ask(now: Long) { state = ASK; stateAt = now; said = 0 }
    private fun update(now: Long) = synchronized(core) {
        val gone = core.players.values.filter { now - (seen[it.cid] ?: now) > GONE_MS }.map { it.cid }
        if (gone.isNotEmpty()) { gone.forEach { core.leave(it); seen.remove(it) }; if (core.players.isEmpty()) { stopNow(); return } }
        val el = now - stateAt
        when (state) {
            COUNT -> { val n = 3 - (el / 1000).toInt()
                if (n != countShown && n >= 0) { countShown = n; sfx?.play("blip", 0.7f) }
                if (el >= 3000) { ask(now); sfx?.play("blip", 1f) } }
            ASK -> { if (core.typed && (said == 0 && el > 500 || said == 1 && el > limit() / 2)) { said++; speak?.invoke(core.current!!.item.answer) }
                if (core.allAnswered || el >= limit()) { state = REVEAL; stateAt = now; sfx?.play(if (core.players.values.any { it.correct }) "chime" else "thump", 0.8f) } }
            REVEAL -> if (el >= REVEAL_MS) {
                if (core.next()) ask(now)
                else { state = END; stateAt = now; sfx?.play("chime", 1f)
                    val win = core.winners()
                    onEnd?.invoke(core.players.values.map { GameResult(it.cid, it in win, words = if (core.typed) it.rights else 0) })
                    if (!core.typed) for (p in core.players.values) if (p.rights > 0) onBonus?.invoke(p.name, p.rights) } }
            END -> if (el > 600000) stopNow()
        }
    }

    // ---- drawing ----
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = false, align: Paint.Align = Paint.Align.CENTER) = Ui.text(cv, view, t, x, y, size, color, outline, align)
    private fun fit(cv: Canvas, t: String, x: Float, y: Float, size: Float, maxWidth: Float, color: Int, outline: Boolean = false) {
        val wd = Ui.width(view, t, size); text(cv, t, x, y, if (wd > maxWidth) size * maxWidth / wd else size, color, outline)
    }

    /** The players along the bottom: name, score, and what they did with this question. */
    private fun chips(cv: Canvas, w: Float, h: Float, k: Float) {
        val list = core.players.values.toList(); if (list.isEmpty()) return
        val cw = minOf(290f * k, (w - 80f * k) / list.size - 14f * k); val total = list.size * (cw + 14f * k) - 14f * k
        list.forEachIndexed { i, pl ->
            val l = (w - total) / 2 + i * (cw + 14f * k); val t = h - 150f * k
            val fill = if (state == REVEAL && pl.correct) Color.argb(240, 22, 110, 54) else if (state == REVEAL && pl.answered) Color.argb(240, 130, 30, 40) else Color.argb(230, 38, 22, 82)
            Ui.panel(cv, RectF(l, t, l + cw, t + 112f * k), k, fill, if (state == ASK && pl.answered) Ui.GOLD else Ui.PURPLE)
            fit(cv, pl.name.ifEmpty { "" + (i + 1) }, l + cw / 2, t + 46f * k, 34f * k, cw - 24f * k, Color.WHITE)
            val line = when { state == REVEAL && pl.correct -> "+" + pl.gained
                state == REVEAL -> "✗"
                state == ASK && pl.answered -> "✓"
                else -> "" + pl.score }
            text(cv, line, l + cw / 2, t + 94f * k, 38f * k, if (state == REVEAL && !pl.correct) Color.WHITE else Ui.GOLD)
        }
    }

    private fun drawFlag(cv: Canvas, code: String, box: RectF, k: Float) {
        val b = flag(code) ?: return
        val sc = minOf(box.width() / b.width, box.height() / b.height); val fw = b.width * sc; val fh = b.height * sc
        val r = RectF(box.centerX() - fw / 2, box.centerY() - fh / 2, box.centerX() + fw / 2, box.centerY() + fh / 2)
        p.style = Paint.Style.FILL; p.color = Color.argb(120, 0, 0, 0); cv.drawRect(r.left + 10f * k, r.top + 14f * k, r.right + 10f * k, r.bottom + 14f * k, p)
        p.color = Color.WHITE; cv.drawRect(r.left - 8f * k, r.top - 8f * k, r.right + 8f * k, r.bottom + 8f * k, p)
        cv.drawBitmap(b, null, r, bp)
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        synchronized(core) {
            val w = view.width.toFloat(); val h = view.height.toFloat(); val k = h / 1080f
            Ui.dim(cv, 232)
            Ui.title(cv, view, s(TITLES[core.mode]), w / 2, 118f * k, 78f * k)
            val q = core.current
            when (state) {
                LOBBY -> {
                    fit(cv, s(intArrayOf(R.string.quiz_flags_how, R.string.quiz_caps_how, R.string.quiz_spell_how)[core.mode]), w / 2, h * 0.36f, 54f * k, w * 0.86f, Color.WHITE, true)
                    text(cv, s(R.string.quiz_setup, core.rounds, core.players.size), w / 2, h * 0.47f, 40f * k, Color.argb(255, 200, 190, 235))
                    Ui.pill(cv, view, s(R.string.press_start), w / 2, h * 0.64f, 50f * k, now)
                    chips(cv, w, h, k)
                }
                COUNT -> { val el = now - stateAt; Ui.countdown(cv, view, 3 - (el / 1000).toInt(), (el % 1000) / 1000f, w / 2, h * 0.52f, k); chips(cv, w, h, k) }
                ASK, REVEAL -> if (q != null) {
                    text(cv, s(R.string.quiz_round, core.round + 1, core.questions.size), 150f * k, 112f * k, 44f * k, Ui.GOLD, true)
                    if (state == ASK) {                                    // the time left, as a bar that runs out
                        val f = (1f - (now - stateAt).toFloat() / limit()).coerceIn(0f, 1f)
                        p.style = Paint.Style.FILL; p.color = Color.argb(255, 24, 14, 56); cv.drawRoundRect(RectF(w * 0.2f, 186f * k, w * 0.8f, 206f * k), 10f * k, 10f * k, p)
                        p.color = if (f < 0.25f) Color.rgb(255, 80, 70) else Ui.GOLD; cv.drawRoundRect(RectF(w * 0.2f, 186f * k, w * 0.2f + w * 0.6f * f, 206f * k), 10f * k, 10f * k, p)
                    }
                    if (core.typed) {
                        fit(cv, s(R.string.quiz_q_spell), w / 2, h * 0.31f, 46f * k, w * 0.86f, Color.argb(255, 200, 190, 235))
                        fit(cv, q.item.prompt, w / 2, h * 0.47f, 150f * k, w * 0.86f, Color.WHITE, true)
                        val word = q.item.answer
                        if (state == REVEAL) fit(cv, word, w / 2, h * 0.68f, 150f * k, w * 0.86f, Ui.GOLD, true)
                        else { val cell = minOf(96f * k, w * 0.8f / word.length); val left = w / 2 - cell * word.length / 2
                            p.style = Paint.Style.FILL; p.color = Color.WHITE
                            for (i in word.indices) cv.drawRoundRect(RectF(left + i * cell + cell * 0.14f, h * 0.68f, left + (i + 1) * cell - cell * 0.14f, h * 0.68f + 10f * k), 5f * k, 5f * k, p)
                            text(cv, s(R.string.quiz_letters, word.length), w / 2, h * 0.68f + 70f * k, 36f * k, Color.argb(255, 200, 190, 235)) }
                    } else {
                        val box = RectF(w * 0.07f, h * 0.27f, w * 0.47f, h * (if (core.mode == QuizCore.CAPITALS) 0.66f else 0.78f))
                        drawFlag(cv, q.item.key, box, k)
                        if (core.mode == QuizCore.CAPITALS) fit(cv, q.item.prompt, box.centerX(), h * 0.77f, 70f * k, box.width(), Color.WHITE, true)
                        fit(cv, if (core.mode == QuizCore.CAPITALS) s(R.string.quiz_q_cap) else s(R.string.quiz_q_flag), w * 0.74f, h * 0.29f, 42f * k, w * 0.42f, Color.argb(255, 200, 190, 235))
                        q.options.forEachIndexed { i, o ->
                            val t = h * 0.33f + i * 118f * k; val r = RectF(w * 0.53f, t, w * 0.95f, t + 100f * k)
                            val right = state == REVEAL && i == q.right; val off = state == REVEAL && !right
                            p.style = Paint.Style.FILL; p.color = if (right) Color.rgb(31, 157, 70) else COLORS[i]; p.alpha = if (off) 70 else 255
                            cv.drawRoundRect(r, 22f * k, 22f * k, p); p.alpha = 255
                            if (right) { p.style = Paint.Style.STROKE; p.strokeWidth = 8f * k; p.color = Color.WHITE; cv.drawRoundRect(r, 22f * k, 22f * k, p); p.style = Paint.Style.FILL }
                            fit(cv, (if (right) "✓ " else "") + o, r.centerX(), t + 68f * k, 52f * k, r.width() - 50f * k, if (off) Color.argb(120, 255, 255, 255) else if (i == 2 && !right) Ui.INK else Color.WHITE)
                        }
                    }
                    chips(cv, w, h, k)
                }
                END -> {
                    val win = core.winners(); val el = now - stateAt
                    Ui.confetti(cv, view, el)
                    fit(cv, when { win.size == 1 -> s(R.string.wins_fmt, win[0].name.ifEmpty { "1" }); win.isEmpty() -> s(R.string.quiz_nobody); else -> s(R.string.quiz_tie) }, w / 2, h * 0.3f, 84f * k, w * 0.86f, Ui.GOLD, true)
                    core.ranking().take(6).forEachIndexed { i, pl -> val y = h * 0.37f + i * 92f * k
                        Ui.panel(cv, RectF(w * 0.27f, y, w * 0.73f, y + 78f * k), k, if (pl in win) Color.argb(240, 90, 60, 10) else Color.argb(225, 38, 22, 82), if (pl in win) Ui.GOLD else Ui.PURPLE)
                        text(cv, if (i < 3) arrayOf("🥇", "🥈", "🥉")[i] else "" + (i + 1), w * 0.27f + 56f * k, y + 56f * k, 46f * k, Color.WHITE)
                        text(cv, pl.name.ifEmpty { "" + (i + 1) }, w * 0.27f + 110f * k, y + 54f * k, 44f * k, Color.WHITE, false, Paint.Align.LEFT)
                        text(cv, s(R.string.quiz_score, pl.score, pl.rights, core.questions.size), w * 0.73f - 34f * k, y + 54f * k, 38f * k, Ui.GOLD, false, Paint.Align.RIGHT) }
                    Ui.pill(cv, view, s(R.string.press_start), w / 2, h * 0.95f, 38f * k, now)
                }
            }
        }
        view.postInvalidateOnAnimation()
        return true
    }
}
