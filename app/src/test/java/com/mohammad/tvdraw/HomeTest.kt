package com.mohammad.tvdraw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "ar-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeTest {
    private val out = System.getProperty("renderOut", "/tmp").also { File(it).mkdirs() }
    private fun read(n: String) = RuntimeEnvironment.getApplication().assets.open(n).use { it.readBytes().toString(Charsets.UTF_8) }
    private val quran by lazy { Quran(read("quran/quran-uthmani.txt"), read("quran/surahs.txt")) }
    private fun png(v: View, name: String) {
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1920, 1080)
        val b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888); v.draw(Canvas(b))
        FileOutputStream(File(out, "$name.png")).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun aSurahIsReadAyahByAyahFromTheTanzilText() {
        val kahf = Daily.surah(quran, 18)
        assertEquals(111, kahf.size)                                  // the basmala and 110 ayahs
        assertEquals(quran.basmala, kahf[0].text); assertEquals(1, kahf[0].surah); assertEquals(1, kahf[0].ayah)
        assertTrue(kahf[1].text.startsWith(quran.text(18, 1))); assertEquals(18, kahf[10].surah); assertEquals(10, kahf[10].ayah)
        assertEquals(7, Daily.surah(quran, 1).size)                   // al-Fatiha: its basmala is its first ayah
        assertEquals(129, Daily.surah(quran, 9).size)                 // at-Tawba has no basmala
        assertTrue(Daily.surah(quran, 2).all { it.quran && it.surah > 0 })
    }

    @Test fun theRuqyahVersesComeFromTheQuranText() {
        val r = Daily.ranges(quran, Daily.RUQYAH)
        assertEquals(7 + 1 + 2 + 5 + 6 + 7, r.size)                   // the last three surahs each open with the basmala
        assertTrue(r[7].text.startsWith(quran.text(2, 255)))
    }

    @Test fun theAdhkarKeepQuranPassagesAsReferences() {
        val json = read("data/adhkar.json")
        val morning = Daily.adhkar(json, quran, false); val evening = Daily.adhkar(json, quran, true)
        assertEquals(26, morning.size); assertEquals(24, evening.size)
        assertTrue(morning[1].quran && morning[1].text.startsWith(quran.text(2, 255)))
        assertTrue(morning.any { it.quran && it.text.startsWith(quran.basmala) && it.text.contains(quran.text(112, 1)) && it.count == 3 })
        assertTrue(evening.any { it.quran && it.text.contains(quran.text(2, 286)) })
        assertFalse(morning.any { it.quran && it.text.contains(quran.text(2, 286)) })       // the end of al-Baqara is for the evening
        assertTrue(morning.filter { !it.quran }.all { it.text.isNotBlank() && it.count >= 1 })
        // no ayah is typed into the data file: the ornate brackets that quote the Quran never appear in it
        assertFalse(json.contains('﴿'))
    }

    @Test fun ammanMatchesThePrintedTimetable() {
        // 25 November 2026 in Amman: Fajr 5:48, Dhuhr 12:24, Asr 3:14, Maghrib 5:39, Isha 6:59
        val t = Prayer.minutes(Prayer.times(2026, 11, 25, 31.9539, 35.9106, 3.0, Prayer.Method(18.0, 18.0, 0, 6)))
        assertEquals(listOf(5 * 60 + 48, 12 * 60 + 24, 15 * 60 + 14, 17 * 60 + 39, 18 * 60 + 59), t.toList())
    }

    @Test fun ammanAndDamascusStayThreeHoursAheadAllYear() {
        // an old box still believes Amman is two hours ahead in winter
        assertEquals(180, TvClock.baseMinutes("Asia/Amman", 2 * 3600000)); assertEquals(180, TvClock.baseMinutes("Asia/Damascus", 2 * 3600000))
        assertEquals(180, TvClock.baseMinutes("Asia/Riyadh", 3 * 3600000)); assertEquals(60, TvClock.baseMinutes("Europe/Berlin", 3600000))
        val before = java.util.TimeZone.getDefault()
        try { java.util.TimeZone.setDefault(java.util.SimpleTimeZone(2 * 3600000, "Asia/Amman"))
            assertEquals(3 * 3600000, TvClock.zone().rawOffset)
            val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).get(java.util.Calendar.HOUR_OF_DAY)
            assertEquals((utc + 3) % 24, TvClock.now().get(java.util.Calendar.HOUR_OF_DAY))
        } finally { java.util.TimeZone.setDefault(before) }
    }

    @Test fun appsAreArrangedByTheFamilyWithYouTubeFirstUntilThen() {
        val apps = listOf("b.netflix" to "Netflix", "com.google.android.youtube.tv" to "YouTube", "a.shahid" to "Shahid", "c.play" to "Google Play")
        assertEquals(listOf("com.google.android.youtube.tv", "c.play", "b.netflix", "a.shahid"), Daily.appOrder(apps, emptyList(), emptySet()))
        var shown = Daily.appOrder(apps, emptyList(), emptySet())
        shown = Daily.moveApp(shown, "a.shahid", "first"); assertEquals("a.shahid", shown[0]); assertEquals("com.google.android.youtube.tv", shown[1])
        shown = Daily.moveApp(shown, "a.shahid", "later"); assertEquals(listOf("com.google.android.youtube.tv", "a.shahid", "c.play", "b.netflix"), shown)
        shown = Daily.moveApp(shown, "b.netflix", "later"); assertEquals("the last one stays last", "b.netflix", shown.last())
        // the saved order is kept, a newly installed app joins after it, a removed one drops out, a hidden one is left out
        val later = apps.filter { it.first != "c.play" } + ("d.new" to "Anghami")
        assertEquals(listOf("com.google.android.youtube.tv", "a.shahid", "b.netflix", "d.new"), Daily.appOrder(later, shown, emptySet()))
        assertEquals(listOf("com.google.android.youtube.tv", "b.netflix", "d.new"), Daily.appOrder(later, shown, setOf("a.shahid")))
    }

    @Test fun theNextPrayerCountsDownAndWrapsToTomorrow() {
        val t = intArrayOf(300, 720, 930, 1050, 1130)
        assertEquals(listOf(0, 60), Daily.next(240, t, 301).toList())
        assertEquals(listOf(4, 14), Daily.next(1116, t, 301).toList())
        assertEquals(listOf(0, 301 + 1440 - 1200), Daily.next(1200, t, 301).toList())
    }

    @Test fun theRecitationAddressUsesPlainDigitsOnAnArabicTv() {
        val before = java.util.Locale.getDefault()
        try { java.util.Locale.setDefault(java.util.Locale("ar", "JO"))
            assertEquals("https://everyayah.com/data/Husary_64kbps/055001.mp3", Hifz.url(1, 55, 1))
            assertEquals("https://everyayah.com/data/Husary_Muallim_128kbps/002286.mp3", Hifz.url(0, 2, 286))
        } finally { java.util.Locale.setDefault(before) }
    }

    @Test fun aPageStaysOpaqueWhenItIsDrawnAgain() {
        val a = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        val v = PageView(a, "x", Daily.surah(quran, 55), true, AyahAudio(a))
        v.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)); v.layout(0, 0, 1920, 1080)
        val b = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        repeat(3) { b.eraseColor(0xFFFF0000.toInt()); v.draw(Canvas(b)) }
        val px = b.getPixel(40, 600)
        assertEquals(255, android.graphics.Color.alpha(px)); assertTrue(android.graphics.Color.red(px) < 40)       // no red showing through
    }

    @Test fun render() {
        val a = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        for (i in 0 until 40) { Thread.sleep(100); shadowOf(Looper.getMainLooper()).idle() }
        png(a.window.decorView, "home")
        a.getSharedPreferences("prayer", 0).edit().putString("bg", "1").apply()
        for (i in 0 until 30) { Thread.sleep(100); shadowOf(Looper.getMainLooper()).idle() }
        png(a.window.decorView, "home_wall1")
        a.getSharedPreferences("prayer", 0).edit().putString("bg", "2").apply()
        for (i in 0 until 30) { Thread.sleep(100); shadowOf(Looper.getMainLooper()).idle() }
        png(a.window.decorView, "home_wall2")
        val audio = AyahAudio(a)
        png(PageView(a, "سورة الكهف", Daily.surah(quran, 18).drop(1), true, audio), "home_surah")
        png(PageView(a, "سورة البقرة", Daily.surah(quran, 2).drop(282), true, audio), "home_long_ayah")
        val morning = Daily.adhkar(read("data/adhkar.json"), quran, false)
        val d = PageView(a, "أذكار الصباح", morning.drop(6), false, audio); d.key(KeyEvent.KEYCODE_DPAD_CENTER)
        png(d, "home_dhikr")
        png(PageView(a, "أذكار الصباح", morning.drop(3), false, audio), "home_dhikr_quran")
        png(PageView(a, "أذكار الصباح", morning.drop(1), false, audio), "home_dhikr_kursi")
    }
}
