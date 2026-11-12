package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.util.concurrent.ConcurrentHashMap

/** One player's ball. side 0 = red (starts left, scores in the right hoop), side 1 = blue. */
class Baller(val side: Int) {
    var cid: String? = null
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
    var ax = 0f; var ay = 0f                     // tilt from the phone, -1..1
    var spin = 0f; var lastInput = 0L; var dashAt = 0L
    var bot = false; var thinkAt = 0L; var defend = false; var jx = 0f; var jy = 0f      // computer-controlled seat
    val trail = ArrayList<Float>()
}
class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, val color: Int, val born: Long, val life: Float, val size: Float)

/**
 * Two-phone basketball seen from above. Each phone tilts to roll its ball; balls bounce off each other
 * and the walls; roll your ball into the hoop at the other end. Faster goals are worth more.
 */
class Game(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    private fun lvl() = s(intArrayOf(R.string.lvl_easy, R.string.lvl_medium, R.string.lvl_hard)[level])
    companion object { const val OFF = 0; const val LOBBY = 1; const val COUNT = 2; const val PLAY = 3; const val GOAL = 4; const val END = 5
        val TEAM = intArrayOf(0xFFFF3B30.toInt(), 0xFF2F80FF.toInt()); const val MATCH_SECONDS = 90f }
    var sfx: Sfx? = null
    private var state = OFF
    private val pl = arrayOf(Baller(0), Baller(1))
    private val score = IntArray(2)
    private var stateAt = 0L; private var roundAt = 0L; private var countMs = 3000L; private var countShown = -1
    private var timeLeft = MATCH_SECONDS; private var last = 0L; private var lastActive = 0L
    private var lastScorer = -1; private var lastPts = 0
    private val bumps = IntArray(2); private var goals = 0
    private val sparks = ArrayList<Spark>()
    private var lastBump = 0L
    private var level = 0                       // computer opponent: 0 easy, 1 medium, 2 hard
    private var court: Bitmap? = null; private var ballBmp: Bitmap? = null
    private val sides = ConcurrentHashMap<String, Int>()
    @Volatile private var snap = "0|0|0|90|0|0|0|1|1|0|0"
    val active get() = state != OFF

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    private fun w() = view.width.toFloat()
    private fun h() = view.height.toFloat()
    private fun sc() = (if (view.height > 0) view.height else 1080) / 1080f
    private fun radius() = 40f * sc()
    private fun margin() = 26f * sc()
    private fun hoopX(i: Int) = if (i == 0) w() * 0.068f else w() * 0.932f      // hoop 0 = left (red's own), 1 = right (blue's own)
    private fun hoopY() = h() * 0.494f
    private fun asset(name: String): Bitmap? =
        try { view.context.assets.open(name).use { BitmapFactory.decodeStream(it) } } catch (e: Throwable) { null }

    fun status(cid: String) = snap + "|" + (sides[cid] ?: -1)

    private fun resetBalls() {
        for (b in pl) { b.x = w() * (if (b.side == 0) 0.30f else 0.70f); b.y = hoopY(); b.vx = 0f; b.vy = 0f; b.trail.clear() }
    }
    fun stopNow() = stop()
    private fun stop() { state = OFF; for (b in pl) b.cid = null; sides.clear(); sparks.clear(); publish() }
    private fun toCount(ms: Long, now: Long) { state = COUNT; stateAt = now; countMs = ms; countShown = -1; resetBalls() }

    /** "y join r|b", "y leave", "y start", "y stop", "y dash" */
    fun command(pp: List<String>, cid: String) {
        val now = SystemClock.uptimeMillis(); lastActive = now
        when (pp[1]) {
            "join" -> {
                val s = if (pp[2] == "r") 0 else 1
                for (b in pl) if (b.cid == cid) b.cid = null
                sides.remove(cid)
                val seat = pl[s]
                if (seat.cid == null || now - seat.lastInput > 20000) { seat.cid?.let { sides.remove(it) }; seat.cid = cid; seat.lastInput = now; sides[cid] = s; seat.bot = false; seat.ax = 0f; seat.ay = 0f }
                if (state == OFF) {
                    if (court == null) court = asset("court.webp")
                    if (ballBmp == null) ballBmp = asset("ball.webp")
                    state = LOBBY; stateAt = now; last = now; score[0] = 0; score[1] = 0; timeLeft = MATCH_SECONDS; resetBalls()
                }
            }
            "leave" -> { for (b in pl) if (b.cid == cid) b.cid = null; sides.remove(cid); if (pl[0].cid == null && pl[1].cid == null) stop() }
            "start" -> if (state == LOBBY || state == END) { score[0] = 0; score[1] = 0; timeLeft = MATCH_SECONDS; lastScorer = -1
                for (b in pl) { b.bot = b.cid == null; b.ax = 0f; b.ay = 0f; b.thinkAt = 0L }      // an empty seat is played by the TV
                toCount(3000, now) }
            "level" -> level = pp[2].toInt().coerceIn(0, 2)
            "stop" -> stop()
            "dash" -> { val b = pl.firstOrNull { it.cid == cid } ?: return
                if (state != PLAY || now - b.dashAt < 3000) return
                var dx = b.ax; var dy = b.ay
                if (Math.hypot(dx.toDouble(), dy.toDouble()) < 0.15) { dx = b.vx; dy = b.vy }
                val l = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat(); if (l < 0.01f) return
                b.vx += dx / l * 1050f * sc(); b.vy += dy / l * 1050f * sc(); b.dashAt = now
                burst(b.x, b.y, TEAM[b.side], 10, 420f); sfx?.play("blip", 0.5f)
            }
        }
        publish()
    }
    fun tilt(cid: String, x: Float, y: Float) {
        val b = pl.firstOrNull { it.cid == cid } ?: return
        b.ax = x.coerceIn(-1f, 1f); b.ay = y.coerceIn(-1f, 1f); b.lastInput = SystemClock.uptimeMillis(); lastActive = b.lastInput
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, speed: Float) {
        val now = SystemClock.uptimeMillis(); val k = sc()
        repeat(n) { val a = Math.random() * Math.PI * 2; val v = speed * k * (0.3f + Math.random().toFloat())
            sparks.add(Spark(x, y, (Math.cos(a) * v).toFloat(), (Math.sin(a) * v).toFloat(), color, now, 350f + Math.random().toFloat() * 450f, (4f + Math.random().toFloat() * 7f) * k)) }
        while (sparks.size > 260) sparks.removeAt(0)
    }

    private fun physics(dt: Float, now: Long) {
        val k = sc(); val r = radius(); val m = margin(); val w = w(); val h = h()
        for (b in pl) {
            if (b.bot && state == PLAY) think(b, now, dt)
            val live = b.bot || (b.cid != null && now - b.lastInput < 1500)
            if (state == PLAY && live) { b.vx += b.ax * 2500f * k * dt; b.vy += b.ay * 2500f * k * dt }
            val f = Math.exp(-1.35 * dt).toFloat(); b.vx *= f; b.vy *= f
            val sp = Math.hypot(b.vx.toDouble(), b.vy.toDouble()).toFloat(); val max = 1900f * k
            if (sp > max) { b.vx *= max / sp; b.vy *= max / sp }
            b.x += b.vx * dt; b.y += b.vy * dt
            b.spin += (b.vx * dt) / r * 57.3f
            var hit = 0f
            if (b.x < m + r) { b.x = m + r; hit = Math.abs(b.vx); b.vx = Math.abs(b.vx) * 0.72f }
            if (b.x > w - m - r) { b.x = w - m - r; hit = Math.abs(b.vx); b.vx = -Math.abs(b.vx) * 0.72f }
            if (b.y < m + r) { b.y = m + r; hit = maxOf(hit, Math.abs(b.vy)); b.vy = Math.abs(b.vy) * 0.72f }
            if (b.y > h - m - r) { b.y = h - m - r; hit = maxOf(hit, Math.abs(b.vy)); b.vy = -Math.abs(b.vy) * 0.72f }
            if (hit > 260f * k) sfx?.play("thump", (hit / (1900f * k)).coerceIn(0.12f, 0.7f))
        }
        val a = pl[0]; val b = pl[1]
        val dx = b.x - a.x; val dy = b.y - a.y; val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (d < 2 * r && d > 0.001f) {
            val nx = dx / d; val ny = dy / d
            val vr = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
            if (vr < 0f) {
                val j = -(1f + 0.92f) * vr / 2f
                a.vx -= j * nx; a.vy -= j * ny; b.vx += j * nx; b.vy += j * ny
                if (-vr > 180f * k && now - lastBump > 220) { lastBump = now; burst(a.x + nx * r, a.y + ny * r, 0xFFFFE08A.toInt(), 14, 520f)
                    sfx?.play("thump", (-vr / (1600f * k)).coerceIn(0.2f, 1f)); bumps[0]++; bumps[1]++ }
            }
            val push = (2 * r - d) / 2f; a.x -= nx * push; a.y -= ny * push; b.x += nx * push; b.y += ny * push
        }
        if (state == PLAY) for (bl in pl) {
            val target = 1 - bl.side                                        // the hoop at the other end
            val hx = hoopX(target); val hy = hoopY()
            val ddx = bl.x - hx; val ddy = bl.y - hy; val dist = Math.hypot(ddx.toDouble(), ddy.toDouble()).toFloat()
            if (dist < 30f * k) {
                val sp = Math.hypot(bl.vx.toDouble(), bl.vy.toDouble()).toFloat()
                if (sp < 1250f * k) { goal(bl.side, now); return }
                val n = if (dist > 0.01f) dist else 1f                       // too fast: rims out
                bl.vx = -bl.vx * 0.55f + ddx / n * 300f * k; bl.vy = -bl.vy * 0.55f + ddy / n * 300f * k
                bl.x = hx + ddx / n * 34f * k; bl.y = hy + ddy / n * 34f * k
                burst(hx, hy, 0xFFFF9F0A.toInt(), 10, 380f); sfx?.play("blip", 0.6f); bumps[bl.side]++
            }
        }
    }
    /** The computer player: head for the far hoop and arrive slowly enough to score; sometimes drop back to block. */
    private fun think(b: Baller, now: Long, dt: Float) {
        val k = sc(); val foe = pl[1 - b.side]
        val tx = hoopX(1 - b.side); val ox = hoopX(b.side); val hy = hoopY()
        val lim = floatArrayOf(0.42f, 0.62f, 0.9f)[level]
        val cruise = floatArrayOf(500f, 780f, 1150f)[level] * k
        if (now >= b.thinkAt) {
            b.thinkAt = now + 350 + (Math.random() * 450).toLong()
            val foeToMine = Math.hypot((foe.x - ox).toDouble(), (foe.y - hy).toDouble())
            val meToGoal = Math.hypot((b.x - tx).toDouble(), (b.y - hy).toDouble())
            b.defend = foeToMine < w() * 0.36 && foeToMine < meToGoal && Math.random() < doubleArrayOf(0.25, 0.55, 0.85)[level]
            val noise = floatArrayOf(95f, 52f, 16f)[level] * k            // imperfect aim: weaker levels miss the ring more
            b.jx = (Math.random().toFloat() - 0.5f) * 2f * noise; b.jy = (Math.random().toFloat() - 0.5f) * 2f * noise
        }
        if (now - roundAt < longArrayOf(1100, 600, 200)[level]) { b.ax = 0f; b.ay = 0f; return }      // reaction time at tip-off
        val gx: Float; val gy: Float
        if (b.defend) { gx = ox + (foe.x - ox) * 0.55f; gy = hy + (foe.y - hy) * 0.55f } else { gx = tx + b.jx; gy = hy + b.jy }
        val dx = gx - b.x; val dy = gy - b.y; val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        var want = if (b.defend) minOf(cruise * 1.2f, d * 4f) else minOf(cruise, d * 2.4f)
        if (!b.defend && d < 330f * k) want = minOf(want, 880f * k)                                 // ease off so it drops in
        var ax = (dx / d * want - b.vx) / (650f * k); var ay = (dy / d * want - b.vy) / (650f * k)
        val l = Math.hypot(ax.toDouble(), ay.toDouble()).toFloat()
        if (l > lim) { ax *= lim / l; ay *= lim / l }
        val f = minOf(1f, dt * 9f); b.ax += (ax - b.ax) * f; b.ay += (ay - b.ay) * f
        if (level > 0 && now - b.dashAt >= 3000 && b.defend) {                                      // shove the player off the hoop
            val fx = foe.x - b.x; val fy = foe.y - b.y; val fd = Math.hypot(fx.toDouble(), fy.toDouble()).toFloat()
            if (fd < radius() * 5f && fd > 1f && Math.random() < (if (level == 2) 0.2 else 0.05)) {
                b.vx += fx / fd * 1050f * k; b.vy += fy / fd * 1050f * k; b.dashAt = now; burst(b.x, b.y, TEAM[b.side], 10, 420f); sfx?.play("blip", 0.5f) }
        }
    }

    private fun goal(side: Int, now: Long) {
        val t = (now - roundAt) / 1000f
        lastPts = if (t <= 5f) 3 else if (t <= 10f) 2 else 1
        lastScorer = side; score[side] += lastPts; goals++
        state = GOAL; stateAt = now
        burst(hoopX(1 - side), hoopY(), TEAM[side], 60, 900f); burst(hoopX(1 - side), hoopY(), Color.WHITE, 30, 700f)
        sfx?.play("swish", 1f)
    }

    private fun update(now: Long) {
        var dt = (now - last) / 1000f; last = now
        if (dt > 0.05f) dt = 0.05f; if (dt < 0f) dt = 0f
        when (state) {
            COUNT -> { val n = ((countMs - (now - stateAt)) / (countMs / 3f)).toInt()
                if (n != countShown && n >= 0) { countShown = n; sfx?.play("blip", 0.7f) }
                if (now - stateAt >= countMs) { state = PLAY; roundAt = now; sfx?.play("blip", 1f) } }
            PLAY -> { timeLeft -= dt; if (timeLeft <= 0f) { timeLeft = 0f; state = END; stateAt = now; sfx?.play("buzzer", 1f) } }
            GOAL -> if (now - stateAt > 1700) toCount(1800, now)
            END -> if (now - stateAt > 9000) { state = LOBBY; stateAt = now }
        }
        if (state != OFF && now - lastActive > 120000) { stop(); return }
        if (state == PLAY || state == GOAL || state == END || state == LOBBY) { physics(dt / 2, now); if (state != GOAL || true) physics(dt / 2, now) }
        for (b in pl) { b.trail.add(b.x); b.trail.add(b.y); while (b.trail.size > 22) { b.trail.removeAt(0); b.trail.removeAt(0) } }
        val it = sparks.iterator()
        while (it.hasNext()) { val s = it.next(); if (now - s.born > s.life) { it.remove(); continue }; s.x += s.vx * dt; s.y += s.vy * dt; s.vx *= 0.96f; s.vy *= 0.96f }
        publish()
    }
    private fun publish() {
        val now = SystemClock.uptimeMillis()
        snap = "" + state + "|" + score[0] + "|" + score[1] + "|" + Math.ceil(timeLeft.toDouble()).toInt() + "|" + bumps[0] + "|" + bumps[1] + "|" + goals +
            "|" + (if (now - pl[0].dashAt >= 3000) 1 else 0) + "|" + (if (now - pl[1].dashAt >= 3000) 1 else 0) +
            "|" + (if (pl[0].cid != null) 1 else 0) + "|" + (if (pl[1].cid != null) 1 else 0)
    }

    // ---- drawing ----
    private fun hoop(cv: Canvas, i: Int) {
        val k = sc(); val x = hoopX(i); val y = hoopY(); val dir = if (i == 0) -1f else 1f
        p.reset(); p.isAntiAlias = true
        p.color = Color.argb(70, 0, 0, 0); cv.drawCircle(x + 8f * k, y + 10f * k, 66f * k, p)                       // shadow on the floor
        p.color = 0xFFE8E8E8.toInt(); cv.drawRoundRect(RectF(x + dir * 84f * k - 9f * k, y - 112f * k, x + dir * 84f * k + 9f * k, y + 112f * k), 6f * k, 6f * k, p)   // backboard edge
        p.color = 0xFFD2691E.toInt(); cv.drawRect(minOf(x + dir * 58f * k, x + dir * 80f * k), y - 12f * k, maxOf(x + dir * 58f * k, x + dir * 80f * k), y + 12f * k, p)
        p.color = Color.argb(150, 20, 16, 12); cv.drawCircle(x, y, 56f * k, p)                                      // looking down through the net
        p.style = Paint.Style.STROKE; p.color = Color.argb(215, 255, 255, 255); p.strokeWidth = 2.4f * k
        for (rr in floatArrayOf(18f, 34f, 48f)) cv.drawCircle(x, y, rr * k, p)
        for (a in 0 until 12) { val t = a * Math.PI / 6
            cv.drawLine(x + (Math.cos(t) * 14f * k).toFloat(), y + (Math.sin(t) * 14f * k).toFloat(), x + (Math.cos(t + 0.26) * 58f * k).toFloat(), y + (Math.sin(t + 0.26) * 58f * k).toFloat(), p) }
        p.color = 0xFFFF6A1A.toInt(); p.strokeWidth = 11f * k; cv.drawCircle(x, y, 60f * k, p)                      // the rim
        p.color = TEAM[i]; p.alpha = 150; p.strokeWidth = 5f * k; cv.drawCircle(x, y, 72f * k, p)                   // whose hoop it is
    }
    private fun ball(cv: Canvas, b: Baller, now: Long) {
        val k = sc(); val r = radius(); val c = TEAM[b.side]
        p.reset(); p.isAntiAlias = true
        val n = b.trail.size / 2
        for (i in 0 until n - 1) { p.color = c; p.alpha = (90f * (i + 1) / n).toInt(); cv.drawCircle(b.trail[2 * i], b.trail[2 * i + 1], r * (0.35f + 0.5f * i / n), p) }
        p.color = Color.argb(95, 0, 0, 0); cv.drawOval(RectF(b.x - r + 7f * k, b.y - r * 0.9f + 12f * k, b.x + r + 7f * k, b.y + r * 0.9f + 12f * k), p)
        val bm = ballBmp
        if (bm != null) { cv.save(); cv.rotate(b.spin, b.x, b.y); cv.drawBitmap(bm, null, RectF(b.x - r, b.y - r, b.x + r, b.y + r), bp); cv.restore() }
        else { p.color = 0xFFE8742A.toInt(); cv.drawCircle(b.x, b.y, r, p) }
        p.style = Paint.Style.STROKE; p.color = c; p.strokeWidth = 7f * k; cv.drawCircle(b.x, b.y, r + 5f * k, p)
        val ready = ((now - b.dashAt) / 3000f).coerceIn(0f, 1f)                                                    // dash recharge ring
        p.color = Color.WHITE; p.alpha = if (ready >= 1f) 230 else 120; p.strokeWidth = 4f * k
        cv.drawArc(RectF(b.x - r - 14f * k, b.y - r - 14f * k, b.x + r + 14f * k, b.y + r + 14f * k), -90f, 360f * ready, false, p)
    }
    private fun text(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = true) {
        tx.textSize = size
        if (outline) { tx.style = Paint.Style.STROKE; tx.strokeWidth = size * 0.14f; tx.color = Color.argb(220, 0, 0, 0); tx.strokeJoin = Paint.Join.ROUND; cv.drawText(s, x, y, tx) }
        tx.style = Paint.Style.FILL; tx.color = color; cv.drawText(s, x, y, tx)
    }

    /** Draws the game and advances it one frame. Returns false when the game is off. */
    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        val k = sc(); val w = w(); val h = h()
        val c = court
        if (c != null) cv.drawBitmap(c, null, RectF(0f, 0f, w, h), bp) else cv.drawColor(0xFFB8793A.toInt())
        hoop(cv, 0); hoop(cv, 1)
        for (b in pl) ball(cv, b, now)
        p.reset(); p.isAntiAlias = true
        for (s in sparks) { val a = 1f - (now - s.born) / s.life; p.color = s.color; p.alpha = (255 * a).toInt().coerceIn(0, 255); cv.drawCircle(s.x, s.y, s.size * (0.5f + a * 0.5f), p) }

        // scoreboard
        p.reset(); p.isAntiAlias = true; p.color = Color.argb(225, 18, 20, 24)
        val bw = 430f * k; val bh = 96f * k; val top = 18f * k
        cv.drawRoundRect(RectF(w / 2 - bw / 2, top, w / 2 + bw / 2, top + bh), 20f * k, 20f * k, p)
        text(cv, score[0].toString(), w / 2 - 150f * k, top + 72f * k, 72f * k, TEAM[0], false)
        text(cv, score[1].toString(), w / 2 + 150f * k, top + 72f * k, 72f * k, TEAM[1], false)
        val secs = Math.ceil(timeLeft.toDouble()).toInt()
        text(cv, (secs / 60).toString() + ":" + (secs % 60).toString().padStart(2, '0'), w / 2, top + 66f * k, 54f * k, if (secs <= 10 && state == PLAY) 0xFFFF453A.toInt() else Color.WHITE, false)
        if (state == PLAY) { val t = (now - roundAt) / 1000f; val pts = if (t <= 5f) 3 else if (t <= 10f) 2 else 1
            p.color = Color.argb(225, 18, 20, 24); cv.drawRoundRect(RectF(w / 2 - 62f * k, top + bh + 8f * k, w / 2 + 62f * k, top + bh + 58f * k), 25f * k, 25f * k, p)
            text(cv, "+$pts", w / 2, top + bh + 46f * k, 36f * k, if (pts == 3) 0xFFFFD60A.toInt() else if (pts == 2) 0xFFFFFFFF.toInt() else 0xFFB0B6C2.toInt(), false) }

        when (state) {
            LOBBY -> { cv.drawColor(Color.argb(120, 0, 0, 0))
                text(cv, s(R.string.basketball), w / 2, h * 0.36f, 110f * k, Color.WHITE)
                val lv = lvl()
                text(cv, if (pl[0].cid != null) s(R.string.red_ready) else s(R.string.red_bot, lv), w * 0.27f, h * 0.56f, 50f * k, TEAM[0])
                text(cv, if (pl[1].cid != null) s(R.string.blue_ready) else s(R.string.blue_bot, lv), w * 0.73f, h * 0.56f, 50f * k, TEAM[1])
                text(cv, s(R.string.press_start_match), w / 2, h * 0.74f, 44f * k, 0xFFFFD60A.toInt())
                text(cv, s(R.string.second_can_join), w / 2, h * 0.74f + 62f * k, 34f * k, Color.WHITE) }
            COUNT -> { val n = 3 - ((now - stateAt) / (countMs / 3f)).toInt().coerceIn(0, 2)
                val f = ((now - stateAt) % (countMs / 3f)) / (countMs / 3f)
                text(cv, n.toString(), w / 2, h * 0.5f + 90f * k, (300f - 90f * f) * k, Color.WHITE) }
            GOAL -> { val f = ((now - stateAt) / 300f).coerceIn(0f, 1f)
                text(cv, s(R.string.goal), w / 2, h * 0.47f, (120f + 70f * f) * k, TEAM[lastScorer.coerceIn(0, 1)])
                text(cv, "+$lastPts", w / 2, h * 0.47f + 150f * k, 110f * k, 0xFFFFD60A.toInt()) }
            END -> { cv.drawColor(Color.argb(140, 0, 0, 0))
                val msg = if (score[0] > score[1]) s(R.string.red_wins) else if (score[1] > score[0]) s(R.string.blue_wins) else s(R.string.draw)
                text(cv, msg, w / 2, h * 0.46f, 150f * k, if (score[0] > score[1]) TEAM[0] else if (score[1] > score[0]) TEAM[1] else Color.WHITE)
                text(cv, score[0].toString() + "  :  " + score[1].toString(), w / 2, h * 0.46f + 170f * k, 120f * k, Color.WHITE) }
        }
        return true
    }
}
