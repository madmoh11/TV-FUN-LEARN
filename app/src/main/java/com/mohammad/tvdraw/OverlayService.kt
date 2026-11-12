package com.mohammad.tvdraw

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.*
import android.view.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class Floaty(val text: String, val color: Int, val emoji: Boolean, val start: Long,
             val w: Float, val x0: Float, val y0: Float)

class DrawView(c: Context) : View(c) {
    val ink = Ink(this)
    val game = Game(this)
    val hockey = Hockey(this)
    val snake = SnakeGame(this)
    val cam = LiveCam(this)

    // ---- floating comments + emoji ----
    private val floats = mutableListOf<Floaty>()
    private val laneFree = LongArray(6)
    private val txt = Paint().apply { color = Color.WHITE; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    private val pill = Paint().apply { color = Color.argb(170, 0, 0, 0); isAntiAlias = true }
    private val edge = Paint().apply { style = Paint.Style.STROKE; isAntiAlias = true }
    private val emo = Paint().apply { isAntiAlias = true }
    private val commentMs = 10000L
    private val emojiMs = 3500L
    private fun sc() = (if (height > 0) height else 1080) / 1080f
    private fun dec(v: String) = java.net.URLDecoder.decode(v, "UTF-8")

    private fun addComment(name: String, text: String) {
        val body = text.trim().take(140); if (body.isEmpty()) return
        val who = name.trim().take(20)
        val line = if (who.isEmpty()) body else "$who: $body"
        txt.textSize = 46f * sc()
        val w = txt.measureText(line)
        val now = SystemClock.uptimeMillis()
        var lane = laneFree.indices.firstOrNull { laneFree[it] <= now } ?: -1
        if (lane < 0) lane = laneFree.indices.minByOrNull { laneFree[it] } ?: 0
        val begin = maxOf(now, laneFree[lane])
        laneFree[lane] = begin + (commentMs * (w + 120f * sc()) / (getWidth() + w)).toLong()
        val hsv = floatArrayOf((Math.abs(who.hashCode()) % 360).toFloat(), 0.75f, 1f)
        floats.add(Floaty(line, Color.HSVToColor(hsv), false, begin, w, 0f, (150f + lane * 92f) * sc()))
    }
    private fun addEmoji(e: String) {
        if (e.isEmpty() || floats.size > 80) return
        val x = getWidth() * (0.08f + 0.84f * Math.random().toFloat())
        floats.add(Floaty(e.take(8), 0, true, SystemClock.uptimeMillis(), 0f, x, height.toFloat()))
    }

    // ---- pranks ----
    var sfx: Sfx? = null
    private val cracks = mutableListOf<Path>()
    private var lastCx = 0f
    private var lastCy = 0f
    private val crackDark = Paint().apply { style = Paint.Style.STROKE; color = Color.argb(150, 0, 0, 0); isAntiAlias = true; strokeJoin = Paint.Join.ROUND }
    private val crackLight = Paint().apply { style = Paint.Style.STROKE; color = Color.argb(235, 236, 240, 245); isAntiAlias = true; strokeJoin = Paint.Join.ROUND }
    private fun addCrack() {
        if (cracks.size >= 8) return
        val w = getWidth().toFloat(); val h = height.toFloat(); val s = sc()
        val r = Math.random().toFloat(); val r2 = Math.random().toFloat()
        if (cracks.isEmpty()) { lastCx = w * (0.3f + 0.4f * r); lastCy = h * (0.3f + 0.4f * r2) }
        else { lastCx = (lastCx + w * (r - 0.5f) * 0.5f).coerceIn(w * 0.1f, w * 0.9f)
               lastCy = (lastCy + h * (r2 - 0.5f) * 0.5f).coerceIn(h * 0.1f, h * 0.9f) }
        val reach = h * minOf(0.45f + 0.15f * cracks.size, 1.1f)
        val path = Path()
        for (l in CrackGen.make(lastCx, lastCy, reach, s)) {
            path.moveTo(l[0], l[1]); var i = 2
            while (i < l.size) { path.lineTo(l[i], l[i + 1]); i += 2 }
        }
        path.addCircle(lastCx, lastCy, 5f * s, Path.Direction.CW)
        cracks.add(path)
        sfx?.play("glass")
    }

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private fun asset(name: String): Bitmap? =
        try { context.assets.open(name).use { BitmapFactory.decodeStream(it) } } catch (e: Throwable) { null }

    // ---- fly: a real photo sprite that darts, stops, turns on the spot ----
    private var flyBmp: Bitmap? = null
    private var flyOn = false
    private var flyFlee = false
    private var fx = 0f; private var fy = 0f
    private var fa = 0f; private var faGoal = 0f          // heading, degrees
    private var ftx = 0f; private var fty = 0f
    private var flyPause = 0L
    private var flyLast = 0L
    private var flyFleeStart = 0L
    private fun toggleFly() {
        val w = getWidth().toFloat(); val h = height.toFloat()
        if (flyBmp == null) flyBmp = asset("fly.webp")
        if (flyOn) {            // already there -> it takes off and leaves through the nearest side
            flyFlee = true; flyFleeStart = SystemClock.uptimeMillis()
            ftx = if (fx < w / 2) -300f else w + 300f; fty = fy - h * 0.35f; flyPause = 0
        } else {
            flyOn = true; flyFlee = false; fx = -40f; fy = h * (0.2f + 0.6f * Math.random().toFloat()); fa = 0f; faGoal = 0f
            flyLast = SystemClock.uptimeMillis(); flyPause = 0
            ftx = w * (0.15f + 0.2f * Math.random().toFloat()); fty = fy + h * 0.1f * (Math.random().toFloat() - 0.5f)
        }
    }
    private fun flyTarget() {
        val w = getWidth().toFloat(); val h = height.toFloat(); val s = sc()
        val r = Math.random().toFloat()
        val a = Math.toRadians(fa.toDouble()) + (Math.random() - 0.5) * 2.6        // mostly onward, sometimes a sharp turn
        val d = (50f + 420f * r * r) * s                                           // many short hops, few long runs
        ftx = (fx + Math.cos(a).toFloat() * d).coerceIn(w * 0.04f, w * 0.96f)
        fty = (fy + Math.sin(a).toFloat() * d).coerceIn(h * 0.05f, h * 0.95f)
    }
    private fun turnToward(cur: Float, goal: Float, maxStep: Float): Float {
        var d = (goal - cur) % 360f; if (d > 180f) d -= 360f; if (d < -180f) d += 360f
        return if (Math.abs(d) <= maxStep) goal else cur + Math.signum(d) * maxStep
    }
    private fun stepFly(now: Long) {
        val dt = minOf(now - flyLast, 100L) / 1000f; flyLast = now
        if (now < flyPause) { fa = turnToward(fa, faGoal, 900f * dt); return }     // standing: maybe turning on the spot
        val dx = ftx - fx; val dy = fty - fy
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val speed = (if (flyFlee) 2800f else 560f) * sc()
        if (dist < speed * dt + 1f) {
            fx = ftx; fy = fty
            if (flyFlee) { flyOn = false; flyFlee = false; return }
            val r = Math.random()
            flyPause = now + 120 + (r * r * 2800).toLong()
            faGoal = if (Math.random() < 0.4) fa + (if (Math.random() < 0.5) -1 else 1) * (20f + 80f * Math.random().toFloat()) else fa
            flyTarget()
        } else {
            faGoal = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            fa = turnToward(fa, faGoal, 1500f * dt)
            fx += dx / dist * speed * dt; fy += dy / dist * speed * dt
        }
    }
    private fun drawFly(cv: Canvas, now: Long) {
        val bmp = flyBmp ?: return
        var hh = 74f * sc(); var alpha = 255
        if (flyFlee) { val k = minOf(1f, (now - flyFleeStart) / 350f); hh *= 1f + 0.7f * k; alpha = (255 * (1f - 0.6f * k)).toInt() }
        val ww = hh * bmp.width / bmp.height
        val moving = now >= flyPause
        val jit = if (moving) Math.sin(now / 17.0).toFloat() * 1.2f * sc() else 0f
        cv.save(); cv.translate(fx, fy); cv.rotate(fa + 90f); cv.translate(jit, 0f)
        bmpPaint.alpha = alpha
        cv.drawBitmap(bmp, null, RectF(-ww / 2, -hh / 2, ww / 2, hh / 2), bmpPaint)
        bmpPaint.alpha = 255
        cv.restore()
    }

    // ---- lion: a thick black frame closes in, a lion runs from far away and bursts out over the frame ----
    private var lionStart = 0L
    private var lionLoading = false
    private var lionBmps: Array<Bitmap?>? = null
    private var lionStep = -1
    private var lionRoared = false
    private val black = Paint().apply { color = Color.BLACK }
    private val shadowP = Paint().apply { color = Color.argb(95, 0, 0, 0); isAntiAlias = true }
    private fun startLion() {
        if (lionStart != 0L || lionLoading) return
        fun go() { lionStart = SystemClock.uptimeMillis(); lionStep = -1; lionRoared = false; invalidate() }
        if (lionBmps != null) { go(); return }
        lionLoading = true
        kotlin.concurrent.thread {
            val arr = arrayOf(asset("lion_a.webp"), asset("lion_b.webp"), asset("lion_c.webp"))
            post { lionBmps = arr; lionLoading = false; go() }
        }
    }
    private fun ease(x: Float) = x * x * (3f - 2f * x)
    private fun drawLion(cv: Canvas, now: Long): Boolean {
        val bm = lionBmps ?: return false
        val w = getWidth().toFloat(); val h = height.toFloat(); val s = sc()
        val el = (now - lionStart).toFloat()
        val tRun0 = 900f; val tRun1 = 5100f; val tLeap1 = 6300f; val tEnd = 7000f
        if (el >= tEnd) { lionStart = 0L; return false }
        val out = ((el - tLeap1) / (tEnd - tLeap1)).coerceIn(0f, 1f)
        val frame = ease((el / 700f).coerceIn(0f, 1f)) * (1f - ease(out))
        val bt = h * 0.14f * frame; val bs = h * 0.18f * frame
        var shake = 0f
        if (el > tRun1 - 500f && el < tLeap1) shake = 24f * s * (if (el < tRun1) (el - (tRun1 - 500f)) / 500f * 0.5f else 1f - 0.6f * (el - tRun1) / (tLeap1 - tRun1))
        cv.save()
        if (shake > 0f) cv.translate((Math.random().toFloat() - 0.5f) * 2f * shake, (Math.random().toFloat() - 0.5f) * 2f * shake)
        val m = 80f * s
        cv.drawRect(-m, -m, w + m, bt, black); cv.drawRect(-m, h - bt, w + m, h + m, black)
        cv.drawRect(-m, -m, bs, h + m, black); cv.drawRect(w - bs, -m, w + m, h + m, black)
        if (el >= tRun0 && el < tRun1) {
            val u = (el - tRun0) / (tRun1 - tRun0)
            val d = Math.pow(26.0, (1.0 - Math.pow(u.toDouble(), 1.15))).toFloat()      // distance: 26 lengths away -> 1
            val lh = h * 0.92f / d
            val ph = (el - tRun0) / 1000f * 2.7f                                         // strides per second
            val step = (ph * 2f).toInt()
            if (step != lionStep) { lionStep = step; sfx?.play("thump", 0.12f + 0.88f * u * u) }
            val b = bm[step % 2] ?: return true
            val anchor = if (step % 2 == 0) 0.46f else 0.5f                              // body centre inside each cut-out
            val lw = lh * b.width / b.height
            val gy = h * (0.50f + 0.43f / d)                                             // where the paws touch
            val bob = -Math.abs(Math.sin(ph * 2 * Math.PI)).toFloat() * 0.045f * lh
            val sway = Math.sin(ph * Math.PI).toFloat()
            val cx = w / 2 + sway * 0.035f * lh
            cv.drawOval(RectF(cx - lw * 0.42f, gy - lh * 0.035f, cx + lw * 0.42f, gy + lh * 0.035f), shadowP)
            cv.save(); cv.rotate(sway * 2.5f, cx, gy)
            cv.drawBitmap(b, null, RectF(cx - lw * anchor, gy - lh + bob, cx + lw * (1f - anchor), gy + bob), bmpPaint)
            cv.restore()
        } else if (el >= tRun1) {
            val c = bm[2]
            if (c != null) {
                val v = ((el - tRun1) / (tLeap1 - tRun1)).coerceIn(0f, 1f)
                val e = 1f - (1f - v) * (1f - v) * (1f - v)
                val lh = h * (1.12f + 1.0f * e)
                val lw = lh * c.width / c.height
                bmpPaint.alpha = (255 * (1f - out)).toInt()
                cv.drawBitmap(c, null, RectF(w / 2 - lw * 0.47f, h * 0.5f - lh * 0.5f, w / 2 + lw * 0.53f, h * 0.5f + lh * 0.5f), bmpPaint)
                bmpPaint.alpha = 255
            }
        }
        if (!lionRoared && el >= tRun1 - 350f) { lionRoared = true; sfx?.play("roar") }
        cv.restore()
        return true
    }

    // ---- notice banner (prayer alerts) ----
    var onPrayerCmd: ((List<String>) -> Unit)? = null
    private var notice: String? = null
    private var noticeStart = 0L
    private val noticeMs = 25000f
    private val noticeBg = Paint().apply { color = Color.argb(240, 12, 74, 50); isAntiAlias = true }
    private val noticeEdge = Paint().apply { color = Color.argb(255, 212, 175, 55); style = Paint.Style.STROKE; isAntiAlias = true }
    private val noticeTx = Paint().apply { color = Color.WHITE; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    fun showNotice(text: String) { notice = "🕌  $text"; noticeStart = SystemClock.uptimeMillis(); invalidate() }
    private fun drawNotice(cv: Canvas, now: Long): Boolean {
        val t = notice ?: return false
        val el = (now - noticeStart).toFloat()
        if (el >= noticeMs) { notice = null; return false }
        val s = sc(); val w = getWidth().toFloat()
        val k = ease(minOf(1f, el / 450f)) * ease(minOf(1f, (noticeMs - el) / 450f))       // slide in, slide out
        noticeTx.textSize = 46f * s
        val tw = noticeTx.measureText(t); val ch = 110f * s; val cw = tw + 110f * s
        val top = 54f * s * k - ch * (1f - k)
        val r = RectF(w / 2 - cw / 2, top, w / 2 + cw / 2, top + ch)
        cv.drawRoundRect(r, ch / 2, ch / 2, noticeBg)
        noticeEdge.strokeWidth = 3f * s; cv.drawRoundRect(r, ch / 2, ch / 2, noticeEdge)
        cv.drawText(t, w / 2, top + ch / 2 + 16f * s, noticeTx)
        return true
    }

    // ---- picture sent from the phone ----
    private var img: Bitmap? = null
    private var imgDim = true
    fun showImage(b: Bitmap?) { val old = img; img = b; if (old != null && old != b) old.recycle(); invalidate() }
    private fun drawImage(cv: Canvas) {
        val b = img ?: return
        val w = getWidth().toFloat(); val h = height.toFloat()
        if (imgDim) cv.drawColor(Color.argb(215, 0, 0, 0))
        val k = minOf(w * 0.94f / b.width, h * 0.94f / b.height)
        val bw = b.width * k; val bh = b.height * k
        cv.drawBitmap(b, null, RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), bmpPaint)
    }

    private var offStart = 0L          // fake "TV will shut down" dialog
    private var revealUntil = 0L
    private val dlg = Paint().apply { isAntiAlias = true }
    private val dlgTx = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private fun clearPranks() { cracks.clear(); flyOn = false; flyFlee = false; offStart = 0L; lionStart = 0L; sfx?.buzz(false, false) }
    private fun reveal() { clearPranks(); revealUntil = SystemClock.uptimeMillis() + 4500 }
    private fun prank(what: String) {
        when (what) {
            "crack" -> addCrack()
            "fly" -> toggleFly()
            "lion" -> startLion()
            "off" -> { offStart = SystemClock.uptimeMillis(); revealUntil = 0L }
            "reveal" -> reveal()
            "clear" -> { clearPranks(); revealUntil = 0L }
        }
    }
    private fun drawPranks(cv: Canvas, now: Long): Boolean {
        val s = sc(); val w = getWidth().toFloat(); val h = height.toFloat()
        var anim = false
        drawImage(cv)
        if (cracks.isNotEmpty()) {
            crackDark.strokeWidth = 5f * s; crackLight.strokeWidth = 2.2f * s
            for (p in cracks) { cv.save(); cv.translate(1.8f * s, 1.8f * s); cv.drawPath(p, crackDark); cv.restore(); cv.drawPath(p, crackLight) }
        }
        if (flyOn) {
            stepFly(now); if (flyOn) drawFly(cv, now); anim = true
            sfx?.buzz(flyOn && now >= flyPause, flyFlee)
        }
        if (lionStart != 0L && drawLion(cv, now)) anim = true
        if (lionLoading) anim = true
        if (drawNotice(cv, now)) anim = true
        if (offStart > 0L) {
            val el = now - offStart
            if (el < 10000) {
                cv.drawColor(Color.argb(120, 0, 0, 0))
                val cw = 980f * s; val ch = 330f * s; val l = (w - cw) / 2; val t = (h - ch) / 2
                dlg.color = Color.argb(255, 32, 33, 36); cv.drawRoundRect(RectF(l, t, l + cw, t + ch), 26f * s, 26f * s, dlg)
                dlgTx.typeface = Typeface.DEFAULT; dlgTx.color = Color.argb(255, 154, 160, 166); dlgTx.textSize = 30f * s
                cv.drawText(context.getString(R.string.sys_alert), w / 2, t + 70f * s, dlgTx)
                dlgTx.typeface = Typeface.DEFAULT_BOLD; dlgTx.color = Color.WHITE; dlgTx.textSize = 46f * s
                val left = 10 - (el / 1000).toInt()
                cv.drawText(context.str(R.string.shutdown_in, left), w / 2, t + 165f * s, dlgTx)
                val bl = l + 70f * s; val br = l + cw - 70f * s; val by = t + 240f * s
                dlg.color = Color.argb(255, 60, 64, 67); cv.drawRoundRect(RectF(bl, by, br, by + 12f * s), 6f * s, 6f * s, dlg)
                dlg.color = Color.argb(255, 138, 180, 248)
                cv.drawRoundRect(RectF(bl, by, bl + (br - bl) * (1f - el / 10000f), by + 12f * s), 6f * s, 6f * s, dlg)
            } else if (el < 12500) {
                cv.drawColor(Color.BLACK)
            } else reveal()
            anim = true
        }
        if (now < revealUntil) {
            dlgTx.typeface = Typeface.DEFAULT_BOLD; dlgTx.textSize = 120f * s
            val msg = context.getString(R.string.pranked)
            val tw = dlgTx.measureText(msg)
            dlg.color = Color.argb(240, 255, 214, 10)
            cv.drawRoundRect(RectF(w / 2 - tw / 2 - 70f * s, h / 2 - 130f * s, w / 2 + tw / 2 + 70f * s, h / 2 + 90f * s), 50f * s, 50f * s, dlg)
            dlgTx.color = Color.BLACK; cv.drawText(msg, w / 2, h / 2 + 30f * s, dlgTx)
            anim = true
        } else if (revealUntil != 0L) { revealUntil = 0L; anim = true }
        return anim
    }

    // ---- info card: address + QR ----
    private var url: String? = null
    private var qr: Bitmap? = null
    private var infoUntil = 0L
    private val cardBg = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val cardTx = Paint().apply { color = Color.BLACK; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
    fun setUrl(u: String, withQr: Boolean) {
        url = u
        if (quietStart) { withQrOnly(u, withQr); return }
        if (withQr) try {
            val m = com.google.zxing.qrcode.QRCodeWriter().encode(u, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
            val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
            for (y in 0 until m.height) for (x in 0 until m.width)
                bmp.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
            qr = bmp
        } catch (e: Exception) { }
        showInfo()
    }
    var quietStart = false               // started by the box booting: don't pop the QR card
    private fun withQrOnly(u: String, withQr: Boolean) {
        if (withQr) try {
            val m = com.google.zxing.qrcode.QRCodeWriter().encode(u, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
            val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
            for (y in 0 until m.height) for (x in 0 until m.width)
                bmp.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
            qr = bmp
        } catch (e: Exception) { }
    }
    fun showInfo() {
        infoUntil = SystemClock.uptimeMillis() + 30000
        postDelayed({ invalidate() }, 30200)
        invalidate()
    }

    /** cid = which phone sent the line, so several phones can draw and point at the same time. */
    fun handle(line: String, cid: String = "tv") {
        try {
            val p = line.trim().split(" ")
            when (p[0]) {
                "d" -> ink.down(cid, p[1].toFloat() * getWidth(), p[2].toFloat() * height)
                "m" -> ink.move(cid, p[1].toFloat() * getWidth(), p[2].toFloat() * height)
                "x" -> ink.end(cid)
                "v" -> game.tilt(cid, p[1].toFloat(), p[2].toFloat())
                "y" -> { if (p[1] == "join") { if (hockey.active) hockey.stopNow(); if (snake.active) snake.stopNow() }; game.command(p, cid) }
                "j" -> { if (p[1] == "join") { if (hockey.active) hockey.stopNow(); if (game.active) game.stopNow() }; snake.command(p, cid) }
                "r" -> snake.steer(cid, p[1].toFloat(), p[2].toFloat())
                "o" -> hockey.pos(cid, p[1].toFloat(), p[2].toFloat())
                "h" -> { if (p[1] == "join") { if (game.active) game.stopNow(); if (snake.active) snake.stopNow() }; hockey.command(p, cid) }
                "c" -> ink.clearAll()
                "u" -> ink.undo(cid)
                "k" -> ink.pen(cid).color = Color.parseColor(p[1])
                "w" -> ink.pen(cid).width = p[1].toFloat().coerceIn(1f, 120f)
                "g" -> { ink.end(cid); ink.pen(cid).tool = p[1] }
                "a" -> ink.pen(cid).nib = p[1].toFloat().coerceIn(0f, 180f)
                "n" -> ink.page = p[1]
                "l" -> when (p[1]) {
                    "off" -> ink.laserOff(cid)
                    "mode" -> ink.laserMode = p[2]
                    "r" -> ink.spotRadius = p[2].toFloat().coerceIn(60f, 700f)
                    else -> ink.laser(cid, p[1].toFloat() * getWidth(), p[2].toFloat() * height)
                }
                "t" -> addComment(dec(p[1]), dec(p[2]))
                "e" -> addEmoji(dec(p[1]))
                "q" -> showInfo()
                "p" -> { prank(p[1]); invalidate() }
                "b" -> sfx?.play("beep", 1f, true)
                "z" -> onPrayerCmd?.invoke(p)
                "i" -> { if (p[1] == "0") showImage(null) else if (p[1] == "d") { imgDim = p[2] == "1"; invalidate() } }
                "f" -> cam.command(p, cid)
                "s" -> { sfx?.muted = p[1] == "0"; if (p[1] == "0") sfx?.buzz(false, false) }
            }
        } catch (e: Exception) { }
    }

    override fun onDraw(cv: Canvas) {
        val now = SystemClock.uptimeMillis()
        val gaming = game.draw(cv, now) or hockey.draw(cv, now) or snake.draw(cv, now)
        ink.draw(cv)
        val s = sc()
        val it = floats.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val t = (now - f.start).toFloat() / (if (f.emoji) emojiMs else commentMs)
            if (t >= 1f) { it.remove(); continue }
            if (t < 0f) continue
            if (f.emoji) {
                emo.textSize = 110f * s
                emo.alpha = if (t > 0.7f) (255 * (1f - t) / 0.3f).toInt() else 255
                cv.drawText(f.text, f.x0, f.y0 - t * height * 0.6f, emo)
            } else {
                txt.textSize = 46f * s
                val x = getWidth() - t * (getWidth() + f.w + 60f * s)
                val r = RectF(x - 26f * s, f.y0 - 52f * s, x + f.w + 26f * s, f.y0 + 22f * s)
                cv.drawRoundRect(r, 37f * s, 37f * s, pill)
                edge.color = f.color; edge.strokeWidth = 4f * s
                cv.drawRoundRect(r, 37f * s, 37f * s, edge)
                cv.drawText(f.text, x, f.y0, txt)
            }
        }
        val pranking = drawPranks(cv, now)
        val u = url
        if (u != null && now < infoUntil) {
            cardTx.textSize = 34f * s
            val q = qr
            val side = 300f * s
            val w = maxOf(cardTx.measureText(u), if (q != null) side else 0f) + 48f * s
            val h = (if (q != null) side + 24f * s else 0f) + 78f * s
            val l = 48f * s; val tp = 48f * s
            cv.drawRoundRect(RectF(l, tp, l + w, tp + h), 20f * s, 20f * s, cardBg)
            var y = tp + 24f * s
            if (q != null) {
                val qx = l + (w - side) / 2
                cv.drawBitmap(q, null, RectF(qx, y, qx + side, y + side), null)
                y += side + 24f * s
            }
            cv.drawText(u, l + 24f * s, y + 30f * s, cardTx)
        }
        cam.draw(cv, now, s)
        val lasering = ink.drawLaser(cv, now)
        if (floats.isNotEmpty() || pranking || lasering || gaming) postInvalidateOnAnimation()
    }
}

class OverlayService : Service() {
    private lateinit var view: DrawView
    private val ui = Handler(Looper.getMainLooper())
    private val camBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    override fun onBind(i: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("d", getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "d").setContentTitle(getString(R.string.running))
            .setSmallIcon(android.R.drawable.ic_menu_edit).build())

        view = DrawView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT)
        getSystemService(WindowManager::class.java).addView(view, lp)

        val sfx = Sfx(this); view.sfx = sfx; view.game.sfx = sfx; view.hockey.sfx = sfx; view.snake.sfx = sfx
        val prayer = PrayerAlerts(this, view, sfx); view.onPrayerCmd = { prayer.command(it) }; prayer.start()
        thread {
            val server = Server({ page }, filesDir, { lines, cid -> ui.post { lines.forEach { view.handle(it, cid) }; view.invalidate() } }, { sfx.voice(it) },
                { bytes -> try {
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                    var ss = 1; while (o.outWidth / ss > 2400 || o.outHeight / ss > 2400) ss *= 2
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = ss })
                    if (bmp != null) ui.post { view.showImage(bmp) }
                } catch (e: Throwable) { } },
                { bytes, cid -> try {                                                    // selfie for a snake head
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) ui.post { view.snake.setFace(cid, bmp); bmp.recycle() }
                } catch (e: Throwable) { } },
                { bytes, cid -> if (camBusy.compareAndSet(false, true)) try {            // live camera frame; frames that arrive too fast are dropped
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ui.post { if (bmp != null) view.cam.frame(cid, bmp); camBusy.set(false) }
                } catch (e: Throwable) { camBusy.set(false) } },
                { path -> if (path.startsWith("/p")) prayer.status()
                    else if (path.startsWith("/c")) view.cam.status(path.substringAfter("c=", "x").filter { it.isLetterOrDigit() }.take(12))
                    else if (path.startsWith("/j")) view.snake.status(path.substringAfter("c=", "x").filter { it.isLetterOrDigit() }.take(12))
                    else if (path.startsWith("/h")) view.hockey.status(path.substringAfter("c=", "x").filter { it.isLetterOrDigit() }.take(12))
                    else if (path.startsWith("/g")) view.game.status(path.substringAfter("c=", "x").filter { it.isLetterOrDigit() }.take(12))
                    else sfx.status() })
            server.start()
            val ok = server.httpPort != 0
            val msg = if (ok) "http://" + localIp() + ":" + server.httpPort else getString(R.string.no_port)
            ui.post { view.setUrl(msg, ok) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("boot", false) == true) view.quietStart = true
        return START_STICKY
    }

    /** The phone page with every translation from assets/web/lang built in, so the page needs a single request. */
    private val page: String by lazy {
        val langs = (assets.list("web/lang") ?: arrayOf()).filter { it.endsWith(".json") }.sorted().joinToString(",") {
            "\"" + it.removeSuffix(".json") + "\":" + assets.open("web/lang/$it").use { s -> s.readBytes().toString(Charsets.UTF_8) }
        }
        assets.open("web/index.html").use { it.readBytes().toString(Charsets.UTF_8) }.replace("__LANGS__", "{$langs}")
    }

    private fun localIp(): String {
        try {
            for (ni in java.net.NetworkInterface.getNetworkInterfaces()) for (a in ni.inetAddresses)
                if (!a.isLoopbackAddress && a is java.net.Inet4Address) return a.hostAddress ?: "?"
        } catch (e: Exception) { }
        return "?"
    }
}
