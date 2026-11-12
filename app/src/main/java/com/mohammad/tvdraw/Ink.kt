package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import android.view.View

/** One stroke drawn by one phone. Coordinates are TV pixels. */
class Stroke(val client: String, val tool: String, val color: Int, val width: Float, val nib: Float) {
    val xs = ArrayList<Float>(); val ys = ArrayList<Float>()
    val ws = ArrayList<Float>()          // per-point width, used by the brush
}

/** What one phone is currently holding: its colour, tool, the stroke in progress and its laser dot. */
class Pen(var color: Int) {
    var width = 10f; var tool = "pen"; var nib = 60f
    var cur: Stroke? = null
    var lx = 0f; var ly = 0f; var sx = 0f; var sy = 0f; var laserT = 0L
    val trail = ArrayList<Float>()
}

/**
 * Drawing engine: several phones draw at once, each with its own pen.
 * Finished strokes are baked into a bitmap; strokes in progress are drawn live on top.
 */
class Ink(private val view: View) {
    companion object {
        val PALETTE = intArrayOf(0xFFFF3B30.toInt(), 0xFF34C759.toInt(), 0xFF0A84FF.toInt(), 0xFFFFD60A.toInt(),
            0xFFFF9F0A.toInt(), 0xFFBF5AF2.toInt(), 0xFF64D2FF.toInt(), 0xFFFF375F.toInt())
        val SHAPES = setOf("line", "arrow", "rect", "oval")
    }
    private val pens = HashMap<String, Pen>()
    private val strokes = ArrayList<Stroke>()
    private var bmp: Bitmap? = null
    private var bc: Canvas? = null
    var page = "none"
    var laserMode = "dot"
    var spotRadius = 260f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val clearMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val path = Path()

    private fun sc() = (if (view.height > 0) view.height else 1080) / 1080f
    fun pen(c: String) = pens.getOrPut(c) { Pen(PALETTE[pens.size % PALETTE.size]) }

    private fun canvas(): Canvas? {
        val w = view.width; val h = view.height
        if (w <= 0 || h <= 0) return null
        val b = bmp
        if (b == null || b.width != w || b.height != h) {
            val nb = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp = nb; bc = Canvas(nb)
            for (s in strokes) render(bc!!, s, true)
        }
        return bc
    }

    // ---- strokes ----
    fun down(c: String, x: Float, y: Float) {
        end(c)
        val pn = pen(c)
        val s = Stroke(c, pn.tool, pn.color, pn.width * sc(), pn.nib)
        s.xs.add(x); s.ys.add(y); s.ws.add(s.width * 0.45f)
        pn.cur = s
    }
    fun move(c: String, x: Float, y: Float) {
        val s = pen(c).cur ?: return
        val n = s.xs.size
        if (n >= 6000) return
        val dx = x - s.xs[n - 1]; val dy = y - s.ys[n - 1]
        val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (d < 0.5f) return
        if (s.tool in SHAPES) {                                   // shapes keep only start + current end
            if (n == 1) { s.xs.add(x); s.ys.add(y); s.ws.add(s.width) } else { s.xs[1] = x; s.ys[1] = y }
            return
        }
        val target = s.width * (1.55f - d / (34f * sc())).coerceIn(0.3f, 1.55f)      // fast = thin, slow = thick
        s.xs.add(x); s.ys.add(y); s.ws.add(s.ws[n - 1] * 0.65f + target * 0.35f)
        if (s.tool == "eraser") canvas()?.let { eraseSeg(it, s, n) }                 // erasing shows immediately
    }
    fun end(c: String) {
        val pn = pen(c); val s = pn.cur ?: return
        pn.cur = null
        if (s.tool != "eraser") canvas()?.let { render(it, s, true) }
        strokes.add(s)
    }
    fun undo(c: String) {
        end(c)
        val i = strokes.indexOfLast { it.client == c }
        if (i < 0) return
        strokes.removeAt(i); rebuild()
    }
    fun clearAll() {
        strokes.clear(); for (pn in pens.values) pn.cur = null
        bmp?.eraseColor(0)
    }
    private fun rebuild() {
        val cv = canvas() ?: return
        bmp?.eraseColor(0)
        for (s in strokes) render(cv, s, true)
    }

    private fun stroke(color: Int, width: Float, cap: Paint.Cap = Paint.Cap.ROUND) {
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = width
        p.strokeCap = cap; p.strokeJoin = Paint.Join.ROUND
    }
    private fun smoothPath(s: Stroke) {
        path.rewind(); path.moveTo(s.xs[0], s.ys[0])
        val n = s.xs.size
        if (n == 1) { path.lineTo(s.xs[0] + 0.1f, s.ys[0]); return }
        for (i in 1 until n - 1) path.quadTo(s.xs[i], s.ys[i], (s.xs[i] + s.xs[i + 1]) / 2, (s.ys[i] + s.ys[i + 1]) / 2)
        path.lineTo(s.xs[n - 1], s.ys[n - 1])
    }
    private fun mix(c: Int, t: Float) = Color.argb(255, (Color.red(c) + (255 - Color.red(c)) * t).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * t).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * t).toInt())
    private fun eraseSeg(cv: Canvas, s: Stroke, i: Int) {
        stroke(Color.BLACK, s.width * 2.6f); p.xfermode = clearMode
        cv.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], p); p.xfermode = null
    }

    /** baked = drawing into the ink bitmap (the only place the eraser can work). */
    private fun render(cv: Canvas, s: Stroke, baked: Boolean) {
        val n = s.xs.size; if (n == 0) return
        val k = sc()
        when (s.tool) {
            "eraser" -> { if (!baked) return
                stroke(Color.BLACK, s.width * 2.6f); p.xfermode = clearMode; smoothPath(s); cv.drawPath(path, p); p.xfermode = null }
            "hl" -> { stroke(s.color, s.width * 2.4f, Paint.Cap.SQUARE); p.alpha = 96; smoothPath(s); cv.drawPath(path, p) }
            "neon" -> { smoothPath(s)
                stroke(s.color, s.width * 3.4f); p.alpha = 42; cv.drawPath(path, p)
                stroke(s.color, s.width * 2.0f); p.alpha = 115; cv.drawPath(path, p)
                stroke(mix(s.color, 0.8f), s.width * 0.75f); cv.drawPath(path, p) }
            "brush" -> { stroke(s.color, s.width)
                if (n == 1) { p.style = Paint.Style.FILL; cv.drawCircle(s.xs[0], s.ys[0], s.width * 0.4f, p) }
                for (i in 1 until n) { p.strokeWidth = (s.ws[i - 1] + s.ws[i]) / 2; cv.drawLine(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i], p) } }
            "qalam" -> {                                           // flat reed-pen nib held at a fixed angle
                val a = Math.toRadians(s.nib.toDouble())
                val nx = (Math.cos(a) * s.width / 2).toFloat(); val ny = (-Math.sin(a) * s.width / 2).toFloat()
                path.rewind(); path.fillType = Path.FillType.WINDING
                fun quad(x0: Float, y0: Float, x1: Float, y1: Float) {
                    val cross = (x1 - x0) * (2 * ny) - (y1 - y0) * (2 * nx)       // keep every piece wound the same way
                    if (cross >= 0) { path.moveTo(x0 + nx, y0 + ny); path.lineTo(x1 + nx, y1 + ny); path.lineTo(x1 - nx, y1 - ny); path.lineTo(x0 - nx, y0 - ny) }
                    else { path.moveTo(x0 - nx, y0 - ny); path.lineTo(x1 - nx, y1 - ny); path.lineTo(x1 + nx, y1 + ny); path.lineTo(x0 + nx, y0 + ny) }
                    path.close()
                }
                if (n == 1) { val mx = -ny; val my = nx; quad(s.xs[0] - mx, s.ys[0] - my, s.xs[0] + mx, s.ys[0] + my) }   // a tap makes the diamond dot
                for (i in 1 until n) quad(s.xs[i - 1], s.ys[i - 1], s.xs[i], s.ys[i])
                p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = s.color; cv.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * k; p.strokeJoin = Paint.Join.ROUND; cv.drawPath(path, p)   // closes hairline gaps between pieces
            }
            "line", "arrow", "rect", "oval" -> { if (n < 2) return
                stroke(s.color, s.width)
                val x0 = s.xs[0]; val y0 = s.ys[0]; val x1 = s.xs[1]; val y1 = s.ys[1]
                when (s.tool) {
                    "rect" -> cv.drawRect(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1), p)
                    "oval" -> cv.drawOval(RectF(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1)), p)
                    else -> { cv.drawLine(x0, y0, x1, y1, p)
                        if (s.tool == "arrow") { val ang = Math.atan2((y1 - y0).toDouble(), (x1 - x0).toDouble()); val hl = maxOf(s.width * 4f, 30f * k)
                            for (sg in intArrayOf(-1, 1)) { val b = ang + Math.PI + sg * 0.45
                                cv.drawLine(x1, y1, x1 + (Math.cos(b) * hl).toFloat(), y1 + (Math.sin(b) * hl).toFloat(), p) } } }
                } }
            else -> { stroke(s.color, s.width); smoothPath(s); cv.drawPath(path, p) }
        }
    }

    // ---- ruled pages ----
    private fun drawPage(cv: Canvas) {
        val w = view.width.toFloat(); val h = view.height.toFloat(); val k = sc()
        if (page == "none") return
        val paper = 0xFFFBF7EA.toInt()
        p.reset(); p.isAntiAlias = true
        when (page) {
            "lines" -> { cv.drawColor(paper)
                p.color = 0xFF9DB7D8.toInt(); p.strokeWidth = 2f * k
                var y = 150f * k; while (y < h - 30f * k) { cv.drawLine(0f, y, w, y, p); y += 84f * k }
                p.color = 0xFFE2848A.toInt(); cv.drawLine(w - 170f * k, 0f, w - 170f * k, h, p); cv.drawLine(w - 178f * k, 0f, w - 178f * k, h, p) }
            "calli" -> { cv.drawColor(paper)                       // base line + guide lines above and below, for Arabic lettering
                var y = 210f * k
                while (y < h - 60f * k) {
                    p.pathEffect = null; p.color = 0xFF5A7FB5.toInt(); p.strokeWidth = 2.6f * k; cv.drawLine(0f, y, w, y, p)
                    p.color = 0xFFB9CBE4.toInt(); p.strokeWidth = 1.6f * k
                    p.pathEffect = DashPathEffect(floatArrayOf(18f * k, 12f * k), 0f)
                    cv.drawLine(0f, y - 118f * k, w, y - 118f * k, p); cv.drawLine(0f, y + 56f * k, w, y + 56f * k, p)
                    p.pathEffect = DashPathEffect(floatArrayOf(4f * k, 10f * k), 0f); cv.drawLine(0f, y - 46f * k, w, y - 46f * k, p)
                    y += 250f * k
                }
                p.pathEffect = null }
            "grid" -> { cv.drawColor(paper); val g = 54f * k
                var i = 0; var x = 0f; while (x <= w) { p.color = if (i % 5 == 0) 0xFF8FB0D9.toInt() else 0xFFCFDDF0.toInt(); p.strokeWidth = (if (i % 5 == 0) 2f else 1.2f) * k; cv.drawLine(x, 0f, x, h, p); x += g; i++ }
                i = 0; var y = 0f; while (y <= h) { p.color = if (i % 5 == 0) 0xFF8FB0D9.toInt() else 0xFFCFDDF0.toInt(); p.strokeWidth = (if (i % 5 == 0) 2f else 1.2f) * k; cv.drawLine(0f, y, w, y, p); y += g; i++ } }
            "dots" -> { cv.drawColor(paper); p.color = 0xFF9DB0C8.toInt(); val g = 54f * k
                var y = g / 2; while (y < h) { var x = g / 2; while (x < w) { cv.drawCircle(x, y, 3f * k, p); x += g }; y += g } }
            "board" -> { cv.drawColor(0xFF1E3D2F.toInt()); p.color = Color.argb(46, 255, 255, 255); p.strokeWidth = 2f * k
                var y = 150f * k; while (y < h - 30f * k) { cv.drawLine(0f, y, w, y, p); y += 84f * k } }
        }
    }

    fun draw(cv: Canvas) {
        drawPage(cv)
        bmp?.let { cv.drawBitmap(it, 0f, 0f, bmpPaint) }
        for (pn in pens.values) pn.cur?.let { render(cv, it, false) }
    }

    // ---- laser pointer / spotlight ----
    fun laser(c: String, x: Float, y: Float) {
        val pn = pen(c); val now = SystemClock.uptimeMillis()
        if (pn.laserT == 0L || now - pn.laserT > 4000) { pn.sx = x; pn.sy = y; pn.trail.clear() }
        pn.lx = x; pn.ly = y; pn.laserT = now
    }
    fun laserOff(c: String) { pen(c).laserT = 0L }
    private val spotMatrix = Matrix()
    fun drawLaser(cv: Canvas, now: Long): Boolean {
        var any = false; val k = sc()
        var spot: Pen? = null
        for (pn in pens.values) {
            if (pn.laserT == 0L) continue
            val age = now - pn.laserT
            if (age > 4000) { pn.laserT = 0L; continue }
            any = true
            pn.sx += (pn.lx - pn.sx) * 0.38f; pn.sy += (pn.ly - pn.sy) * 0.38f
            if (laserMode == "spot") { if (spot == null || pn.laserT > spot.laserT) spot = pn; continue }
            val dark = Color.red(pn.color) * 0.3f + Color.green(pn.color) * 0.59f + Color.blue(pn.color) * 0.11f < 70f
            val lc = if (dark) 0xFFFF3B30.toInt() else pn.color                  // a black pen still gets a visible red dot
            val a = if (age > 3300) (4000 - age) / 700f else 1f
            pn.trail.add(pn.sx); pn.trail.add(pn.sy); while (pn.trail.size > 20) { pn.trail.removeAt(0); pn.trail.removeAt(0) }
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL
            val tn = pn.trail.size / 2
            for (i in 0 until tn - 1) { p.color = lc; p.alpha = (70f * a * (i + 1) / tn).toInt(); cv.drawCircle(pn.trail[2 * i], pn.trail[2 * i + 1], (5f + 9f * i / tn) * k, p) }
            val r = 60f * k
            p.alpha = 255
            p.shader = RadialGradient(pn.sx, pn.sy, r, intArrayOf(Color.argb((235 * a).toInt(), Color.red(lc), Color.green(lc), Color.blue(lc)),
                Color.argb((120 * a).toInt(), Color.red(lc), Color.green(lc), Color.blue(lc)), Color.argb(0, Color.red(lc), Color.green(lc), Color.blue(lc))),
                floatArrayOf(0f, 0.32f, 1f), Shader.TileMode.CLAMP)
            cv.drawCircle(pn.sx, pn.sy, r, p); p.shader = null
            p.color = lc; p.alpha = (255 * a).toInt(); cv.drawCircle(pn.sx, pn.sy, 15f * k, p)
            p.color = Color.WHITE; p.alpha = (255 * a).toInt(); cv.drawCircle(pn.sx, pn.sy, 6.5f * k, p)
        }
        if (spot != null) {
            val age = now - spot.laserT
            val a = if (age > 3300) (4000 - age) / 700f else 1f
            val r = spotRadius * k
            p.reset(); p.isAntiAlias = true
            p.shader = RadialGradient(spot.sx, spot.sy, r, intArrayOf(0, 0, Color.argb((205 * a).toInt(), 0, 0, 0)), floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP)
            cv.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), p); p.shader = null
        }
        return any
    }
}
