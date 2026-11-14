package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

/** The vocabulary space race on the TV: lobby, countdown, lanes, rockets, podium and the review board. */
class SpaceGame(private val view: View) {
    private fun s(id: Int, vararg a: Any) = view.context.str(id, *a)
    companion object { const val OFF = 0; const val LOBBY = 1; const val COUNT = 2; const val PLAY = 3; const val END = 5
        const val GONE_MS = 8000L; const val ROCKETS = 5
        val COLORS = intArrayOf(0xFFFF5A4F.toInt(), 0xFF2F80FF.toInt(), 0xFFFFD60A.toInt(), 0xFF34C759.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF9F0A.toInt())
        val CATEGORIES = intArrayOf(R.string.cat_all, R.string.cat_animals, R.string.cat_verbs, R.string.cat_school, R.string.cat_food, R.string.cat_adjectives,
            R.string.cat_home, R.string.cat_jobs, R.string.cat_time, R.string.cat_space, R.string.cat_master) }
    var sfx: Sfx? = null
    /** Says an English word aloud on the TV, when the device has a speech engine. */
    var speak: ((String) -> Unit)? = null
    private val core = SpaceCore()
    private var state = OFF
    private var stateAt = 0L; private var countShown = -1; private var last = 0L
    private val shown = HashMap<String, Float>()               // drawn progress of each rocket; glides towards the real one
    private val seen = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var boostsSeen = 0; private var spoken = -1
    val active get() = state != OFF

    /** The word bank, read once from assets/data/words_bank.json: id -> English, Arabic. */
    private val words: Map<Int, Pair<String, String>> by lazy {
        val m = HashMap<Int, Pair<String, String>>()
        try { val a = org.json.JSONArray(view.context.assets.open("data/words_bank.json").use { it.readBytes().toString(Charsets.UTF_8) })
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); m[o.getInt("id")] = Pair(o.getString("en"), o.getString("ar")) } } catch (e: Throwable) { }
        m
    }
    private val stars = java.util.Random(11).let { r -> Array(150) { floatArrayOf(r.nextFloat(), r.nextFloat(), 1f + r.nextFloat() * 2.4f, r.nextFloat() * 6f) } }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private fun w() = view.width.toFloat()
    private fun h() = view.height.toFloat()
    private fun sc() = (if (view.height > 0) view.height else 1080) / 1080f

    fun stopNow() { state = OFF; core.racers.clear(); seen.clear(); shown.clear(); core.reset() }

    /** state|galaxy|words|robo mode|robo speed|players|in race|my progress|turbo|streak|my place|did I win|rocket */
    fun status(cid: String): String = synchronized(core) {
        seen[cid] = SystemClock.uptimeMillis()
        val me = core.racers[cid]
        "" + state + "|" + core.category + "|" + core.length + "|" + core.roboMode + "|" + core.roboSpeed + "|" + core.humans + "|" + (if (me != null) 1 else 0) +
            "|" + Math.round(me?.progress ?: 0f) + "|" + (if (me?.turbo == true) 1 else 0) + "|" + (me?.streak ?: 0) + "|" + (if (me == null) 0 else core.ranking().indexOf(me) + 1) +
            "|" + (if (!core.over || me == null) -1 else if (core.winner === me) 1 else 0) + "|" + (me?.rocket ?: 0)
    }

    /** "sr join NAME ROCKET MASTERED", "sr leave", "sr start", "sr stop", "sr cat n", "sr len n", "sr robo n", "sr speed n", "sr rocket n", "sr hit WORD MS GOLD", "sr miss WORD" */
    fun command(pp: List<String>, cid: String) = synchronized(core) {
        val now = SystemClock.uptimeMillis(); seen[cid] = now
        val idle = state == LOBBY || state == END
        when (pp[1]) {
            "join" -> { val name = java.net.URLDecoder.decode(pp.getOrElse(2) { "" }, "UTF-8").trim().take(14)
                if (core.join(cid, name, pp.getOrElse(3) { "0" }.toInt().coerceIn(0, ROCKETS - 1), pp.getOrElse(4) { "0" }.toInt().coerceIn(0, 100000))) {
                    if (state == OFF) { state = LOBBY; stateAt = now; last = now }
                    if (state == LOBBY) core.reset() } }
            "leave" -> { core.leave(cid); shown.remove(cid); if (core.humans == 0) stopNow() else if (state == LOBBY) core.reset() }
            "start" -> if (idle && core.humans > 0) { core.reset(); shown.clear(); boostsSeen = 0; spoken = -1; state = COUNT; stateAt = now; countShown = -1 }
            "cat" -> if (idle) core.category = pp[2].toInt().coerceIn(0, 10)
            "len" -> if (idle) core.length = pp[2].toInt().let { if (it == 15 || it == 20) it else 10 }
            "robo" -> if (idle) { core.roboMode = pp[2].toInt().coerceIn(0, 2); if (state == LOBBY) core.reset() }
            "speed" -> if (idle) core.roboSpeed = pp[2].toInt().coerceIn(0, 2)
            "rocket" -> core.racers[cid]?.rocket = pp[2].toInt().coerceIn(0, ROCKETS - 1)
            "hit" -> if (state == PLAY) { core.hit(cid, pp.getOrElse(3) { "9999" }.toInt(), pp.getOrElse(4) { "0" } == "1"); sfx?.play("swish", 0.4f) }
            "miss" -> if (state == PLAY) core.miss(cid, pp.getOrElse(2) { "0" }.toInt())
            "stop" -> stopNow()
        }
        view.invalidate()
    }

    private fun review() = core.missed.mapNotNull { words[it] }.take(8)

    private fun update(now: Long) = synchronized(core) {
        val dt = ((now - last) / 1000f).coerceIn(0f, 0.1f); last = now
        val gone = core.racers.values.filter { !it.robot && now - (seen[it.cid] ?: now) > GONE_MS }.map { it.cid }
        if (gone.isNotEmpty()) { gone.forEach { core.leave(it); seen.remove(it); shown.remove(it) }; if (core.humans == 0) { stopNow(); return } }
        when (state) {
            COUNT -> { val n = 3 - ((now - stateAt) / 1000).toInt()
                if (n != countShown && n >= 0) { countShown = n; sfx?.play("blip", 0.7f) }
                if (now - stateAt >= 3000) { state = PLAY; sfx?.play("blip", 1f) } }
            PLAY -> { core.step(dt)
                if (core.boosts != boostsSeen) { boostsSeen = core.boosts; sfx?.play("thump", 0.6f) }
                if (core.over) { state = END; stateAt = now; sfx?.play("chime", 1f) } }
            END -> { val i = ((now - stateAt - 2500) / 2200).toInt()          // read the review words aloud, one at a time
                val list = review()
                if (now - stateAt >= 2500 && i != spoken && i < list.size) { spoken = i; speak?.invoke(list[i].first) }
                if (now - stateAt > 600000) stopNow() }
        }
        for (r in core.racers.values) { val cur = shown[r.cid] ?: 0f; shown[r.cid] = cur + (r.progress - cur) * (1f - Math.pow(0.004, dt.toDouble()).toFloat()) }
    }

    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = true, align: Paint.Align = Paint.Align.CENTER) {
        tx.textSize = size; tx.textAlign = align
        if (outline) { tx.style = Paint.Style.STROKE; tx.strokeWidth = size * 0.14f; tx.color = Color.argb(220, 0, 0, 0); tx.strokeJoin = Paint.Join.ROUND; cv.drawText(t, x, y, tx) }
        tx.style = Paint.Style.FILL; tx.color = color; cv.drawText(t, x, y, tx); tx.textAlign = Paint.Align.CENTER
    }

    /** A spacecraft pointing right, about 150 units long. design: 0 rocket, 1 golden rocket, 2 flying saucer, 3 shuttle, 4 fire rocket, -1 Robo-X. */
    private fun craft(cv: Canvas, x: Float, y: Float, k: Float, color: Int, design: Int, burning: Boolean, turbo: Boolean, now: Long) {
        p.style = Paint.Style.FILL; p.shader = null
        if (burning && design != 2) {                                        // exhaust flame; blue while the turbo is ready
            val len = (design.let { if (it == 4) 95f else 62f } + 14f * sin(now / 45.0 + x).toFloat()) * k * (if (turbo) 1.5f else 1f)
            p.color = if (turbo) Color.rgb(64, 180, 255) else Color.rgb(255, 150, 30)
            val f = Path(); f.moveTo(x - 62f * k, y - 17f * k); f.lineTo(x - 62f * k - len, y); f.lineTo(x - 62f * k, y + 17f * k); f.close(); cv.drawPath(f, p)
            p.color = if (turbo) Color.WHITE else Color.rgb(255, 228, 120)
            val g = Path(); g.moveTo(x - 62f * k, y - 9f * k); g.lineTo(x - 62f * k - len * 0.55f, y); g.lineTo(x - 62f * k, y + 9f * k); g.close(); cv.drawPath(g, p)
        }
        when (design) {
            2 -> { if (burning) { p.color = if (turbo) Color.argb(150, 64, 180, 255) else Color.argb(120, 150, 255, 150); cv.drawOval(RectF(x - 100f * k, y + 2f * k, x - 40f * k, y + 26f * k), p) }
                p.color = Color.rgb(150, 220, 255); cv.drawOval(RectF(x - 36f * k, y - 44f * k, x + 36f * k, y + 10f * k), p)          // dome
                p.color = Color.rgb(170, 176, 190); cv.drawOval(RectF(x - 80f * k, y - 14f * k, x + 80f * k, y + 26f * k), p)          // saucer
                p.color = color; for (i in -2..2) cv.drawCircle(x + i * 28f * k, y + 6f * k, 6.5f * k, p) }
            3 -> { p.color = Color.rgb(40, 44, 52); val wing = Path(); wing.moveTo(x - 60f * k, y); wing.lineTo(x - 74f * k, y - 48f * k); wing.lineTo(x - 30f * k, y - 48f * k); wing.lineTo(x + 20f * k, y); wing.lineTo(x - 30f * k, y + 48f * k); wing.lineTo(x - 74f * k, y + 48f * k); wing.close(); cv.drawPath(wing, p)
                p.color = Color.rgb(240, 242, 246); cv.drawRoundRect(RectF(x - 66f * k, y - 22f * k, x + 50f * k, y + 22f * k), 20f * k, 20f * k, p)
                p.color = Color.rgb(40, 44, 52); val nose = Path(); nose.moveTo(x + 44f * k, y - 22f * k); nose.quadTo(x + 92f * k, y, x + 44f * k, y + 22f * k); nose.close(); cv.drawPath(nose, p)
                p.color = color; cv.drawRect(x - 40f * k, y - 22f * k, x - 26f * k, y + 22f * k, p)
                p.color = Color.rgb(90, 190, 255); cv.drawRoundRect(RectF(x + 14f * k, y - 12f * k, x + 40f * k, y + 2f * k), 6f * k, 6f * k, p) }
            -1 -> { p.color = Color.rgb(120, 128, 140); cv.drawRoundRect(RectF(x - 62f * k, y - 30f * k, x + 40f * k, y + 30f * k), 12f * k, 12f * k, p)
                val nose = Path(); nose.moveTo(x + 40f * k, y - 30f * k); nose.lineTo(x + 84f * k, y); nose.lineTo(x + 40f * k, y + 30f * k); nose.close(); cv.drawPath(nose, p)
                p.color = Color.rgb(60, 66, 76); cv.drawRect(x - 70f * k, y - 40f * k, x - 46f * k, y + 40f * k, p)
                p.color = Color.rgb(255, 80, 70); cv.drawCircle(x + 2f * k, y - 6f * k, 9f * k, p); cv.drawCircle(x + 28f * k, y - 6f * k, 9f * k, p)
                p.color = Color.rgb(230, 232, 238); cv.drawRect(x - 4f * k, y + 12f * k, x + 34f * k, y + 18f * k, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 4f * k; p.color = Color.rgb(200, 204, 212); cv.drawLine(x - 20f * k, y - 30f * k, x - 28f * k, y - 52f * k, p); p.style = Paint.Style.FILL
                p.color = Color.rgb(255, 214, 10); cv.drawCircle(x - 28f * k, y - 54f * k, 6f * k, p) }
            else -> { val body = when (design) { 1 -> Color.rgb(246, 196, 40); 4 -> Color.rgb(214, 44, 36); else -> Color.rgb(236, 238, 244) }
                val trim = when (design) { 1 -> Color.rgb(176, 124, 12); 4 -> Color.rgb(255, 170, 30); else -> color }
                p.color = trim; val fins = Path(); fins.moveTo(x - 30f * k, y); fins.lineTo(x - 78f * k, y - 46f * k); fins.lineTo(x - 54f * k, y - 46f * k); fins.lineTo(x - 6f * k, y - 20f * k); fins.lineTo(x - 6f * k, y + 20f * k); fins.lineTo(x - 54f * k, y + 46f * k); fins.lineTo(x - 78f * k, y + 46f * k); fins.close(); cv.drawPath(fins, p)
                p.color = body; cv.drawRoundRect(RectF(x - 64f * k, y - 24f * k, x + 40f * k, y + 24f * k), 16f * k, 16f * k, p)
                p.color = trim; val nose = Path(); nose.moveTo(x + 34f * k, y - 24f * k); nose.quadTo(x + 100f * k, y, x + 34f * k, y + 24f * k); nose.close(); cv.drawPath(nose, p)
                if (design == 1) { p.color = Color.argb(150, 255, 255, 255); cv.drawRoundRect(RectF(x - 54f * k, y - 18f * k, x + 30f * k, y - 10f * k), 4f * k, 4f * k, p) }
                p.color = if (design == 0) Color.rgb(40, 44, 52) else color; cv.drawCircle(x + 6f * k, y, 13f * k, p)
                p.color = Color.rgb(140, 214, 255); cv.drawCircle(x + 6f * k, y, 8.5f * k, p) }
        }
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        val w = w(); val h = h(); val k = sc()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w, h, Color.rgb(8, 10, 34), Color.rgb(38, 14, 62), Shader.TileMode.CLAMP); cv.drawRect(0f, 0f, w, h, p); p.shader = null
        for (st in stars) { p.color = Color.argb((150 + 100 * sin(now / 500.0 + st[3])).toInt().coerceIn(40, 255), 255, 255, 255); cv.drawCircle(st[0] * w, st[1] * h, st[2] * k, p) }
        // the planet every rocket is flying to
        val px = w * 0.985f; val py = h * 0.56f; val pr = 210f * k
        p.shader = RadialGradient(px - pr * 0.4f, py - pr * 0.4f, pr * 1.6f, Color.rgb(255, 190, 110), Color.rgb(170, 70, 60), Shader.TileMode.CLAMP); cv.drawCircle(px, py, pr, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 14f * k; p.color = Color.argb(170, 255, 226, 170); cv.drawOval(RectF(px - pr * 1.5f, py - pr * 0.3f, px + pr * 1.5f, py + pr * 0.3f), p); p.style = Paint.Style.FILL
        val racers = core.racers.values.toList()
        val x0 = w * 0.1f; val x1 = w * 0.8f
        val top = h * 0.17f; val bottom = h * 0.8f
        val lane = ((bottom - top) / maxOf(racers.size, 1)).coerceAtMost(150f * k)
        val size = (lane / (150f * k)).coerceIn(0.62f, 1f) * k
        val first = (top + bottom) / 2 - lane * racers.size / 2 + lane / 2
        p.color = Color.argb(200, 255, 255, 255); cv.drawRect(x0 - 3f * k, first - lane / 2, x0 + 3f * k, first + lane * (racers.size - 0.5f), p)
        var human = 0
        racers.forEachIndexed { i, r ->
            val y = first + i * lane
            val color = if (r.robot) Color.rgb(170, 176, 190) else COLORS[human++ % COLORS.size]
            p.color = Color.argb(70, 255, 255, 255); var dx = x0; while (dx < x1 + 90f * k) { cv.drawRect(dx, y - 1.5f * k, dx + 18f * k, y + 1.5f * k, p); dx += 40f * k }
            val prog = (shown[r.cid] ?: 0f).coerceIn(0f, 100f)
            val x = x0 + (x1 - x0) * prog / 100f
            craft(cv, x, y, size, color, if (r.robot) -1 else r.rocket, state == PLAY || state == COUNT, r.turbo, now)
            text(cv, r.name, x - 6f * size, y - 46f * size, 30f * size, color)
            text(cv, "" + Math.round(r.progress) + "%", x + 118f * size, y + 12f * size, 32f * size, Color.WHITE, true, Paint.Align.LEFT)
        }
        val galaxy = s(CATEGORIES[core.category.coerceIn(0, 10)])
        if (state == PLAY || state == COUNT) text(cv, "🚀 " + s(R.string.space_title) + "  •  " + galaxy, w / 2, 76f * k, 50f * k, Color.WHITE)
        when (state) {
            LOBBY -> { p.color = Color.argb(150, 0, 0, 0); cv.drawRect(0f, 0f, w, h * 0.17f, p); cv.drawRect(0f, h * 0.8f, w, h, p)
                text(cv, "🚀 " + s(R.string.space_title), w / 2, 100f * k, 84f * k, Color.WHITE)
                var hi = 0; racers.forEachIndexed { i, r -> if (!r.robot) { text(cv, "⭐ " + r.mastered + " / " + maxOf(words.size, 1000), w * 0.5f, first + i * lane + 12f * size, 34f * size, COLORS[hi++ % COLORS.size], true, Paint.Align.LEFT) } }
                val robo = s(if (core.racers[SpaceCore.ROBOT] == null) R.string.sr_robo_off else intArrayOf(R.string.lvl_easy, R.string.lvl_medium, R.string.lvl_hard)[core.roboSpeed])
                text(cv, s(R.string.sr_settings, galaxy, core.length, robo), w / 2, h * 0.865f, 42f * k, Color.WHITE)
                text(cv, s(R.string.press_start), w / 2, h * 0.865f + 58f * k, 42f * k, 0xFFFFD60A.toInt())
                text(cv, s(R.string.sr_join_hint), w / 2, h * 0.865f + 106f * k, 30f * k, Color.WHITE) }
            COUNT -> { val n = 3 - ((now - stateAt) / 1000).toInt(); text(cv, "" + n.coerceAtLeast(1), w / 2, h * 0.5f, 300f * k, Color.WHITE) }
            END -> { cv.drawColor(Color.argb(185, 0, 0, 0))
                text(cv, s(R.string.wins_fmt, core.winner?.name ?: ""), w / 2, h * 0.16f, 104f * k, 0xFFFFD60A.toInt())
                text(cv, core.ranking().take(4).mapIndexed { i, r -> "" + (i + 1) + ". " + r.name + " " + Math.round(r.progress) + "%" }.joinToString("    "), w / 2, h * 0.16f + 74f * k, 40f * k, Color.WHITE)
                val list = review()
                text(cv, s(if (list.isEmpty()) R.string.sr_perfect else R.string.sr_review), w / 2, h * 0.38f, 56f * k, if (list.isEmpty()) 0xFF7FD18B.toInt() else Color.WHITE)
                list.forEachIndexed { i, wd -> val col = i % 2; val row = i / 2
                    val cx = w * (if (col == 0) 0.27f else 0.73f); val cy = h * 0.5f + row * 104f * k
                    p.color = if (i == spoken) Color.argb(255, 60, 110, 220) else Color.argb(235, 34, 38, 50); cv.drawRoundRect(RectF(cx - 380f * k, cy - 62f * k, cx + 380f * k, cy + 28f * k), 20f * k, 20f * k, p)
                    text(cv, wd.first, cx - 190f * k, cy, 46f * k, Color.WHITE, false); text(cv, wd.second, cx + 190f * k, cy, 46f * k, 0xFFFFD60A.toInt(), false) } }
        }
        return true
    }
}
