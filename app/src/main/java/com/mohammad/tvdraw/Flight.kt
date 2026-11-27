package com.mohammad.tvdraw

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import org.json.JSONObject

/** The flight simulator's link between the phone and the TV's 3D page: the latest stick and throttle, what the page reports back, and the map keys.
    The page itself (assets/fly) does all the flying; this only carries messages. */
object Flight {
    const val PLACES = 6
    @Volatile var port = 0                       // the app's own web server, a second way to serve the page
    @Volatile private var sound = true
    const val STALE_MS = 1200L                  // a phone that has been silent this long is no longer holding the stick
    @Volatile var active = false; private set
    @Volatile private var x = 0; @Volatile private var y = 0; @Volatile private var thr = -1
    @Volatile private var level = 0; @Volatile private var goSeq = 0; @Volatile private var goIdx = 1; @Volatile private var menu = false
    @Volatile private var gear = -1; @Volatile private var flaps = -1; @Volatile private var brake = false      // -1: the phone has not chosen, so the page decides
    @Volatile private var inputAt = 0L; @Volatile private var owner: String? = null
    @Volatile private var report = ""; @Volatile private var reportAt = 0L

    private fun sp(c: Context) = c.getSharedPreferences("fly", 0)
    private fun now() = SystemClock.uptimeMillis()

    /** "fl on [place]", "fl off", "fl c X Y THROTTLE" (hundredths), "fl lvl", "fl go n", "fl menu 0|1", "fl gear 0|1", "fl flap 0..2", "fl brk 0|1", "fl snd 0|1", "fl q 0..2", "fl eng 0..2", "fl key g|i VALUE". */
    fun command(c: Context, p: List<String>, cid: String) {
        when (p.getOrNull(1)) {
            "on" -> { x = 0; y = 0; thr = -1; menu = false; gear = -1; flaps = -1; brake = false; owner = cid; inputAt = 0L; report = ""; reportAt = 0L
                p.getOrNull(2)?.toIntOrNull()?.let { goIdx = it.coerceIn(0, PLACES - 1) }
                if (!active) { active = true; try { c.startActivity(Intent(c, FlightActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { active = false; report = "err|start" } } }
            "off" -> stop()
            "c" -> if (p.size >= 5 && pilot(cid)) { x = (p[2].toIntOrNull() ?: 0).coerceIn(-100, 100); y = (p[3].toIntOrNull() ?: 0).coerceIn(-100, 100); thr = (p[4].toIntOrNull() ?: 0).coerceIn(0, 100); inputAt = now() }
            "lvl" -> if (pilot(cid)) level++
            "go" -> if (pilot(cid)) { goIdx = (p.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(0, PLACES - 1); goSeq++; menu = false; gear = -1; flaps = -1; brake = false }      // a new place sets its own wheels and flaps
            "gear" -> if (pilot(cid)) gear = if (p.getOrNull(2) == "1") 1 else 0
            "flap" -> if (pilot(cid)) flaps = (p.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 2)
            "brk" -> if (pilot(cid)) brake = p.getOrNull(2) == "1"
            "snd" -> sp(c).edit().putBoolean("snd", p.getOrNull(2) != "0").apply().also { sound = p.getOrNull(2) != "0" }
            "menu" -> if (pilot(cid)) menu = p.getOrNull(2) == "1"
            "q" -> sp(c).edit().putInt("q", (p.getOrNull(2)?.toIntOrNull() ?: 1).coerceIn(0, 2)).apply()
            "eng" -> sp(c).edit().putInt("eng", (p.getOrNull(2)?.toIntOrNull() ?: 0).coerceIn(0, 2)).apply()      // 0: the TV chooses, 1: the light engine, 2: the full one
            "key" -> { val which = if (p.getOrNull(2) == "i") "ion" else "g"
                val v = try { java.net.URLDecoder.decode(p.getOrNull(3) ?: "", "UTF-8").trim() } catch (e: Throwable) { "" }
                // a key is letters, digits and a few signs; anything else is not one
                sp(c).edit().putString(which, if (v.length in 20..2000 && v.all { it.isLetterOrDigit() || it in "-_.~" }) v else "").apply() }
        }
    }
    /** One phone flies at a time; another may take over once the first has let go. */
    private fun pilot(cid: String): Boolean { if (!active) return false
        if (owner == cid) return true
        if (now() - inputAt > STALE_MS * 2) { owner = cid; return true }
        return false }
    /** The TV remote's volume keys move the throttle a step. */
    fun nudge(d: Int) { thr = ((if (thr < 0) 60 else thr) + d).coerceIn(0, 100) }
    fun stop() { active = false; owner = null; menu = false; brake = false }

    /** What the page asks for every frame: stick x, stick y, throttle (-1 while no phone has set it), then counters and flags. */
    fun poll(): String = "$x,$y,$thr,$level,$goSeq,$goIdx,${if (menu) 1 else 0},${if (active) 1 else 0},${if (now() - inputAt < STALE_MS) 1 else 0},$gear,$flaps,${if (brake) 1 else 0},${if (sound) 1 else 0}"
    fun tell(s: String) { report = s.take(480).filter { it >= ' ' && it != '&' }; reportAt = now() }

    /** Settings for the page. The keys go to the page and nowhere else. */
    fun conf(c: Context): String = JSONObject().also { sound = sp(c).getBoolean("snd", true) }.put("g", sp(c).getString("g", "")).put("ion", sp(c).getString("ion", "")).put("q", sp(c).getInt("q", 1)).put("eng", sp(c).getInt("eng", 0)).put("spot", goIdx)
        .put("lang", if (c.resources.configuration.locales[0].language == "ar") "ar" else "en").toString()
    /** For the phone. It learns whether a key is saved, never the key. */
    fun status(c: Context): String = "on=" + (if (active) 1 else 0) + "|g=" + (if (sp(c).getString("g", "").isNullOrEmpty()) 0 else 1) + "|i=" + (if (sp(c).getString("ion", "").isNullOrEmpty()) 0 else 1) +
        "|q=" + sp(c).getInt("q", 1) + "|eng=" + sp(c).getInt("eng", 0) + "|snd=" + (if (sp(c).getBoolean("snd", true)) 1 else 0) + "|rep=" + (if (now() - reportAt < 3000) report else "")
}
