package com.mohammad.tvdraw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * The shared look of every TV screen: one font, one set of colours, and the pieces all games use
 * (dimmed backdrop, title ribbon, "press start" pill, bouncing countdown, confetti).
 * Call these only from the UI thread.
 */
object Ui {
    const val INK = 0xFF1B1035.toInt()          // dark indigo used for outlines and panels
    const val GOLD = 0xFFFFD60A.toInt()
    const val PURPLE = 0xFF7A3FD8.toInt()
    const val PINK = 0xFFFF5FA2.toInt()
    const val TEAL = 0xFF34D1BF.toInt()
    val PARTY = intArrayOf(0xFFFF5A4F.toInt(), 0xFF2F80FF.toInt(), GOLD, 0xFF34C759.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF9F0A.toInt(), PINK, TEAL)

    private var typeface: Typeface? = null
    /** The app font (Baloo Bhaijaan 2, Arabic and Latin). Falls back to the system bold font if it cannot be read. */
    fun font(c: Context): Typeface {
        typeface?.let { return it }
        val t = try { Typeface.createFromAsset(c.assets, "fonts/BalooBhaijaan2-Bold.ttf") } catch (e: Throwable) { Typeface.DEFAULT_BOLD }
        typeface = t; return t
    }

    /** A picture from assets, or null when it is missing. rgb565 halves the memory of pictures with no transparency. */
    fun bitmap(c: Context, name: String, rgb565: Boolean = false): Bitmap? = try {
        c.assets.open(name).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { if (rgb565) inPreferredConfig = Bitmap.Config.RGB_565 }) }
    } catch (e: Throwable) { null }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /** Text with a dark outline, readable over any picture. */
    fun text(cv: Canvas, v: View, t: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = true, align: Paint.Align = Paint.Align.CENTER) {
        tx.typeface = font(v.context); tx.textSize = size; tx.textAlign = align
        if (outline) { tx.style = Paint.Style.STROKE; tx.strokeWidth = size * 0.16f; tx.strokeJoin = Paint.Join.ROUND; tx.color = INK; cv.drawText(t, x, y + size * 0.03f, tx) }
        tx.style = Paint.Style.FILL; tx.color = color; cv.drawText(t, x, y, tx); tx.textAlign = Paint.Align.CENTER
    }
    fun width(v: View, t: String, size: Float): Float { tx.typeface = font(v.context); tx.textSize = size; return tx.measureText(t) }

    /** Darkens what is behind a lobby or a result screen. */
    fun dim(cv: Canvas, strength: Int = 170) { cv.drawColor(Color.argb(strength, 22, 12, 52)) }

    /** A rounded panel with a soft border. */
    fun panel(cv: Canvas, r: RectF, k: Float, fill: Int = Color.argb(225, 38, 22, 82), border: Int = Color.argb(255, 122, 63, 216)) {
        p.style = Paint.Style.FILL; p.color = Color.argb(90, 0, 0, 0); cv.drawRoundRect(RectF(r.left + 4f * k, r.top + 8f * k, r.right + 4f * k, r.bottom + 8f * k), 26f * k, 26f * k, p)
        p.color = fill; cv.drawRoundRect(r, 26f * k, 26f * k, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f * k; p.color = border; cv.drawRoundRect(r, 26f * k, 26f * k, p); p.style = Paint.Style.FILL
    }

    /** The name of a game on a ribbon. y is the text baseline. */
    fun title(cv: Canvas, v: View, t: String, cx: Float, y: Float, size: Float) {
        val k = size / 110f; val half = width(v, t, size) / 2 + 70f * k
        val r = RectF(cx - half, y - size * 0.98f, cx + half, y + size * 0.42f)
        p.style = Paint.Style.FILL
        p.color = Color.argb(110, 0, 0, 0); cv.drawRoundRect(RectF(r.left + 6f * k, r.top + 12f * k, r.right + 6f * k, r.bottom + 12f * k), 40f * k, 40f * k, p)
        p.color = PURPLE; cv.drawRoundRect(r, 40f * k, 40f * k, p)
        p.color = Color.argb(60, 255, 255, 255); cv.drawRoundRect(RectF(r.left + 14f * k, r.top + 10f * k, r.right - 14f * k, r.top + (r.height()) * 0.45f), 28f * k, 28f * k, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 8f * k; p.color = GOLD; cv.drawRoundRect(r, 40f * k, 40f * k, p); p.style = Paint.Style.FILL
        text(cv, v, t, cx, y, size, Color.WHITE)
    }

    /** A gently pulsing gold pill, used for "press start on the phone". */
    fun pill(cv: Canvas, v: View, t: String, cx: Float, y: Float, size: Float, now: Long) {
        val pulse = 1f + 0.035f * sin(now / 260.0).toFloat()
        val s = size * pulse; val half = width(v, t, s) / 2 + s * 0.9f
        val r = RectF(cx - half, y - s * 1.05f, cx + half, y + s * 0.5f)
        p.style = Paint.Style.FILL; p.color = Color.argb(110, 0, 0, 0); cv.drawRoundRect(RectF(r.left + 3f, r.top + s * 0.14f, r.right + 3f, r.bottom + s * 0.14f), r.height() / 2, r.height() / 2, p)
        p.color = GOLD; cv.drawRoundRect(r, r.height() / 2, r.height() / 2, p)
        text(cv, v, t, cx, y, s, INK, false)
        v.postInvalidateOnAnimation()
    }

    /** 3, 2, 1: each number drops in big and bounces. f runs from 0 to 1 while one number is on screen. */
    fun countdown(cv: Canvas, v: View, n: Int, f: Float, cx: Float, cy: Float, k: Float) {
        val pop = if (f < 0.25f) 1.9f - 3.6f * f else 1f + 0.1f * sin((f - 0.25f) * 18.0).toFloat() * (1f - f)
        val size = 300f * k * pop
        p.style = Paint.Style.FILL; p.color = Color.argb(150, 22, 12, 52); cv.drawCircle(cx, cy, 230f * k * (0.9f + 0.1f * pop), p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 12f * k; p.color = PARTY[(3 - n).coerceIn(0, 2)]; cv.drawCircle(cx, cy, 230f * k * (0.9f + 0.1f * pop), p); p.style = Paint.Style.FILL
        text(cv, v, n.coerceAtLeast(1).toString(), cx, cy + size * 0.34f, size, Color.WHITE)
    }

    /** Paper confetti falling over a result screen. It needs no memory of its own: every piece follows from the time. */
    fun confetti(cv: Canvas, v: View, ms: Long, pieces: Int = 90) {
        val w = v.width.toFloat(); val h = v.height.toFloat(); val k = h / 1080f; val t = ms / 1000f
        if (t > 9f) return
        for (i in 0 until pieces) {
            val a = (i * 7919 % 1000) / 1000f; val b = (i * 104729 % 1000) / 1000f; val c = (i * 1299709 % 1000) / 1000f
            val delay = b * 1.6f; if (t < delay) continue
            val fall = (t - delay) * (260f + 240f * c) * k
            val y = -40f * k + fall; if (y > h + 40f * k) continue
            val x = a * w + sin((t * (1.5f + c) + i).toDouble()).toFloat() * 46f * k
            p.color = PARTY[i % PARTY.size]; p.alpha = if (t > 7.5f) (255 * (9f - t) / 1.5f).toInt().coerceIn(0, 255) else 255
            cv.save(); cv.rotate(t * (160f + 240f * b) + i * 37f, x, y)
            val sw = (10f + 12f * c) * k * abs(sin((t * (3f + 2f * a) + i).toDouble()).toFloat()).coerceAtLeast(0.25f)
            cv.drawRect(x - sw, y - 7f * k, x + sw, y + 7f * k, p); cv.restore()
        }
        p.alpha = 255
        v.postInvalidateOnAnimation()
    }
}
