package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import java.net.URLDecoder

/** Rock, paper, scissors on the TV: the chant, the hands of every player shown at once, and the score. */
class RpsGame(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    companion object { const val OFF = 0; const val LOBBY = 1; const val PICK = 2; const val REVEAL = 3; const val END = 4
        const val GONE_MS = 8000L; const val CHANT_MS = 2400L; const val PICK_MS = 9000L; const val REVEAL_MS = 4200L
        val HANDS = arrayOf("✊", "✋", "✌️") }
    var sfx: Sfx? = null
    var onEnd: ((List<GameResult>) -> Unit)? = null
    private val core = RpsCore()
    private var state = OFF
    val phase get() = state
    private var stateAt = 0L; private var beat = -1
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val active get() = state != OFF

    fun stopNow() { state = OFF; core.players.clear(); seen.clear() }

    /** state|round|rounds|seconds left|my score|my hand (-1 none)|points just won|players|my place at the end|in game|how the round went for me (1 won, 0 even, -1 lost) */
    fun status(cid: String): String = synchronized(core) {
        val now = SystemClock.uptimeMillis(); seen[cid] = now
        val me = core.players[cid]
        "" + state + '|' + (core.round + 1) + '|' + core.rounds + '|' + (if (state == PICK) Math.ceil((PICK_MS - (now - stateAt)) / 1000.0).toInt().coerceAtLeast(0) else 0) + '|' +
            (me?.score ?: 0) + '|' + (me?.pick ?: -1) + '|' + (me?.gained ?: 0) + '|' + core.humans.size + '|' + (if (state == END && me != null) core.place(me) else 0) + '|' +
            (if (me != null) 1 else 0) + '|' + (if (me == null || state != REVEAL) 0 else if (me.gained > me.lost) 1 else if (me.gained < me.lost) -1 else 0)
    }

    /** "rps join NAME", "rps leave", "rps start", "rps stop", "rps rounds n", "rps pick ROUND HAND" */
    fun command(pp: List<String>, cid: String) = synchronized(core) {
        val now = SystemClock.uptimeMillis(); seen[cid] = now
        val idle = state == LOBBY || state == END
        when (pp[1]) {
            "join" -> if (core.join(cid, Board.cleanName(URLDecoder.decode(pp.getOrElse(2) { "" }, "UTF-8"))) && state == OFF) { state = LOBBY; stateAt = now }
            "leave" -> { core.leave(cid); if (core.humans.isEmpty()) stopNow() }
            "start" -> if (idle && core.humans.isNotEmpty()) { core.begin(s(R.string.rps_tv)); deal(now) }
            "rounds" -> if (idle) core.rounds = pp[2].toInt().let { if (it == 3 || it == 7) it else 5 }
            "pick" -> if (state == PICK && pp[2].toInt() == core.round + 1 && core.pick(cid, pp[3].toInt())) sfx?.play("blip", 0.5f)
            "stop" -> stopNow()
        }
        view.invalidate()
    }
    private fun deal(now: Long) { state = PICK; stateAt = now; beat = -1 }

    private fun update(now: Long) = synchronized(core) {
        val gone = core.humans.filter { now - (seen[it.cid] ?: now) > GONE_MS }.map { it.cid }
        if (gone.isNotEmpty()) { gone.forEach { core.leave(it); seen.remove(it) }; if (core.humans.isEmpty()) { stopNow(); return } }
        val el = now - stateAt
        when (state) {
            PICK -> { val b = (el / (CHANT_MS / 3)).toInt().coerceAtMost(3)
                if (b != beat && b < 3) { beat = b; sfx?.play("blip", 0.7f) }
                if ((core.allPicked && el >= CHANT_MS) || el >= PICK_MS) { core.reveal(); state = REVEAL; stateAt = now; sfx?.play("chime", 0.8f) } }
            REVEAL -> if (el >= REVEAL_MS) {
                if (core.next()) deal(now)
                else { state = END; stateAt = now; sfx?.play("chime", 1f)
                    val win = core.winners(); onEnd?.invoke(core.humans.map { GameResult(it.cid, it in win) }) } }
            END -> if (el > 600000) stopNow()
        }
    }

    // ---- drawing ----
    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = false) = Ui.text(cv, view, t, x, y, size, color, outline)
    private fun fit(cv: Canvas, t: String, x: Float, y: Float, size: Float, maxWidth: Float, color: Int, outline: Boolean = false) {
        val wd = Ui.width(view, t, size); text(cv, t, x, y, if (wd > maxWidth) size * maxWidth / wd else size, color, outline)
    }

    /** One column for each player: the hand (hidden until all are shown), the name and the score. */
    private fun hands(cv: Canvas, w: Float, h: Float, k: Float, now: Long) {
        val list = core.players.values.toList(); if (list.isEmpty()) return
        val cw = minOf(430f * k, (w - 100f * k) / list.size - 20f * k); val total = list.size * (cw + 20f * k) - 20f * k
        val top = h * 0.3f; val bottom = h * 0.9f
        list.forEachIndexed { i, pl ->
            val l = (w - total) / 2 + i * (cw + 20f * k)
            val won = state == REVEAL && pl.gained > pl.lost; val lost = state == REVEAL && pl.gained < pl.lost
            Ui.panel(cv, RectF(l, top, l + cw, bottom), k, if (won) Color.argb(240, 22, 110, 54) else if (lost) Color.argb(235, 90, 26, 40) else Color.argb(230, 38, 22, 82),
                if (won) Ui.GOLD else if (state == PICK && pl.pick != RpsCore.NONE) Ui.GOLD else Ui.PURPLE)
            val cx = l + cw / 2; val size = minOf(cw * 0.62f, 300f * k)
            if (state == REVEAL) { val pop = minOf(1f, (now - stateAt) / 220f)          // the hands land together
                if (pl.pick == RpsCore.NONE) text(cv, "—", cx, top + (bottom - top) * 0.5f, size * 0.6f, Color.argb(160, 255, 255, 255))
                else text(cv, HANDS[pl.pick], cx, top + (bottom - top) * 0.52f, size * (0.6f + 0.4f * pop), Color.WHITE)
                if (pl.gained > 0) text(cv, "+" + pl.gained, cx, bottom - 118f * k, 54f * k, Ui.GOLD, true)
            } else { val ready = pl.robot || pl.pick != RpsCore.NONE
                val shake = if (state == PICK && now - stateAt < CHANT_MS) Math.abs(Math.sin((now - stateAt) / (CHANT_MS / 3.0) * Math.PI)).toFloat() * 40f * k else 0f
                text(cv, "✊", cx, top + (bottom - top) * 0.5f - shake, size * 0.8f, Color.WHITE)
                if (state == PICK) text(cv, if (ready) "✓" else "…", cx, bottom - 118f * k, 54f * k, if (ready) Color.rgb(90, 230, 130) else Color.argb(200, 255, 255, 255)) }
            fit(cv, pl.name.ifEmpty { "" + (i + 1) }, cx, bottom - 58f * k, 40f * k, cw - 30f * k, Color.WHITE)
            text(cv, "⭐ " + pl.score, cx, bottom - 14f * k, 34f * k, Ui.GOLD)
        }
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        synchronized(core) {
            val w = view.width.toFloat(); val h = view.height.toFloat(); val k = h / 1080f
            Ui.dim(cv, 232)
            Ui.title(cv, view, s(R.string.rps_title), w / 2, 118f * k, 78f * k)
            when (state) {
                LOBBY -> {
                    fit(cv, s(R.string.rps_how), w / 2, h * 0.36f, 52f * k, w * 0.86f, Color.WHITE, true)
                    text(cv, HANDS.joinToString("   "), w / 2, h * 0.56f, 150f * k, Color.WHITE)
                    text(cv, s(R.string.rps_setup, core.rounds, core.humans.size), w / 2, h * 0.68f, 40f * k, Color.argb(255, 200, 190, 235))
                    fit(cv, core.humans.joinToString("  •  ") { it.name.ifEmpty { "?" } }, w / 2, h * 0.76f, 40f * k, w * 0.86f, Ui.GOLD)
                    Ui.pill(cv, view, s(R.string.press_start), w / 2, h * 0.9f, 46f * k, now)
                }
                PICK, REVEAL -> {
                    text(cv, s(R.string.quiz_round, core.round + 1, core.rounds), 150f * k, 112f * k, 44f * k, Ui.GOLD, true)
                    val el = now - stateAt
                    val line = if (state == PICK) { if (el < CHANT_MS) s(intArrayOf(R.string.rps_rock, R.string.rps_paper, R.string.rps_scissors)[(el / (CHANT_MS / 3)).toInt().coerceIn(0, 2)]) + "…" else s(R.string.rps_choose) }
                        else { val top = core.players.values.filter { it.gained > it.lost }
                            if (top.isEmpty()) s(R.string.quiz_tie) else if (top.size == 1) s(R.string.rps_round_to, top[0].name.ifEmpty { "1" }) else s(R.string.rps_round_many) }
                    fit(cv, line, w / 2, h * 0.25f, 66f * k, w * 0.8f, if (state == REVEAL) Ui.GOLD else Color.WHITE, true)
                    hands(cv, w, h, k, now)
                }
                END -> {
                    val win = core.winners(); val el = now - stateAt
                    Ui.confetti(cv, view, el)
                    fit(cv, when { win.size == 1 -> s(R.string.wins_fmt, win[0].name.ifEmpty { "1" }); win.isEmpty() -> s(R.string.quiz_nobody); else -> s(R.string.quiz_tie) }, w / 2, h * 0.3f, 84f * k, w * 0.86f, Ui.GOLD, true)
                    core.ranking().take(7).forEachIndexed { i, pl -> val y = h * 0.37f + i * 84f * k
                        Ui.panel(cv, RectF(w * 0.3f, y, w * 0.7f, y + 72f * k), k, if (pl in win) Color.argb(240, 90, 60, 10) else Color.argb(225, 38, 22, 82), if (pl in win) Ui.GOLD else Ui.PURPLE)
                        text(cv, if (i < 3) arrayOf("🥇", "🥈", "🥉")[i] else "" + (i + 1), w * 0.3f + 56f * k, y + 52f * k, 42f * k, Color.WHITE)
                        Ui.text(cv, view, pl.name.ifEmpty { "" + (i + 1) }, w * 0.3f + 110f * k, y + 50f * k, 40f * k, Color.WHITE, false, Paint.Align.LEFT)
                        Ui.text(cv, view, "⭐ " + pl.score, w * 0.7f - 34f * k, y + 50f * k, 38f * k, Ui.GOLD, false, Paint.Align.RIGHT) }
                    Ui.pill(cv, view, s(R.string.press_start), w / 2, h * 0.95f, 38f * k, now)
                }
            }
        }
        view.postInvalidateOnAnimation()
        return true
    }
}
