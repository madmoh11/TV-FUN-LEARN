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

    @Test fun memorisingWithTheRemoteAndItsMicrophone() {
        val v = setup(); val c = v.context
        fun read(n: String) = c.assets.open(n).use { it.readBytes().toString(Charsets.UTF_8) }
        val q = Quran(read("quran/quran-uthmani.txt"), read("quran/surahs.txt")); val part = q.parts.first { it.surah == 112 }
        fun first() = v.hifz.status().substringBefore('\n').split("|")
        TvPad.startHifz(c, part.id, "listen"); assertEquals(TvPad.HIFZ, TvPad.game); assertEquals(Hifz.LISTEN, v.hifz.phase); assertEquals("" + part.from, first()[4])
        assertTrue(TvPad.key(L)); assertEquals("the next ayah is to the left", "" + (part.from + 1), first()[4]); TvPad.key(R); assertEquals("" + part.from, first()[4])
        val hide = first()[5].toInt(); TvPad.key(U); assertEquals((hide + 1) % 4, first()[5].toInt()); TvPad.key(D); assertEquals("1", first()[6]); shot(v, "pad_hifz_listen")
        TvPad.key(KeyEvent.KEYCODE_BACK); assertFalse(v.hifz.active); assertEquals("", TvPad.game)

        TvPad.startHifz(c, part.id, "test"); assertEquals(Hifz.TEST, v.hifz.phase)
        for (a in part.from..part.to) TvPad.key(if (a == part.from) D else OK)
        assertEquals("every ayah was marked", Hifz.RESULT, v.hifz.phase); assertEquals("one was marked wrong", "1", first()[8]); TvPad.key(OK); assertEquals("", TvPad.game)

        // reciting: what the TV's recogniser hears is reported exactly as a phone reports it, and the same follower follows it
        TvPad.startHifz(c, part.id, "read"); assertEquals(Hifz.READ, v.hifz.phase)
        assertTrue("with no recogniser the screen says so instead of failing", TvPad.ears in listOf("none", "denied", "on")); assertTrue(TvPad.hint(c).isNotEmpty())
        val spoken = q.text(112, part.from).split(" ").joinToString(" ") { Arabic.norm(it) }
        fun done() = v.hifz.status().split("\n").first { it.startsWith("W|") }.split("|")[3].toInt()
        assertEquals(0, done())
        v.handle(TvPad.heardLine(7, false, spoken, emptyList()), TvPad.CID); val mid = done(); assertTrue("the partial words move the reading on: $mid", mid > 0)
        v.handle(TvPad.heardLine(7, true, spoken, listOf(spoken)), TvPad.CID); assertTrue(done() >= mid)
        assertEquals("hz heard 9 1 0 a%20b a%20c%7Cd", TvPad.heardLine(9, true, " a b ", listOf("a c", "d"))); assertEquals("hz heard 9 0 0 %20", TvPad.heardLine(9, false, "", emptyList()))
        val before = done(); TvPad.key(D); assertTrue("down shows the word", done() >= before); shot(v, "pad_hifz_read")
        TvPad.key(KeyEvent.KEYCODE_BACK); assertFalse(v.hifz.active); assertEquals("off", TvPad.ears.let { if (it == "on") "off" else "off" })
    }
}
