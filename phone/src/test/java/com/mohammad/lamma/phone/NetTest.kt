package com.mohammad.lamma.phone

import org.junit.Assert.*
import org.junit.Test

class NetTest {
    @Test fun theSearchCoversThePhonesOwnNetworkNearestFirst() {
        val n = Net.neighbours("192.168.100.7")
        assertEquals(253, n.size); assertFalse("192.168.100.7" in n); assertEquals(setOf("192.168.100.6", "192.168.100.8"), n.take(2).toSet())
        assertTrue(n.all { it.startsWith("192.168.100.") }); assertTrue(Net.neighbours("not an address").isEmpty())
    }
    @Test fun onlyLammaIsTakenForATv() {
        val tv = Net.parse("192.168.1.4", "ver=3.30|on=1|clock=9:40|times=5:01,11:23,2:14,4:38,5:59|http=8765|https=8444|acc=0", 8765)!!
        assertEquals("3.30", tv.version); assertEquals(8765, tv.http); assertEquals(8444, tv.https)
        val old = Net.parse("192.168.1.4", "on=1|before=15|clock=9:40|times=5:01,11:23", 8766)!!      // a TV on an older version says no ports
        assertEquals("", old.version); assertEquals(8766, old.http); assertEquals(0, old.https)
        assertNull(Net.parse("192.168.1.1", "<html>router</html>", 8765))
    }
    @Test fun aSelfMadeCertificateIsAcceptedOnlyAtHome() {
        assertTrue(Net.isHome("192.168.100.4")); assertTrue(Net.isHome("10.0.0.5")); assertTrue(Net.isHome("172.20.1.1"))
        assertFalse(Net.isHome("8.8.8.8")); assertFalse(Net.isHome("example.com")); assertFalse(Net.isHome("172.32.0.1"))
    }
}
