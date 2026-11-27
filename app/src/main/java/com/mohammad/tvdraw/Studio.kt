package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.net.URLDecoder
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A pretend news and weather broadcast. One phone films a person and sends them already cut out of their background;
 * the TV stands them in front of a moving backdrop with a news ticker, a name caption, a channel badge and, for the
 * weather, the real forecast of the next three days. Everything is set from the phone.
 */
class Studio(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    companion object { const val NEWS = 0; const val SUMMER = 1; const val WINTER = 2; const val SPRING = 3; const val AUTUMN = 4
        const val STALE_MS = 2500L; const val IDLE_MS = 600000L
        /** Text from a phone for the ticker or a caption: one line, no control marks, not endless. */
        fun clean(t: String, max: Int) = t.replace(Regex("[\\u0000-\\u001F|]+"), " ").replace(Regex("\\s+"), " ").trim().take(max)
        /** A ticker line that is long enough to run across the screen is made from the items the family wrote. */
        fun ticker(text: String) = text.split('\n', '•').map { it.trim() }.filter { it.isNotEmpty() } }

    var active = false; private set
    private var scene = NEWS
    private var person: Bitmap? = null; private var personAt = 0L; private var owner: String? = null
    private var pos = 0; private var size = 1                    // where the presenter stands (0 left, 1 middle, 2 right) and how tall
    private var tick = listOf<String>(); private var tickLine = ""; private var tickW = 0f
    private var name = ""; private var title = ""; private var channel = ""
    private var breaking = false; private var cards = true
    private var startedAt = 0L; private var touched = 0L
    private val bgs = HashMap<Int, Bitmap?>()
    private fun bg(i: Int) = bgs.getOrPut(i) { Ui.bitmap(view.context, "studio/s$i.webp", true) }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun stopNow() { active = false; person?.recycle(); person = null; owner = null }

    /** A picture of the presenter from a phone, with everything around them already transparent. Call on the UI thread. */
    fun frame(cid: String, b: Bitmap) {
        val now = SystemClock.uptimeMillis()
        if (owner != null && owner != cid && now - personAt < STALE_MS) { b.recycle(); return }      // one camera is on air at a time
        owner = cid; person?.recycle(); person = b; personAt = now; touched = now
        view.invalidate()
    }

    /** "st on SCENE", "st off", "st scene n", "st tick TEXT", "st name TEXT", "st title TEXT", "st chan TEXT", "st pos n", "st size n", "st brk 0|1", "st cards 0|1" */
    fun command(pp: List<String>) {
        val now = SystemClock.uptimeMillis(); touched = now
        fun text(max: Int) = clean(URLDecoder.decode(pp.getOrElse(2) { "" }, "UTF-8"), max)
        when (pp[1]) {
            "on" -> { if (!active) startedAt = now; active = true; pp.getOrNull(2)?.toIntOrNull()?.let { scene = it.coerceIn(0, 4) } }
            "off" -> stopNow()
            "scene" -> scene = pp[2].toInt().coerceIn(0, 4)
            "tick" -> { tick = ticker(URLDecoder.decode(pp.getOrElse(2) { "" }, "UTF-8")).map { clean(it, 140) }.take(12); tickLine = "" }
            "name" -> name = text(28)
            "title" -> title = text(44)
            "chan" -> channel = text(16)
            "pos" -> pos = pp[2].toInt().coerceIn(0, 2)
            "size" -> size = pp[2].toInt().coerceIn(0, 3)
            "brk" -> breaking = pp[2] == "1"
            "cards" -> cards = pp[2] == "1"
        }
        view.invalidate()
    }

    fun status() = "on=" + (if (active) 1 else 0) + "|scene=" + scene + "|live=" + (if (person != null && SystemClock.uptimeMillis() - personAt < STALE_MS) 1 else 0)

    // ---- moving weather over the backdrop: every piece follows from the time, so nothing has to be remembered ----
    private fun frac(x: Float) = x - Math.floor(x.toDouble()).toFloat()
    private fun rnd(i: Int, salt: Int) = frac(sin(i * 12.9898f + salt * 78.233f) * 43758.547f)
    private fun weatherFx(cv: Canvas, w: Float, h: Float, k: Float, t: Float) {
        when (scene) {
            NEWS -> {                                               // a slow sweep of studio light, and the panels breathing
                p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0, Color.argb(34, 180, 220, 255), 0), floatArrayOf(0f, frac(t / 14f), 1f).also { it[1] = it[1].coerceIn(0.05f, 0.95f) }, Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w, h, p); p.shader = null
                p.color = Color.argb((14 + 12 * sin(t * 0.8f)).toInt().coerceIn(0, 40), 120, 180, 255); cv.drawRect(0f, 0f, w, h, p)
            }
            SUMMER -> {                                             // the sun glowing, and thin haze drifting across
                val pulse = 1f + 0.05f * sin(t * 0.9f)
                p.shader = RadialGradient(w * 0.84f, h * 0.1f, h * 0.62f * pulse, intArrayOf(Color.argb(150, 255, 246, 200), Color.argb(40, 255, 230, 150), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, w, h, p); p.shader = null
                for (i in 0 until 4) { val x = frac(t / (70f + 20f * i) + rnd(i, 1)) * (w * 1.6f) - w * 0.3f; val y = h * (0.1f + 0.16f * rnd(i, 2)); val r = h * (0.16f + 0.1f * rnd(i, 3))
                    p.shader = RadialGradient(x, y, r, Color.argb(54, 255, 255, 255), 0, Shader.TileMode.CLAMP); cv.save(); cv.scale(2.6f, 1f, x, y); cv.drawCircle(x, y, r, p); cv.restore() }
                p.shader = null
            }
            WINTER -> {                                             // rain driven by the wind, low cloud moving, and far lightning now and then
                for (i in 0 until 3) { val x = frac(t / (40f + 12f * i) + rnd(i, 4)) * (w * 1.8f) - w * 0.4f; val y = h * (0.05f + 0.1f * i); val r = h * 0.3f
                    p.shader = RadialGradient(x, y, r, Color.argb(70, 20, 26, 36), 0, Shader.TileMode.CLAMP); cv.save(); cv.scale(3f, 1f, x, y); cv.drawCircle(x, y, r, p); cv.restore() }
                p.shader = null
                val flash = frac(t / 9.3f); if (flash < 0.035f || (flash > 0.06f && flash < 0.075f)) { p.color = Color.argb(70, 235, 240, 255); cv.drawRect(0f, 0f, w, h * 0.7f, p) }
                p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
                for (i in 0 until 170) { val near = i % 3 == 0; val speed = if (near) 1.9f else 1.3f
                    val y = frac(t * speed + rnd(i, 5)) * (h * 1.2f) - h * 0.1f; val x = rnd(i, 6) * (w * 1.2f) - y * 0.16f
                    val len = (if (near) 46f else 28f) * k; p.strokeWidth = (if (near) 2.6f else 1.6f) * k; p.color = Color.argb(if (near) 120 else 70, 215, 228, 245)
                    cv.drawLine(x, y, x - len * 0.16f, y + len, p) }
                p.style = Paint.Style.FILL
            }
            SPRING, AUTUMN -> {                                     // petals or leaves turning as they fall
                val leaf = scene == AUTUMN; val n = if (leaf) 30 else 26
                val tints = if (leaf) intArrayOf(0xFFD9691B.toInt(), 0xFFB5451A.toInt(), 0xFFE0A21C.toInt(), 0xFF8C4A1E.toInt()) else intArrayOf(0xFFFFE3EC.toInt(), 0xFFFFC2D6.toInt(), 0xFFFFFFFF.toInt())
                for (i in 0 until n) { val fall = 0.05f + 0.05f * rnd(i, 7)
                    val y = frac(t * fall + rnd(i, 8)) * (h * 1.15f) - h * 0.08f
                    val x = rnd(i, 9) * w + sin(t * (0.6f + rnd(i, 10)) + i) * 70f * k + t * (if (leaf) 14f else 6f) * k % (w * 0.1f)
                    val sz = (if (leaf) 22f else 12f) * k * (0.7f + 0.8f * rnd(i, 11))
                    p.color = tints[i % tints.size]; p.alpha = if (leaf) 235 else 215
                    cv.save(); cv.rotate(t * (40f + 90f * rnd(i, 12)) + i * 47f, x, y); cv.scale(1f, abs(cos(t * (1.1f + rnd(i, 13)) + i)).coerceAtLeast(0.3f), x, y)
                    path.rewind(); path.moveTo(x - sz, y); path.quadTo(x, y - sz * 0.75f, x + sz, y); path.quadTo(x, y + sz * 0.75f, x - sz, y); cv.drawPath(path, p); cv.restore() }
                p.alpha = 255
            }
        }
    }

    private fun label(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) = Ui.text(cv, view, t, x, y, size, color, false, align)
    private fun fit(cv: Canvas, t: String, x: Float, y: Float, size: Float, maxW: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        val wd = Ui.width(view, t, size); label(cv, t, x, y, if (wd > maxW) size * maxW / wd else size, color, align) }

    /** The next three days from the forecast the home screen keeps: the day's name, a picture, the high and the low. */
    private fun forecast(cv: Canvas, left: Float, top: Float, width: Float, k: Float) {
        val sp = view.context.getSharedPreferences("prayer", 0)
        if (System.currentTimeMillis() - sp.getLong("weather_at", 0) > 12 * 3600000L) return
        val days = Weather.days(sp.getString("weather", "") ?: ""); if (days.isEmpty()) return
        val n = minOf(3, days.size); val gap = 18f * k; val cw = (width - gap * (n - 1)) / n; val ch = 400f * k
        val fmt = java.text.SimpleDateFormat("EEEE", view.resources.configuration.locales[0]); val cal = TvClock.now(); fmt.timeZone = cal.timeZone
        for (i in 0 until n) { val d = days[i]; val l = left + (n - 1 - i) * (cw + gap)                     // read from the right: today is the card on the right
            val r = RectF(l, top, l + cw, top + ch)
            p.color = Color.argb(150, 12, 30, 70); cv.drawRoundRect(r, 18f * k, 18f * k, p)
            p.color = Color.argb(235, 24, 66, 150); path.rewind(); path.addRoundRect(RectF(l, top, l + cw, top + 86f * k), floatArrayOf(18f * k, 18f * k, 18f * k, 18f * k, 0f, 0f, 0f, 0f), Path.Direction.CW); cv.drawPath(path, p)
            val day = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            fit(cv, if (i == 0) s(R.string.st_today) else fmt.format(day.time), r.centerX(), top + 60f * k, 44f * k, cw - 20f * k, Color.WHITE)
            label(cv, Weather.icon(d.code, true), r.centerX(), top + 236f * k, 130f * k, Color.WHITE)
            val half = cw / 2
            p.color = Color.rgb(200, 30, 40); cv.drawRoundRect(RectF(l + 10f * k, top + ch - 62f * k, l + half - 5f * k, top + ch - 12f * k), 10f * k, 10f * k, p)
            p.color = Color.rgb(30, 70, 200); cv.drawRoundRect(RectF(l + half + 5f * k, top + ch - 62f * k, l + cw - 10f * k, top + ch - 12f * k), 10f * k, 10f * k, p)
            label(cv, "" + d.high, l + half / 2, top + ch - 78f * k, 62f * k, Color.WHITE); label(cv, "" + d.low, l + half * 1.5f, top + ch - 78f * k, 62f * k, Color.WHITE)
            fit(cv, s(R.string.st_high), l + half / 2 + 2f * k, top + ch - 26f * k, 28f * k, half - 26f * k, Color.WHITE); fit(cv, s(R.string.st_low), l + half * 1.5f - 2f * k, top + ch - 26f * k, 28f * k, half - 26f * k, Color.WHITE)
        }
    }

    /** Returns true while the studio fills the screen. */
    fun draw(cv: Canvas, now: Long): Boolean {
        if (!active) return false
        if (now - touched > IDLE_MS) { stopNow(); return false }
        val w = view.width.toFloat(); val h = view.height.toFloat(); val k = h / 1080f; val t = (now - startedAt) / 1000f
        // the backdrop, breathing slowly like a camera that is never quite still
        cv.drawColor(Color.rgb(8, 16, 36))
        bg(scene)?.let { b -> val zoom = 1.06f + 0.02f * sin(t * 0.11f); val sc = maxOf(w / b.width, h / b.height) * zoom
            val bw = b.width * sc; val bh = b.height * sc; val ox = (w - bw) / 2 + sin(t * 0.07f) * (bw - w) * 0.4f; val oy = (h - bh) / 2 + cos(t * 0.05f) * (bh - h) * 0.4f
            cv.drawBitmap(b, null, RectF(ox, oy, ox + bw, oy + bh), bp) }
        weatherFx(cv, w, h, k, t)

        val barTop = h * 0.9f
        if (scene != NEWS && cards) forecast(cv, if (pos == 2) w * 0.05f else if (pos == 1) w * 0.585f else w * 0.5f, h * 0.2f, if (pos == 1) w * 0.385f else w * 0.45f, k)

        // the presenter: the phone sends the person only, cut to their outline, so the whole body stands on the floor above the ticker
        val per = person
        if (per != null && now - personAt < STALE_MS) {
            val floor = barTop - 14f * k
            var ph = h * (0.5f + 0.1f * size); var pw = ph * per.width / per.height
            if (pw > w * 0.46f) { pw = w * 0.46f; ph = pw * per.height / per.width }
            val cx = w * (if (pos == 0) 0.25f else if (pos == 1) 0.5f else 0.75f)
            if (per.hasAlpha()) { val sw = minOf(pw, ph * 0.5f) * 0.62f      // a soft shadow where the feet meet the floor
                p.shader = RadialGradient(0f, 0f, 1f, Color.argb(115, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                cv.save(); cv.translate(cx, floor - 4f * k); cv.scale(sw, 20f * k); cv.drawCircle(0f, 0f, 1f, p); cv.restore(); p.shader = null }
            cv.drawBitmap(per, null, RectF(cx - pw / 2, floor - ph, cx + pw / 2, floor), bp)
        }

        // the caption with the presenter's name
        if (name.isNotEmpty() || title.isNotEmpty()) {
            val cw = if (pos == 1) w * 0.33f else w * 0.4f; val l = if (pos == 0) w * 0.56f else w * 0.04f; val top = h * 0.75f      // beside the presenter, never over the body
            val slide = minOf(1f, t / 0.6f); cv.save(); cv.clipRect(l, top - 10f * k, l + cw * slide, barTop)
            p.color = Color.rgb(198, 22, 34); cv.drawRect(l, top, l + cw, top + 64f * k, p)
            p.color = Color.argb(240, 250, 250, 252); cv.drawRect(l, top + 64f * k, l + cw, top + 112f * k, p)
            p.color = Ui.GOLD; cv.drawRect(l + cw - 12f * k, top, l + cw, top + 112f * k, p)
            fit(cv, name, l + cw - 34f * k, top + 48f * k, 44f * k, cw - 60f * k, Color.WHITE, Paint.Align.RIGHT)
            fit(cv, title, l + cw - 34f * k, top + 100f * k, 32f * k, cw - 60f * k, Color.rgb(20, 30, 60), Paint.Align.RIGHT)
            cv.restore()
        }

        // the channel badge, "live" and the clock
        val bx = w - 300f * k; val by = 44f * k
        p.color = Color.rgb(198, 22, 34); cv.drawRoundRect(RectF(bx, by, w - 44f * k, by + 78f * k), 10f * k, 10f * k, p)
        fit(cv, channel.ifEmpty { s(R.string.st_channel) }, bx + 128f * k, by + 56f * k, 44f * k, 236f * k, Color.WHITE)
        p.color = Color.argb(200, 8, 16, 36); cv.drawRoundRect(RectF(bx, by + 86f * k, w - 44f * k, by + 136f * k), 10f * k, 10f * k, p)
        if (frac(t) < 0.6f) { p.color = Color.rgb(255, 60, 60); cv.drawCircle(w - 78f * k, by + 111f * k, 11f * k, p) }
        label(cv, s(R.string.st_live), w - 104f * k, by + 122f * k, 30f * k, Color.WHITE, Paint.Align.RIGHT)
        val c = TvClock.now(); val hh = c.get(Calendar.HOUR_OF_DAY); label(cv, "" + (if (hh % 12 == 0) 12 else hh % 12) + ":" + c.get(Calendar.MINUTE).toString().padStart(2, '0'), bx + 18f * k, by + 122f * k, 30f * k, Color.WHITE, Paint.Align.LEFT)

        // the news ticker along the bottom. Arabic is read from the right, so the words enter on the left and travel right
        p.color = Color.rgb(10, 22, 58); cv.drawRect(0f, barTop, w, h, p)
        p.color = Ui.GOLD; cv.drawRect(0f, barTop, w, barTop + 5f * k, p)
        val tagW = 250f * k
        tp.typeface = Ui.font(view.context); tp.textSize = 50f * k; tp.color = Color.WHITE
        val items = tick.ifEmpty { listOf(s(R.string.st_tick_default)) }
        if (tickLine.isEmpty()) { tickLine = items.joinToString("") { "$it      ◆      " }; tickW = tp.measureText(tickLine) }
        val rtl = view.layoutDirection == View.LAYOUT_DIRECTION_RTL || tickLine.any { it in '؀'..'ۿ' }
        cv.save(); cv.clipRect(if (rtl) 0f else tagW, barTop + 5f * k, if (rtl) w - tagW else w, h)
        if (tickW > 0f) { val shift = (t * 150f * k) % tickW; val base = barTop + 76f * k
            var x = if (rtl) shift - tickW else -shift
            while (x < w) { cv.drawText(tickLine, x, base, tp); x += tickW } }
        cv.restore()
        p.color = if (breaking) Color.rgb(214, 20, 30) else Color.rgb(24, 66, 150)
        cv.drawRect(if (rtl) w - tagW else 0f, barTop + 5f * k, if (rtl) w else tagW, h, p)
        if (!breaking || frac(t * 1.2f) < 0.7f) label(cv, s(if (breaking) R.string.st_breaking else R.string.st_news), (if (rtl) w - tagW / 2 else tagW / 2), barTop + 78f * k, 52f * k, Color.WHITE)
        view.postInvalidateOnAnimation()
        return true
    }
}
