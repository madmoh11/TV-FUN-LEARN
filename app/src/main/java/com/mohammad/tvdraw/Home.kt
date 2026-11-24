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
 * Announcements for the whole house ("dinner is ready"), sent from a phone or rung every day at a set time,
 * and the children's chores, which add points to the family board when they are ticked.
 */
class Home(private val view: View, private val family: Family) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    var sfx: Sfx? = null
    private val sp = view.context.getSharedPreferences("family", 0)
    val core = HomeCore().apply { load(sp.getString("home", "") ?: "") }

    private var sayText = ""; private var sayFrom = ""; private var sayAt = 0L; private var chimes = 0
    private var cheerText = ""; private var cheerPts = 0; private var cheerAt = 0L

    private fun enc(t: String) = URLEncoder.encode(t, "UTF-8")
    private fun dec(t: String) = URLDecoder.decode(t, "UTF-8")
    private fun store() = sp.edit().putString("home", core.save()).apply()
    private fun today() = TvClock.now().let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    private fun minuteNow() = TvClock.now().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    private val tick = object : Runnable { override fun run() { check(); view.postDelayed(this, 20000) } }
    fun start() { view.removeCallbacks(tick); view.postDelayed(tick, 3000) }
    /** Rings the alarms whose time has come. */
    fun check(minute: Int = minuteNow(), day: Int = today()) = synchronized(core) {
        val due = core.due(minute, day)
        if (due.isNotEmpty()) { store(); announce(due.joinToString("  •  ") { it.text }, "") }
    }

    fun announce(text: String, from: String) {
        sayText = text; sayFrom = from; sayAt = SystemClock.uptimeMillis(); chimes = 0
        view.invalidate()
    }

    /** "al now TEXT", "al add MINUTE TEXT", "al del ID", "ch add POINTS TEXT", "ch del ID", "ch done ID", "ch undo ID" */
    fun command(pp: List<String>, cid: String) = synchronized(core) {
        when (pp[0]) {
            "al" -> when (pp[1]) {
                "now" -> { val t = HomeCore.clean(dec(pp.getOrElse(2) { "" }), 60); if (t.isNotEmpty()) announce(t, family.nameOf(cid)) }
                "add" -> if (core.addAlarm(pp[2].toInt(), dec(pp.getOrElse(3) { "" })) != null) store()
                "del" -> if (core.delAlarm(pp[2].toInt())) store()
            }
            "ch" -> when (pp[1]) {
                "add" -> if (core.addChore(pp[2].toInt(), dec(pp.getOrElse(3) { "" })) != null) store()
                "del" -> if (core.delChore(pp[2].toInt())) store()
                "done" -> core.done(pp[2].toInt(), family.nameOf(cid), today())?.let { c ->
                    store(); family.bonus(c.doneBy, c.points)
                    cheerText = s(R.string.chore_done, c.doneBy, c.text); cheerPts = c.points; cheerAt = SystemClock.uptimeMillis()
                    sfx?.play("chime", 0.9f)
                }
                "undo" -> core.undo(pp[2].toInt(), today())?.let { c -> store(); family.bonus(c.doneBy, -c.points) }
            }
        }
        view.invalidate()
    }

    /** One line per alarm "A|id|minute|text" and per chore "H|id|points|who did it today|text". */
    fun status(): String = synchronized(core) {
        val sb = StringBuilder(); val day = today()
        for (a in core.alarms) sb.append("A|").append(a.id).append('|').append(a.minute).append('|').append(enc(a.text)).append('\n')
        for (c in core.chores) sb.append("H|").append(c.id).append('|').append(c.points).append('|').append(if (core.isDone(c, day)) enc(c.doneBy) else "").append('|').append(enc(c.text)).append('\n')
        sb.toString()
    }

    private fun fit(cv: Canvas, t: String, x: Float, y: Float, size: Float, maxWidth: Float, color: Int, outline: Boolean = false) {
        val wd = Ui.width(view, t, size); Ui.text(cv, view, t, x, y, if (wd > maxWidth) size * maxWidth / wd else size, color, outline)
    }

    /** Returns true while something on screen is moving. */
    fun draw(cv: Canvas, now: Long): Boolean {
        val w = view.width.toFloat(); val h = view.height.toFloat(); val k = h / 1080f
        var moving = false
        if (sayAt > 0L) {
            val el = now - sayAt
            if (el < SAY_MS) {
                if (chimes < 2 && el >= chimes * 900L) { chimes++; sfx?.play("chime", 1f, true) }
                val slide = minOf(1f, el / 350f).let { 1f - (1f - it) * (1f - it) }
                val out = if (el > SAY_MS - 400) (SAY_MS - el) / 400f else 1f
                val top = h * 0.07f - (1f - slide * out) * 320f * k
                val pop = 1f + 0.015f * sin(now / 180.0).toFloat()
                Ui.panel(cv, RectF(w / 2 - 700f * k * pop, top, w / 2 + 700f * k * pop, top + 230f * k), k, Color.argb(240, 26, 96, 74), Ui.GOLD)
                fit(cv, "📣 $sayText", w / 2, top + (if (sayFrom.isEmpty()) 142f else 118f) * k, 84f * k, 1300f * k, Color.WHITE, true)
                if (sayFrom.isNotEmpty()) fit(cv, s(R.string.say_from, sayFrom), w / 2, top + 190f * k, 38f * k, 1200f * k, Ui.GOLD)
                moving = true
            } else sayAt = 0L
        }
        if (cheerAt > 0L) {
            val el = now - cheerAt
            if (el < CHEER_MS) {
                Ui.confetti(cv, view, el, 70)
                val pop = minOf(1f, el / 250f)
                Ui.panel(cv, RectF(w / 2 - 640f * k * pop, h * 0.4f, w / 2 + 640f * k * pop, h * 0.4f + 230f * k), k, Color.argb(240, 90, 60, 10), Ui.GOLD)
                if (pop >= 1f) {
                    fit(cv, "🎉 $cheerText", w / 2, h * 0.4f + 104f * k, 66f * k, 1180f * k, Color.WHITE, true)
                    Ui.text(cv, view, s(R.string.chore_points, cheerPts), w / 2, h * 0.4f + 184f * k, 54f * k, Ui.GOLD, false)
                }
                moving = true
            } else cheerAt = 0L
        }
        if (moving) view.postInvalidateOnAnimation()
        return moving
    }

    companion object { const val SAY_MS = 10000L; const val CHEER_MS = 7000L }
}
