package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.util.concurrent.ConcurrentHashMap

/** One player's striker. side 0 = red (left half, scores in the right goal), side 1 = black. */
class Mallet(val side: Int) {
    var cid: String? = null
    var x = 0f; var y = 0f; var tx = 0f; var ty = 0f; var vx = 0f; var vy = 0f
    var lastInput = 0L
    var bot = false; var thinkAt = 0L; var err = 0f
}

/** Air hockey seen from above. Each phone slides its striker inside its own half; first to 7 wins. */
class Hockey(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    private fun lvl() = s(intArrayOf(R.string.lvl_easy, R.string.lvl_medium, R.string.lvl_hard)[level])
    companion object { const val OFF = 0; const val LOBBY = 1; const val COUNT = 2; const val PLAY = 3; const val GOAL = 4; const val END = 5
        val TEAM = intArrayOf(0xFFE53935.toInt(), 0xFF26282C.toInt()); const val WIN = 7 }
    var sfx: Sfx? = null
    private var state = OFF
    private val pl = arrayOf(Mallet(0), Mallet(1))
    private val score = IntArray(2)
    private var px = 0f; private var py = 0f; private var pvx = 0f; private var pvy = 0f
    private var stateAt = 0L; private var countMs = 3000L; private var countShown = -1
    private var last = 0L; private var lastActive = 0L; private var lastScorer = -1; private var serveTo = -1
    private val hits = IntArray(2); private var goals = 0; private var lastClack = 0L
    private var level = 0
    private var slowSince = 0L; private var ancX = 0f; private var ancY = 0f     // where and since when the puck has barely moved
    private val sparks = ArrayList<Spark>()
    private val sides = ConcurrentHashMap<String, Int>()
    @Volatile private var snap = "0|0|0|0|0|0|0|1|1|0|0"
    val active get() = state != OFF

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private fun w() = view.width.toFloat()
    private fun h() = view.height.toFloat()
    private fun sc() = (if (view.height > 0) view.height else 1080) / 1080f
    private fun rail() = 48f * sc()
    private fun mr() = 64f * sc()
    private fun pr() = 31f * sc()
    private fun goalHalf() = h() * 0.165f

    fun status(cid: String) = snap + "|" + (sides[cid] ?: -1)
    fun stopNow() { state = OFF; for (m in pl) { m.cid = null; m.bot = false }; sides.clear(); sparks.clear(); publish() }

    private fun home(m: Mallet) { m.x = w() * (if (m.side == 0) 0.13f else 0.87f); m.y = h() / 2; m.tx = m.x; m.ty = m.y; m.vx = 0f; m.vy = 0f }
    private fun placePuck(toSide: Int) { px = w() * (if (toSide == 0) 0.36f else if (toSide == 1) 0.64f else 0.5f); py = h() / 2; pvx = 0f; pvy = 0f }
    private fun toCount(ms: Long, now: Long) { slowSince = 0L; state = COUNT; stateAt = now; countMs = ms; countShown = -1; for (m in pl) if (m.bot) home(m); placePuck(serveTo) }

    /** "h join r|k", "h leave", "h start", "h stop", "h level n" */
    fun command(pp: List<String>, cid: String) {
        val now = SystemClock.uptimeMillis(); lastActive = now
        when (pp[1]) {
            "join" -> {
                val s = if (pp[2] == "r") 0 else 1
                for (m in pl) if (m.cid == cid) m.cid = null
                sides.remove(cid)
                val seat = pl[s]
                if (seat.cid == null || now - seat.lastInput > 20000) { seat.cid?.let { sides.remove(it) }; seat.cid = cid; seat.lastInput = now; sides[cid] = s; seat.bot = false }
                if (state == OFF) { state = LOBBY; stateAt = now; last = now; score[0] = 0; score[1] = 0; serveTo = -1; for (m in pl) home(m); placePuck(-1) }
            }
            "leave" -> { for (m in pl) if (m.cid == cid) m.cid = null; sides.remove(cid); if (pl[0].cid == null && pl[1].cid == null) stopNow() }
            "start" -> if (state == LOBBY || state == END) { score[0] = 0; score[1] = 0; lastScorer = -1; serveTo = if (Math.random() < 0.5) 0 else 1
                for (m in pl) { m.bot = m.cid == null; home(m) }
                toCount(3000, now) }
            "level" -> level = pp[2].toInt().coerceIn(0, 2)
            "stop" -> stopNow()
        }
        publish()
    }
    /** Finger position on the phone pad, 0..1 across that player's own half (same orientation as the TV). */
    fun pos(cid: String, nx: Float, ny: Float) {
        val m = pl.firstOrNull { it.cid == cid } ?: return
        val r = mr(); val w = w(); val h = h(); val rl = rail()
        val x0 = if (m.side == 0) rl + r else w / 2 + r; val x1 = if (m.side == 0) w / 2 - r else w - rl - r
        m.tx = x0 + nx.coerceIn(0f, 1f) * (x1 - x0); m.ty = rl + r + ny.coerceIn(0f, 1f) * (h - 2 * rl - 2 * r)
        m.lastInput = SystemClock.uptimeMillis(); lastActive = m.lastInput
    }

    private fun burst(x: Float, y: Float, color: Int, n: Int, speed: Float) {
        val now = SystemClock.uptimeMillis(); val k = sc()
        repeat(n) { val a = Math.random() * Math.PI * 2; val v = speed * k * (0.3f + Math.random().toFloat())
            sparks.add(Spark(x, y, (Math.cos(a) * v).toFloat(), (Math.sin(a) * v).toFloat(), color, now, 300f + Math.random().toFloat() * 400f, (4f + Math.random().toFloat() * 6f) * k)) }
        while (sparks.size > 240) sparks.removeAt(0)
    }
    private fun clack(gain: Float, now: Long) { if (now - lastClack > 45) { lastClack = now; sfx?.play("clack", gain.coerceIn(0.12f, 1f)) } }

    /** Computer player: guard the goal, and when the puck is in its half get behind it and drive through it. */
    private fun think(m: Mallet, now: Long) {
        val k = sc(); val w = w(); val h = h()
        if (now >= m.thinkAt) { m.thinkAt = now + longArrayOf(260, 150, 70)[level]
            m.err = (Math.random().toFloat() - 0.5f) * 2f * floatArrayOf(90f, 45f, 12f)[level] * k
            val mine = if (m.side == 0) px < w * 0.5f + pr() else px > w * 0.5f - pr()        // includes a puck resting on the centre line
            val toward = if (m.side == 0) 1f else -1f                              // direction of the goal it attacks
            if (mine && Math.hypot(pvx.toDouble(), pvy.toDouble()) < 1500f * k) {
                val dx = px - m.x; val dy = py - m.y; val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val behind = (px - m.x) * toward > mr() * 0.3f                       // striker is on the correct side of the puck
                if (behind && d < (mr() + pr()) * 2.2f) { m.tx = px + toward * 220f * k; m.ty = py + m.err * 0.4f }   // strike through
                else { m.tx = px - toward * (mr() + pr() + 30f * k); m.ty = py + m.err }                                // get behind it
            } else { m.tx = w * (if (m.side == 0) 0.11f else 0.89f); m.ty = (h / 2 + (py - h / 2) * 0.55f + m.err).coerceIn(h / 2 - goalHalf() * 1.1f, h / 2 + goalHalf() * 1.1f) }
            val r = mr(); val rl = rail()
            m.tx = m.tx.coerceIn(if (m.side == 0) rl + r else w / 2 + r, if (m.side == 0) w / 2 - r else w - rl - r)
            m.ty = m.ty.coerceIn(rl + r, h - rl - r)
        }
    }

    private fun physics(dt: Float, now: Long) {
        val k = sc(); val w = w(); val h = h(); val rl = rail(); val r = pr(); val mrr = mr()
        for (m in pl) {
            if (m.bot && state == PLAY) think(m, now)
            val vmax = (if (m.bot) floatArrayOf(520f, 1000f, 1800f)[level] else 4200f) * k
            val dx = m.tx - m.x; val dy = m.ty - m.y; val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val step = minOf(d, vmax * dt)
            val nvx = if (d > 0.01f) dx / d * step / dt else 0f; val nvy = if (d > 0.01f) dy / d * step / dt else 0f
            m.vx += (nvx - m.vx) * 0.6f; m.vy += (nvy - m.vy) * 0.6f
            if (d > 0.01f) { m.x += dx / d * step; m.y += dy / d * step }
        }
        if (state != PLAY) return
        val f = Math.exp(-0.22 * dt).toFloat(); pvx *= f; pvy *= f
        px += pvx * dt; py += pvy * dt
        for (m in pl) {                                                             // striker hits puck
            val dx = px - m.x; val dy = py - m.y; val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val minD = r + mrr
            if (d < minD && d > 0.001f) {
                val nx = dx / d; val ny = dy / d
                val vr = (pvx - m.vx) * nx + (pvy - m.vy) * ny
                if (vr < 0f) { pvx -= 1.9f * vr * nx; pvy -= 1.9f * vr * ny
                    if (-vr > 120f * k) { clack(-vr / (2200f * k), now); hits[m.side]++; burst(px - nx * r, py - ny * r, 0xFFFFFFFF.toInt(), 6, 380f) } }
                px = m.x + nx * minD; py = m.y + ny * minD
            }
        }
        val sp = Math.hypot(pvx.toDouble(), pvy.toDouble()).toFloat(); val max = 2700f * k
        if (Math.hypot((px - ancX).toDouble(), (py - ancY).toDouble()) > 80f * k || slowSince == 0L) { ancX = px; ancY = py; slowSince = now }
        else if (now - slowSince > 2600) {                                           // puck stuck or pinned: fire it back toward the middle
            val gx = w / 2 - px; val gy = h / 2 - py + (Math.random().toFloat() - 0.5f) * 300f * k
            val gd = Math.hypot(gx.toDouble(), gy.toDouble()).toFloat().coerceAtLeast(1f)
            if (gd < 60f * k) { pvx = (if (Math.random() < 0.5) -1f else 1f) * 520f * k; pvy = (Math.random().toFloat() - 0.5f) * 300f * k }
            else { pvx = gx / gd * 650f * k; pvy = gy / gd * 650f * k }
            for (m in pl) if (m.bot) { m.tx = w * (if (m.side == 0) 0.11f else 0.89f); m.ty = h / 2; m.thinkAt = now + 900 }   // computer backs off
            slowSince = 0L
        }
        if (py < rl + r) { py = rl + r; if (pvy < 0) { clack(-pvy / (2200f * k), now); pvy = -pvy * 0.92f } }
        if (py > h - rl - r) { py = h - rl - r; if (pvy > 0) { clack(pvy / (2200f * k), now); pvy = -pvy * 0.92f } }
        for (m in pl) { val dx = px - m.x; val dy = py - m.y; val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat(); val minD = r + mrr
            if (d < minD && d > 0.001f) { m.x = px - dx / d * minD; m.y = py - dy / d * minD } }                       // puck against the rail: the striker gives way
        val inMouth = Math.abs(py - h / 2) < goalHalf() - r * 0.35f
        if (px < rl + r) { if (inMouth) { if (px < rl - r * 0.4f) { goal(1, now); return } } else { px = rl + r; if (pvx < 0) { clack(-pvx / (2200f * k), now); pvx = -pvx * 0.92f } } }
        if (px > w - rl - r) { if (inMouth) { if (px > w - rl + r * 0.4f) { goal(0, now); return } } else { px = w - rl - r; if (pvx > 0) { clack(pvx / (2200f * k), now); pvx = -pvx * 0.92f } } }
    }
    private fun goal(side: Int, now: Long) {
        score[side]++; goals++; lastScorer = side; serveTo = 1 - side                 // the player who conceded serves
        burst(if (side == 0) w() - rail() else rail(), h() / 2, TEAM[0], 50, 900f); burst(if (side == 0) w() - rail() else rail(), h() / 2, Color.WHITE, 30, 700f)
        if (score[side] >= WIN) { state = END; stateAt = now; sfx?.play("buzzer", 1f) } else { state = GOAL; stateAt = now; sfx?.play("chime", 0.9f) }
    }

    private fun update(now: Long) {
        var dt = (now - last) / 1000f; last = now
        if (dt > 0.05f) dt = 0.05f; if (dt < 0f) dt = 0f
        when (state) {
            COUNT -> { val n = ((countMs - (now - stateAt)) / (countMs / 3f)).toInt()
                if (n != countShown && n >= 0) { countShown = n; sfx?.play("blip", 0.7f) }
                if (now - stateAt >= countMs) { state = PLAY; sfx?.play("blip", 1f) } }
            GOAL -> if (now - stateAt > 1500) toCount(1500, now)
            END -> if (now - stateAt > 9000) { state = LOBBY; stateAt = now; placePuck(-1) }
        }
        if (state != OFF && now - lastActive > 120000) { stopNow(); return }
        if (dt > 0.0005f) for (i in 0 until 3) physics(dt / 3, now)             // two frames in the same millisecond: nothing to advance
        if (px.isNaN() || py.isNaN() || pvx.isNaN() || pvy.isNaN()) placePuck(-1)
        for (m in pl) if (m.x.isNaN() || m.y.isNaN() || m.vx.isNaN() || m.vy.isNaN()) home(m)
        val it = sparks.iterator()
        while (it.hasNext()) { val s = it.next(); if (now - s.born > s.life) { it.remove(); continue }; s.x += s.vx * dt; s.y += s.vy * dt; s.vx *= 0.95f; s.vy *= 0.95f }
        publish()
    }
    private fun publish() {
        snap = "" + state + "|" + score[0] + "|" + score[1] + "|" + WIN + "|" + hits[0] + "|" + hits[1] + "|" + goals + "|1|1|" +
            (if (pl[0].cid != null) 1 else 0) + "|" + (if (pl[1].cid != null) 1 else 0)
    }

    // ---- drawing ----
    private fun table(cv: Canvas) {
        val k = sc(); val w = w(); val h = h(); val rl = rail(); val gh = goalHalf()
        cv.drawColor(0xFF0E0F12.toInt())
        p.reset(); p.isAntiAlias = true
        p.color = 0xFF1F2227.toInt(); cv.drawRoundRect(RectF(0f, 0f, w, h), 70f * k, 70f * k, p)                     // rail
        p.color = 0xFFF2742B.toInt()                                                                                 // orange corner pads
        val c = 150f * k
        for (cx in floatArrayOf(0f, w - c)) for (cy in floatArrayOf(0f, h - c)) cv.drawRoundRect(RectF(cx, cy, cx + c, cy + c), 64f * k, 64f * k, p)
        p.shader = LinearGradient(0f, rl, w, h - rl, intArrayOf(0xFFFFFFFF.toInt(), 0xFFF1F4F7.toInt(), 0xFFE4E9EE.toInt()), null, Shader.TileMode.CLAMP)
        cv.drawRoundRect(RectF(rl, rl, w - rl, h - rl), 34f * k, 34f * k, p); p.shader = null                          // playing surface
        p.color = Color.argb(60, 90, 105, 125); p.strokeWidth = 3.2f * k; p.strokeCap = Paint.Cap.ROUND               // air holes
        val g = 40f * k; var y = rl + g
        while (y < h - rl) { var x = rl + g; while (x < w - rl) { cv.drawPoint(x, y, p); x += g }; y += g }
        p.style = Paint.Style.STROKE
        p.color = 0xFF2F6FE0.toInt(); p.strokeWidth = 4f * k; cv.drawLine(w * 0.34f, rl, w * 0.34f, h - rl, p); cv.drawLine(w * 0.66f, rl, w * 0.66f, h - rl, p)
        p.color = 0xFFD9363E.toInt(); p.strokeWidth = 5f * k
        cv.drawLine(w / 2, rl, w / 2, h - rl, p); cv.drawCircle(w / 2, h / 2, 150f * k, p); cv.drawCircle(w / 2, h / 2, 96f * k, p)
        cv.drawArc(RectF(rl - gh * 1.25f, h / 2 - gh * 1.25f, rl + gh * 1.25f, h / 2 + gh * 1.25f), -90f, 180f, false, p)
        cv.drawArc(RectF(w - rl - gh * 1.25f, h / 2 - gh * 1.25f, w - rl + gh * 1.25f, h / 2 + gh * 1.25f), 90f, 180f, false, p)
        p.style = Paint.Style.FILL; cv.drawCircle(w / 2, h / 2, 9f * k, p)
        p.color = 0xFF050506.toInt()                                                                                  // goal slots cut into the rails
        cv.drawRoundRect(RectF(0f, h / 2 - gh, rl + 4f * k, h / 2 + gh), 10f * k, 10f * k, p); cv.drawRoundRect(RectF(w - rl - 4f * k, h / 2 - gh, w, h / 2 + gh), 10f * k, 10f * k, p)
        p.color = TEAM[0]; cv.drawRect(rl - 2f * k, h / 2 - gh, rl + 6f * k, h / 2 + gh, p)                           // whose goal: red defends left
        p.color = 0xFF6B7078.toInt(); cv.drawRect(w - rl - 6f * k, h / 2 - gh, w - rl + 2f * k, h / 2 + gh, p)
    }
    private fun disc(cv: Canvas, x: Float, y: Float, r: Float, base: Int, mallet: Boolean) {
        val k = sc()
        p.reset(); p.isAntiAlias = true
        p.color = Color.argb(70, 0, 0, 0); cv.drawCircle(x + 7f * k, y + 10f * k, r, p)
        fun shade(c: Int, t: Float) = Color.argb(255, (Color.red(c) * t).toInt().coerceIn(0, 255), (Color.green(c) * t).toInt().coerceIn(0, 255), (Color.blue(c) * t).toInt().coerceIn(0, 255))
        p.color = shade(base, 0.72f); cv.drawCircle(x, y, r, p)
        p.color = base; cv.drawCircle(x, y, r * 0.9f, p)
        if (mallet) {
            p.color = shade(base, 0.6f); cv.drawCircle(x, y, r * 0.72f, p)                                              // the well around the knob
            p.shader = RadialGradient(x - r * 0.14f, y - r * 0.16f, r * 0.52f, intArrayOf(shade(base, 1.9f), base, shade(base, 0.8f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(x, y, r * 0.5f, p); p.shader = null
            p.color = Color.argb(150, 255, 255, 255); cv.drawOval(RectF(x - r * 0.28f, y - r * 0.34f, x - r * 0.04f, y - r * 0.16f), p)
        } else {
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f * k; p.color = shade(base, 0.72f)
            for (q in floatArrayOf(0.72f, 0.5f, 0.28f)) cv.drawCircle(x, y, r * q, p)
        }
    }
    private fun text(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, edge: Int = Color.argb(220, 0, 0, 0)) {
        tx.textSize = size
        if (edge != 0) { tx.style = Paint.Style.STROKE; tx.strokeWidth = size * 0.14f; tx.color = edge; tx.strokeJoin = Paint.Join.ROUND; cv.drawText(s, x, y, tx) }
        tx.style = Paint.Style.FILL; tx.color = color; cv.drawText(s, x, y, tx)
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        val k = sc(); val w = w(); val h = h()
        table(cv)
        disc(cv, px, py, pr(), 0xFFE53935.toInt(), false)
        for (m in pl) disc(cv, m.x, m.y, mr(), TEAM[m.side], true)
        p.reset(); p.isAntiAlias = true
        for (s in sparks) { val a = 1f - (now - s.born) / s.life; p.color = s.color; p.alpha = (255 * a).toInt().coerceIn(0, 255); cv.drawCircle(s.x, s.y, s.size * (0.5f + a * 0.5f), p) }
        p.color = Color.argb(235, 18, 20, 24); val bw = 330f * k; val bh = 92f * k; val top = 6f * k
        cv.drawRoundRect(RectF(w / 2 - bw / 2, top, w / 2 + bw / 2, top + bh), 20f * k, 20f * k, p)
        text(cv, score[0].toString(), w / 2 - 95f * k, top + 70f * k, 70f * k, 0xFFFF5A4F.toInt(), 0)
        text(cv, ":", w / 2, top + 64f * k, 56f * k, Color.WHITE, 0)
        text(cv, score[1].toString(), w / 2 + 95f * k, top + 70f * k, 70f * k, Color.WHITE, 0)
        val white = Color.argb(235, 255, 255, 255); val dark = 0xFF26282C.toInt()
        when (state) {
            LOBBY -> { cv.drawColor(Color.argb(150, 0, 0, 0)); val lv = lvl()
                text(cv, s(R.string.hockey), w / 2, h * 0.36f, 110f * k, Color.WHITE)
                text(cv, if (pl[0].cid != null) s(R.string.red_ready) else s(R.string.red_bot, lv), w * 0.27f, h * 0.56f, 50f * k, 0xFFFF5A4F.toInt())
                text(cv, if (pl[1].cid != null) s(R.string.black_ready) else s(R.string.black_bot, lv), w * 0.73f, h * 0.56f, 50f * k, Color.WHITE)
                text(cv, s(R.string.press_start_match), w / 2, h * 0.74f, 44f * k, 0xFFFFD60A.toInt())
                text(cv, s(R.string.hockey_first_to, WIN), w / 2, h * 0.74f + 62f * k, 34f * k, Color.WHITE) }
            COUNT -> { val n = 3 - ((now - stateAt) / (countMs / 3f)).toInt().coerceIn(0, 2); val f = ((now - stateAt) % (countMs / 3f)) / (countMs / 3f)
                text(cv, n.toString(), w / 2, h * 0.5f + 90f * k, (300f - 90f * f) * k, Color.WHITE) }
            GOAL -> { val f = ((now - stateAt) / 300f).coerceIn(0f, 1f)
                if (lastScorer == 0) text(cv, s(R.string.goal), w / 2, h * 0.5f + 50f * k, (120f + 70f * f) * k, TEAM[0]) else text(cv, s(R.string.goal), w / 2, h * 0.5f + 50f * k, (120f + 70f * f) * k, dark, white) }
            END -> { cv.drawColor(Color.argb(150, 0, 0, 0))
                text(cv, if (score[0] > score[1]) s(R.string.red_wins) else s(R.string.black_wins), w / 2, h * 0.46f, 150f * k, if (score[0] > score[1]) 0xFFFF5A4F.toInt() else Color.WHITE)
                text(cv, score[0].toString() + "  :  " + score[1].toString(), w / 2, h * 0.46f + 170f * k, 120f * k, Color.WHITE) }
        }
        return true
    }
}
