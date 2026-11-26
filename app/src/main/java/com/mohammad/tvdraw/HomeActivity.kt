package com.mohammad.tvdraw

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import kotlin.concurrent.thread

/**
 * The home screen of the TV: the clock, the Hijri date and the next prayer on top, then rows of large tiles for the
 * Quran, the adhkar and the other apps of the TV. Everything is reached with the remote.
 */
class HomeActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private var k = 1f                                           // one design pixel on a 1080-line screen
    private lateinit var root: FrameLayout
    private lateinit var home: ScrollView
    private lateinit var clock: TextView
    private lateinit var dates: TextView
    private lateinit var nextUp: TextView
    private lateinit var times: LinearLayout
    private lateinit var weather: TextView
    private var weatherAt = 0L; private var weatherFor = ""
    private var extra: View? = null                              // a list or a reading page laid over the home screen
    private var lastFocus: View? = null
    private var page: PageView? = null
    private val audio by lazy { AyahAudio(this) }
    private var quran: Quran? = null
    private var adhkarJson = ""
    private var friday = false
    private val sp by lazy { getSharedPreferences("prayer", 0) }
    private val tick = object : Runnable { override fun run() { refresh(); ui.postDelayed(this, 1000L * (60 - TvClock.now().get(Calendar.SECOND)).coerceIn(1, 60)) } }

    private fun px(v: Float) = (v * k).toInt()
    private fun font() = Ui.font(this)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { startForegroundService(Intent(this, OverlayService::class.java)) } catch (e: Throwable) { }
        k = resources.displayMetrics.heightPixels / 1080f
        root = FrameLayout(this); root.layoutDirection = View.LAYOUT_DIRECTION_LOCALE
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF06262B.toInt(), 0xFF0B3C44.toInt(), 0xFF0A2540.toInt()))
        TvClock.init(this)
        wall = ImageView(this); wall.scaleType = ImageView.ScaleType.CENTER_CROP
        root.addView(wall, FrameLayout.LayoutParams(-1, -1))
        shade = View(this); root.addView(shade, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        wallpaper()
        sp.registerOnSharedPreferenceChangeListener(watch)
        build()
        thread {
            fun read(n: String) = try { assets.open(n).use { it.readBytes().toString(Charsets.UTF_8) } } catch (e: Throwable) { "" }
            val q = Quran(read("quran/quran-uthmani.txt"), read("quran/surahs.txt")); val a = read("data/adhkar.json")
            ui.post { quran = q; adhkarJson = a }
        }
    }
    override fun onDestroy() { super.onDestroy(); sp.unregisterOnSharedPreferenceChangeListener(watch) }

    private lateinit var wall: ImageView
    private lateinit var shade: View
    private var wallIs = ""
    private val watch = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "bg") ui.post { wallpaper() } else if (key == "shift" || key == "lat" || key == "magMin") ui.post { refresh() } }

    /** The picture behind the home screen: one that comes with the app, or a photo sent from the phone. A dark veil keeps the text readable. */
    private fun wallpaper() {
        val want = sp.getString("bg", "0") ?: "0"
        if (want == wallIs) return
        wallIs = want
        thread {
            val bmp = try { when {
                want.startsWith("c") -> android.graphics.BitmapFactory.decodeFile(java.io.File(filesDir, "wallpaper.jpg").path, android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true; android.graphics.BitmapFactory.decodeFile(java.io.File(filesDir, "wallpaper.jpg").path, this)
                    var ss = 1; while (outWidth / ss > 2400 || outHeight / ss > 2400) ss *= 2
                    inJustDecodeBounds = false; inSampleSize = ss })
                want in listOf("1", "2", "3", "4") -> Ui.bitmap(this, "bg/bg$want.webp")
                else -> null } } catch (e: Throwable) { null }
            ui.post { if (wallIs == want) { wall.setImageBitmap(bmp)
                shade.setBackgroundColor(if (bmp == null) Color.TRANSPARENT else Color.argb(if (want.startsWith("c") || want == "2") 130 else 70, 0, 12, 16)) } }
        }
    }

    override fun onResume() { super.onResume(); ui.removeCallbacks(tick); ui.post(tick) }
    override fun onPause() { super.onPause(); ui.removeCallbacks(tick); page?.let { closeExtra() } }

    // ---- building blocks ----
    private fun label(t: String, size: Float, color: Int = Color.WHITE): TextView = TextView(this).apply {
        text = t; typeface = font(); setTextColor(color); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * k); includeFontPadding = false }

    private fun tileBg(color: Int, focused: Boolean) = GradientDrawable().apply {
        cornerRadius = 30f * k; setColor(color); if (focused) setStroke(px(6f), Ui.GOLD) else setStroke(px(2f), Color.argb(60, 255, 255, 255)) }

    /** A large button for the remote: it grows and gets a gold edge while it is chosen. */
    private fun tile(title: String, sub: String, color: Int, w: Float, h: Float, mushaf: Boolean = false, icon: android.graphics.drawable.Drawable? = null, run: () -> Unit): View {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; box.gravity = Gravity.CENTER
        box.background = tileBg(color, false); box.isFocusable = true; box.isClickable = true
        box.setPadding(px(16f), px(10f), px(16f), px(10f))
        if (icon != null) box.addView(ImageView(this).apply { setImageDrawable(icon); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(px(w - 60f), px(h - 86f)))
        box.addView(label(title, if (icon != null) 24f else 40f).apply { gravity = Gravity.CENTER; maxLines = if (icon != null) 1 else 2; if (mushaf) { typeface = Ui.quranFont(this@HomeActivity); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 46f * k) } })
        if (sub.isNotEmpty()) box.addView(label(sub, 22f, Color.argb(210, 255, 255, 255)).apply { gravity = Gravity.CENTER })
        box.setOnFocusChangeListener { v, f -> v.background = tileBg(color, f); v.animate().scaleX(if (f) 1.08f else 1f).scaleY(if (f) 1.08f else 1f).setDuration(120).start() }
        box.setOnClickListener { run() }
        box.layoutParams = LinearLayout.LayoutParams(px(w), px(h)).apply { setMargins(px(14f), px(18f), px(14f), px(18f)) }
        return box
    }

    private fun row(into: LinearLayout, title: String, tiles: List<View>) {
        if (tiles.isEmpty()) return
        into.addView(label(title, 34f, Color.argb(235, 255, 214, 10)).apply { setShadowLayer(10f * k, 0f, 2f * k, Color.argb(200, 0, 0, 0)); setPadding(px(70f), px(18f), px(70f), 0) })
        val line = LinearLayout(this); line.orientation = LinearLayout.HORIZONTAL; line.setPadding(px(56f), 0, px(56f), 0)
        line.clipChildren = false; line.clipToPadding = false
        for (t in tiles) line.addView(t)
        val sc = HorizontalScrollView(this); sc.isHorizontalScrollBarEnabled = false; sc.clipChildren = false; sc.clipToPadding = false; sc.addView(line)
        // a row laid out from the right starts at its right end
        var placed = false
        sc.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (!placed && line.width > 0) { placed = true
            if (sc.layoutDirection == View.LAYOUT_DIRECTION_RTL) sc.scrollTo(line.width, 0) } }
        into.addView(sc)
    }

    private fun build() {
        if (::home.isInitialized) root.removeView(home)
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.clipChildren = false
        // the clock, the dates and the next prayer
        val head = LinearLayout(this); head.orientation = LinearLayout.HORIZONTAL; head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(px(70f), px(44f), px(70f), px(6f))
        clock = label("", 150f); clock.setShadowLayer(14f * k, 0f, 3f * k, Color.argb(160, 0, 0, 0)); clock.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val side = LinearLayout(this); side.orientation = LinearLayout.VERTICAL; side.setPadding(px(44f), 0, px(44f), 0)
        dates = label("", 34f, Color.argb(230, 190, 235, 240)); nextUp = label("", 46f, Ui.GOLD)
        dates.setShadowLayer(10f * k, 0f, 2f * k, Color.argb(200, 0, 0, 0)); nextUp.setShadowLayer(10f * k, 0f, 2f * k, Color.argb(200, 0, 0, 0))
        weather = label("", 38f); weather.setShadowLayer(10f * k, 0f, 2f * k, Color.argb(200, 0, 0, 0))
        side.addView(dates); side.addView(nextUp); side.addView(weather)
        showWeather()
        head.addView(clock); head.addView(side)
        col.addView(head)
        times = LinearLayout(this); times.orientation = LinearLayout.HORIZONTAL; times.setPadding(px(70f), 0, px(70f), px(8f))
        col.addView(times)

        val cal = TvClock.now(); friday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY
        val names = surahNames()
        val order = if (friday) Daily.SURAHS.toList() else Daily.SURAHS.toList()      // al-Kahf already leads; on Friday it is marked
        val qt = ArrayList<View>()
        for (n in order) qt.add(tile(names[n - 1], if (n == 18 && friday) getString(R.string.home_friday) else "", if (n == 18 && friday) 0xFF8A6A00.toInt() else 0xFF0F6B63.toInt(), 300f, 170f, true) { openSurah(n) })
        qt.add(tile(getString(R.string.home_all_surahs), "", 0xFF14505C.toInt(), 300f, 170f) { openSurahList() })
        row(col, getString(R.string.home_quran), qt)
        row(col, getString(R.string.home_adhkar), listOf(
            tile(getString(R.string.home_morning), "", 0xFFB5651D.toInt(), 300f, 170f) { openAdhkar(false) },
            tile(getString(R.string.home_evening), "", 0xFF2B3F8F.toInt(), 300f, 170f) { openAdhkar(true) },
            tile(getString(R.string.home_ruqyah), "", 0xFF1F7A4D.toInt(), 300f, 170f) { openRuqyah() },
            tile(getString(R.string.home_kursi), "", 0xFF1F7A4D.toInt(), 300f, 170f) { openPages(getString(R.string.home_kursi), { Daily.ranges(it, listOf(intArrayOf(2, 255, 255))) }, true) }))
        row(col, getString(R.string.app_name), listOf(
            tile(getString(R.string.home_phone), "", 0xFF5B3FA8.toInt(), 420f, 170f) { showPhone() }))
        row(col, getString(R.string.home_apps) + "   •   " + getString(R.string.home_app_tip), appTiles())
        // which version is running, so an update that did not go in is easy to spot
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Throwable) { "" }
        col.addView(label(getString(R.string.app_name) + " " + ver, 24f, Color.argb(150, 255, 255, 255)).apply { setPadding(px(70f), px(20f), px(70f), px(40f)) })

        home = ScrollView(this); home.isVerticalScrollBarEnabled = false; home.clipChildren = false; home.addView(col)
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        refresh()
        val stand = focusApp?.let { p -> col.findViewWithTag<View>(p) }; focusApp = null
        (stand ?: qt[0]).requestFocus()
    }

    private fun surahNames(): List<String> = try {
        assets.open("quran/surahs.txt").use { it.readBytes().toString(Charsets.UTF_8) }.split('\n').map { it.trim().split('|') }
            .filter { it.size >= 4 && it[0] != "J" }.map { it[2] }
    } catch (e: Throwable) { emptyList() }.let { l -> if (l.size == 114) l else List(114) { (it + 1).toString() } }

    /** The other apps of the TV, so the home screen can stand in for the launcher of the box. */
    private var focusApp: String? = null                         // the app to stand on again after the row is rebuilt
    private fun saved(key: String) = (sp.getString(key, "") ?: "").split(',').filter { it.isNotEmpty() }

    /** The other apps of the TV, in the order the family chose (YouTube first until then). Holding the middle button on one arranges it. */
    private fun appTiles(): List<View> {
        val out = ArrayList<View>()
        try {
            val pm = packageManager; val info = LinkedHashMap<String, Triple<String, android.graphics.drawable.Drawable?, Intent>>()
            for (cat in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER))
                for (r in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) { val pkg = r.activityInfo.packageName
                    if (pkg == packageName || pkg in info) continue
                    val icon = try { r.activityInfo.loadBanner(pm) ?: r.activityInfo.applicationInfo.loadBanner(pm) ?: r.loadIcon(pm) } catch (e: Throwable) { null }
                    val go = (if (cat == Intent.CATEGORY_LEANBACK_LAUNCHER) pm.getLeanbackLaunchIntentForPackage(pkg) else null) ?: pm.getLaunchIntentForPackage(pkg) ?: continue
                    info[pkg] = Triple(r.loadLabel(pm).toString(), icon, go) }
            val shown = Daily.appOrder(info.map { it.key to it.value.first }, saved("apps"), saved("apps_hidden").toSet())
            for (pkg in shown.take(40)) { val a = info[pkg] ?: continue
                val t = tile(a.first, "", 0xFF16323A.toInt(), 300f, 200f, false, a.second) { try { startActivity(a.third) } catch (e: Throwable) { } }
                t.tag = pkg; t.isLongClickable = true
                t.setOnLongClickListener { arrange(pkg, a.first, shown); true }
                out.add(t) }
        } catch (e: Throwable) { }
        return out
    }

    private fun arrange(pkg: String, title: String, shown: List<String>) {
        val hidden = saved("apps_hidden")
        val items = arrayListOf(getString(R.string.home_app_first), getString(R.string.home_app_earlier), getString(R.string.home_app_later), getString(R.string.home_app_hide))
        if (hidden.isNotEmpty()) items.add(getString(R.string.home_app_show_all))
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title).setItems(items.toTypedArray()) { _, which ->
            val e = sp.edit()
            when (which) {
                0, 1, 2 -> e.putString("apps", Daily.moveApp(shown, pkg, arrayOf("first", "earlier", "later")[which]).joinToString(","))
                3 -> { e.putString("apps", shown.joinToString(",")); e.putString("apps_hidden", (hidden + pkg).distinct().joinToString(",")) }
                else -> e.putString("apps_hidden", "")
            }
            e.apply(); focusApp = if (which == 3) null else pkg; build()
        }.show()
    }

    // ---- the clock and the prayer times ----
    private fun clock12(mins: Int): String { val h = (mins / 60) % 24; return (if (h % 12 == 0) 12 else h % 12).toString() + ":" + (mins % 60).toString().padStart(2, '0') }

    private fun hijri(cal: Calendar): String = try {
        val loc = android.icu.util.ULocale(resources.configuration.locales[0].language + "@calendar=islamic-umalqura")
        val f = android.icu.text.SimpleDateFormat("d MMMM y", loc)
        f.calendar = android.icu.util.Calendar.getInstance(loc); f.timeZone = android.icu.util.TimeZone.getTimeZone("GMT" + (if (cal.timeZone.rawOffset < 0) "-" else "+") + Math.abs(cal.timeZone.rawOffset) / 3600000 + ":" + (Math.abs(cal.timeZone.rawOffset) / 60000 % 60).toString().padStart(2, '0')); f.format(cal.time)
    } catch (e: Throwable) { "" }

    private fun refresh() {
        val cal = TvClock.now(); val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        clock.text = clock12(now)
        val day = java.text.SimpleDateFormat("EEEE d MMMM", resources.configuration.locales[0]).apply { timeZone = cal.timeZone }.format(cal.time)
        val h = hijri(cal)
        dates.text = if (h.isEmpty()) day else "$day  •  $h"
        val today = prayerMinutes(sp, cal)
        val tom = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val nx = Daily.next(now, today, prayerMinutes(sp, tom)[0])
        val names = intArrayOf(R.string.fajr, R.string.dhuhr, R.string.asr, R.string.maghrib, R.string.isha)
        fun name(i: Int) = if (i == 1 && cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) getString(R.string.jumuah) else getString(names[i])
        fetchWeather(); showWeather()
        nextUp.text = str(R.string.home_next, name(nx[0]), "" + nx[1] / 60 + ":" + (nx[1] % 60).toString().padStart(2, '0'))
        times.removeAllViews()
        for (i in 0..4) { val on = i == nx[0] && today[i] > now
            val chip = label(name(i) + "  " + clock12(today[i]), 28f, if (on) Ui.INK else Color.argb(230, 255, 255, 255))
            chip.background = GradientDrawable().apply { cornerRadius = 40f * k; setColor(if (on) Ui.GOLD else Color.argb(110, 0, 20, 26)) }
            chip.setPadding(px(26f), px(10f), px(26f), px(10f))
            times.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = px(14f) }) }
        if ((cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) != friday && extra == null) build()        // the day changed while the screen was open
    }

    // ---- the weather for the chosen city ----
    private fun showWeather() {
        val w = Weather.parse(sp.getString("weather", "") ?: "")
        // a forecast older than three hours says nothing about now
        if (w == null || System.currentTimeMillis() - sp.getLong("weather_at", 0) > 3 * 3600000L) { weather.text = ""; return }
        val words = resources.getStringArray(R.array.weather)
        weather.text = Weather.icon(w.code, w.day) + "  " + str(R.string.weather_now, w.temp, words[Weather.kind(w.code)], w.high, w.low)
    }
    private fun fetchWeather() {
        val lat = sp.getFloat("lat", 31.9539f).toDouble(); val lon = sp.getFloat("lon", 35.9106f).toDouble(); val place = "$lat,$lon"
        val now = android.os.SystemClock.elapsedRealtime()
        if (place == weatherFor && weatherAt != 0L && now - weatherAt < 30 * 60000L) return
        weatherFor = place; weatherAt = now
        thread {
            try { val c = java.net.URL(Weather.url(lat, lon)).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 10000
                try { val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    if (Weather.parse(body) != null) { sp.edit().putString("weather", body).putLong("weather_at", System.currentTimeMillis()).apply(); ui.post { showWeather() } } } finally { c.disconnect() }
            } catch (e: Throwable) { weatherAt = now - 25 * 60000L }      // no answer: try again in five minutes
        }
    }

    // ---- lists and reading pages over the home screen ----
    private fun showExtra(v: View) {
        closeExtra(); lastFocus = currentFocus
        home.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        extra = v; root.addView(v, FrameLayout.LayoutParams(-1, -1))
    }
    private fun closeExtra() {
        audio.stop(); page = null
        extra?.let { root.removeView(it) }; extra = null
        home.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        lastFocus?.requestFocus(); lastFocus = null
    }
    override fun onBackPressed() { if (extra != null) closeExtra() }      // a home screen has nowhere to go back to

    private fun panel(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF06262B.toInt(), 0xFF0A2F36.toInt())) }

    private fun openSurahList() {
        val names = surahNames()
        val box = panel()
        box.addView(label(getString(R.string.home_all_surahs), 50f, Ui.GOLD).apply { setPadding(px(70f), px(34f), px(70f), px(8f)) })
        val grid = GridLayout(this); grid.columnCount = 6; grid.setPadding(px(50f), 0, px(50f), px(50f)); grid.clipChildren = false
        var first: View? = null
        for (n in 1..114) { val t = tile(names[n - 1], n.toString(), 0xFF0F6B63.toInt(), 270f, 130f, true) { openSurah(n) }
            if (first == null) first = t
            grid.addView(t, GridLayout.LayoutParams().apply { width = px(270f); height = px(130f); setMargins(px(15f), px(14f), px(15f), px(14f)) }) }
        val sc = ScrollView(this); sc.isVerticalScrollBarEnabled = false; sc.clipChildren = false; sc.addView(grid)
        box.addView(sc)
        showExtra(box); first?.requestFocus()
    }

    private fun openSurah(n: Int) = openPages(surahNames()[n - 1], { Daily.surah(it, n) }, true)
    private fun openRuqyah() = openPages(getString(R.string.home_ruqyah), { Daily.ranges(it, Daily.RUQYAH) }, true)
    private fun openAdhkar(evening: Boolean) = openPages(getString(if (evening) R.string.home_evening else R.string.home_morning), { Daily.adhkar(adhkarJson, it, evening) }, false)

    private fun openPages(title: String, make: (Quran) -> List<Daily.Page>, recite: Boolean) {
        val q = quran ?: return
        val pages = try { make(q) } catch (e: Throwable) { emptyList() }
        if (pages.isEmpty()) return
        val v = PageView(this, title, pages, recite, audio)
        showExtra(v); page = v; v.isFocusable = true; v.isFocusableInTouchMode = true; v.requestFocus(); v.start()
    }

    private fun showPhone() {
        var ip = "?"
        try { for (ni in java.net.NetworkInterface.getNetworkInterfaces()) for (a in ni.inetAddresses) if (!a.isLoopbackAddress && a is java.net.Inet4Address) ip = a.hostAddress ?: ip } catch (e: Throwable) { }
        val box = panel(); box.gravity = Gravity.CENTER
        box.addView(label(getString(R.string.home_phone), 60f, Ui.GOLD).apply { gravity = Gravity.CENTER })
        box.addView(label(getString(R.string.home_phone_how), 36f).apply { gravity = Gravity.CENTER; setPadding(px(120f), px(30f), px(120f), px(30f)) })
        box.addView(label("http://$ip:8765", 80f).apply { gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR })
        val ok = tile(getString(R.string.home_back), "", 0xFF5B3FA8.toInt(), 300f, 110f) { closeExtra() }
        box.addView(ok, LinearLayout.LayoutParams(px(300f), px(110f)).apply { topMargin = px(50f) })
        showExtra(box); ok.requestFocus()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val p = page
        if (p != null && e.action == KeyEvent.ACTION_DOWN && p.key(e.keyCode)) return true
        return super.dispatchKeyEvent(e)
    }
}

/**
 * One page of reading at a time: an ayah with its recitation, or a dhikr with the number of times it is said.
 * Left goes on and right goes back, as the pages of a mushaf turn; the middle button pauses, or counts a dhikr.
 */
class PageView(c: Context, private val title: String, private val pages: List<Daily.Page>, private val recite: Boolean, private val audio: AyahAudio) : View(c) {
    var at = 0; private set
    private var said = 0
    private var paused = false
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    fun start() { show(0) }

    private fun url(pg: Daily.Page) = Hifz.url(1, pg.surah, pg.ayah)
    private fun show(i: Int) {
        at = i.coerceIn(0, pages.size - 1); said = 0; paused = false; invalidate()
        val pg = pages[at]
        if (!recite || pg.surah == 0) { audio.stop(); return }
        pages.getOrNull(at + 1)?.let { if (it.surah != 0) audio.prepare(url(it)) }
        val mine = at
        audio.onError = { invalidate() }
        audio.play(url(pg)) { if (mine == at && !paused) {
            if (audio.error.isNotEmpty()) invalidate()               // stay on the ayah that could not be played
            else if (at < pages.size - 1) show(at + 1) else { paused = true; invalidate() } } }
    }

    /** Returns true when the key was used. */
    fun key(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_DPAD_DOWN -> { if (at < pages.size - 1) show(at + 1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_DPAD_UP -> { if (at > 0) show(at - 1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (recite) { if (audio.error.isNotEmpty() || (paused && at == pages.size - 1)) show(if (audio.error.isNotEmpty()) at else 0)
                    else { paused = !paused; if (paused) audio.pause() else audio.resume() } }
                else { said++; if (said >= pages[at].count && at < pages.size - 1) show(at + 1) }
                invalidate(); return true }
        }
        return false
    }

    private fun para(cv: Canvas, t: String, face: Typeface, top: Float, maxH: Float, size: Float, min: Float, color: Int, spacing: Float): Float {
        val w = (width * 0.86f).toInt(); var sz = size; var lay: StaticLayout
        while (true) { tp.typeface = face; tp.textSize = sz; tp.color = color
            lay = StaticLayout.Builder.obtain(t, 0, t.length, tp, w).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, spacing).build()
            if (lay.height <= maxH || sz <= min) break
            sz *= 0.93f }
        cv.save(); cv.translate((width - w) / 2f, top + maxOf(0f, (maxH - lay.height) / 2)); lay.draw(cv); cv.restore()
        return lay.height.toFloat()
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val k = h / 1080f
        p.color = Color.BLACK; cv.drawRect(0f, 0f, w, h, p)          // nothing of the home screen may show through the page
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xFF06262B.toInt(), 0xFF0A2F36.toInt(), Shader.TileMode.CLAMP); cv.drawRect(0f, 0f, w, h, p); p.shader = null
        val pg = pages[at]; val ctx = context
        Ui.text(cv, this, title, w / 2, 96f * k, 56f * k, Ui.GOLD, false)
        val sub = if (pg.quran && pg.label.isNotEmpty() && pg.surah != 0) pg.label else "" + (at + 1) + " / " + pages.size
        Ui.text(cv, this, sub, w / 2, 150f * k, 30f * k, Color.argb(210, 190, 235, 240), false)
        p.color = Color.argb(70, 255, 214, 10); cv.drawRect(w * 0.2f, 176f * k, w * 0.8f, 178f * k, p)

        var top = 200f * k; val foot = if (recite) 120f * k else 250f * k
        if (pg.quran && pg.surah == 0 && pg.label.isNotEmpty()) top += para(cv, pg.label, Ui.font(ctx), top, 70f * k, 36f * k, 24f * k, Color.argb(220, 255, 255, 255), 1f) + 10f * k
        if (pg.quran) para(cv, pg.text, Ui.quranFont(ctx), top, h - top - foot, 96f * k, 30f * k, Color.WHITE, 0.82f)
        else para(cv, pg.text, Ui.font(ctx), top, h - top - foot, 64f * k, 28f * k, Color.WHITE, 1.05f)

        if (recite) {
            val msg = when { audio.error.isNotEmpty() -> ctx.getString(R.string.hz_no_sound) + "  " + audio.error
                paused && at == pages.size - 1 -> ctx.getString(R.string.home_done)
                paused -> ctx.getString(R.string.home_paused); else -> ctx.getString(R.string.home_keys) }
            Ui.text(cv, this, msg, w / 2, h - 50f * k, 28f * k, if (audio.error.isNotEmpty()) Color.argb(255, 255, 150, 130) else Color.argb(170, 255, 255, 255), false)
        } else {
            if (pg.note.isNotEmpty()) para(cv, pg.note, Ui.font(ctx), h - 240f * k, 90f * k, 28f * k, 18f * k, Color.argb(200, 190, 235, 240), 1f)
            val chip = if (pg.count > 1) ctx.str(R.string.home_count, minOf(said, pg.count), pg.count) else ctx.getString(R.string.home_once)
            val tw = Ui.width(this, chip, 36f * k) + 80f * k
            p.color = if (said >= pg.count) 0xFF1F9D46.toInt() else Ui.GOLD
            cv.drawRoundRect(RectF(w / 2 - tw / 2, h - 132f * k, w / 2 + tw / 2, h - 66f * k), 33f * k, 33f * k, p)
            Ui.text(cv, this, chip, w / 2, h - 86f * k, 36f * k, Ui.INK, false)
            Ui.text(cv, this, ctx.getString(R.string.home_keys_dhikr), w / 2, h - 24f * k, 24f * k, Color.argb(150, 255, 255, 255), false)
        }
    }
}
