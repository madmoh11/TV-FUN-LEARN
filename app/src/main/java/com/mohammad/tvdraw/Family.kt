package com.mohammad.tvdraw

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Calendar
import kotlin.math.sin

/**
 * Family tools that sit on top of whatever is playing: a quick vote, a countdown timer,
 * and the hall of fame that collects points from every game.
 */
class Family(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    var sfx: Sfx? = null
    private val sp = view.context.getSharedPreferences("family", 0)
    val board = Board().apply { load(sp.getString("board", "") ?: "") }
    private val names = java.util.concurrent.ConcurrentHashMap<String, String>()      // what each phone calls itself

    // quick vote
    private var question = ""; private var options = listOf<String>()
    private val votes = LinkedHashMap<String, Int>()
    private var pollState = NONE; private var pollClosedAt = 0L
    // timer
    private var timerEnd = 0L; private var timerLabel = ""; private var timerDoneAt = 0L; private var chimes = 0
    // hall of fame on the TV
    private var boardUntil = 0L

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun enc(t: String) = URLEncoder.encode(t, "UTF-8")
    private fun dec(t: String) = Board.cleanName(URLDecoder.decode(t, "UTF-8")).let { if (it.length > 0) it else "" }
    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = false, align: Paint.Align = Paint.Align.CENTER) = Ui.text(cv, view, t, x, y, size, color, outline, align)
    /** Draws text, made smaller when it would not fit in maxWidth. */
    private fun fit(cv: Canvas, t: String, x: Float, y: Float, size: Float, maxWidth: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        val wd = Ui.width(view, t, size); text(cv, t, x, y, if (wd > maxWidth) size * maxWidth / wd else size, color, false, align)
    }
    private fun weekNow() = Calendar.getInstance().let { it.get(Calendar.YEAR) * 100 + it.get(Calendar.WEEK_OF_YEAR) }
    fun nameOf(cid: String) = names[cid] ?: ""

    /** Called by a game when it ends: every named player gets points on the board. */
    fun award(results: List<GameResult>) = synchronized(board) {
        var changed = false
        for (r in results) if (board.record(nameOf(r.cid), r.won, r.math, r.words, weekNow()) > 0) changed = true
        if (changed) sp.edit().putString("board", board.save()).apply()
    }

    /** "nm NAME", "poll new Q A B [C] [D]", "poll vote n", "poll close", "poll off", "tm start SECONDS LABEL", "tm add SECONDS", "tm stop", "hof show", "hof reset" */
    fun command(pp: List<String>, cid: String) = synchronized(board) {
        val now = SystemClock.uptimeMillis()
        when (pp[0]) {
            "nm" -> { val n = dec(pp.getOrElse(1) { "" }); if (n.isEmpty()) names.remove(cid) else names[cid] = n }
            "poll" -> when (pp[1]) {
                "new" -> { val q = URLDecoder.decode(pp.getOrElse(2) { "" }, "UTF-8").replace('|', ' ').trim().take(60)
                    val o = pp.drop(3).map { URLDecoder.decode(it, "UTF-8").replace('|', ' ').trim().take(24) }.filter { it.isNotEmpty() }.take(4)
                    if (q.isNotEmpty() && o.size >= 2) { question = q; options = o; votes.clear(); pollState = OPEN; sfx?.play("blip", 0.8f) } }
                "vote" -> if (pollState == OPEN) { val i = pp[2].toInt(); if (i in options.indices) { votes[cid] = i; sfx?.play("blip", 0.4f) } }
                "close" -> if (pollState == OPEN) { pollState = CLOSED; pollClosedAt = now; sfx?.play("chime", 0.8f) }
                "off" -> pollState = NONE
            }
            "tm" -> when (pp[1]) {
                "start" -> { val secs = pp[2].toInt().coerceIn(5, 6 * 3600); timerEnd = now + secs * 1000L; timerDoneAt = 0L; chimes = 0
                    timerLabel = URLDecoder.decode(pp.getOrElse(3) { "" }, "UTF-8").replace('|', ' ').trim().take(28) }
                "add" -> if (timerEnd > now) timerEnd = (timerEnd + pp[2].toInt().coerceIn(-3600, 3600) * 1000L).coerceAtLeast(now + 1000)
                "stop" -> { timerEnd = 0L; timerDoneAt = 0L }
            }
            "hof" -> when (pp[1]) {
                "show" -> { board.rollWeek(weekNow()); boardUntil = now + BOARD_MS }
                "hide" -> boardUntil = 0L
                "reset" -> { board.clear(); sp.edit().putString("board", board.save()).apply() }
            }
        }
        view.invalidate()
    }

    /** One line each: the timer, the vote, this week's champions, then one line per player on the board. */
    fun status(cid: String): String = synchronized(board) {
        val now = SystemClock.uptimeMillis()
        val sb = StringBuilder()
        sb.append("T|").append(if (timerEnd > now) Math.ceil((timerEnd - now) / 1000.0).toInt() else if (timerDoneAt > 0 && now - timerDoneAt < DONE_MS) 0 else -1).append('|').append(enc(timerLabel)).append('\n')
        sb.append("P|").append(pollState).append('|').append(votes[cid] ?: -1).append('|').append(enc(question))
        options.forEachIndexed { i, o -> sb.append('|').append(enc(o)).append('|').append(votes.values.count { it == i }) }
        sb.append('\n')
        board.rollWeek(weekNow())
        sb.append("C|").append(enc(board.weekChampion()?.name ?: "")).append('|').append(enc(board.mathChampion()?.name ?: "")).append('|').append(enc(board.wordChampion()?.name ?: "")).append('\n')
        for (e in board.top(10)) sb.append("B|").append(enc(e.name)).append('|').append(e.points).append('|').append(e.weekPoints).append('|').append(e.wins).append('|').append(e.games).append('\n')
        sb.toString()
    }

    private fun clock(secs: Int) = if (secs >= 3600) "" + secs / 3600 + ":" + (secs % 3600 / 60).toString().padStart(2, '0') + ":" + (secs % 60).toString().padStart(2, '0')
        else "" + secs / 60 + ":" + (secs % 60).toString().padStart(2, '0')

    /** Draws whatever is active. Returns true while something on screen is moving. */
    fun draw(cv: Canvas, now: Long): Boolean = synchronized(board) {
        val w = view.width.toFloat(); val h = view.height.toFloat(); val k = h / 1080f
        var moving = false
        // ---- timer: a pill in the top right corner, then a big "time is up"
        if (timerEnd > 0L && now >= timerEnd && timerDoneAt == 0L) { timerDoneAt = now; timerEnd = 0L }
        if (timerEnd > now) {
            val left = Math.ceil((timerEnd - now) / 1000.0).toInt(); val urgent = left <= 60
            val t = "⏱ " + (if (timerLabel.isEmpty()) "" else "$timerLabel  ") + clock(left)
            val size = 44f * k * (if (urgent) 1f + 0.05f * sin(now / 160.0).toFloat() else 1f)
            val half = Ui.width(view, t, size) / 2 + 34f * k; val cx = w - 60f * k - half
            Ui.panel(cv, RectF(cx - half, 40f * k, cx + half, 122f * k), k, if (urgent) Color.argb(235, 150, 28, 40) else Color.argb(225, 38, 22, 82), if (urgent) Ui.GOLD else Ui.PURPLE)
            text(cv, t, cx, 98f * k, size, Color.WHITE); moving = true
        } else if (timerDoneAt > 0L) {
            val el = now - timerDoneAt
            if (el < DONE_MS) {
                if (chimes < 3 && el >= chimes * 1300L) { chimes++; sfx?.play("chime", 1f, true) }
                val pop = 1f + 0.04f * sin(now / 140.0).toFloat()
                Ui.panel(cv, RectF(w / 2 - 620f * k * pop, h * 0.36f - 20f * k, w / 2 + 620f * k * pop, h * 0.64f + 20f * k), k, Color.argb(240, 150, 28, 40), Ui.GOLD)
                text(cv, "⏰ " + s(R.string.timer_done), w / 2, h * 0.5f - 6f * k, 110f * k * pop, Color.WHITE, true)
                if (timerLabel.isNotEmpty()) fit(cv, timerLabel, w / 2, h * 0.5f + 96f * k, 64f * k, 1100f * k, Ui.GOLD)
                moving = true
            } else timerDoneAt = 0L
        }
        // ---- quick vote: a panel on the right side that leaves most of the picture free
        if (pollState == CLOSED && now - pollClosedAt > RESULT_MS) pollState = NONE
        if (pollState != NONE) {
            val pw = 600f * k; val rowH = 96f * k; val ph = 150f * k + options.size * rowH + 66f * k
            val l = w - pw - 50f * k; val t = (h - ph) / 2
            Ui.panel(cv, RectF(l, t, l + pw, t + ph), k)
            fit(cv, "🗳 $question", l + pw / 2, t + 78f * k, 44f * k, pw - 60f * k, Color.WHITE)
            val total = votes.size; val best = options.indices.maxOfOrNull { i -> votes.values.count { it == i } } ?: 0
            options.forEachIndexed { i, o -> val n = votes.values.count { it == i }; val y = t + 126f * k + i * rowH
                val win = pollState == CLOSED && n == best && best > 0
                p.style = Paint.Style.FILL; p.color = Color.argb(255, 24, 14, 56); cv.drawRoundRect(RectF(l + 30f * k, y, l + pw - 30f * k, y + rowH - 16f * k), 18f * k, 18f * k, p)
                if (n > 0) { p.color = if (win) Ui.GOLD else Ui.PARTY[i % Ui.PARTY.size]; p.alpha = if (win) 255 else 210
                    cv.drawRoundRect(RectF(l + 30f * k, y, l + 30f * k + (pw - 60f * k) * n / maxOf(total, 1), y + rowH - 16f * k), 18f * k, 18f * k, p); p.alpha = 255 }
                fit(cv, o, l + 52f * k, y + 54f * k, 38f * k, pw - 200f * k, if (win) Ui.INK else Color.WHITE, Paint.Align.LEFT)
                text(cv, "" + n, l + pw - 52f * k, y + 54f * k, 40f * k, if (win && n == total) Ui.INK else Color.WHITE, false, Paint.Align.RIGHT) }
            fit(cv, if (pollState == CLOSED) s(R.string.poll_result, total) else s(R.string.poll_hint, total), l + pw / 2, t + ph - 30f * k, 28f * k, pw - 50f * k, if (pollState == CLOSED) Ui.GOLD else Color.argb(255, 200, 190, 235))
            if (pollState == CLOSED) moving = true
        }
        // ---- hall of fame
        if (now < boardUntil) {
            Ui.dim(cv, 190)
            Ui.title(cv, view, "🏆 " + s(R.string.board_title), w / 2, h * 0.15f, 84f * k)
            val list = board.top(6)
            if (list.isEmpty()) text(cv, s(R.string.board_empty), w / 2, h * 0.5f, 50f * k, Color.WHITE, true)
            list.forEachIndexed { i, e -> val y = h * 0.27f + i * 96f * k
                Ui.panel(cv, RectF(w * 0.2f, y, w * 0.8f, y + 80f * k), k, if (i == 0) Color.argb(240, 90, 60, 10) else Color.argb(225, 38, 22, 82), if (i == 0) Ui.GOLD else Ui.PURPLE)
                text(cv, if (i < 3) arrayOf("🥇", "🥈", "🥉")[i] else "" + (i + 1), w * 0.2f + 60f * k, y + 58f * k, 48f * k, Color.WHITE)
                fit(cv, e.name, w * 0.2f + 120f * k, y + 56f * k, 46f * k, w * 0.28f, Color.WHITE, Paint.Align.LEFT)
                text(cv, s(R.string.board_week_pts, e.weekPoints), w * 0.63f, y + 54f * k, 30f * k, Color.argb(255, 200, 190, 235), false, Paint.Align.RIGHT)
                text(cv, s(R.string.board_pts, e.points), w * 0.8f - 40f * k, y + 56f * k, 46f * k, Ui.GOLD, false, Paint.Align.RIGHT) }
            val champs = listOfNotNull(board.weekChampion()?.let { s(R.string.champ_week, it.name) }, board.mathChampion()?.let { s(R.string.champ_math, it.name) }, board.wordChampion()?.let { s(R.string.champ_words, it.name) })
            if (champs.isNotEmpty()) fit(cv, champs.joinToString("    "), w / 2, h * 0.93f, 38f * k, w * 0.9f, Ui.GOLD)
            view.postInvalidateDelayed(boardUntil - now + 50)
        }
        if (moving) view.postInvalidateOnAnimation()
        moving
    }

    companion object { const val NONE = 0; const val OPEN = 1; const val CLOSED = 2
        const val RESULT_MS = 12000L; const val DONE_MS = 12000L; const val BOARD_MS = 15000L }
}
