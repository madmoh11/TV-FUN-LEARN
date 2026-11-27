package com.mohammad.tvdraw

import android.view.KeyEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "ar-w1920dp-h1080dp-land-mdpi")
class TvPadTest {
    private fun setup(): DrawView { val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val v = DrawView(act); act.window.setContentView(v)
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080)
        TvPad.view = v; return v }
    private fun shot(v: DrawView, name: String) { val b = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888); v.draw(android.graphics.Canvas(b))
        val dir = java.io.File("build/renders"); dir.mkdirs(); java.io.FileOutputStream(java.io.File(dir, "$name.png")).use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    private val OK = KeyEvent.KEYCODE_DPAD_CENTER; private val L = KeyEvent.KEYCODE_DPAD_LEFT; private val R = KeyEvent.KEYCODE_DPAD_RIGHT; private val U = KeyEvent.KEYCODE_DPAD_UP; private val D = KeyEvent.KEYCODE_DPAD_DOWN

    @Test fun lettersWithTheRemote() {
        val v = setup(); val said = ArrayList<String>(); TvPad.speak = { t, lang -> said.add("$lang:$t") }
        assertFalse("with no game on, the keys are the home screen's", TvPad.key(OK))
        TvPad.start(v.context, TvPad.KIDS, KidsCore.AR); assertEquals(TvPad.KIDS, TvPad.game); assertEquals(KidsGame.LEARN, v.kids.phase)
        assertEquals("1", v.kids.status().split("|")[2])
        assertTrue(TvPad.key(L)); assertEquals("in Arabic the next card is to the left", "2", v.kids.status().split("|")[2]); TvPad.key(R); assertEquals("1", v.kids.status().split("|")[2])
        org.robolectric.shadows.ShadowLooper.idleMainLooper(700, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertTrue("the card is read aloud in Arabic: $said", said.isNotEmpty() && said.last().startsWith("ar-SA:"))
        assertTrue(TvPad.hint(v.context).isNotEmpty()); shot(v, "pad_kids_learn")
        TvPad.key(U); assertEquals(KidsGame.ASK, v.kids.phase); shot(v, "pad_kids_ask")
        // the right answer's place on the screen decides which key takes it: in Arabic the first choice is drawn on the right
        var guard = 0
        while (v.kids.phase == KidsGame.ASK && guard++ < 3) { for (k in listOf(L, OK, R)) if (v.kids.phase == KidsGame.ASK) TvPad.key(k) }
        assertEquals("one of the three keys finds the answer", KidsGame.RIGHT, v.kids.phase)
        assertTrue(TvPad.key(KeyEvent.KEYCODE_BACK)); assertEquals("", TvPad.game); assertFalse(v.kids.active)
        TvPad.start(v.context, TvPad.KIDS, KidsCore.EN); TvPad.key(R); assertEquals("in English the next card is to the right", "2", v.kids.status().split("|")[2]); TvPad.stop()
    }

    @Test fun rockPaperScissorsAndSnakeWithTheRemote() {
        val v = setup()
        TvPad.start(v.context, TvPad.RPS); assertEquals(RpsGame.LOBBY, v.rps.phase); assertTrue(TvPad.hint(v.context).contains("OK"))
        TvPad.key(OK); assertEquals("OK starts the game against the TV", RpsGame.PICK, v.rps.phase)
        TvPad.key(L); assertEquals("the left key is rock", "" + RpsCore.ROCK, v.rps.status(TvPad.CID).split("|")[5]); shot(v, "pad_rps")
        TvPad.key(KeyEvent.KEYCODE_BACK); assertFalse(v.rps.active)
        TvPad.start(v.context, TvPad.SNAKE); assertEquals(SnakeGame.LOBBY, v.snake.phase); TvPad.key(OK); assertEquals(SnakeGame.COUNT, v.snake.phase)
        assertTrue(TvPad.key(U)); assertTrue(TvPad.key(D)); shot(v, "pad_snake")
        TvPad.start(v.context, TvPad.RPS); assertFalse("starting another game leaves the first", v.snake.active); TvPad.stop(); assertEquals("", TvPad.game)
        Flight.stop(); Flight.command(v.context, listOf("fl", "on", "1"), TvPad.CID); val t0 = Flight.poll().split(",")[2].toInt(); Flight.nudge(4); Flight.nudge(4)
        assertEquals("the volume keys move the throttle", 68, Flight.poll().split(",")[2].toInt()); assertEquals(-1, t0); repeat(20) { Flight.nudge(-10) }; assertEquals(0, Flight.poll().split(",")[2].toInt()); Flight.stop()
    }
}
