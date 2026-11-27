package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.net.URLEncoder
import java.util.Locale

/** Letters and numbers for small children on the TV: big cards to learn from, and a gentle game with three choices on the phone. */
class KidsGame(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    companion object { const val OFF = 0; const val LEARN = 1; const val ASK = 2; const val RIGHT = 3; const val DONE = 4
        const val GONE_MS = 20000L; const val RIGHT_MS = 2600L
        val COLORS = intArrayOf(0xFFD92D20.toInt(), 0xFF1F6FFF.toInt(), 0xFF1F9D46.toInt()) }
    var sfx: Sfx? = null
    private val core = KidsCore()
    private var state = OFF
    val phase get() = state
    private var stateAt = 0L; private var lastSeen = 0L; private var shake = 0L
    val active get() = state != OFF
    private val arabic get() = Locale.getDefault().language == "ar"

    fun stopNow() { state = OFF }
    private fun enc(t: String) = URLEncoder.encode(t, "UTF-8")
    /** The name of a number in the language of the TV; for letters, the word that goes with the letter. */
    private fun word(c: KidsCore.Card) = if (core.track == KidsCore.NUM && arabic) c.say else c.word
    private fun spoken(c: KidsCore.Card) = when (core.track) { KidsCore.AR -> c.say + "، " + c.word; KidsCore.EN -> c.sign + ". " + c.word; else -> word(c) }

    /** state|track|card|cards|stars|asked|round|right choice (-1 until found)|wrong choices tried, as bits|sign|what to say|word|picture|count|language to say it in|choices... */
    fun status(): String = synchronized(core) {
        lastSeen = SystemClock.uptimeMillis()
        val q = core.question; val c = if (state == ASK || state == RIGHT) q?.card ?: core.card else core.card
        val sb = StringBuilder()
        sb.append(state).append('|').append(core.track).append('|').append(core.index + 1).append('|').append(core.cards.size).append('|').append(core.stars).append('|')
            .append(core.asked).append('|').append(KidsCore.ROUND).append('|').append(if (state == RIGHT && q != null) q.right else -1).append('|')
            .append(core.tried.fold(0) { m, i -> m or (1 shl i) }).append('|').append(enc(c.sign)).append('|').append(enc(spoken(c))).append('|').append(enc(word(c))).append('|')
            .append(enc(if (state == ASK || state == RIGHT) q?.picture ?: c.picture else c.picture)).append('|').append(c.count).append('|')
            .append(if (core.track == KidsCore.EN || (core.track == KidsCore.NUM && !arabic)) "en-US" else "ar-SA")
        if ((state == ASK || state == RIGHT) && q != null) for (o in q.options) sb.append('|').append(enc(o))
        sb.toString()
    }

    /** "kd open TRACK", "kd learn", "kd play", "kd next", "kd prev", "kd ans CHOICE", "kd close" */
    fun command(pp: List<String>) = synchronized(core) {
        val now = SystemClock.uptimeMillis(); lastSeen = now
        when (pp[1]) {
            "open" -> { core.open(pp.getOrElse(2) { "0" }.toInt()); state = LEARN; stateAt = now }
            "learn" -> if (state != OFF) { state = LEARN; stateAt = now }
            "play" -> if (state != OFF) { core.play(); state = ASK; stateAt = now; sfx?.play("blip", 0.6f) }
            "next" -> if (state == LEARN) { core.next(); stateAt = now; sfx?.play("blip", 0.4f) }
            "prev" -> if (state == LEARN) { core.prev(); stateAt = now; sfx?.play("blip", 0.4f) }
            "ans" -> if (state == ASK) when (core.answer(pp[2].toInt())) {
                KidsCore.RIGHT -> { state = RIGHT; stateAt = now; sfx?.play("chime", 0.9f) }
                KidsCore.WRONG -> { shake = now; sfx?.play("blip", 0.3f) } }
            "close" -> stopNow()
        }
        view.invalidate()
    }

    private fun update(now: Long) = synchronized(core) {
        if (now - lastSeen > GONE_MS) { stopNow(); return }                  // the phone that was leading has gone
        if (state == RIGHT && now - stateAt >= RIGHT_MS) { core.advance(); stateAt = now
            if (core.question == null) { state = DONE; sfx?.play("chime", 1f) } else state = ASK }
    }

    // ---- drawing ----
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = false) = Ui.text(cv, view, t, x, y, size, color, outline)
    private fun fit(cv: Canvas, t: String, x: Float, y: Float, size: Float, maxWidth: Float, color: Int, outline: Boolean = false) {
        val wd = Ui.width(view, t, size); text(cv, t, x, y, if (wd > maxWidth) size * maxWidth / wd else size, color, outline)
    }
    /** So many of one thing, in rows of five, to be counted with a finger. */
    private fun things(cv: Canvas, picture: String, n: Int, box: RectF, k: Float) {
        val per = if (n <= 5) n else 5; val rows = (n + 4) / 5
        val size = minOf(box.width() / (per * 1.25f), box.height() / (rows * 1.3f), 190f * k)
        for (i in 0 until n) { val r = i / 5; val inRow = if (r == rows - 1) n - r * 5 else 5; val c = i % 5
            val x = box.centerX() + (c - (inRow - 1) / 2f) * size * 1.25f
            val y = box.centerY() + (r - (rows - 1) / 2f) * size * 1.3f + size * 0.36f
            text(cv, picture, x, y, size, Color.WHITE) }
    }
    private fun sign(c: KidsCore.Card) = if (core.track == KidsCore.NUM && arabic) c.sign + "  " + KidsCore.eastern(c.count) else c.sign

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        synchronized(core) {
            val w = view.width.toFloat(); val h = view.height.toFloat(); val k = h / 1080f
            cv.drawColor(Color.rgb(20, 28, 64))                              // a calm, plain backdrop: nothing of the programme behind distracts a small child
            Ui.title(cv, view, s(intArrayOf(R.string.kids_ar, R.string.kids_en, R.string.kids_num)[core.track]), w / 2, 112f * k, 70f * k)
            val q = core.question; val num = core.track == KidsCore.NUM
            // the side the letter stands on follows the direction the language is read in
            val signX = if (core.track == KidsCore.EN) w * 0.27f else w * 0.73f; val picX = w - signX
            when (state) {
                LEARN -> { val c = core.card; val pop = 0.85f + 0.15f * minOf(1f, (now - stateAt) / 180f)
                    Ui.panel(cv, RectF(signX - w * 0.2f, h * 0.2f, signX + w * 0.2f, h * 0.84f), k, Color.argb(255, 44, 35, 110), Ui.GOLD)
                    fit(cv, sign(c), signX, h * 0.66f, 430f * k * pop, w * 0.36f, Ui.GOLD)
                    if (num) things(cv, c.picture, c.count, RectF(picX - w * 0.23f, h * 0.2f, picX + w * 0.23f, h * 0.68f), k)
                    else text(cv, c.picture, picX, h * 0.58f, 360f * k * pop, Color.WHITE)
                    fit(cv, word(c), picX, h * 0.82f, 120f * k, w * 0.44f, Color.WHITE, true)
                    text(cv, "" + (core.index + 1) + " / " + core.cards.size, w / 2, h * 0.95f, 34f * k, Color.argb(170, 255, 255, 255))
                }
                ASK, RIGHT -> if (q != null) { val c = q.card; val found = state == RIGHT
                    text(cv, "⭐".repeat(core.stars), w / 2, 196f * k, 44f * k, Ui.GOLD)
                    fit(cv, if (found) s(R.string.kids_yes) else s(if (num) R.string.kids_q_num else R.string.kids_q_letter), w / 2, h * 0.27f, 64f * k, w * 0.86f, if (found) Color.rgb(110, 235, 150) else Color.WHITE, true)
                    if (num) things(cv, q.picture, c.count, RectF(w * 0.2f, h * 0.29f, w * 0.8f, h * 0.66f), k)
                    else { text(cv, q.picture, w / 2, h * 0.56f, 280f * k, Color.WHITE); fit(cv, word(c), w / 2, h * 0.68f, 84f * k, w * 0.6f, Color.WHITE, true) }
                    val bw = w * 0.2f; val gap = w * 0.04f; val total = 3 * bw + 2 * gap
                    q.options.forEachIndexed { i, o ->
                        val wobble = if (i in core.tried && now - shake < 400) Math.sin((now - shake) / 30.0).toFloat() * 14f * k else 0f
                        val l = (w - total) / 2 + (if (core.track == KidsCore.EN) i else 2 - i) * (bw + gap) + wobble
                        val r = RectF(l, h * 0.72f, l + bw, h * 0.95f)
                        val right = found && i == q.right; val off = i in core.tried || (found && !right)
                        p.style = Paint.Style.FILL; p.color = if (right) Color.rgb(31, 157, 70) else COLORS[i]; p.alpha = if (off) 55 else 255; cv.drawRoundRect(r, 30f * k, 30f * k, p); p.alpha = 255
                        if (right) { p.style = Paint.Style.STROKE; p.strokeWidth = 10f * k; p.color = Ui.GOLD; cv.drawRoundRect(r, 30f * k, 30f * k, p); p.style = Paint.Style.FILL }
                        val ar = core.track == KidsCore.AR                    // Arabic letters hang below the line, so they sit higher in the box
                        fit(cv, if (num && arabic) o + " " + KidsCore.eastern(o.toInt()) else o, r.centerX(), r.centerY() + (if (ar) 30f else 62f) * k, (if (ar) 150f else 170f) * k, bw - 30f * k, if (off) Color.argb(110, 255, 255, 255) else Color.WHITE)
                    }
                    if (found) Ui.confetti(cv, view, now - stateAt, 40)
                }
                DONE -> { Ui.confetti(cv, view, now - stateAt)
                    fit(cv, s(R.string.kids_done), w / 2, h * 0.38f, 130f * k, w * 0.86f, Ui.GOLD, true)
                    text(cv, "⭐".repeat(core.stars.coerceAtLeast(1)), w / 2, h * 0.56f, 96f * k, Ui.GOLD)
                    fit(cv, s(R.string.kids_score, core.stars, KidsCore.ROUND), w / 2, h * 0.7f, 60f * k, w * 0.8f, Color.WHITE)
                }
            }
        }
        view.postInvalidateOnAnimation()
        return true
    }
}
