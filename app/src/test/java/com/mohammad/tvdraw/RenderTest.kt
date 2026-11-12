package com.mohammad.tvdraw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import android.view.View
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.io.FileOutputStream
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RenderTest {
    private val out = System.getProperty("renderOut", "/tmp").also { File(it).mkdirs() }
    /** -DrenderLang=ar renders the TV texts in another language. */
    @org.junit.Before fun language() { System.getProperty("renderLang", "").let { if (it.isNotEmpty()) org.robolectric.RuntimeEnvironment.setQualifiers("+$it") } }
    private lateinit var v: DrawView
    private var fill: () -> Unit = {}
    private fun shot(name: String) {
        fill()
        val b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val bg = Paint(); bg.shader = LinearGradient(0f, 0f, 1920f, 1080f, 0xFF2B5F9E.toInt(), 0xFF7A3F8F.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1920f, 1080f, bg)                      // stands in for the TV picture
        v.draw(c)
        FileOutputStream(File(out, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun tick(ms: Long) { ShadowSystemClock.advanceBy(Duration.ofMillis(ms)); shadowOf(Looper.getMainLooper()).idle(); fill() }
    private fun line(cid: String, tool: String, color: String, w: Int, pts: List<Pair<Float, Float>>, nib: Int = 60) {
        v.handle("g $tool", cid); v.handle("k $color", cid); v.handle("w $w", cid); v.handle("a $nib", cid)
        v.handle("d ${pts[0].first} ${pts[0].second}", cid)
        for (p in pts.drop(1)) v.handle("m ${p.first} ${p.second}", cid)
        v.handle("x", cid)
    }
    private fun wave(x0: Float, x1: Float, y: Float, amp: Float, n: Int = 60) =
        (0..n).map { val t = it / n.toFloat(); Pair(x0 + (x1 - x0) * t, y + amp * Math.sin(t * Math.PI * 4).toFloat()) }

    @Test fun render() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        act.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        tick(1000)

        // 1) every tool over the TV picture, two phones
        line("a", "pen", "#ff3b30", 10, wave(0.05f, 0.45f, 0.10f, 0.03f))
        line("a", "brush", "#ffd60a", 16, wave(0.05f, 0.45f, 0.22f, 0.03f, 25))
        line("a", "hl", "#34c759", 12, wave(0.05f, 0.45f, 0.34f, 0.0f))
        line("a", "neon", "#0a84ff", 9, wave(0.05f, 0.45f, 0.46f, 0.03f))
        line("b", "qalam", "#ffffff", 26, wave(0.55f, 0.95f, 0.12f, 0.04f))
        line("b", "qalam", "#ffffff", 26, listOf(Pair(0.60f, 0.24f), Pair(0.60f, 0.40f)))            // vertical (alif)
        line("b", "qalam", "#ffffff", 26, listOf(Pair(0.90f, 0.32f), Pair(0.66f, 0.32f)))            // horizontal
        line("b", "qalam", "#ffffff", 26, listOf(Pair(0.72f, 0.42f)))                               // dot
        line("b", "qalam", "#ffffff", 26, (0..40).map { val t = it / 40.0 * Math.PI * 2; Pair(0.86f + 0.035f * Math.cos(t).toFloat(), 0.44f + 0.06f * Math.sin(t).toFloat()) })
        line("a", "line", "#ff9f0a", 8, listOf(Pair(0.06f, 0.62f), Pair(0.30f, 0.72f)))
        line("a", "arrow", "#ff375f", 8, listOf(Pair(0.06f, 0.80f), Pair(0.30f, 0.90f)))
        line("b", "rect", "#64d2ff", 8, listOf(Pair(0.36f, 0.62f), Pair(0.56f, 0.90f)))
        line("b", "oval", "#bf5af2", 8, listOf(Pair(0.60f, 0.62f), Pair(0.80f, 0.90f)))
        line("a", "eraser", "#000000", 14, listOf(Pair(0.02f, 0.02f), Pair(0.50f, 0.56f)))            // erases across the left strokes
        v.handle("l mode dot", "a"); v.handle("l 0.9 0.75", "a"); v.handle("l 0.3 0.55", "b")
        tick(120); shot("ink_tools"); tick(60); shot("ink_tools")
        v.handle("u", "b"); v.handle("l off", "a"); v.handle("l off", "b"); shot("ink_undo_b")

        // 2) ruled pages
        v.handle("c", "a")
        for (pg in listOf("lines", "calli", "grid", "dots", "board")) {
            v.handle("n $pg", "a")
            line("a", "qalam", if (pg == "board") "#ffffff" else "#1c1c1e", 30, wave(0.2f, 0.8f, 0.40f, 0.05f), 65)
            shot("page_$pg"); v.handle("c", "a")
        }
        v.handle("n none", "a")

        // 3) spotlight, notice, fly, crack, lion
        v.handle("l mode spot", "a"); v.handle("l r 260", "a"); v.handle("l 0.6 0.5", "a"); tick(100); shot("spot"); shot("spot"); shot("spot")
        v.handle("l off", "a"); v.handle("l mode dot", "a")
        v.showNotice(v.context.str(R.string.prayer_before, 15, v.context.getString(R.string.asr), "3:18")); tick(1500); shot("notice")
        v.handle("p crack", "a"); v.handle("p fly", "a"); for (i in 0..40) { tick(50); shot("fly") }
        v.handle("p clear", "a"); tick(26000)
        v.handle("l mode dot", "a"); v.handle("k #ff3b30", "a"); v.handle("l 0.25 0.3", "a"); v.handle("k #34c759", "b"); v.handle("l 0.7 0.6", "b"); tick(100); shot("laser"); tick(100); shot("laser")
        v.handle("l off", "a"); v.handle("l off", "b")
        v.handle("p lion", "a"); Thread.sleep(2500); tick(10)
        tick(3300); shot("lion_far"); tick(1000); shot("lion_near"); tick(650); shot("lion_out"); tick(600); shot("lion_leap")
    }

    @Test fun replay() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        tick(1000)
        var shots = 0
        for (l in File(System.getProperty("cmdLog")).readLines()) {
            val t = l.indexOf('\t'); if (t < 0) continue
            val cid = l.substring(0, t); val cmd = l.substring(t + 1)
            if (cmd == "u") { shot("replay_before_undo") }
            if (cmd.startsWith("l mode spot")) { tick(60); shot("replay_laser"); tick(60); shot("replay_laser") }
            v.handle(cmd, cid)
        }
        shot("replay_final_draw_hidden_by_spot")
        v.handle("l off", "x"); for (c in listOf("21g70v", "5jwc4l")) v.handle("l off", c)
        tick(5000); shot("replay_final")
    }

    private val tiny by lazy { Canvas(Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888)) }
    private fun frames(ms: Int) { var t = 0; while (t < ms) { ShadowSystemClock.advanceBy(Duration.ofMillis(16)); fill(); v.draw(tiny); t += 16 } }
    @Test fun game() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        tick(1000)
        v.handle("y join r", "a"); frames(200); shot("game_lobby1"); println("STATUS a " + v.game.status("a") + " | b " + v.game.status("b"))
        v.handle("y join b", "b"); v.handle("y join r", "c"); frames(200); shot("game_lobby2"); println("STATUS c(should be -1) " + v.game.status("c"))
        v.handle("y start", "a"); frames(1300); shot("game_count"); frames(1800)
        v.handle("v 1 0", "a"); v.handle("v -1 0", "b")
        var hit = false
        for (i in 0 until 80) { frames(32); v.handle("v 1 0", "a"); v.handle("v -1 0", "b"); if (!hit && v.game.status("a").split("|")[4] != "0") { hit = true; frames(48); shot("game_collide"); println("collided after " + (i * 32) + "ms") } }
        // red eases toward the right hoop, blue wanders off upward
        var scored = false
        for (i in 0 until 400) { v.handle("v 0.55 0", "a"); v.handle("v 0 -0.8", "b"); frames(32)
            if (i == 20) shot("game_play")
            val st = v.game.status("a").split("|")
            if (!scored && st[0] == "4") { scored = true; frames(250); shot("game_goal"); println("GOAL status " + v.game.status("a")); break } }
        println("scored=" + scored)
        // too fast: full tilt straight at the hoop should rim out, not score
        frames(4000); val before = v.game.status("a")
        for (i in 0 until 60) { v.handle("v 1 0", "a"); v.handle("v 0 -1", "b"); frames(32) }
        println("after full-speed run: " + before + " -> " + v.game.status("a"))
        v.handle("y dash", "a"); frames(100); shot("game_dash")
        for (i in 0 until 3200) { frames(32); if (v.game.status("a").startsWith("5|")) break }
        frames(600); shot("game_end"); println("END status " + v.game.status("a"))
        v.handle("y stop", "a"); frames(100); println("after stop " + v.game.status("a"))
    }

    @Test fun gameReplay() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        tick(5000)
        var t = -1L; var n = 0; var next = 0L; var maxScore = "0|0"
        for (l in File(System.getProperty("cmdLog")).readLines()) {
            val p = l.split("\t"); if (p.size < 3) continue
            val ms = p[0].toLong(); if (t < 0) t = ms
            if (ms > t) { frames((ms - t).toInt()); t = ms }
            v.handle(p[2], p[1])
            if (p[2].startsWith("y stop")) { shot("greplay_last") }
            val st = v.game.status("x").split("|")
            if (st[0] != "0") maxScore = st[1] + "|" + st[2] + " state " + st[0] + " bumps " + st[4]
            if (ms >= next && st[0] == "3") { shot("greplay_" + n); n++; next = ms + 1100 }
        }
        println("REPLAY shots=" + n + " last=" + maxScore)
    }

    @Test fun solo() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        tick(5000)
        v.handle("y join r", "a"); frames(200); shot("solo_lobby")
        for (lv in 0..2) {
            // (1) computer against a player who never moves: how fast does it score on its own?
            v.handle("y level $lv", "a"); v.handle("y start", "a"); var t = 0
            while (t < 100000) { frames(160); t += 160; v.handle("v 0 0", "a"); if (lv == 1 && t == 8000) shot("solo_play"); if (v.game.status("a").startsWith("5|")) break }
            println("SOLO level $lv vs idle player: " + v.game.status("a")); frames(9500)
            // (2) against a simple attacking player: full tilt toward the hoop, easing off near it
            v.handle("y start", "a"); t = 0; var x = 0.30f
            while (t < 100000) { frames(64); t += 64
                v.handle("v " + (if ((t / 64) % 40 < 26) "0.6" else "0.25") + " " + (if ((t / 640) % 2 == 0) "0.05" else "-0.05"), "a")
                if (v.game.status("a").startsWith("5|")) break }
            println("SOLO level $lv vs attacking player: " + v.game.status("a")); frames(9500)
        }
        v.handle("y join b", "b"); frames(100); println("SOLO second player joined: " + v.game.status("b"))
    }

    @Test fun hockey() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        tick(5000)
        v.handle("y join r", "z"); frames(100); v.handle("h join r", "a"); frames(200)
        println("HOCKEY basketball stopped when hockey joined: " + v.game.status("z") + " | hockey " + v.hockey.status("a")); shot("hockey_lobby")
        for (lv in 0..2) {
            v.handle("h level $lv", "a"); v.handle("h start", "a"); frames(1400); if (lv == 0) shot("hockey_count"); frames(1800)
            var t = 0; var n = 0
            while (t < 150000) { frames(48); t += 48
                // scripted human: sweep the striker forward and back while wandering up and down
                val ph = (t % 900) / 900f; val x = if (ph < 0.45f) 0.15f + ph * 1.7f else 0.9f - (ph - 0.45f) * 1.36f
                val y = 0.5f + 0.32f * Math.sin(t / 700.0).toFloat()
                v.handle("o $x $y", "a")
                if (lv == 1 && t % 1680 == 0 && n < 4) { shot("hockey_play$n"); n++ }
                val st = v.hockey.status("a"); if (lv == 1 && st.startsWith("4|") && n < 6) { frames(200); shot("hockey_goal"); n = 9 }
                if (st.startsWith("5|")) break }
            frames(500); if (lv == 1) shot("hockey_end")
            println("HOCKEY level $lv final after " + (t / 1000) + "s: " + v.hockey.status("a")); frames(9500)
        }
        v.handle("h join k", "b"); frames(100); println("HOCKEY second player: " + v.hockey.status("b"))
        v.handle("h stop", "a"); frames(100); println("HOCKEY after stop: " + v.hockey.status("a"))
    }

    @Test fun snake() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        tick(5000)
        v.handle("h join r", "z"); frames(100); v.handle("j join g", "a"); frames(100)
        println("SNAKE hockey stopped when snake joined: " + v.hockey.status("z") + " | " + v.snake.status("a"))
        v.snake.setFace("a", android.graphics.BitmapFactory.decodeStream(act.assets.open("lion_c.webp")))
        v.handle("j join o", "b"); v.handle("j join g", "c"); frames(200); shot("snake_lobby"); println("SNAKE c should be -1: " + v.snake.status("c"))
        v.handle("j start", "a"); frames(1500); shot("snake_count"); frames(1600)
        frames(6650); shot("snake_wrap"); println("SNAKE after 6.6s straight: " + v.snake.status("a"))
        var t = 0; while (t < 12000 && !v.snake.status("a").startsWith("5|")) { v.handle("r -0.2 -1", "b"); frames(48); t += 48; if (t == 480) shot("snake_turn") }
        frames(400); shot("snake_end_crash"); println("SNAKE crash end: " + v.snake.status("a"))
        frames(10500); v.handle("j leave", "b"); frames(100); v.handle("j level 2", "a"); v.handle("j face off", "a"); frames(100); shot("snake_lobby_solo")
        v.handle("j start", "a"); frames(3100); t = 0
        while (t < 95000 && !v.snake.status("a").startsWith("5|")) { val a = t / 1000.0 * 0.9; v.handle("r " + Math.cos(a) + " " + Math.sin(a), "a"); frames(48); t += 48; if (t == 9600) shot("snake_solo_play") }
        frames(400); shot("snake_end_solo"); println("SNAKE solo end after " + (t / 1000) + "s: " + v.snake.status("a"))
        v.handle("j stop", "a"); frames(100); println("SNAKE after stop: " + v.snake.status("a"))
    }

    @Test fun snakeReplay() {
        val face = System.getProperty("faceFile", ""); if (face.isEmpty()) return
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        tick(5000)
        var t = -1L; var n = 0; var next = 0L; var lastSt = ""
        for (l in File(System.getProperty("cmdLog")).readLines()) {
            val p = l.split("\t"); if (p.size < 3) continue
            val ms = p[0].toLong(); if (t < 0) t = ms
            if (ms > t) { frames((ms - t).toInt()); t = ms }
            if (p[2].startsWith("FACE")) { v.snake.setFace(p[1], android.graphics.BitmapFactory.decodeFile(face)); frames(50); shot("sreplay_lobby"); continue }
            if (p[2].startsWith("j stop") || p[2].startsWith("j leave")) { if (n < 90) { shot("sreplay_last"); n = 99 } }
            v.handle(p[2], p[1])
            val st = v.snake.status(p[1]); if (st.split("|")[0] != "0") lastSt = st
            if (ms >= next && st.startsWith("3|") && n < 6) { shot("sreplay_" + n); n++; next = ms + 900 }
        }
        println("SREPLAY last status " + lastSt)
    }

    @Test fun liveCamera() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        fill()
        fun pic(w: Int, h: Int): Bitmap { val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(b)
            c.drawColor(0xFF3A6B4F.toInt()); val p = Paint().apply { color = 0xFFF2C9A0.toInt(); isAntiAlias = true }; c.drawCircle(w / 2f, h * 0.42f, minOf(w, h) * 0.24f, p); return b }
        v.handle("f name Sam", "a"); v.handle("f pos br", "a"); v.handle("f size 1", "a")
        v.cam.frame("a", pic(480, 360)); shot("cam_corner")
        assertTrue(v.cam.status("a").startsWith("live=1|mine=1"))
        v.cam.frame("b", pic(360, 480))                                   // a second phone cannot take over while the first is live
        assertTrue(v.cam.status("b").startsWith("live=1|mine=0"))
        v.handle("f pos tl", "a"); v.handle("f size 2", "a"); v.cam.frame("a", pic(360, 480)); shot("cam_tall_topleft")
        v.handle("f size 3", "a"); v.cam.frame("a", pic(480, 360)); shot("cam_full")
        tick(LiveCam.TIMEOUT + 200); shot("cam_gone")                     // frames stopped: the window goes away
        assertTrue(v.cam.status("a").startsWith("live=0"))
        v.cam.frame("b", pic(480, 360)); assertTrue(v.cam.status("b").startsWith("live=1|mine=1"))
    }
}
