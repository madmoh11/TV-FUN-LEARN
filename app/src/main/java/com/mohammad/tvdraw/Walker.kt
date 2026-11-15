package com.mohammad.tvdraw

import android.graphics.*
import android.os.SystemClock
import kotlin.math.*

/**
 * The "someone walked past" prank: a person's shadow crosses the bottom of the picture, head and
 * shoulders only, the way it does when somebody walks in front of a cinema screen.
 * A jointed figure seen from the side is drawn small and solid, then stretched over the screen
 * half transparent, which gives the soft edge of a real shadow. The stride is tied to the distance
 * covered, so the feet do not slide.
 */
class Walker {
    private var start = 0L
    private var woman = false
    private var dir = -1                     // -1 walks right to left, +1 left to right
    private var buf: Bitmap? = null
    private var bc: Canvas? = null
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val out = Paint(Paint.FILTER_BITMAP_FLAG)
    private val path = Path()

    val active get() = start != 0L
    fun stop() { start = 0L }
    fun go(woman: Boolean) { this.woman = woman; dir = if (woman) 1 else -1; start = SystemClock.uptimeMillis() }

    private fun limb(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, w: Float) {
        ink.style = Paint.Style.STROKE; ink.strokeWidth = w; c.drawLine(x0, y0, x1, y1, ink)
    }

    /** One leg from the hip: thigh, bent shin and a foot. Angles are from straight down, forward positive. */
    private fun leg(c: Canvas, hx: Float, hy: Float, ph: Float, u: Float, wide: Float) {
        val th = 0.44f * sin(ph)
        val bend = 0.08f + 0.8f * max(0f, cos(ph))
        val kx = hx + sin(th) * 0.25f * u; val ky = hy + cos(th) * 0.25f * u
        val sa = th - bend
        val ax = kx + sin(sa) * 0.25f * u; val ay = ky + cos(sa) * 0.25f * u
        limb(c, hx, hy, kx, ky, 0.088f * u * wide); limb(c, kx, ky, ax, ay, 0.062f * u * wide)
        val fa = sa + 1.35f
        limb(c, ax, ay, ax + sin(fa) * 0.065f * u, ay + cos(fa) * 0.065f * u, 0.04f * u)
    }
    private fun arm(c: Canvas, sx: Float, sy: Float, ph: Float, u: Float, wide: Float) {
        val a = -0.42f * sin(ph)
        val ex = sx + sin(a) * 0.16f * u; val ey = sy + cos(a) * 0.16f * u
        val fa = a + 0.32f
        limb(c, sx, sy, ex, ey, 0.055f * u * wide); limb(c, ex, ey, ex + sin(fa) * 0.16f * u, ey + cos(fa) * 0.16f * u, 0.045f * u * wide)
    }

    /** Draws the figure facing right with the feet on y = ground and the hips above x = cx. */
    private fun figure(c: Canvas, cx: Float, ground: Float, u: Float, ph: Float) {
        val bob = 0.016f * cos(2 * ph) * u
        val hy = ground - 0.5f * u + bob
        val lean = 0.025f * u
        val sy = hy - 0.3f * u; val sx = cx + lean
        val slim = if (woman) 0.8f else 1f
        arm(c, sx, sy + 0.01f * u, ph + PI.toFloat(), u, slim)             // far arm and leg first
        leg(c, cx, hy, ph + PI.toFloat(), u, slim)
        leg(c, cx, hy, ph, u, slim)
        // trunk: shoulders to hips
        ink.style = Paint.Style.FILL
        val sw = (if (woman) 0.075f else 0.095f) * u; val hw = (if (woman) 0.08f else 0.075f) * u
        path.rewind()                                                      // rounded back, sloping shoulder, chest
        path.moveTo(sx - 0.035f * u, sy - 0.055f * u)
        path.cubicTo(sx - sw * 0.9f, sy - 0.05f * u, sx - sw * 1.25f, sy + 0.01f * u, sx - sw * 1.2f, sy + 0.1f * u)
        path.lineTo(cx - hw, hy + 0.02f * u); path.quadTo(cx, hy + 0.06f * u, cx + hw, hy + 0.02f * u)
        path.lineTo(sx + sw * 1.1f, sy + 0.12f * u)
        path.cubicTo(sx + sw * 1.2f, sy + 0.02f * u, sx + sw * 0.8f, sy - 0.045f * u, sx + 0.04f * u, sy - 0.05f * u)
        path.close()
        c.drawPath(path, ink)
        if (woman) {                                                       // a skirt that swings with the stride
            val sway = 0.03f * u * sin(ph)
            path.rewind()
            path.moveTo(cx - hw * 0.9f, hy - 0.08f * u); path.lineTo(cx + hw * 0.9f, hy - 0.08f * u)
            path.lineTo(cx + 0.15f * u + sway, hy + 0.2f * u); path.quadTo(cx + sway, hy + 0.235f * u, cx - 0.13f * u + sway, hy + 0.2f * u); path.close()
            c.drawPath(path, ink)
        }
        // neck and head
        val nx = sx + 0.012f * u; val hcy = sy - 0.115f * u; val hr = (if (woman) 0.06f else 0.064f) * u
        limb(c, sx, sy - 0.02f * u, nx, hcy + hr * 0.5f, 0.078f * u * slim)
        ink.style = Paint.Style.FILL
        c.drawOval(RectF(nx - hr * 0.95f, hcy - hr * 1.1f, nx + hr * 1.05f, hcy + hr * 1.1f), ink)
        c.drawCircle(nx + hr * 0.98f, hcy + hr * 0.15f, hr * 0.2f, ink)    // nose
        c.drawCircle(nx + hr * 0.62f, hcy + hr * 0.78f, hr * 0.38f, ink)   // jaw and chin
        if (!woman) c.drawOval(RectF(nx - hr * 1.05f, hcy - hr * 1.2f, nx + hr * 0.85f, hcy + hr * 0.2f), ink)   // hair
        if (woman) {                                                       // long hair falling behind the shoulders
            val sw2 = 0.02f * u * sin(ph * 2)
            path.rewind()
            path.moveTo(nx - hr * 0.2f, hcy - hr * 1.15f); path.quadTo(nx - hr * 1.5f, hcy - hr * 0.9f, nx - hr * 1.25f, hcy + hr * 0.6f)
            path.quadTo(nx - hr * 1.5f + sw2, sy + 0.08f * u, nx - hr * 1.1f + sw2, sy + 0.17f * u)
            path.lineTo(nx - hr * 0.1f + sw2, sy + 0.13f * u); path.quadTo(nx - hr * 0.2f, sy, nx, hcy); path.close()
            c.drawPath(path, ink)
        }
        arm(c, sx, sy + 0.01f * u, ph, u, slim)                            // near arm on top
    }

    /** Returns true while the shadow is still crossing. */
    fun draw(cv: Canvas, w: Float, h: Float, now: Long): Boolean {
        if (start == 0L) return false
        // like someone crossing in front of a cinema screen: only the head and shoulders rise into the picture
        val show = h * (if (woman) 0.44f else 0.52f)                       // how far the head reaches up the screen
        val tall = show / 0.34f                                            // so the whole figure is about three times that
        val speed = tall * (if (woman) 0.6f else 0.66f)                    // pixels a second: an unhurried walk
        val margin = tall * 0.3f
        val gone = (now - start) / 1000f * speed
        if (gone > w + 2 * margin) { start = 0L; return false }
        val x = if (dir > 0) -margin + gone else w + margin - gone
        val ph = gone / (tall * 0.215f)                                    // one full stride every 1.35 heights
        val k = 8f                                                         // drawn this many times smaller
        val bw = ceil(tall * 0.8f / k).toInt().coerceAtLeast(8); val bh = ceil(h / k).toInt().coerceAtLeast(8)
        if (buf == null || buf!!.width != bw || buf!!.height != bh) { buf = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); bc = Canvas(buf!!) }
        val c = bc!!
        buf!!.eraseColor(Color.TRANSPARENT)
        c.save()
        if (dir < 0) c.scale(-1f, 1f, bw / 2f, 0f)
        figure(c, bw / 2f, (h - show + tall) / k, tall / k, ph)
        c.restore()
        out.alpha = 225
        val half = bw * k / 2f
        cv.drawBitmap(buf!!, null, RectF(x - half, 0f, x + half, bh * k), out)
        return true
    }
}
