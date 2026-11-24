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
    init { TvClock.init(ctx)      // Jordan's timetable puts Maghrib 6 minutes after sunset; earlier versions saved 5
        if (!sp.getBoolean("mag6", false)) { val e = sp.edit().putBoolean("mag6", true)
            if (sp.getInt("magMin", 6) == 5 && sp.getFloat("fajr", 18f) == 18f) e.putInt("magMin", 6)
            e.apply() }
    }
    fun start() { ui.post(tick); if (adhanOn()) fetchAdhan() }

    // Defaults: Amman, Jordan (Ministry of Awqaf angles), 15 minutes before.
    private fun on() = sp.getBoolean("on", true)
    private fun before() = sp.getInt("before", 15)
    private fun atTime() = sp.getBoolean("at", true)
    private fun todayMinutes(cal: Calendar) = prayerMinutes(sp, cal)
    private fun clock12(mins: Int): String {
        val h = (mins / 60) % 24; val m = mins % 60
        return (if (h % 12 == 0) 12 else h % 12).toString() + ":" + m.toString().padStart(2, '0')
    }
    private fun name(i: Int, cal: Calendar) =
        if (i == 1 && cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) ctx.getString(R.string.jumuah) else ctx.getString(NAMES[i])

    private fun check() {
        if (!on()) return
        val cal = TvClock.now()
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val t = todayMinutes(cal)
        val b = before()
        val day = "" + cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.DAY_OF_YEAR)
        if (fired.size > 60) fired.clear()
        for (i in 0..4) {
            if (now >= t[i] - b && now < t[i] - b + 2 && fired.add("$day-$i-b"))
                notify(ctx.str(R.string.prayer_before, b, name(i, cal), clock12(t[i])))
            if (atTime() && now >= t[i] && now < t[i] + 2 && fired.add("$day-$i-a"))
                call(ctx.str(R.string.prayer_now, name(i, cal)))
        }
    }
    private fun notify(text: String) { view.showNotice(text); sfx.play("chime", 0.8f, true) }

    private fun adhanOn() = sp.getBoolean("adhan", true)
    private var adhan: android.media.MediaPlayer? = null
    fun stopAdhan() { try { adhan?.release() } catch (e: Throwable) { }; adhan = null }
    private val adhanFile get() = java.io.File(ctx.filesDir, "adhan.mp3")
    private var fetching = false
    /** Saves the recording on the TV the first time, so the next call to prayer has it. */
    private fun fetchAdhan() {
        if (fetching || (adhanFile.exists() && adhanFile.length() > 100000)) return
        fetching = true
        kotlin.concurrent.thread {
            try {
                val c = java.net.URL(ADHAN_URL).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 30000; c.setRequestProperty("User-Agent", "LammaTV/3 (Android TV app)")
                try { if (c.responseCode == 200) { val part = java.io.File(adhanFile.path + ".part")
                    c.inputStream.use { i -> part.outputStream().use { o -> i.copyTo(o) } }
                    if (part.length() > 100000) part.renameTo(adhanFile) else part.delete() } } finally { c.disconnect() }
            } catch (e: Throwable) { }
            fetching = false
        }
    }
    /** The recorded call to prayer when it is switched on and already saved on the TV; a chime otherwise. */
    private fun call(text: String) {
        view.showNotice(text)
        if (!adhanOn()) { sfx.play("chime", 0.8f, true); return }
        try {
            stopAdhan()
            if (!adhanFile.exists() || adhanFile.length() < 100000) throw java.io.IOException("not saved yet")
            val m = android.media.MediaPlayer(); adhan = m
            m.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            m.setDataSource(adhanFile.path)
            m.setOnCompletionListener { stopAdhan() }
            m.prepare(); m.start()
        } catch (e: Throwable) { stopAdhan(); sfx.play("chime", 0.8f, true); fetchAdhan() }
    }

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
            "adhan" -> { e.putBoolean("adhan", p[2] == "1"); if (p[2] != "1") stopAdhan() else fetchAdhan() }
            "boot" -> e.putBoolean("boot", p[2] == "1")
            "shift" -> e.putInt("shift", p[2].toInt().coerceIn(-3, 3))
            "bg" -> e.putString("bg", p[2].filter { it.isLetterOrDigit() }.take(20))
            "stop" -> stopAdhan()
            "call" -> call(ctx.str(R.string.prayer_now, ctx.getString(R.string.prayer_test)))
            "test" -> notify(ctx.str(R.string.prayer_before, before(), ctx.getString(R.string.asr), ctx.getString(R.string.prayer_test)))
        }
        e.apply(); fired.clear()
    }

    fun status(): String {
        val cal = TvClock.now()
        val t = todayMinutes(cal)
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return "on=" + (if (on()) 1 else 0) + "|before=" + before() + "|at=" + (if (atTime()) 1 else 0) + "|adhan=" + (if (adhanOn()) 1 else 0) + "|boot=" + (if (sp.getBoolean("boot", true)) 1 else 0) + "|shift=" + sp.getInt("shift", 0) + "|bg=" + (sp.getString("bg", "0") ?: "0").take(1) + "|zone=" + TimeZone.getDefault().id + "|saved=" + (if (adhanFile.exists() && adhanFile.length() > 100000) 1 else 0) +
            "|lat=" + sp.getFloat("lat", 31.9539f) + "|lon=" + sp.getFloat("lon", 35.9106f) + "|clock=" + clock12(now) +
            "|times=" + t.joinToString(",") { clock12(it) }
    }
}

/** The call to prayer recited by Aaqib Azeez, from Wikimedia Commons (CC BY-SA 4.0, uploaded by Atcovi). */
const val ADHAN_URL = "https://upload.wikimedia.org/wikipedia/commons/7/7d/The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3"

/** The five prayer times of a day in minutes, for the place and method saved from the phone (Amman by default). */
fun prayerMinutes(sp: android.content.SharedPreferences, cal: Calendar): IntArray {
    val m = Prayer.Method(sp.getFloat("fajr", 18f).toDouble(), sp.getFloat("isha", 18f).toDouble(), sp.getInt("ishaMin", 0), sp.getInt("magMin", 6))
    val tz = cal.timeZone.getOffset(cal.timeInMillis) / 3600000.0
    val t = Prayer.times(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        sp.getFloat("lat", 31.9539f).toDouble(), sp.getFloat("lon", 35.9106f).toDouble(), tz, m)
    return Prayer.minutes(t)
}

/** Starts the overlay again after the box restarts, so alerts keep working without opening the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) try {
            c.startForegroundService(Intent(c, OverlayService::class.java).putExtra("boot", true))
            if (c.getSharedPreferences("prayer", 0).getBoolean("boot", true))
                c.startActivity(Intent(c, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Throwable) { }
    }
}
