package com.mohammad.tvdraw


import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "ar-w1920dp-h1080dp-land-mdpi")
class FlightTest {
    private val c: android.content.Context get() = org.robolectric.RuntimeEnvironment.getApplication()
    private fun cmd(line: String, cid: String = "a") = Flight.command(c, line.split(" "), cid)

    @Test fun thePhoneFliesAndOnlyOnePhoneAtATime() {
        Flight.stop(); cmd("fl c 50 20 80"); assertTrue("nothing is taken before the flight starts", Flight.poll().startsWith("0,0,") || !Flight.active)
        cmd("fl on 3"); assertTrue(Flight.active)
        assertEquals("the throttle is unset until a phone sets it", "-1", Flight.poll().split(",")[2])
        cmd("fl c 50 -20 80"); var p = Flight.poll().split(",")
        assertEquals(listOf("50", "-20", "80"), p.take(3)); assertEquals("1", p[7]); assertEquals("the stick is fresh", "1", p[8])
        cmd("fl c 999 -999 500"); p = Flight.poll().split(","); assertEquals("out-of-range values are held to the limits", listOf("100", "-100", "100"), p.take(3))
        cmd("fl c -30 0 10", "b"); assertEquals("a second phone does not take the stick", "100", Flight.poll().split(",")[0])
        val lvl = p[3]; cmd("fl lvl"); assertNotEquals(lvl, Flight.poll().split(",")[3])
        val go = Flight.poll().split(",")[4]; cmd("fl menu 1"); assertEquals("1", Flight.poll().split(",")[6])
        cmd("fl go 4"); p = Flight.poll().split(","); assertNotEquals(go, p[4]); assertEquals("4", p[5]); assertEquals("choosing a place closes the list", "0", p[6])
        cmd("fl go 99"); assertEquals("5", Flight.poll().split(",")[5])
        assertEquals("wheels and flaps are the page's to choose until the phone does", listOf("-1", "-1", "0"), Flight.poll().split(",").subList(9, 12))
        cmd("fl gear 1"); cmd("fl flap 7"); cmd("fl brk 1"); assertEquals(listOf("1", "2", "1"), Flight.poll().split(",").subList(9, 12))
        cmd("fl gear 0", "b"); assertEquals("only the pilot's phone moves the wheels", "1", Flight.poll().split(",")[9])
        cmd("fl go 0"); assertEquals("a new place sets its own wheels and flaps", listOf("-1", "-1", "0"), Flight.poll().split(",").subList(9, 12))
        cmd("fl snd 0"); assertEquals("0", Flight.poll().split(",")[12]); assertTrue(Flight.status(c).contains("snd=0")); cmd("fl snd 1"); assertEquals("1", Flight.poll().split(",")[12])
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(Flight.STALE_MS + 50))
        assertEquals("a silent phone is no longer holding the stick", "0", Flight.poll().split(",")[8])
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(Flight.STALE_MS * 2))
        cmd("fl c -30 0 10", "b"); assertEquals("then another phone may take over", "-30", Flight.poll().split(",")[0])
        cmd("fl off"); assertFalse(Flight.active); assertEquals("0", Flight.poll().split(",")[7])
    }

    @Test fun mapKeysAreKeptButNeverShownBack() {
        val key = "AIzaSyD-EXAMPLE_ONLY_0123456789abcdefghij"
        cmd("fl key g $key"); cmd("fl q 2")
        val st = Flight.status(c); assertTrue(st, st.contains("g=1") && st.contains("i=0") && st.contains("q=2")); assertFalse("the phone never gets the key back", st.contains(key))
        assertEquals(key, org.json.JSONObject(Flight.conf(c)).getString("g")); assertEquals("ar", org.json.JSONObject(Flight.conf(c)).getString("lang"))
        cmd("fl key g <script>alert(1)</script>aaaaaaaaaaaaaaaaaaaaa"); assertTrue("something that is not a key is not kept", Flight.status(c).contains("g=0"))
        cmd("fl key i " + "x".repeat(60)); assertTrue(Flight.status(c).contains("i=1")); cmd("fl key i"); assertTrue("an empty value removes the key", Flight.status(c).contains("i=0"))
        Flight.tell("on|480|1500|35|sat|1|60|0|1\nx&y"); assertTrue(Flight.status(c).contains("rep=on|480|1500|35|sat|1|60|0|1xy"))
    }

    @Test fun pageFilesHaveTheRightTypes() {
        assertEquals("text/javascript", FlightActivity.mime("fly/cesium/Cesium.js")); assertEquals("application/wasm", FlightActivity.mime("fly/cesium/ThirdParty/draco_decoder.wasm"))
        assertEquals("model/gltf-binary", FlightActivity.mime("fly/plane.glb")); assertEquals("text/html", FlightActivity.mime("fly/tv.html"))
        for (f in listOf("fly/tv.html", "fly/fly.js", "fly/core.js", "fly/plane.glb", "fly/cesium/Cesium.js", "fly/cesium/Widgets/widgets.css", "fonts/BalooBhaijaan2-Bold.ttf")) c.assets.open(f).use { assertTrue(f, it.read() >= 0) }
    }
}
