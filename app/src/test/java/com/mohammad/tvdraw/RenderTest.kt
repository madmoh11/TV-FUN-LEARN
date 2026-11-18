package com.mohammad.tvdraw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Looper
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        v.handle("bug s 300", "a"); for (i in 0..30) { tick(50); shot("fly_big") }
        v.handle("p fly", "a"); for (i in 0..3) { tick(60); shot("fly_away") }; tick(2000); shot("fly_gone"); v.handle("bug s 100", "a")
        v.handle("p clear", "a"); tick(26000)
        v.handle("l mode dot", "a"); v.handle("k #ff3b30", "a"); v.handle("l 0.25 0.3", "a"); v.handle("k #34c759", "b"); v.handle("l 0.7 0.6", "b"); tick(100); shot("laser"); tick(100); shot("laser")
        v.handle("l off", "a"); v.handle("l off", "b")
        for (k in listOf("roach", "spider", "ant", "wasp")) {
            v.handle("bug k $k", "a"); v.handle("bug s 200", "a"); v.handle("p fly", "a"); for (i in 0..24) { tick(50); shot("bug_$k") }
            v.handle("p fly", "a"); tick(60); shot("bug_x"); tick(60); shot("bug_${k}_away"); for (i in 0..60) { tick(50); shot("bug_x") }
        }
        v.handle("bug k nosuch", "a"); v.handle("bug k fly", "a"); v.handle("bug s 100", "a")
        v.handle("p man", "a"); tick(700); shot("walk_man1"); tick(500); shot("walk_man2"); tick(250); shot("walk_man3"); tick(4000); shot("walk_gone")
        v.handle("p girl", "a"); tick(800); shot("walk_girl1"); tick(300); shot("walk_girl2"); tick(700); shot("walk_girl3"); tick(5000)
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

    @Test fun tugOfWar() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        fill()
        v.handle("tug join l Sara", "a"); shot("tug_lobby_solo")
        v.handle("tug join r Omar", "b"); v.handle("tug join l Lina", "c"); v.handle("tug join r Ali", "d"); v.handle("tug join r Noor", "e"); v.handle("tug join l Zaid", "f")
        v.handle("tug join r Late", "g")                                   // a seventh player does not fit
        assertTrue(v.tug.status("g").split("|")[8] == "-1"); assertTrue(v.tug.status("a").startsWith("1|0|90|"))
        v.handle("tug level 2", "a"); shot("tug_lobby")
        v.handle("tug start", "a"); tick(1200); shot("tug_count"); tick(2000); shot("tug_go")       // the game only moves on when it is drawn
        assertTrue(v.tug.status("a").startsWith("3|"))
        repeat(5) { v.handle("tug hit 3 800", "a") }; v.handle("tug miss", "b"); tick(700); shot("tug_play_left_ahead")
        assertTrue(v.tug.status("a").split("|")[9] == "1")               // five in a row: boosted
        repeat(4) { v.handle("tug hit 4 600", "b"); v.handle("tug hit 4 600", "d") }; tick(900); shot("tug_play_right_ahead")
        repeat(3) { v.handle("tug hit 4 500", "e") }; tick(600); shot("tug_end")
        assertTrue("right team should have won: " + v.tug.status("a"), v.tug.status("a").startsWith("5|1000|") && v.tug.status("a").split("|")[11] == "1")
        // phones that stop asking for the status lose their place; the last one leaving closes the game
        for (c in listOf("a", "b", "c", "d", "e")) v.tug.status(c); tick(5000); shot("tug_x1"); for (c in listOf("a", "b", "c", "d", "e")) v.tug.status(c); tick(5000); shot("tug_x2")
        assertTrue("the phone that went away should be gone: " + v.tug.status("a"), v.tug.status("a").split("|")[6] == "2")
        v.handle("tug join l Late", "g"); assertTrue(v.tug.status("g").split("|")[8] == "0")
        v.handle("j join g", "a"); assertTrue("joining another game closes this one", !v.tug.active)
    }

    @Test fun quiz() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        act.getSharedPreferences("family", 0).edit().clear().commit()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        fill()
        val said = ArrayList<String>(); v.quiz.speak = { said.add(it) }
        fun st(cid: String) = v.quiz.status(cid).split("|")
        fun dec(x: String) = java.net.URLDecoder.decode(x, "UTF-8")
        for (mode in 0..2) {
            v.handle("nm Sara", "a"); v.handle("nm Omar", "b"); v.handle("nm Lina", "c")
            v.handle("qz join $mode Sara", "a"); v.handle("qz join $mode Omar", "b"); v.handle("qz join $mode Lina", "c"); v.handle("qz rounds 5", "a")
            shot("quiz${mode}_lobby"); assertEquals("1", st("a")[0]); assertEquals("" + mode, st("a")[1]); assertEquals("3", st("a")[8])
            v.handle("qz start", "a"); tick(1200); shot("quiz${mode}_count"); tick(2000); shot("quiz${mode}_ask"); shot("quiz${mode}_ask")
            assertEquals("3", st("a")[0])
            for (round in 1..5) {
                val s = st("a"); assertEquals("" + round, s[2]); assertEquals("-1", s[7]); assertEquals("", s[14])
                if (mode == 2) { assertEquals(16, s.size); tick(700); shot("quiz2_ask")
                    v.handle("qz ans $round " + s[15].uppercase(), "a"); v.handle("qz ans $round zzz", "b") }
                else { assertEquals(20, s.size); v.handle("qz ans $round 0", "a"); v.handle("qz ans $round 1", "b"); v.handle("qz ans ${round + 1} 2", "c") }   // c answers the wrong round
                assertEquals("1", st("a")[6]); assertEquals("0", st("c")[6]); shot("quiz${mode}_wait")
                assertEquals("still waiting for Lina", "3", st("a")[0])
                v.handle("qz ans $round " + (if (mode == 2) "nope" else "2"), "c"); shot("quiz${mode}_reveal"); shot("quiz${mode}_reveal")
                val r = st("a"); assertEquals("4", r[0]); assertTrue(r[14].isNotEmpty())
                if (mode == 2) { assertEquals("1", r[7]); assertEquals("0", st("b")[7]) }
                else assertTrue("the answer shown is one of the four choices", (16..19).any { dec(r[it]) == dec(r[14]) })
                tick(QuizGame.REVEAL_MS + 100); shot("quiz${mode}_next")
            }
            assertEquals("5", st("a")[0]); tick(1500); shot("quiz${mode}_end")
            if (mode == 2) { assertEquals("1", st("a")[9]); assertTrue(said.size >= 5); assertTrue(st("a")[5].toInt() >= 50) }
            v.handle("qz stop", "a"); assertEquals("0", st("a")[0])
        }
        assertTrue("spelling points reach the board", v.family.status("a").contains("B|Sara|"))
        v.handle("qz join 0 Sara", "a"); v.handle("sr join Sara 0 0", "a"); assertFalse(v.quiz.active)
        v.handle("qz join 0 Sara", "a"); assertFalse(v.space.active); tick(QuizGame.GONE_MS + 500); shot("quiz_left"); assertFalse("a phone that stops asking is dropped", v.quiz.active)
    }

    @Test fun move() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        act.getSharedPreferences("family", 0).edit().clear().commit()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        fill()
        fun st(cid: String) = v.move.status(cid).split("|")
        v.handle("nm Sara", "a"); v.handle("mv join Sara", "a"); shot("move_lobby"); assertEquals("1", st("a")[0]); assertEquals("1", st("a")[7])
        v.handle("mv level 1", "a"); v.handle("mv start", "a"); tick(1500); shot("move_count"); tick(1600); shot("move_go"); assertEquals("3", st("a")[0])
        // play blind: try every move a few times a second, as a body that follows the track would
        var hits = 0
        for (step in 0 until 300) { tick(100); if (step % 3 == 0) shot("move_x"); val before = st("a")[9]
            for (kind in 0..3) v.handle("mv act $kind", "a")
            if (st("a")[9] != before) hits++
            if (step == 60) shot("move_play"); if (step == 61) shot("move_play2") }
        assertTrue("moves are judged", hits > 20); assertTrue(st("a")[4].toInt() > 2000); assertTrue(st("a")[11].toInt() >= 5)
        for (i in 0 until 400) { tick(100); shot("move_x"); if (st("a")[0] == "5") break }
        assertEquals("5", st("a")[0]); tick(1500); shot("move_end")
        assertTrue("playing alone still counts on the board", v.family.status("a").contains("B|Sara|2|"))
        // two players side by side
        v.handle("mv join Omar", "b"); v.handle("mv level 2", "b"); v.handle("mv start", "b"); tick(3100); shot("move_x")
        for (step in 0 until 80) { tick(100); shot("move_x"); for (kind in 0..3) v.handle("mv act $kind", "a"); if (step % 4 == 0) v.handle("mv act 2", "b") }
        shot("move_two"); assertEquals("2", st("b")[7]); assertFalse(v.handle("mv join x", "c").let { v.move.status("c").split("|")[6] == "1" })
        v.handle("qz join 0 Sara", "a"); assertFalse(v.move.active)
    }

    @Test fun spaceRace() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        fill()
        val said = ArrayList<String>(); v.space.speak = { said.add(it) }
        v.handle("sr join Sara 0 12", "a"); shot("space_lobby_solo")              // alone: Robo-X joins
        assertTrue(v.space.status("a").startsWith("1|0|10|0|0|1|1|"))
        v.handle("sr join Omar 1 60", "b"); v.handle("sr join Lina 2 130", "c"); v.handle("sr join Ali 3 150", "d"); v.handle("sr join Noor 4 210", "e")
        v.handle("sr cat 5", "a"); v.handle("sr robo 1", "a"); shot("space_lobby")
        v.handle("sr start", "a"); tick(1200); shot("space_count"); tick(2000); shot("space_go")
        assertTrue(v.space.status("a").startsWith("3|5|10|1|"))
        repeat(3) { v.handle("sr hit 401 900 0", "a") }; v.handle("sr hit 402 4000 0", "b"); v.handle("sr miss 419 0", "c"); v.handle("sr miss 455 0", "d"); v.handle("sr miss 402 0", "e"); v.handle("sr hit 430 3000 0", "c")
        tick(900); shot("space_play")
        assertTrue("three fast answers and a ready turbo: " + v.space.status("a"), v.space.status("a").split("|").let { it[7] == "36" && it[8] == "1" })
        repeat(5) { v.handle("sr hit 403 800 0", "a") }; tick(700); shot("space_end"); tick(3000); shot("space_review"); tick(2300); shot("space_review2")
        assertTrue("winner sees the win: " + v.space.status("a"), v.space.status("a").split("|").let { it[0] == "5" && it[11] == "1" && it[10] == "1" })
        assertTrue(v.space.status("b").split("|")[11] == "0")
        assertTrue("the missed words are read aloud: $said", said.take(2) == listOf("brave", "careful") || said.size >= 2)
        v.handle("tug join l Sara", "a"); assertTrue("joining another game closes this one", !v.space.active)
    }

    @Test fun familyTools() {
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        act.getSharedPreferences("family", 0).edit().clear().commit()
        v = DrawView(act); act.window.setContentView(v)
        fill = { v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080) }
        fill()
        fun line(cid: String, tag: String) = v.family.status(cid).split("\n").first { it.startsWith(tag) }
        // quick vote
        v.handle("poll new What+shall+we+eat%3F Pizza Burger Mansaf", "a"); shot("vote_empty")
        v.handle("poll vote 2", "a"); v.handle("poll vote 2", "b"); v.handle("poll vote 0", "c"); v.handle("poll vote 1", "b")      // b changes its mind
        v.handle("poll vote 9", "d"); shot("vote_open")
        assertTrue(line("a", "P") == "P|1|2|What+shall+we+eat%3F|Pizza|1|Burger|1|Mansaf|1"); assertTrue(line("d", "P").startsWith("P|1|-1|"))
        v.handle("poll vote 2", "c"); v.handle("poll close", "a"); shot("vote_result")
        v.handle("poll vote 0", "a"); assertTrue("no voting after the vote is closed", line("a", "P").endsWith("|Pizza|0|Burger|1|Mansaf|2"))
        tick(Family.RESULT_MS + 500); shot("vote_gone"); assertTrue(line("a", "P").startsWith("P|0|"))
        // announcements and chores
        v.handle("nm Sara", "a"); v.handle("al now Dinner+is+ready+%F0%9F%8D%BD", "a"); tick(600); shot("say"); tick(Home.SAY_MS)
        v.handle("al add 1200 Bedtime", "a"); v.home.check(1200, 7); tick(600); shot("say_daily"); tick(Home.SAY_MS); shot("say_gone")
        v.handle("ch add 10 Tidy+your+room", "a"); v.handle("ch add 20 Homework", "a"); v.handle("ch done 2", "zz")       // zz has no name
        assertTrue(v.home.status().contains("H|2|10||Tidy+your+room"))
        v.handle("ch done 2", "a"); tick(900); shot("chore"); assertTrue(v.home.status().contains("H|2|10|Sara|Tidy+your+room"))
        assertTrue(line("a", "B|Sara").startsWith("B|Sara|10|10|0|0")); v.handle("ch done 2", "a"); assertTrue(line("a", "B|Sara").startsWith("B|Sara|10|"))
        v.handle("ch undo 2", "a"); assertTrue(line("a", "B|Sara").startsWith("B|Sara|0|")); tick(Home.CHEER_MS); v.handle("hof reset", "a")
        // timer
        v.handle("tm start 300 Bedtime", "a"); shot("timer"); assertTrue(line("a", "T") == "T|300|Bedtime")
        tick(250000); shot("timer_urgent"); assertTrue(line("a", "T").startsWith("T|50|"))
        v.handle("tm add 60", "a"); assertTrue(line("a", "T").startsWith("T|110|"))
        tick(111000); shot("timer_done"); assertTrue(line("a", "T").startsWith("T|0|"))
        tick(Family.DONE_MS + 500); shot("timer_gone"); assertTrue(line("a", "T").startsWith("T|-1|"))
        // hall of fame: points come from real games
        v.handle("nm Sara", "a"); v.handle("nm Omar", "b"); v.handle("nm %D9%84%D9%8A%D9%86%D8%A7", "c")
        v.handle("tug join l", "a"); v.handle("tug join r", "b"); v.handle("tug join r", "c"); v.handle("tug join l", "x")       // x has no name
        v.handle("tug level 2", "a"); v.handle("tug start", "a"); tick(1500); shot("f1"); tick(1700); shot("f2")
        repeat(6) { v.handle("tug hit 4 500", "a") }; v.handle("tug hit 3 500", "b"); tick(400); shot("f3")
        // five answers are a knockout: 2 for playing + 10 for winning + 5 correct; the losers get 2 each
        assertTrue("left team won: " + v.family.status("a"), v.family.status("a").contains("B|Sara|17|17|1|1") && v.family.status("a").contains("B|Omar|2|2|0|1"))
        assertTrue(v.family.status("a").split("\n").count { it.startsWith("B|") } == 3)
        v.handle("tug stop", "a"); v.handle("hof show", "a"); shot("board")
        val again = DrawView(act); assertTrue("the board is saved on the TV", again.family.status("a").contains("B|Sara|17|"))
        v.handle("hof reset", "a"); assertTrue(!v.family.status("a").contains("B|"))
    }
}
