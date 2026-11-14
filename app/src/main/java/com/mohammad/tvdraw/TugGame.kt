package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/** The math tug of war on the TV: lobby, countdown, clock, drawing and sounds around the rules in TugCore. */
class TugGame(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    companion object { const val OFF = 0; const val LOBBY = 1; const val COUNT = 2; const val PLAY = 3; const val END = 5
        val TEAM = intArrayOf(0xFF2F80FF.toInt(), 0xFFFF5A4F.toInt()); const val GONE_MS = 8000L }
    var sfx: Sfx? = null
    private val core = TugCore()
    private var state = OFF
    private var stateAt = 0L; private var countShown = -1; private var last = 0L
    private var shown = 0f                       // where the rope is drawn; it glides towards core.pos
    private var pullsSeen = 0; private var shakeUntil = 0L; private var shake = 0f; private var suddenSeen = false
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Long>()   // last time each phone asked for the status
    val active get() = state != OFF

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.font(view.context); textAlign = Paint.Align.CENTER }
    private fun w() = view.width.toFloat()
    private fun h() = view.height.toFloat()
    private fun sc() = (if (view.height > 0) view.height else 1080) / 1080f

    fun stopNow() { state = OFF; core.players.clear(); seen.clear(); core.reset() }

    /** state|rope -1000..1000|seconds|level now|level setting|duration|left|right|my team|boost|streak|winner|sudden|my correct */
    fun status(cid: String): String = synchronized(core) {
        seen[cid] = SystemClock.uptimeMillis()
        val me = core.players[cid]
        "" + state + "|" + Math.round(core.pos / TugCore.LIMIT * 1000) + "|" + Math.ceil(core.timeLeft.toDouble()).toInt() + "|" + core.currentLevel() + "|" + core.level +
            "|" + core.duration + "|" + core.count(0) + "|" + core.count(1) + "|" + (me?.team ?: -1) + "|" + (if ((me?.boost ?: 0f) > 0f) 1 else 0) +
            "|" + (me?.streak ?: 0) + "|" + core.winner + "|" + (if (core.sudden) 1 else 0) + "|" + (me?.correct ?: 0)
    }

    /** "tug join l|r NAME", "tug leave", "tug start", "tug stop", "tug level n", "tug time n", "tug hit POINTS MS", "tug miss" */
    fun command(pp: List<String>, cid: String) = synchronized(core) {
        val now = SystemClock.uptimeMillis(); seen[cid] = now
        when (pp[1]) {
            "join" -> { val name = java.net.URLDecoder.decode(pp.getOrElse(3) { "" }, "UTF-8").trim().take(14)
                if (core.join(cid, name, if (pp[2] == "l") 0 else 1) && state == OFF) { state = LOBBY; stateAt = now; last = now; core.reset(); shown = 0f } }
            "leave" -> { core.leave(cid); if (core.players.isEmpty()) stopNow() }
            "start" -> if ((state == LOBBY || state == END) && core.players.isNotEmpty()) { core.reset(); shown = 0f; pullsSeen = 0; suddenSeen = false; state = COUNT; stateAt = now; countShown = -1 }
            "level" -> if (state != PLAY) core.level = pp[2].toInt().coerceIn(0, 3)
            "time" -> if (state != PLAY) { core.duration = pp[2].toInt().coerceIn(30, 300); if (state == LOBBY) core.reset() }
            "hit" -> if (state == PLAY) core.hit(cid, pp[2].toInt(), pp.getOrElse(3) { "0" }.toInt())
            "miss" -> if (state == PLAY) { core.miss(cid); sfx?.play("buzzer", 0.35f) }
            "stop" -> stopNow()
        }
        view.invalidate()
    }

    private fun update(now: Long) = synchronized(core) {
        val dt = ((now - last) / 1000f).coerceIn(0f, 0.1f); last = now
        // A phone that closed the page stops asking for the status: free its place so others can join.
        val gone = core.players.keys.filter { now - (seen[it] ?: now) > GONE_MS }
        if (gone.isNotEmpty()) { gone.forEach { core.leave(it); seen.remove(it) }; if (core.players.isEmpty()) { stopNow(); return } }
        when (state) {
            COUNT -> { val n = 3 - ((now - stateAt) / 1000).toInt()
                if (n != countShown && n >= 0) { countShown = n; sfx?.play("blip", 0.7f) }
                if (now - stateAt >= 3000) { state = PLAY; sfx?.play("blip", 1f) } }
            PLAY -> { core.step(dt)
                if (core.pulls != pullsSeen) { pullsSeen = core.pulls
                    val strong = abs(core.lastPull) >= 3f
                    sfx?.play(if (strong) "thump" else "swish", if (strong) 0.9f else 0.5f)
                    if (strong) { shakeUntil = now + 350; shake = 14f } }
                if (core.sudden && !suddenSeen) { suddenSeen = true; sfx?.play("buzzer", 0.9f) }
                if (core.over) { state = END; stateAt = now; sfx?.play("chime", 1f) } }
            END, LOBBY -> if (now - stateAt > 600000 && state == END) stopNow()
        }
        shown += (core.pos - shown) * (1f - Math.pow(0.002, dt.toDouble()).toFloat())
    }

    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = true) {
        tx.textSize = size
        if (outline) { tx.style = Paint.Style.STROKE; tx.strokeWidth = size * 0.14f; tx.color = Color.argb(220, 0, 0, 0); tx.strokeJoin = Paint.Join.ROUND; cv.drawText(t, x, y, tx) }
        tx.style = Paint.Style.FILL; tx.color = color; cv.drawText(t, x, y, tx)
    }

    /** One puller: body, head, a face that shows how the match is going, the name, and a gold ring while boosted. */
    private fun figure(cv: Canvas, pl: TugCore.Player, x: Float, ground: Float, k: Float, mood: Float, now: Long, playing: Boolean) {
        val dir = if (pl.team == 0) -1f else 1f                       // leans away from the middle
        val strain = if (playing) sin(now / 90.0 + x).toFloat() * 3f * k else 0f
        val lean = 26f * k * dir + strain * dir
        val hipY = ground - 95f * k; val headR = 44f * k
        val shoulderX = x + lean; val shoulderY = hipY - 95f * k
        val headX = shoulderX + lean * 0.35f; val headY = shoulderY - headR * 0.9f
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 22f * k; p.color = Color.argb(255, 40, 44, 52)
        cv.drawLine(x, hipY, x - 34f * k * dir, ground, p); cv.drawLine(x, hipY, x + 46f * k * dir, ground, p)   // legs braced
        p.color = TEAM[pl.team]; p.strokeWidth = 46f * k; cv.drawLine(x, hipY, shoulderX, shoulderY, p)          // body
        p.strokeWidth = 18f * k; cv.drawLine(shoulderX, shoulderY + 14f * k, x - 70f * k * dir, ground - 150f * k, p)  // arms reach the rope
        p.style = Paint.Style.FILL
        if (pl.boost > 0f) { p.color = Color.argb(150 + (60 * sin(now / 80.0)).toInt(), 255, 214, 10); cv.drawCircle(headX, headY, headR + 14f * k, p) }
        p.color = Color.rgb(255, 221, 179); cv.drawCircle(headX, headY, headR, p)
        p.color = Color.rgb(40, 44, 52)
        val eyeY = headY - 8f * k
        cv.drawCircle(headX - 15f * k, eyeY, 5.5f * k, p); cv.drawCircle(headX + 15f * k, eyeY, 5.5f * k, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f * k
        val my = headY + 18f * k; val curve = 16f * k * mood                // mood 1 = winning (smile), -1 = losing (frown)
        val mouth = Path(); mouth.moveTo(headX - 17f * k, my - curve * 0.4f); mouth.quadTo(headX, my + curve, headX + 17f * k, my - curve * 0.4f); cv.drawPath(mouth, p)
        if (mood < -0.3f) { cv.drawLine(headX - 24f * k, eyeY - 16f * k, headX - 8f * k, eyeY - 11f * k, p); cv.drawLine(headX + 24f * k, eyeY - 16f * k, headX + 8f * k, eyeY - 11f * k, p) }
        p.style = Paint.Style.FILL
        text(cv, pl.name, headX, headY - headR - 18f * k, 30f * k, Color.WHITE)
    }

    private fun botFigure(cv: Canvas, team: Int, x: Float, ground: Float, k: Float) {
        p.style = Paint.Style.FILL; p.color = Color.rgb(120, 128, 140)
        cv.drawRoundRect(RectF(x - 52f * k, ground - 210f * k, x + 52f * k, ground - 40f * k), 16f * k, 16f * k, p)
        p.color = TEAM[team]; cv.drawRect(x - 52f * k, ground - 130f * k, x + 52f * k, ground - 110f * k, p)
        tx.textSize = 70f * k; tx.style = Paint.Style.FILL; tx.color = Color.WHITE; cv.drawText("🤖", x, ground - 140f * k, tx)
        p.color = Color.rgb(40, 44, 52); cv.drawCircle(x - 30f * k, ground - 24f * k, 22f * k, p); cv.drawCircle(x + 30f * k, ground - 24f * k, 22f * k, p)
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        val w = w(); val h = h(); val k = sc()
        cv.save()
        if (now < shakeUntil) { val a = shake * k * (shakeUntil - now) / 350f; cv.translate(sin(now / 17.0).toFloat() * a, sin(now / 23.0).toFloat() * a * 0.6f) }
        // sky and grass
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.62f, Color.rgb(74, 152, 222), Color.rgb(190, 226, 250), Shader.TileMode.CLAMP); cv.drawRect(-40f, -40f, w + 40f, h * 0.62f, p)
        p.shader = LinearGradient(0f, h * 0.62f, 0f, h, Color.rgb(96, 178, 84), Color.rgb(52, 124, 58), Shader.TileMode.CLAMP); cv.drawRect(-40f, h * 0.62f, w + 40f, h + 40f, p)
        p.shader = null
        val ground = h * 0.82f; val ropeY = ground - 150f * k
        val reach = w * 0.27f                                          // the flag crossing a win line is a knockout
        // mud pit in the middle and the two win lines
        p.color = Color.rgb(104, 74, 48); cv.drawOval(RectF(w / 2 - 110f * k, ground - 22f * k, w / 2 + 110f * k, ground + 34f * k), p)
        for (t in 0..1) { val lx = w / 2 + (if (t == 0) -reach else reach)
            p.color = TEAM[t]; cv.drawRect(lx - 5f * k, h * 0.62f, lx + 5f * k, ground + 60f * k, p) }
        p.color = Color.argb(200, 255, 255, 255); var yy = h * 0.62f; while (yy < ground + 60f * k) { cv.drawRect(w / 2 - 3f * k, yy, w / 2 + 3f * k, yy + 22f * k, p); yy += 44f * k }
        // rope, flag and the two teams
        val fx = w / 2 + shown / TugCore.LIMIT * reach
        val teams = Array(2) { t -> core.players.values.filter { it.team == t } }
        val gap = 150f * k; val first = 190f * k
        val ends = FloatArray(2) { t -> fx + (if (t == 0) -1 else 1) * (first + (maxOf(teams[t].size, 1) - 1) * gap + 90f * k) }
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = 16f * k; p.color = Color.rgb(196, 152, 96); cv.drawLine(ends[0], ropeY, ends[1], ropeY, p)
        p.strokeWidth = 4f * k; p.color = Color.rgb(140, 104, 62); var rx = ends[0]; while (rx < ends[1]) { cv.drawLine(rx, ropeY - 6f * k, rx + 10f * k, ropeY + 6f * k, p); rx += 26f * k }
        p.style = Paint.Style.FILL
        val lead = (shown / TugCore.LIMIT).coerceIn(-1f, 1f)             // > 0 means the right team is ahead
        for (t in 0..1) { val mood = ((if (t == 0) -lead else lead) * 2.2f).coerceIn(-1f, 1f)
            if (teams[t].isEmpty()) botFigure(cv, t, fx + (if (t == 0) -first else first), ground, k)
            else teams[t].forEachIndexed { i, pl -> figure(cv, pl, fx + (if (t == 0) -1 else 1) * (first + i * gap), ground, k, mood, now, state == PLAY) } }
        p.color = Color.rgb(230, 57, 53); val flag = Path(); flag.moveTo(fx, ropeY); flag.lineTo(fx - 22f * k, ropeY + 70f * k); flag.lineTo(fx, ropeY + 52f * k); flag.lineTo(fx + 22f * k, ropeY + 70f * k); flag.close(); cv.drawPath(flag, p)
        // clock
        p.color = Color.argb(220, 20, 22, 28); cv.drawRoundRect(RectF(w / 2 - 150f * k, 22f * k, w / 2 + 150f * k, 108f * k), 20f * k, 20f * k, p)
        val secs = Math.ceil(core.timeLeft.toDouble()).toInt()
        text(cv, if (core.sudden) "⚡" else "" + secs / 60 + ":" + (secs % 60).toString().padStart(2, '0'), w / 2, 86f * k, 58f * k, if (core.sudden || secs <= 10) 0xFFFFD60A.toInt() else Color.WHITE, false)
        text(cv, s(R.string.team_left) + "  " + teams[0].size.coerceAtLeast(1), w * 0.17f, 82f * k, 44f * k, TEAM[0])
        text(cv, "" + teams[1].size.coerceAtLeast(1) + "  " + s(R.string.team_right), w * 0.83f, 82f * k, 44f * k, TEAM[1])
        if (state == PLAY && core.sudden) text(cv, s(R.string.tug_sudden), w / 2, 190f * k, 54f * k, 0xFFFFD60A.toInt())
        cv.restore()
        when (state) {
            LOBBY -> { Ui.dim(cv, 165)
                Ui.title(cv, view, s(R.string.tug_title), w / 2, h * 0.2f, 96f * k)
                for (t in 0..1) { val x = w * (if (t == 0) 0.27f else 0.73f)
                    text(cv, s(if (t == 0) R.string.team_left else R.string.team_right), x, h * 0.34f, 58f * k, TEAM[t])
                    if (teams[t].isEmpty()) text(cv, s(R.string.tug_bot), x, h * 0.34f + 70f * k, 44f * k, Color.WHITE)
                    else teams[t].forEachIndexed { i, pl -> text(cv, pl.name + " ✓", x, h * 0.34f + (70f + i * 58f) * k, 44f * k, Color.WHITE) } }
                val lv = s(intArrayOf(R.string.lvl_easy, R.string.lvl_medium, R.string.lvl_hard, R.string.lvl_graded)[core.level])
                text(cv, s(R.string.snake_level_line, lv, core.duration), w / 2, h * 0.76f, 46f * k, Color.WHITE)
                Ui.pill(cv, view, s(R.string.press_start), w / 2, h * 0.76f + 74f * k, 42f * k, now)
                text(cv, s(R.string.tug_join_hint), w / 2, h * 0.76f + 142f * k, 34f * k, Color.WHITE) }
            COUNT -> { val el = now - stateAt; Ui.countdown(cv, view, 3 - (el / 1000).toInt(), (el % 1000) / 1000f, w / 2, h * 0.5f, k) }
            END -> { Ui.dim(cv, 160); Ui.confetti(cv, view, now - stateAt)
                val win = core.winner.coerceIn(0, 1)
                text(cv, s(R.string.wins_fmt, s(if (win == 0) R.string.team_left else R.string.team_right)), w / 2, h * 0.34f, 130f * k, TEAM[win])
                text(cv, s(when (core.reason) { TugCore.KNOCKOUT -> R.string.tug_ko; TugCore.SUDDEN -> R.string.tug_sudden_win; else -> R.string.tug_time }), w / 2, h * 0.34f + 84f * k, 50f * k, Color.WHITE)
                var y = h * 0.6f
                core.mostCorrect()?.let { text(cv, s(R.string.tug_most, it.name, it.correct), w / 2, y, 50f * k, Color.WHITE); y += 70f * k }
                core.fastest()?.let { text(cv, s(R.string.tug_fastest, it.name, String.format(java.util.Locale.US, "%.1f", it.avgMs / 1000f)), w / 2, y, 50f * k, Color.WHITE) } }
        }
        return true
    }
}
