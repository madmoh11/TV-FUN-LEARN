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
    var onEnd: ((List<GameResult>) -> Unit)? = null
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
    fun stopNow() { state = OFF; core.racers.clear(); seen.clear(); shown.clear(); jolt.clear(); lastProgress.clear(); core.reset() }

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
                if (core.over) { state = END; stateAt = now; sfx?.play("chime", 1f); onEnd?.invoke(core.racers.values.filter { !it.robot }.map { GameResult(it.cid, it === core.winner, words = it.correct) }) } }
            END -> { val i = ((now - stateAt - 2500) / 2200).toInt()          // read the review words aloud, one at a time
                val list = review()
                if (now - stateAt >= 2500 && i != spoken && i < list.size) { spoken = i; speak?.invoke(list[i].first) }
                if (now - stateAt > 600000) stopNow() }
        }
        for (r in core.racers.values) { val cur = shown[r.cid] ?: 0f; shown[r.cid] = cur + (r.progress - cur) * (1f - Math.pow(0.004, dt.toDouble()).toFloat()) }
    }

    // ---- drawing ----
    private val bg by lazy { Ui.bitmap(view.context, "space_bg.webp", true) }
    private val planet by lazy { Ui.bitmap(view.context, "planet.webp") }
    private val ships by lazy { Array(ROCKETS) { Ui.bitmap(view.context, "ship$it.webp") } }
    private val robo by lazy { Ui.bitmap(view.context, "robo.webp") }
    /** Small stars that drift past at three speeds, so space never stands still: x, y, size, speed, twinkle phase. */
    private val stars = java.util.Random(11).let { r -> Array(110) { val layer = it % 3; floatArrayOf(r.nextFloat(), r.nextFloat(), 1.2f + layer * 1.1f, 0.008f + layer * 0.014f, r.nextFloat() * 6f) } }
    private val jolt = HashMap<String, Float>()                 // how hard each rocket was just pushed, fading away
    private val lastProgress = HashMap<String, Float>()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private fun w() = view.width.toFloat()
    private fun h() = view.height.toFloat()
    private fun sc() = (if (view.height > 0) view.height else 1080) / 1080f
    private fun text(cv: Canvas, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = true, align: Paint.Align = Paint.Align.CENTER) = Ui.text(cv, view, t, x, y, size, color, outline, align)

    private fun backdrop(cv: Canvas, w: Float, h: Float, k: Float, now: Long, racing: Boolean) {
        val b = bg
        if (b != null) cv.drawBitmap(b, null, RectF(0f, 0f, w, h), bp) else cv.drawColor(Color.rgb(24, 14, 60))
        p.style = Paint.Style.FILL
        val t = now / 1000f * (if (racing) 2.6f else 1f)
        for (st in stars) {
            var x = (st[0] - t * st[3]) % 1f; if (x < 0f) x += 1f
            val y = 0.14f + st[1] * 0.72f                         // only over the dark middle of the picture
            p.color = Color.argb((150 + 100 * sin(now / 400.0 + st[4])).toInt().coerceIn(50, 255), 255, 255, 255)
            if (racing && st[3] > 0.03f) cv.drawRect(x * w, y * h - st[2] * k * 0.5f, x * w + 26f * k, y * h + st[2] * k * 0.5f, p) else cv.drawCircle(x * w, y * h, st[2] * k, p)
        }
        // a shooting star every few seconds
        val cycle = 5200L; val ph = (now % cycle) / 900f
        if (ph < 1f) { val n = now / cycle; val sx = w * (0.25f + ((n * 37) % 50) / 100f) + ph * 420f * k; val sy = h * (0.16f + ((n * 53) % 30) / 100f) + ph * 170f * k
            p.shader = LinearGradient(sx, sy, sx - 170f * k, sy - 70f * k, Color.argb((255 * (1f - ph)).toInt(), 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            p.style = Paint.Style.STROKE; p.strokeWidth = 5f * k; p.strokeCap = Paint.Cap.ROUND; cv.drawLine(sx, sy, sx - 170f * k, sy - 70f * k, p); p.shader = null; p.style = Paint.Style.FILL }
    }

    /** Cartoon exhaust: an outlined flame that flickers, with puffs drifting away behind it. Blue while the turbo is ready. */
    private fun exhaust(cv: Canvas, x: Float, y: Float, k: Float, turbo: Boolean, power: Float, now: Long, seed: Float) {
        p.style = Paint.Style.FILL
        for (i in 0 until 6) {                                    // puffs
            val f = ((now / 700f + i / 6f + seed) % 1f)
            val px = x - (30f + 210f * f) * k * power; val py = y + sin((f * 9f + i + seed * 7f).toDouble()).toFloat() * 13f * k * f
            p.color = if (turbo) Color.argb((170 * (1f - f)).toInt(), 120, 210, 255) else Color.argb((150 * (1f - f)).toInt(), 255, 214, 150)
            cv.drawCircle(px, py, (13f - 8f * f) * k * minOf(power, 1.5f), p)
        }
        val flick = 1f + 0.16f * sin(now / 38.0 + seed * 9f).toFloat()
        val len = 88f * k * power * flick
        fun flame(l: Float, half: Float, color: Int) { p.color = color; val f = Path(); f.moveTo(x + 8f * k, y - half); f.quadTo(x - l * 0.45f, y - half * 1.15f, x - l, y); f.quadTo(x - l * 0.45f, y + half * 1.15f, x + 8f * k, y + half); f.close(); cv.drawPath(f, p) }
        flame(len + 10f * k, 24f * k, Ui.INK)
        flame(len, 19f * k, if (turbo) Color.rgb(40, 150, 255) else Color.rgb(255, 120, 30))
        flame(len * 0.66f, 12.5f * k, if (turbo) Color.rgb(140, 220, 255) else Color.rgb(255, 208, 60))
        flame(len * 0.36f, 6.5f * k, Color.WHITE)
    }

    /** One spacecraft with its flame, bobbing gently; it stretches forward for a moment when it is pushed. */
    private fun craft(cv: Canvas, r: SpaceCore.Racer, x: Float, y: Float, k: Float, burning: Boolean, now: Long, index: Int) {
        val pic = if (r.robot) robo else ships[r.rocket.coerceIn(0, ROCKETS - 1)]
        val push = jolt[r.cid] ?: 0f
        val bob = sin(now / 420.0 + index * 1.7).toFloat() * 7f * k
        val cy = y + bob
        val ufo = !r.robot && r.rocket == 2
        if (burning) {
            if (ufo) { p.style = Paint.Style.FILL; val glow = 0.6f + 0.4f * sin(now / 120.0).toFloat()                       // the saucer rides on a glowing beam
                p.color = if (r.turbo) Color.argb((150 * glow).toInt(), 90, 190, 255) else Color.argb((130 * glow).toInt(), 150, 255, 170)
                cv.drawOval(RectF(x - 150f * k - 60f * k * push, cy + 8f * k, x - 20f * k, cy + 44f * k), p) }
            else exhaust(cv, x - 78f * k, cy, k, r.turbo, 1f + push * 0.9f + (if (r.turbo) 0.35f else 0f), now, index * 0.37f)
        }
        val sx = 1f + 0.16f * push; val sy = 1f - 0.1f * push
        cv.save(); cv.rotate(-3f * push + sin(now / 700.0 + index).toFloat() * 1.5f, x, cy); cv.scale(sx, sy, x, cy)
        if (pic != null) { val half = 92f * k; val hh = half * pic.height / pic.width; cv.drawBitmap(pic, null, RectF(x - half, cy - hh, x + half, cy + hh), bp) }
        else { p.color = Color.WHITE; cv.drawOval(RectF(x - 80f * k, cy - 26f * k, x + 80f * k, cy + 26f * k), p) }
        cv.restore()
        if (r.turbo) { p.style = Paint.Style.STROKE; p.strokeWidth = 4f * k; p.color = Color.argb((140 + 100 * sin(now / 90.0)).toInt().coerceIn(0, 255), 120, 210, 255)
            cv.drawOval(RectF(x - 108f * k, cy - 62f * k, x + 108f * k, cy + 62f * k), p); p.style = Paint.Style.FILL }
    }

    /** The player's name on a tag in their colour, so identical rockets can still be told apart. */
    private fun tag(cv: Canvas, t: String, x: Float, y: Float, k: Float, color: Int) {
        val size = 27f * k; val half = Ui.width(view, t, size) / 2 + 16f * k
        p.style = Paint.Style.FILL; p.color = Ui.INK; cv.drawRoundRect(RectF(x - half - 3f * k, y - size - 3f * k, x + half + 3f * k, y + 13f * k), 20f * k, 20f * k, p)
        p.color = color; cv.drawRoundRect(RectF(x - half, y - size, x + half, y + 10f * k), 18f * k, 18f * k, p)
        text(cv, t, x, y, size, Ui.INK, false)
    }

    fun draw(cv: Canvas, now: Long): Boolean {
        if (state == OFF) return false
        update(now)
        if (state == OFF) return false
        val w = w(); val h = h(); val k = sc()
        val racing = state == PLAY
        backdrop(cv, w, h, k, now, racing)
        val racers = core.racers.values.toList()
        val x0 = w * 0.11f; val x1 = w * 0.77f
        val top = h * 0.17f; val bottom = h * (if (state == LOBBY) 0.76f else 0.83f)
        val lane = ((bottom - top) / maxOf(racers.size, 1)).coerceAtMost(150f * k)
        val size = (lane / (150f * k)).coerceIn(0.6f, 1f) * k
        val first = (top + bottom) / 2 - lane * racers.size / 2 + lane / 2
        // the planet at the end of the track, floating a little
        planet?.let { val pw = 400f * k; val ph = pw * it.height / it.width; val px = w * 0.9f; val py = (top + bottom) / 2 + sin(now / 900.0).toFloat() * 10f * k
            cv.save(); cv.rotate(sin(now / 1300.0).toFloat() * 2.5f, px, py); cv.drawBitmap(it, null, RectF(px - pw / 2, py - ph / 2, px + pw / 2, py + ph / 2), bp); cv.restore() }
        // start line and tracks; the dashes run backwards while the race is on
        p.style = Paint.Style.FILL; p.color = Color.argb(210, 255, 255, 255)
        var cy0 = first - lane / 2; val sq = 12f * k; var row = 0
        while (cy0 < first + lane * (racers.size - 0.5f)) { p.color = if (row % 2 == 0) Color.WHITE else Ui.INK; cv.drawRect(x0 - 96f * k, cy0, x0 - 96f * k + sq, cy0 + sq, p); p.color = if (row % 2 == 0) Ui.INK else Color.WHITE; cv.drawRect(x0 - 96f * k + sq, cy0, x0 - 96f * k + 2 * sq, cy0 + sq, p); cy0 += sq; row++ }
        val slide = if (racing) (now / 6f) % (46f * k) else 0f
        var human = 0
        racers.forEachIndexed { i, r ->
            val y = first + i * lane
            val color = if (r.robot) Color.rgb(200, 206, 218) else COLORS[human++ % COLORS.size]
            p.color = Color.argb(60, 255, 255, 255); var dx = x0 - slide; while (dx < x1 + 110f * k) { if (dx > x0 - 60f * k) cv.drawRoundRect(RectF(dx, y - 2f * k, dx + 20f * k, y + 2f * k), 2f * k, 2f * k, p); dx += 46f * k }
            val before = lastProgress[r.cid] ?: r.progress
            if (r.progress > before + 0.5f) jolt[r.cid] = ((r.progress - before) / 14f).coerceIn(0.5f, 1.6f)
            lastProgress[r.cid] = r.progress
            jolt[r.cid] = ((jolt[r.cid] ?: 0f) * 0.9f).let { if (it < 0.02f) 0f else it }
            val prog = (shown[r.cid] ?: 0f).coerceIn(0f, 100f)
            val x = x0 + (x1 - x0) * prog / 100f
            craft(cv, r, x, y, size, state == PLAY || state == COUNT, now, i)
            tag(cv, r.name, x, y - 62f * size, size, color)
            text(cv, "" + Math.round(r.progress) + "%", x + 108f * size, y + 12f * size, 30f * size, Color.WHITE, true, Paint.Align.LEFT)
        }
        val galaxy = s(CATEGORIES[core.category.coerceIn(0, 10)])
        when (state) {
            LOBBY -> { Ui.title(cv, view, "🚀 " + s(R.string.space_title), w / 2, 104f * k, 78f * k)
                var hi = 0; racers.forEachIndexed { i, r -> if (!r.robot) text(cv, "⭐ " + r.mastered + " / " + maxOf(words.size, 1000), w * 0.42f, first + i * lane + 12f * size, 34f * size, COLORS[hi++ % COLORS.size], true, Paint.Align.LEFT) }
                val robot = s(if (core.racers[SpaceCore.ROBOT] == null) R.string.sr_robo_off else intArrayOf(R.string.lvl_easy, R.string.lvl_medium, R.string.lvl_hard)[core.roboSpeed])
                Ui.panel(cv, RectF(w * 0.2f, h * 0.845f, w * 0.8f, h * 0.985f), k)
                text(cv, s(R.string.sr_settings, galaxy, core.length, robot), w / 2, h * 0.845f + 50f * k, 36f * k, Color.WHITE, false)
                text(cv, s(R.string.sr_join_hint), w / 2, h * 0.845f + 94f * k, 26f * k, Color.argb(255, 200, 190, 235), false)
                Ui.pill(cv, view, s(R.string.press_start), w / 2, h * 0.8f, 38f * k, now) }
            COUNT -> { val el = now - stateAt; Ui.countdown(cv, view, 3 - (el / 1000).toInt(), (el % 1000) / 1000f, w / 2, h * 0.5f, k) }
            PLAY -> { val t = "🚀 " + galaxy; val half = Ui.width(view, t, 40f * k) / 2 + 30f * k
                Ui.panel(cv, RectF(w / 2 - half, 26f * k, w / 2 + half, 100f * k), k); text(cv, t, w / 2, 78f * k, 40f * k, Color.WHITE, false) }
            END -> { Ui.dim(cv, 185); Ui.confetti(cv, view, now - stateAt)
                Ui.title(cv, view, s(R.string.wins_fmt, core.winner?.name ?: ""), w / 2, h * 0.17f, 92f * k)
                text(cv, core.ranking().take(4).mapIndexed { i, r -> "" + (i + 1) + ". " + r.name + " " + Math.round(r.progress) + "%" }.joinToString("    "), w / 2, h * 0.17f + 96f * k, 38f * k, Color.WHITE)
                val list = review()
                text(cv, s(if (list.isEmpty()) R.string.sr_perfect else R.string.sr_review), w / 2, h * 0.4f, 54f * k, if (list.isEmpty()) Ui.TEAL else Ui.GOLD)
                list.forEachIndexed { i, wd -> val col = i % 2; val rw = i / 2
                    val cx = w * (if (col == 0) 0.27f else 0.73f); val cy = h * 0.52f + rw * 112f * k
                    val pop = if (i == spoken) 1.04f else 1f
                    Ui.panel(cv, RectF(cx - 390f * k * pop, cy - 66f * k * pop, cx + 390f * k * pop, cy + 30f * k * pop), k, if (i == spoken) Color.argb(255, 70, 110, 230) else Color.argb(235, 38, 22, 82), if (i == spoken) Ui.GOLD else Ui.PURPLE)
                    text(cv, wd.first, cx - 195f * k, cy, 46f * k, Color.WHITE, false); text(cv, wd.second, cx + 195f * k, cy, 46f * k, Ui.GOLD, false) } }
        }
        return true
    }
}
