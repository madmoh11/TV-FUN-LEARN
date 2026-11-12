package com.mohammad.tvdraw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View

/**
 * Live picture from one phone's camera, shown in a corner of the TV like a reporter's window.
 * The phone posts small JPEG frames about ten times a second; the window disappears when frames stop.
 */
class LiveCam(private val view: View) {
    private var bmp: Bitmap? = null
    private var at = 0L                    // when the last frame arrived
    private var owner: String? = null      // the phone that is on air; others wait until it stops
    private var frames = 0
    private var pos = "br"                 // tl, tr, bl, br
    private var size = 1                   // 0 small, 1 medium, 2 large, 3 full screen
    private var name = ""
    private val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    private val tx = Paint().apply { isAntiAlias = true; color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD }
    private val clip = Path()

    private fun live(now: Long) = bmp != null && now - at < TIMEOUT
    private fun mine(cid: String, now: Long) = owner == cid || !live(now)

    /** A decoded frame from a phone. Call on the UI thread. */
    fun frame(cid: String, b: Bitmap) {
        val now = SystemClock.uptimeMillis()
        if (!mine(cid, now)) { b.recycle(); return }
        if (owner != cid) { owner = cid; frames = 0 }
        bmp?.recycle(); bmp = b; at = now; frames++
        view.invalidate(); view.postInvalidateDelayed(TIMEOUT + 100)
    }

    /** Commands from the phone page, already split on spaces: f off | f pos br | f size 1 | f name NAME */
    fun command(p: List<String>, cid: String) {
        if (!mine(cid, SystemClock.uptimeMillis())) return
        when (p[1]) {
            "off" -> if (owner == cid) { bmp?.recycle(); bmp = null; owner = null }
            "pos" -> if (p[2] in setOf("tl", "tr", "bl", "br")) pos = p[2]
            "size" -> size = p[2].toInt().coerceIn(0, 3)
            "name" -> name = java.net.URLDecoder.decode(p.getOrElse(2) { "" }, "UTF-8").trim().take(20)
        }
        view.invalidate()
    }

    fun status(cid: String): String {
        val on = live(SystemClock.uptimeMillis())
        return "live=" + (if (on) 1 else 0) + "|mine=" + (if (on && owner == cid) 1 else 0) + "|frames=" + (if (owner == cid) frames else 0)
    }

    fun draw(cv: Canvas, now: Long, s: Float) {
        val b = bmp ?: return
        if (now - at >= TIMEOUT) { b.recycle(); bmp = null; owner = null; return }
        val w = view.width.toFloat(); val h = view.height.toFloat()
        val full = size == 3
        val frac = floatArrayOf(0.2f, 0.3f, 0.42f, 1f)[size]
        if (full) cv.drawColor(Color.BLACK)
        // A sideways phone gives a wide picture and an upright one a tall picture: both get a similar area.
        val k = if (full) minOf(w / b.width, h / b.height) else minOf(w * frac / b.width, h * frac * 1.5f / b.height)
        val bw = b.width * k; val bh = b.height * k
        val m = 36f * s
        val l = if (full) (w - bw) / 2 else if (pos[1] == 'l') m else w - m - bw
        val t = if (full) (h - bh) / 2 else if (pos[0] == 't') m else h - m - bh
        val box = RectF(l, t, l + bw, t + bh)
        val rad = if (full) 0f else 22f * s
        if (!full) { paint.color = Color.argb(110, 0, 0, 0); cv.drawRoundRect(RectF(l + 6f * s, t + 8f * s, l + bw + 6f * s, t + bh + 8f * s), rad, rad, paint) }
        paint.color = Color.WHITE
        clip.rewind(); clip.addRoundRect(box, rad, rad, Path.Direction.CW)
        cv.save(); cv.clipPath(clip); cv.drawBitmap(b, null, box, paint)
        val u = if (full) 1.5f * s else s * (0.75f + size * 0.2f)       // badge and name grow with the window
        if (name.isNotEmpty()) {
            tx.textSize = 26f * u; tx.textAlign = Paint.Align.CENTER
            paint.color = Color.argb(150, 0, 0, 0); cv.drawRect(l, t + bh - 44f * u, l + bw, t + bh, paint)
            cv.drawText(name, l + bw / 2, t + bh - 14f * u, tx)
        }
        cv.restore()
        if (!full) { paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f * s; paint.color = Color.WHITE; cv.drawRoundRect(box, rad, rad, paint); paint.style = Paint.Style.FILL }
        val label = view.context.getString(R.string.live)
        tx.textSize = 22f * u; tx.textAlign = Paint.Align.LEFT
        val lw = tx.measureText(label) + 46f * u; val bx = l + 12f * u; val by = t + 12f * u
        paint.color = Color.argb(235, 217, 45, 32); cv.drawRoundRect(RectF(bx, by, bx + lw, by + 36f * u), 8f * u, 8f * u, paint)
        paint.color = Color.argb(if ((now / 600) % 2 == 0L) 255 else 110, 255, 255, 255); cv.drawCircle(bx + 18f * u, by + 18f * u, 6f * u, paint)
        cv.drawText(label, bx + 32f * u, by + 26f * u, tx)
        view.postInvalidateDelayed(600)                                  // keeps the red dot blinking
    }

    companion object { const val TIMEOUT = 3000L }
}
