package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.util.concurrent.ConcurrentHashMap

/** The snake game on the TV: seats, countdown, clock, drawing and sounds around the rules in SnakeCore. */
class SnakeGame(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    private fun lvl() = s(intArrayOf(R.string.lvl_easy, R.string.lvl_medium, R.string.lvl_hard)[level])
    private fun name(i: Int) = s(if (i == 0) R.string.green else R.string.orange)
    companion object { const val OFF = 0; const val LOBBY = 1; const val COUNT = 2; const val PLAY = 3; const val END = 5
        val TEAM = intArrayOf(0xFF34C759.toInt(), 0xFFFF9F0A.toInt()); const val MATCH_SECONDS = 90f }
    var sfx: Sfx? = null
    private var core: SnakeCore? = null
    private var state = OFF
    private val seat = arrayOfNulls<String>(2)
    private val faces = HashMap<String, Bitmap>()                // selfie heads, by phone
    private var level = 0
    private var stateAt = 0L; private var countShown = -1; private var last = 0L; private var lastActive = 0L
    private var timeLeft = MATCH_SECONDS; private var ends = 0
    private val eatSeen = IntArray(2)
    private val sides = ConcurrentHashMap<String, Int>()
    @Volatile private var snap = "0|0|0|90|0|0|0|-1|0|0|0"
    val active get() = state != OFF

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private fun w() = view.width.toFloat()
    private fun h() = view.height.toFloat()
    private fun sc() = (if (view.height > 0) view.height else 1080) / 1080f

    fun status(cid: String) = snap + "|" + (sides[cid] ?: -1)
    fun stopNow() { state = OFF; seat[0] = null; seat[1] = null; sides.clear(); core = null; publish() }
    private fun fresh() { val c = SnakeCore(w(), h(), sc()); c.level = level; c.reset(seat[0] != null, seat[1] != null); core = c; eatSeen[0] = 0; eatSeen[1] = 0 }

    /** "j join g|o", "j leave", "j start", "j stop", "j level n", "j face off" */
    fun command(pp: List<String>, cid: String) {
        val now = SystemClock.uptimeMillis(); lastActive = now
        when (pp[1]) {
            "join" -> { val s = if (pp[2] == "g") 0 else 1
                for (i in 0..1) if (seat[i] == cid) seat[i] = null
                sides.remove(cid)
                if (seat[s] == null) { seat[s] = cid; sides[cid] = s }
                if (state == OFF) { state = LOBBY; stateAt = now; last = now; timeLeft = MATCH_SECONDS }
                if (state == LOBBY || state == END) { state = LOBBY; fresh() } }
            "leave" -> { for (i in 0..1) if (seat[i] == cid) seat[i] = null; sides.remove(cid)
                if (seat[0] == null && seat[1] == null) stopNow() else if (state == LOBBY) fresh() }
            "start" -> if ((state == LOBBY || state == END) && (seat[0] != null || seat[1] != null)) { fresh(); timeLeft = MATCH_SECONDS; state = COUNT; stateAt = now; countShown = -1 }
            "level" -> { level = pp[2].toInt().coerceIn(0, 2); if (state == LOBBY) fresh() }
            "face" -> if (pp[2] == "off") faces.remove(cid)?.recycle()
            "stop" -> stopNow()
        }
        publish()
    }
    fun steer(cid: String, x: Float, y: Float) {
        val s = sides[cid] ?: return
        lastActive = SystemClock.uptimeMillis()
        if (state == PLAY || state == COUNT) core?.steer(s, x, y)
    }
    /** A selfie for this phone's snake head: cropped to a circle. */
    fun setFace(cid: String, src: Bitmap) {
        val n = 192; val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val side = minOf(src.width, src.height).toFloat()
        val sh = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val m = Matrix(); m.setTranslate(-(src.width - side) / 2f, -(src.height - side) / 2f); m.postScale(n / side, n / side); sh.setLocalMatrix(m)
        val fp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG); fp.shader = sh
        Canvas(out).drawCircle(n / 2f, n / 2f, n / 2f, fp)
        faces.put(cid, out)?.recycle()
        view.invalidate()
    }

    private fun update(now: Long) {
        var dt = (now - last) / 1000f; last = now
        if (dt > 0.05f) dt = 0.05f; if (dt < 0f) dt = 0f
        val c = core
        when (state) {
            COUNT -> { val n = ((3000 - (now - stateAt)) / 1000f).toInt()
                if (n != countShown && n >= 0) { countShown = n; sfx?.play("blip", 0.7f) }
                if (now - stateAt >= 3000) { state = PLAY; sfx?.play("blip", 1f) } }
            PLAY -> if (c != null) {
                c.step(dt); timeLeft -= dt
                for (i in 0..1) if (c.eats[i] != eatSeen[i]) { eatSeen[i] = c.eats[i]; sfx?.play(if (c.lastEatGold) "chime" else "blip", 0.8f) }
                if (!c.over && timeLeft <= 0f) { timeLeft = 0f; c.timeUp() }
                if (c.over) { state = END; stateAt = now; ends++; if (c.reason == SnakeCore.TIME) sfx?.play("buzzer", 1f) else { sfx?.play("thump", 1f); sfx?.play("buzzer", 0.6f) } } }
            END -> if (now - stateAt > 10000) { state = LOBBY; stateAt = now; timeLeft = MATCH_SECONDS; fresh() }
        }
        if (state != OFF && now - lastActive > 120000) { stopNow(); return }
        publish()
    }
    private fun publish() {
        val c = core
        snap = "" + state + "|" + (c?.s?.get(0)?.score ?: 0) + "|" + (c?.s?.get(1)?.score ?: 0) + "|" + Math.ceil(timeLeft.toDouble()).toInt() +
            "|" + (c?.eats?.get(0) ?: 0) + "|" + (c?.eats?.get(1) ?: 0) + "|" + ends + "|" + (c?.winner ?: -1) + "|" + (c?.reason ?: 0) +
            "|" + (if (seat[0] != null) 1 else 0) + "|" + (if (seat[1] != null) 1 else 0)
    }

    // ---- drawing ----
    /** Draws a circle, and its copies across the edges when it straddles one, so a wrapping body never shows a gap. */
    private fun dot(cv: Canvas, x: Float, y: Float, r: Float) {
        val w = w(); val h = h()
        cv.drawCircle(x, y, r, p)
        val ox = if (x < r) w else if (x > w - r) -w else 0f
        val oy = if (y < r) h else if (y > h - r) -h else 0f
        if (ox != 0f) cv.drawCircle(x + ox, y, r, p)
        if (oy != 0f) cv.drawCircle(x, y + oy, r, p)
        if (ox != 0f && oy != 0f) cv.drawCircle(x + ox, y + oy, r, p)
    }
    private fun shade(c: Int, t: Float) = Color.argb(255, (Color.red(c) * t).toInt().coerceIn(0, 255), (Color.green(c) * t).toInt().coerceIn(0, 255), (Color.blue(c) * t).toInt().coerceIn(0, 255))
    private fun serpent(cv: Canvas, c: SnakeCore, sn: Serpent, now: Long) {
        if (!sn.playing) return
        val r = c.r; val dead = !sn.alive
        val base = if (dead) (if ((now / 160) % 2 == 0L) 0xFF8A8F98.toInt() else 0xFF5C6068.toInt()) else TEAM[sn.side]
        val dark = shade(base, 0.72f); val n = sn.px.size
        p.reset(); p.isAntiAlias = true
        p.color = Color.argb(70, 0, 0, 0)                                              // soft shadow under the body
        var i = 0; while (i < n) { val t = minOf(1f, 0.45f + 0.55f * i / 22f); dot(cv, sn.px[i] + 5f * c.k, sn.py[i] + 7f * c.k, r * t); i += 3 }
        i = 0; while (i < n) { val t = minOf(1f, 0.45f + 0.55f * i / 22f)                // tail tapers
            p.color = if ((i / 8) % 2 == 0) base else dark; dot(cv, sn.px[i], sn.py[i], r * t); i += 2 }
        p.color = shade(base, 1.18f); i = 1; while (i < n) { dot(cv, sn.px[i], sn.py[i], r * 0.32f * minOf(1f, 0.45f + 0.55f * i / 22f)); i += 16 }   // ridge of light scales
        val face = seat[sn.side]?.let { faces[it] }
        val hx = sn.x; val hy = sn.y
        if (face != null && !dead) {
            val fr = r * 2.0f
            p.color = Color.argb(80, 0, 0, 0); dot(cv, hx + 5f * c.k, hy + 7f * c.k, fr)
            p.color = base; dot(cv, hx, hy, fr + 5f * c.k)
            val w = w(); val h = h()
            val ox = if (hx < fr) w else if (hx > w - fr) -w else 0f; val oy = if (hy < fr) h else if (hy > h - fr) -h else 0f
            cv.drawBitmap(face, null, RectF(hx - fr, hy - fr, hx + fr, hy + fr), bp)                       // the photo stays upright so the face is readable
            if (ox != 0f) cv.drawBitmap(face, null, RectF(hx + ox - fr, hy - fr, hx + ox + fr, hy + fr), bp)
            if (oy != 0f) cv.drawBitmap(face, null, RectF(hx - fr, hy + oy - fr, hx + fr, hy + oy + fr), bp)
            if (ox != 0f && oy != 0f) cv.drawBitmap(face, null, RectF(hx + ox - fr, hy + oy - fr, hx + ox + fr, hy + oy + fr), bp)
        } else {
            val hr = r * 1.35f; val cx = Math.cos(sn.heading.toDouble()).toFloat(); val cy = Math.sin(sn.heading.toDouble()).toFloat()
            if (!dead && Math.sin(now / 130.0) > 0.35) {                                 // flicking forked tongue
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f * c.k; p.color = 0xFFE53935.toInt(); p.strokeCap = Paint.Cap.ROUND
                val bx = hx + cx * hr; val by = hy + cy * hr; val tx2 = hx + cx * hr * 1.7f; val ty2 = hy + cy * hr * 1.7f
                cv.drawLine(bx, by, tx2, ty2, p)
                cv.drawLine(tx2, ty2, tx2 + (cx - cy * 0.6f) * hr * 0.35f, ty2 + (cy + cx * 0.6f) * hr * 0.35f, p)
                cv.drawLine(tx2, ty2, tx2 + (cx + cy * 0.6f) * hr * 0.35f, ty2 + (cy - cx * 0.6f) * hr * 0.35f, p)
                p.style = Paint.Style.FILL }
            p.color = shade(base, 0.85f); dot(cv, hx, hy, hr)
            p.color = shade(base, 1.12f); dot(cv, hx, hy, hr * 0.86f)
            for (sg in intArrayOf(-1, 1)) {
                val ex = hx + cx * hr * 0.38f - cy * sg * hr * 0.5f; val ey = hy + cy * hr * 0.38f + cx * sg * hr * 0.5f
                p.color = Color.WHITE; dot(cv, ex, ey, hr * 0.34f)
                if (dead) { p.color = Color.BLACK; p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f * c.k; val q = hr * 0.2f
                    cv.drawLine(ex - q, ey - q, ex + q, ey + q, p); cv.drawLine(ex - q, ey + q, ex + q, ey - q, p); p.style = Paint.Style.FILL }
                else { p.color = Color.BLACK; dot(cv, ex + cx * hr * 0.1f, ey + cy * hr * 0.1f, hr * 0.17f) }
            }
        }
    }
    private fun text(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = true) {
        tx.textSize = size
        if (outline) { tx.style = Paint.Style.STROKE; tx.strokeWidth = size * 0.14f; tx.color = Color.argb(220, 0, 0, 0); tx.strokeJoin = Paint.Join.ROUND; cv.drawText(s, x, y, tx) }
        tx.style = Paint.Style.FILL; tx.color = color; cv.drawText(s, x, y, tx)
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        val c = core ?: return true
        val k = sc(); val w = w(); val h = h()
        cv.drawColor(0xFF0F2A1E.toInt())
        p.reset(); p.color = 0xFF123324.toInt(); val g = 120f * k
        var row = 0; var y = 0f
        while (y < h) { var col = 0; var x = 0f; while (x < w) { if ((row + col) % 2 == 0) cv.drawRect(x, y, x + g, y + g, p); x += g; col++ }; y += g; row++ }
        p.isAntiAlias = true
        for (f in c.food) {                                                              // food pops in, and blinks before it disappears
            val age = c.time - f.born; val left = f.life - age
            if (left < 1.6f && ((age * 7f).toInt() % 2 == 0)) continue
            val s = minOf(1f, age / 0.22f) * (1f + 0.06f * Math.sin(age * 6.0).toFloat())
            if (f.gold) { p.color = Color.argb(70, 255, 214, 10); cv.drawCircle(f.x, f.y, 36f * k * s, p)
                p.color = 0xFFFFD60A.toInt(); cv.drawCircle(f.x, f.y, 19f * k * s, p); p.color = 0xFFFFF3B0.toInt(); cv.drawCircle(f.x - 5f * k * s, f.y - 5f * k * s, 5f * k * s, p) }
            else { p.color = Color.argb(60, 0, 0, 0); cv.drawCircle(f.x + 3f * k, f.y + 5f * k, 17f * k * s, p)
                p.color = 0xFFE53935.toInt(); cv.drawCircle(f.x, f.y, 17f * k * s, p); p.color = 0xFFFF8A80.toInt(); cv.drawCircle(f.x - 4.5f * k * s, f.y - 4.5f * k * s, 4f * k * s, p)
                p.color = 0xFF2E9E4F.toInt(); cv.drawOval(RectF(f.x, f.y - 27f * k * s, f.x + 14f * k * s, f.y - 16f * k * s), p) }
        }
        val order = if (c.s[0].alive) intArrayOf(1, 0) else intArrayOf(0, 1)
        for (i in order) serpent(cv, c, c.s[i], now)

        val two = c.s[0].playing && c.s[1].playing
        p.reset(); p.isAntiAlias = true; p.color = Color.argb(225, 18, 20, 24)
        val bw = (if (two) 430f else 300f) * k; val bh = 96f * k; val top = 18f * k
        cv.drawRoundRect(RectF(w / 2 - bw / 2, top, w / 2 + bw / 2, top + bh), 20f * k, 20f * k, p)
        val secs = Math.ceil(timeLeft.toDouble()).toInt()
        val clock = (secs / 60).toString() + ":" + (secs % 60).toString().padStart(2, '0'); val cc = if (secs <= 10 && state == PLAY) 0xFFFF453A.toInt() else Color.WHITE
        if (two) { text(cv, c.s[0].score.toString(), w / 2 - 150f * k, top + 72f * k, 72f * k, TEAM[0], false); text(cv, c.s[1].score.toString(), w / 2 + 150f * k, top + 72f * k, 72f * k, TEAM[1], false); text(cv, clock, w / 2, top + 66f * k, 54f * k, cc, false) }
        else { val me = if (c.s[0].playing) 0 else 1; text(cv, c.s[me].score.toString(), w / 2 - 80f * k, top + 72f * k, 72f * k, TEAM[me], false); text(cv, clock, w / 2 + 60f * k, top + 66f * k, 54f * k, cc, false) }

        when (state) {
            LOBBY -> { cv.drawColor(Color.argb(140, 0, 0, 0))
                text(cv, s(R.string.snake), w / 2, h * 0.34f, 110f * k, Color.WHITE)
                text(cv, if (seat[0] != null) s(R.string.seat_ready, name(0)) else s(R.string.seat_empty, name(0)), w * 0.27f, h * 0.52f, 50f * k, TEAM[0])
                text(cv, if (seat[1] != null) s(R.string.seat_ready, name(1)) else s(R.string.seat_empty, name(1)), w * 0.73f, h * 0.52f, 50f * k, TEAM[1])
                text(cv, s(R.string.snake_level_line, lvl(), MATCH_SECONDS.toInt()), w / 2, h * 0.66f, 46f * k, Color.WHITE)
                text(cv, s(R.string.press_start), w / 2, h * 0.66f + 70f * k, 44f * k, 0xFFFFD60A.toInt())
                text(cv, s(R.string.snake_rules), w / 2, h * 0.66f + 132f * k, 34f * k, Color.WHITE) }
            COUNT -> { val n = 3 - ((now - stateAt) / 1000f).toInt().coerceIn(0, 2); val f = ((now - stateAt) % 1000) / 1000f
                text(cv, n.toString(), w / 2, h * 0.5f + 90f * k, (300f - 90f * f) * k, Color.WHITE) }
            END -> { cv.drawColor(Color.argb(150, 0, 0, 0))
                var title: String; var sub: String; var col = Color.WHITE
                if (two) {
                    when (c.winner) { 0, 1 -> { title = s(R.string.wins_fmt, name(c.winner)); col = TEAM[c.winner] } else -> title = s(R.string.draw) }
                    val loser = name(if (c.winner == 0) 1 else 0)
                    sub = when (c.reason) { SnakeCore.SELF -> s(R.string.hit_self, loser); SnakeCore.HIT_OTHER -> s(R.string.hit_other, loser)
                        SnakeCore.HEAD_ON -> s(R.string.head_on); else -> if (c.winner == SnakeCore.DRAW) s(R.string.time_equal) else s(R.string.time_longest) }
                    text(cv, title, w / 2, h * 0.42f, 140f * k, col); text(cv, sub, w / 2, h * 0.42f + 100f * k, 54f * k, Color.WHITE)
                    text(cv, c.s[0].score.toString() + "  :  " + c.s[1].score.toString(), w / 2, h * 0.42f + 230f * k, 110f * k, Color.WHITE)
                } else { val me = if (c.s[0].playing) 0 else 1
                    title = if (c.reason == SnakeCore.TIME) s(R.string.solo_time) else s(R.string.solo_hit)
                    text(cv, title, w / 2, h * 0.42f, 140f * k, if (c.reason == SnakeCore.TIME) TEAM[me] else 0xFFFF453A.toInt())
                    text(cv, s(R.string.score_fmt, c.s[me].score), w / 2, h * 0.42f + 150f * k, 100f * k, Color.WHITE) } }
        }
        return true
    }
}
