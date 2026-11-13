package com.mohammad.tvdraw

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class VoiceFxTest {
    private fun tone(hz: Double, seconds: Double = 2.0) = ShortArray((VoiceFx.RATE * seconds).toInt()) { (9000 * sin(2 * PI * hz * it / VoiceFx.RATE)).toInt().toShort() }

    /** Runs the sound through the effect in phone-sized chunks, as it arrives in real use. */
    private fun run(name: String, x: ShortArray): ShortArray {
        val fx = VoiceFx(); fx.set(name)
        val out = ArrayList<Short>()
        var i = 0
        while (i < x.size) { val n = minOf(743, x.size - i); fx.process(x.copyOfRange(i, i + n)).forEach { out.add(it) }; i += n }
        return out.toShortArray()
    }

    /** Pitch in Hz, from upward zero crossings in the second half of the sound. */
    private fun pitch(x: ShortArray): Double {
        var c = 0; val from = x.size / 2
        for (i in from + 1 until x.size) if (x[i - 1] < 0 && x[i] >= 0) c++
        return c / ((x.size - from) / VoiceFx.RATE.toDouble())
    }

    @Test fun normalLeavesTheVoiceUntouched() { val x = tone(200.0); assertArrayEquals(x, run("normal", x)) }
    @Test fun unknownNameMeansNormal() { val x = tone(200.0); assertArrayEquals(x, run("nonsense", x)) }

    @Test fun everyEffectKeepsLengthAndStaysAudible() {
        val x = tone(200.0)
        for (name in VoiceFx.PRESETS.keys) {
            val y = run(name, x)
            assertEquals(name, x.size, y.size)                     // same length: the voice is not sped up or slowed down
            val peak = y.drop(4000).maxOf { abs(it.toInt()) }
            assertTrue("$name is silent or too quiet: $peak", peak > 3000)
        }
    }

    @Test fun pitchMovesByTheExpectedAmount() {
        val x = tone(200.0)
        for ((name, want) in mapOf("deep" to 160.0, "woman" to 252.0, "child" to 300.0, "chipmunk" to 390.0)) {
            val got = pitch(run(name, x))
            assertTrue("$name: expected about $want Hz, got $got", abs(got - want) < want * 0.06)
        }
        assertTrue("monster should be much lower", pitch(run("monster", x)) < 140.0)
    }

    @Test fun caveEchoKeepsRingingAfterTheVoiceStops() {
        val x = tone(300.0, 0.5) + ShortArray(VoiceFx.RATE)       // half a second of voice, then silence
        val tail = run("cave", x).drop(VoiceFx.RATE * 3 / 4).maxOf { abs(it.toInt()) }
        assertTrue("no echo tail: $tail", tail > 500)
        assertEquals(0, run("normal", x).drop(VoiceFx.RATE * 3 / 4).maxOf { abs(it.toInt()) })
    }
}
