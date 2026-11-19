package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.pow
import kotlin.math.sin

/** The rhythm workout on the TV: a track runs towards the player and moves arrive on the beat. */
class MoveGame(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    companion object { const val OFF = 0; const val LOBBY = 1; const val COUNT = 2; const val PLAY = 3; const val END = 5
        const val GONE_MS = 8000L; const val MOVE_MS = 460L; const val LAG_MS = 60L          // a move reaches the TV about this long after the body made it
        val TRAVEL = longArrayOf(2400, 2100, 1800)                // how long a move is on its way, per level
        val TINT = intArrayOf(0xFF2F80FF.toInt(), 0xFFFF4FA3.toInt(), 0xFFFFD60A.toInt(), 0xFFFF7A1A.toInt())   // left, right, jump, duck
        val NAMES = intArrayOf(R.string.mv_left, R.string.mv_right, R.string.mv_jump, R.string.mv_duck) }
    var sfx: Sfx? = null
    var onEnd: ((List<GameResult>) -> Unit)? = null
    private val core = MoveCore()
    private var state = OFF
    private var stateAt = 0L; private var countShown = -1; private var loops = -1
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val moved = HashMap<String, LongArray>()          // the last move each player made and when, for the figure on the track
    val active get() = state != OFF

    fun stopNow() { state = OFF; core.players.clear(); seen.clear(); moved.clear() }
    private fun clock(now: Long) = now - stateAt

    /** state|level|length|seconds left|score|combo|in game|players|my place at the end|judgement number|last judgement|best streak|multiplier */
    fun status(cid: String): String = synchronized(core) {
        val now = SystemClock.uptimeMillis(); seen[cid] = now
        val me = core.players[cid]
        "" + state + "|" + core.level + "|" + core.seconds + "|" + (if (state == PLAY) ((core.seconds * 1000L - clock(now)) / 1000).coerceAtLeast(0) else 0) + "|" + (me?.score ?: 0) + "|" + (me?.combo ?: 0) +
            "|" + (if (me != null) 1 else 0) + "|" + core.players.size + "|" + (if (state == END && me != null) core.place(me) else 0) + "|" + (me?.judgeSeq ?: 0) + "|" + (me?.lastJudge ?: 0) +
            "|" + (me?.best ?: 0) + "|" + (if (me != null) core.multiplier(me) else 1)
    }

    /** "mv join NAME", "mv leave", "mv start", "mv stop", "mv level n", "mv len seconds", "mv act KIND" */
    fun command(pp: List<String>, cid: String) = synchronized(core) {
        val now = SystemClock.uptimeMillis(); seen[cid] = now
        val idle = state == LOBBY || state == END
        when (pp[1]) {
            "join" -> if (core.join(cid, Board.cleanName(java.net.URLDecoder.decode(pp.getOrElse(2) { "" }, "UTF-8"))) && state == OFF) { state = LOBBY; stateAt = now }
            "leave" -> { core.leave(cid); if (core.players.isEmpty()) stopNow() }
            "start" -> if (idle && core.players.isNotEmpty()) { core.begin(); state = COUNT; stateAt = now; countShown = -1 }
            "level" -> if (idle) core.level = pp[2].toInt().coerceIn(0, 2)
            "len" -> if (idle) core.seconds = pp[2].toInt().let { if (it == 90 || it == 120) it else 60 }
            "act" -> if (core.players.containsKey(cid)) { val kind = pp[2].toInt().coerceIn(0, 3)
                moved[cid] = longArrayOf(kind.toLong(), now)                  // the figure always answers, so the player sees the move arrived
                if (state == PLAY) when (core.act(cid, kind, clock(now) - LAG_MS)) {
                    MoveCore.PERFECT -> sfx?.play("blip", 0.9f); MoveCore.GOOD -> sfx?.play("blip", 0.5f) } }
            "stop" -> stopNow()
        }
        view.invalidate()
    }

    private fun update(now: Long) = synchronized(core) {
        val gone = core.players.values.filter { now - (seen[it.cid] ?: now) > GONE_MS }.map { it.cid }
        if (gone.isNotEmpty()) { gone.forEach { core.leave(it); seen.remove(it) }; if (core.players.isEmpty()) { stopNow(); return } }
        when (state) {
            COUNT -> { val n = 3 - ((now - stateAt) / 1000).toInt()
                if (n != countShown && n >= 0) { countShown = n; sfx?.play("blip", 0.7f) }
                if (now - stateAt >= 3000) { state = PLAY; stateAt = now; loops = -1 } }
            PLAY -> { val t = clock(now)
                val loop = (t / (16 * core.beatMs)).toInt()               // the music is a four bar loop, started again on its first beat
                if (loop != loops) { loops = loop; sfx?.play("beat" + core.level, 0.8f) }
                core.sweep(t)
                if (core.over(t) && t >= core.seconds * 1000L) { state = END; stateAt = now; sfx?.play("chime", 1f)
                    val win = core.winners(); onEnd?.invoke(core.players.values.map { GameResult(it.cid, it in win && core.players.size > 1) }) } }
            END -> if (now - stateAt > 600000) stopNow()
        }
    }

    // ---- drawing ----
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = false, align: Paint.Align = Paint.Align.CENTER) = Ui.text(cv, view, t, x, y, size, color, outline, align)
    private fun fit(cv: Canvas, t: String, x: Float, y: Float, size: Float, maxWidth: Float, color: Int, outline: Boolean = false) {
        val wd = Ui.width(view, t, size); text(cv, t, x, y, if (wd > maxWidth) size * maxWidth / wd else size, color, outline)
    }

    /** One player's track. depth 0 is the horizon, 1 the line where the move must be made. */
    private inner class Track(val cx: Float, val half: Float, val top: Float, val line: Float) {
        fun f(d: Float) = d.coerceAtLeast(0f).pow(1.8f)
        fun y(d: Float) = top + (line - top) * f(d)
        fun sc(d: Float) = 0.2f + 0.8f * f(d)
        fun x(lane: Float, d: Float) = cx + lane * half * sc(d)
        fun quad(cv: Canvas, l0: Float, l1: Float, d0: Float, d1: Float, lift0: Float, lift1: Float) {
            path.rewind(); path.moveTo(x(l0, d0), y(d0) - lift0 * sc(d0)); path.lineTo(x(l1, d0), y(d0) - lift0 * sc(d0))
            path.lineTo(x(l1, d1), y(d1) - lift1 * sc(d1)); path.lineTo(x(l0, d1), y(d1) - lift1 * sc(d1)); path.close(); cv.drawPath(path, p)
        }
    }

    /** An arrow head pointing left (0), right (1), up (2) or down (3), centred on x, y. */
    private fun chevron(cv: Canvas, kind: Int, x: Float, y: Float, r: Float, color: Int) {
        cv.save(); cv.translate(x, y); cv.rotate(floatArrayOf(180f, 0f, -90f, 90f)[kind])
        p.style = Paint.Style.FILL; p.color = color
        for (o in floatArrayOf(-0.42f, 0.28f)) { path.rewind(); path.moveTo(o * r - 0.3f * r, -r); path.lineTo(o * r + 0.5f * r, 0f); path.lineTo(o * r - 0.3f * r, r)
            path.lineTo(o * r - 0.62f * r, r); path.lineTo(o * r + 0.18f * r, 0f); path.lineTo(o * r - 0.62f * r, -r); path.close(); cv.drawPath(path, p) }
        cv.restore()
    }

    private fun drawTrack(cv: Canvas, tr: Track, pl: MoveCore.Player, t: Long, k: Float, playing: Boolean) {
        val travel = TRAVEL[core.level].toFloat()
        // the road
        p.style = Paint.Style.FILL; p.color = Color.argb(235, 16, 9, 40); tr.quad(cv, -1f, 1f, 0f, 1.22f, 0f, 0f)
        p.color = Color.argb(70, 122, 63, 216); tr.quad(cv, -1f / 3, 1f / 3, 0f, 1.22f, 0f, 0f)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        for (lane in floatArrayOf(-1f, -1f / 3, 1f / 3, 1f)) { val edge = lane == -1f || lane == 1f
            p.color = if (edge) Color.rgb(170, 110, 255) else Color.argb(150, 122, 63, 216); p.strokeWidth = (if (edge) 7f else 3f) * k
            cv.drawLine(tr.x(lane, 0f), tr.y(0f), tr.x(lane, 1.22f), tr.y(1.22f), p) }
        // a bar across for every beat, sliding towards the player
        if (playing) { val beat = core.beatMs; var b = (t / beat) * beat
            while (b - t < travel) { val d = 1f - (b - t) / travel
                if (d > 0.03f) { p.color = Color.argb((70 + 90 * d).toInt().coerceAtMost(200), 122, 63, 216); p.strokeWidth = 3f * k * tr.sc(d) + 1f; cv.drawLine(tr.x(-1f, d), tr.y(d), tr.x(1f, d), tr.y(d), p) }
                b += beat } }
        // the line to move on
        val pulse = if (playing) 1f - ((t % core.beatMs).toFloat() / core.beatMs) else 0.4f
        p.color = Color.argb((150 + 105 * pulse).toInt(), 255, 214, 10); p.strokeWidth = (10f + 8f * pulse) * k
        cv.drawLine(tr.x(-1.04f, 1f), tr.y(1f), tr.x(1.04f, 1f), tr.y(1f), p)
        p.style = Paint.Style.FILL
        if (!playing) return
        // the moves on their way, the farthest first
        for (i in core.cues.indices.reversed()) { val c = core.cues[i]
            val d = 1f - (c.at - t) / travel
            if (d < 0.04f || d > 1.2f) continue
            val mark = pl.marks.getOrElse(i) { 0 }
            if (mark == MoveCore.PERFECT || mark == MoveCore.GOOD) continue
            val a = (if (mark == MoveCore.MISS) 70 else 255) * minOf(1f, d / 0.18f)
            val col = TINT[c.kind]; val sc = tr.sc(d)
            p.color = col; p.alpha = a.toInt()
            when (c.kind) {
                MoveCore.LEFT, MoveCore.RIGHT -> { val l0 = if (c.kind == MoveCore.LEFT) -1f else 1f / 3; val l1 = l0 + 2f / 3
                    tr.quad(cv, l0 + 0.03f, l1 - 0.03f, d - 0.05f, d + 0.05f, 0f, 0f)
                    if (a > 200) chevron(cv, c.kind, tr.x((l0 + l1) / 2, d), tr.y(d), 30f * k * sc, Color.WHITE) }
                MoveCore.JUMP -> { tr.quad(cv, -1f, 1f, d, d, 0f, 90f * k)                       // a low wall to jump over
                    p.color = Color.WHITE; p.alpha = (a * 0.5f).toInt(); tr.quad(cv, -1f, 1f, d, d, 78f * k, 90f * k)
                    if (a > 200) chevron(cv, MoveCore.JUMP, tr.x(0f, d), tr.y(d) - 42f * k * sc, 26f * k * sc, Ui.INK) }
                else -> { tr.quad(cv, -1f, 1f, d, d, 250f * k, 340f * k)                          // a beam to duck under, on two posts
                    tr.quad(cv, -1f, -0.94f, d, d, 0f, 250f * k); tr.quad(cv, 0.94f, 1f, d, d, 0f, 250f * k)
                    if (a > 200) chevron(cv, MoveCore.DUCK, tr.x(0f, d), tr.y(d) - 295f * k * sc, 26f * k * sc, Color.WHITE) }
            }
            p.alpha = 255
        }
    }

    /**
     * The player's figure standing on the line: it steps aside, jumps or squats with every move the phone sends,
     * and bobs on the beat while it waits. Feet at x, ground; tall is its height.
     */
    private fun figure(cv: Canvas, x: Float, ground: Float, tall: Float, side: Float, cid: String, now: Long, color: Int, bob: Float) {
        val m = moved[cid]; val age = if (m == null) 9999L else now - m[1]; val kind = if (m == null || age > MOVE_MS) -1 else m[0].toInt()
        val bell = if (kind < 0) 0f else sin(Math.PI * age / MOVE_MS).toFloat()
        val shift = when (kind) { MoveCore.LEFT -> -side * bell; MoveCore.RIGHT -> side * bell; else -> 0f }
        val up = if (kind == MoveCore.JUMP) 0.42f * tall * bell else 0f
        val squat = if (kind == MoveCore.DUCK) bell else if (kind == MoveCore.JUMP) 0.25f * (1f - bell) * (if (age < MOVE_MS / 2) 1f else 0f) else 0.04f * bob
        val cx = x + shift; val gy = ground - up
        val hip = gy - tall * (0.46f - 0.2f * squat); val sh = hip - tall * (0.3f - 0.05f * squat); val head = sh - tall * 0.13f
        val lean = when (kind) { MoveCore.LEFT -> -0.06f * tall * bell; MoveCore.RIGHT -> 0.06f * tall * bell; else -> 0f }
        // shadow on the track: it stays down when the figure jumps
        p.style = Paint.Style.FILL; p.color = Color.argb((110 - 60 * (up / (0.42f * tall + 1f))).toInt(), 0, 0, 0)
        cv.drawOval(RectF(cx - tall * 0.2f, ground - tall * 0.03f, cx + tall * 0.2f, ground + tall * 0.03f), p)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        fun limb(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, wd: Float, c: Int) {
            p.color = c; p.strokeWidth = wd; path.rewind(); path.moveTo(x0, y0); path.lineTo(x1, y1); path.lineTo(x2, y2); cv.drawPath(path, p) }
        for (pass in 0..1) { val c = if (pass == 0) Ui.INK else color; val fat = if (pass == 0) tall * 0.03f else 0f      // a dark edge first, then the colour
            for (sd in intArrayOf(-1, 1)) {
                val stance = tall * (0.09f + 0.07f * squat + (if (kind == MoveCore.LEFT || kind == MoveCore.RIGHT) 0.06f * bell else 0f))
                val fx = cx + sd * stance; val fy = gy - (if (kind == MoveCore.JUMP) tall * 0.06f * bell else 0f)
                limb(cx + lean + sd * tall * 0.05f, hip, (cx + lean + fx) / 2 + sd * tall * (0.03f + 0.13f * squat), (hip + fy) / 2, fx, fy, tall * 0.085f + fat, c)
                val sx = cx + lean + sd * tall * 0.11f
                val (ex, ey, hx, hy) = when (kind) {
                    MoveCore.JUMP -> floatArrayOf(sx + sd * tall * 0.12f * bell, sh - tall * 0.1f * bell + tall * 0.14f * (1f - bell), sx + sd * tall * 0.16f, sh - tall * 0.3f * bell + tall * 0.28f * (1f - bell))
                    MoveCore.DUCK -> floatArrayOf(sx + sd * tall * 0.12f, sh + tall * 0.1f, sx + sd * tall * (0.1f + 0.1f * bell), sh + tall * (0.26f - 0.16f * bell))
                    MoveCore.LEFT, MoveCore.RIGHT -> { val out = if ((kind == MoveCore.LEFT) == (sd < 0)) bell else 0f
                        floatArrayOf(sx + sd * tall * (0.07f + 0.1f * out), sh + tall * (0.14f - 0.1f * out), sx + sd * tall * (0.1f + 0.2f * out), sh + tall * (0.28f - 0.26f * out)) }
                    else -> floatArrayOf(sx + sd * tall * 0.07f, sh + tall * 0.14f, sx + sd * tall * 0.1f, sh + tall * (0.28f - 0.02f * bob))
                }.let { a -> listOf(a[0], a[1], a[2], a[3]) }
                limb(sx, sh, ex, ey, hx, hy, tall * 0.065f + fat, c)
            }
            limb(cx + lean, sh, cx + lean, (sh + hip) / 2, cx + lean, hip, tall * 0.2f + fat, c)                           // the trunk
            p.style = Paint.Style.FILL; p.color = c; cv.drawCircle(cx + lean, head, tall * 0.085f + fat / 2, p); p.style = Paint.Style.STROKE
        }
        p.style = Paint.Style.FILL
    }

    private fun hud(cv: Canvas, tr: Track, pl: MoveCore.Player, t: Long, k: Float, wide: Float) {
        fit(cv, pl.name.ifEmpty { "★" }, tr.cx, tr.top - 96f * k, 44f * k, wide, Color.WHITE, true)
        text(cv, "" + pl.score, tr.cx, tr.top - 30f * k, 70f * k, Ui.GOLD, true)
        if (pl.combo >= 2) { val mult = core.multiplier(pl)
            text(cv, s(R.string.mv_combo, pl.combo) + (if (mult > 1) "   ×$mult" else ""), tr.cx, tr.line + 96f * k, 50f * k, if (mult > 1) Ui.GOLD else Color.WHITE, true) }
        val age = t - pl.lastAt
        if (pl.lastJudge != 0 && age in 0..650) { val f = age / 650f
            val (word, col) = when (pl.lastJudge) { MoveCore.PERFECT -> s(R.string.mv_perfect) to Ui.GOLD; MoveCore.GOOD -> s(R.string.mv_good) to Color.WHITE; else -> s(R.string.mv_miss) to Color.rgb(255, 90, 80) }
            p.alpha = 255; text(cv, word, tr.cx, tr.line - 330f * k - 50f * k * f, (70f + 30f * (1f - f)) * k, Color.argb((255 * (1f - f * f)).toInt(), Color.red(col), Color.green(col), Color.blue(col)), f < 0.5f) }
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        synchronized(core) {
            val w = view.width.toFloat(); val h = view.height.toFloat(); val k = h / 1080f
            Ui.dim(cv, 236)
            val list = core.players.values.toList(); val two = list.size > 1
            val t = if (state == PLAY) clock(now) else 0L
            if (state == PLAY || state == COUNT) {
                list.forEachIndexed { i, pl ->
                    val tr = Track(if (two) w * (0.27f + 0.46f * i) else w / 2, (if (two) 330f else 440f) * k, h * 0.27f, h * 0.8f)
                    drawTrack(cv, tr, pl, t, k, state == PLAY)
                    val bob = if (state == PLAY) sin(Math.PI * 2 * (t % core.beatMs) / core.beatMs).toFloat() else 0f
                    figure(cv, tr.cx, tr.y(1f) + 8f * k, (if (two) 250f else 300f) * k, tr.half * 2f / 3, pl.cid, now, if (i == 0) Color.rgb(90, 220, 255) else Color.rgb(255, 150, 60), bob)
                    hud(cv, tr, pl, t, k, (if (two) 600f else 800f) * k)
                }
            }
            when (state) {
                LOBBY -> {
                    Ui.title(cv, view, s(R.string.mv_title), w / 2, 118f * k, 78f * k)
                    fit(cv, s(R.string.mv_how), w / 2, h * 0.28f, 46f * k, w * 0.9f, Color.WHITE, true)
                    for (kind in 0..3) { val x = w * (0.2f + 0.2f * kind)                    // the four moves and what they look like
                        Ui.panel(cv, RectF(x - 150f * k, h * 0.34f, x + 150f * k, h * 0.34f + 190f * k), k)
                        chevron(cv, kind, x, h * 0.34f + 70f * k, 34f * k, TINT[kind]); text(cv, s(NAMES[kind]), x, h * 0.34f + 160f * k, 40f * k, Color.WHITE) }
                    text(cv, s(R.string.mv_setup, core.bpm, core.seconds, list.size), w / 2, h * 0.57f, 36f * k, Color.argb(255, 200, 190, 235))
                    fit(cv, s(R.string.mv_try), w / 2, h * 0.64f, 40f * k, w * 0.9f, Ui.GOLD, true)                      // practise before the round
                    list.forEachIndexed { i, pl -> val x = if (two) w * (0.36f + 0.28f * i) else w / 2
                        figure(cv, x, h * 0.955f, 250f * k, 190f * k, pl.cid, now, if (i == 0) Color.rgb(90, 220, 255) else Color.rgb(255, 150, 60), 0f)
                        fit(cv, pl.name, x, h * 0.995f, 30f * k, 300f * k, Color.WHITE) }
                    Ui.pill(cv, view, s(R.string.press_start), w * 0.82f, h * 0.9f, 40f * k, now)
                }
                COUNT -> { val el = now - stateAt; Ui.countdown(cv, view, 3 - (el / 1000).toInt(), (el % 1000) / 1000f, w / 2, h * 0.46f, k)
                    fit(cv, s(R.string.mv_still), w / 2, 120f * k, 48f * k, w * 0.9f, Ui.GOLD, true) }
                PLAY -> {                                                       // how much of the round is left
                    val f = (t.toFloat() / (core.seconds * 1000f)).coerceIn(0f, 1f)
                    p.style = Paint.Style.FILL; p.color = Color.argb(255, 24, 14, 56); cv.drawRoundRect(RectF(w * 0.3f, 40f * k, w * 0.7f, 56f * k), 8f * k, 8f * k, p)
                    p.color = Ui.GOLD; cv.drawRoundRect(RectF(w * 0.3f, 40f * k, w * 0.3f + w * 0.4f * f, 56f * k), 8f * k, 8f * k, p)
                }
                END -> {
                    val win = core.winners(); Ui.confetti(cv, view, now - stateAt)
                    Ui.title(cv, view, s(R.string.mv_title), w / 2, 118f * k, 78f * k)
                    fit(cv, when { two && win.size == 1 -> s(R.string.wins_fmt, win[0].name.ifEmpty { "1" }); two && win.size > 1 -> s(R.string.quiz_tie); else -> s(R.string.mv_done) }, w / 2, h * 0.3f, 80f * k, w * 0.86f, Ui.GOLD, true)
                    core.ranking().forEachIndexed { i, pl -> val cx = if (two) w * (0.3f + 0.4f * i) else w / 2; val top = h * 0.37f
                        Ui.panel(cv, RectF(cx - 330f * k, top, cx + 330f * k, top + 430f * k), k, if (pl in win) Color.argb(240, 90, 60, 10) else Color.argb(225, 38, 22, 82), if (pl in win) Ui.GOLD else Ui.PURPLE)
                        fit(cv, pl.name.ifEmpty { "★" }, cx, top + 66f * k, 48f * k, 600f * k, Color.WHITE)
                        text(cv, "" + pl.score, cx, top + 170f * k, 100f * k, Ui.GOLD, true)
                        val total = maxOf(1, core.cues.size)
                        text(cv, s(R.string.mv_perfect) + "  " + pl.perfect + "     " + s(R.string.mv_good) + "  " + pl.good, cx, top + 250f * k, 36f * k, Color.WHITE)
                        text(cv, s(R.string.mv_miss) + "  " + pl.miss + "     " + s(R.string.mv_best, pl.best), cx, top + 310f * k, 36f * k, Color.argb(255, 200, 190, 235))
                        text(cv, s(R.string.mv_hit_rate, (pl.perfect + pl.good) * 100 / total), cx, top + 380f * k, 42f * k, Ui.GOLD) }
                    Ui.pill(cv, view, s(R.string.press_start), w / 2, h * 0.94f, 38f * k, now)
                }
            }
        }
        view.postInvalidateOnAnimation()
        return true
    }
}
