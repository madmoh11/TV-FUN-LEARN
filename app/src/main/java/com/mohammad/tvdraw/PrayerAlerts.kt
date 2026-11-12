package com.mohammad.tvdraw

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.TimeZone

/** Shows a banner on the TV some minutes before each prayer (and optionally at the prayer time). */
class PrayerAlerts(private val ctx: Context, private val view: DrawView, private val sfx: Sfx) {
    private val sp = ctx.getSharedPreferences("prayer", 0)
    private val ui = Handler(Looper.getMainLooper())
    private val fired = HashSet<String>()
    private val NAMES = intArrayOf(R.string.fajr, R.string.dhuhr, R.string.asr, R.string.maghrib, R.string.isha)
    private val tick = object : Runnable {
        override fun run() { try { check() } catch (e: Throwable) { }; ui.postDelayed(this, 20000) }
    }
    fun start() { ui.post(tick) }

    // Defaults: Amman, Jordan (Ministry of Awqaf angles), 15 minutes before.
    private fun on() = sp.getBoolean("on", true)
    private fun before() = sp.getInt("before", 15)
    private fun atTime() = sp.getBoolean("at", true)
    private fun method() = Prayer.Method(sp.getFloat("fajr", 18f).toDouble(), sp.getFloat("isha", 18f).toDouble(),
        sp.getInt("ishaMin", 0), sp.getInt("magMin", 5))

    private fun todayMinutes(cal: Calendar): IntArray {
        val tz = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3600000.0
        val t = Prayer.times(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
            sp.getFloat("lat", 31.9539f).toDouble(), sp.getFloat("lon", 35.9106f).toDouble(), tz, method())
        return IntArray(5) { Math.round(t[it] * 60).toInt() }
    }
    private fun clock12(mins: Int): String {
        val h = (mins / 60) % 24; val m = mins % 60
        return (if (h % 12 == 0) 12 else h % 12).toString() + ":" + m.toString().padStart(2, '0')
    }
    private fun name(i: Int, cal: Calendar) =
        if (i == 1 && cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) ctx.getString(R.string.jumuah) else ctx.getString(NAMES[i])

    private fun check() {
        if (!on()) return
        val cal = Calendar.getInstance()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val t = todayMinutes(cal)
        val b = before()
        val day = "" + cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR)
        if (fired.size > 60) fired.clear()
        for (i in 0..4) {
            if (now >= t[i] - b && now < t[i] - b + 2 && fired.add("$day-$i-b"))
                notify(ctx.str(R.string.prayer_before, b, name(i, cal), clock12(t[i])))
            if (atTime() && now >= t[i] && now < t[i] + 2 && fired.add("$day-$i-a"))
                notify(ctx.str(R.string.prayer_now, name(i, cal)))
        }
    }
    private fun notify(text: String) { view.showNotice(text); sfx.play("chime", 0.8f, true) }

    /** Commands from the phone page, already split on spaces: z on 1 | z before 15 | z at 1 | z city ... | z test */
    fun command(p: List<String>) {
        val e = sp.edit()
        when (p[1]) {
            "on" -> e.putBoolean("on", p[2] == "1")
            "at" -> e.putBoolean("at", p[2] == "1")
            "before" -> e.putInt("before", p[2].toInt().coerceIn(1, 60))
            "city" -> { e.putFloat("lat", p[2].toFloat()); e.putFloat("lon", p[3].toFloat())
                e.putFloat("fajr", p[4].toFloat()); e.putFloat("isha", p[5].toFloat())
                e.putInt("ishaMin", p[6].toInt()); e.putInt("magMin", p[7].toInt()) }
            "test" -> notify(ctx.str(R.string.prayer_before, before(), ctx.getString(R.string.asr), ctx.getString(R.string.prayer_test)))
        }
        e.apply(); fired.clear()
    }

    fun status(): String {
        val cal = Calendar.getInstance()
        val t = todayMinutes(cal)
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return "on=" + (if (on()) 1 else 0) + "|before=" + before() + "|at=" + (if (atTime()) 1 else 0) +
            "|lat=" + sp.getFloat("lat", 31.9539f) + "|lon=" + sp.getFloat("lon", 35.9106f) + "|clock=" + clock12(now) +
            "|times=" + t.joinToString(",") { clock12(it) }
    }
}

/** Starts the overlay again after the box restarts, so alerts keep working without opening the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) try {
            c.startForegroundService(Intent(c, OverlayService::class.java).putExtra("boot", true))
        } catch (e: Throwable) { }
    }
}
