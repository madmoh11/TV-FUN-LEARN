package com.mohammad.tvdraw

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Voice changer for the live microphone. Works on the 16 kHz stream from the phone, one chunk at a time.
 * The pitch is moved with two read heads that slide over a short delay line and cross-fade into each other,
 * so the voice gets higher or lower without getting faster or slower.
 */
class VoiceFx {
    /** pitch: 1 = unchanged. growl and ring are modulation frequencies in Hz (0 = off). echo is the feedback amount (0 = off). */
    class Preset(val pitch: Float, val growl: Float = 0f, val ring: Float = 0f, val echo: Float = 0f, val gain: Float = 1f)

    @Volatile var preset: Preset = PRESETS.getValue("normal"); private set
    fun set(name: String) { preset = PRESETS[name] ?: PRESETS.getValue("normal") }

    private val buf = FloatArray(SIZE)
    private var w = 0
    private var d = 0f                        // delay of the first read head, in samples
    private var t = 0L                        // sample counter for the modulators
    private val echoBuf = FloatArray(ECHO)
    private var e = 0

    private fun tap(delay: Float): Float {
        val p = w - 1 - delay
        val i = Math.floor(p.toDouble()).toInt(); val f = p - i
        val a = buf[(i + SIZE * 2) % SIZE]; val b = buf[(i + 1 + SIZE * 2) % SIZE]
        return a + (b - a) * f
    }

    fun process(x: ShortArray): ShortArray {
        val p = preset
        if (p.pitch == 1f && p.growl == 0f && p.ring == 0f && p.echo == 0f && p.gain == 1f) return x
        val out = ShortArray(x.size)
        for (n in x.indices) {
            buf[w] = x[n].toFloat(); w = (w + 1) % SIZE
            var y: Float
            if (p.pitch == 1f) y = x[n].toFloat() else {
                d += 1f - p.pitch
                if (d < 0f) d += WIN; if (d >= WIN) d -= WIN
                val d2 = if (d + WIN / 2 >= WIN) d - WIN / 2 else d + WIN / 2
                val g = 0.5f - 0.5f * cos(2 * PI * d / WIN).toFloat()        // loudest in the middle of the window, silent where a head jumps
                y = g * tap(d) + (1f - g) * tap(d2)
            }
            val sec = t / RATE.toDouble(); t++
            if (p.growl > 0f) y *= 1f + 0.45f * sin(2 * PI * p.growl * sec).toFloat()
            if (p.ring > 0f) y *= sin(2 * PI * p.ring * sec).toFloat()
            if (p.echo > 0f) { y += echoBuf[e] * p.echo; echoBuf[e] = y; e = (e + 1) % ECHO }
            out[n] = (y * p.gain).coerceIn(-32767f, 32767f).toInt().toShort()
        }
        return out
    }

    companion object {
        const val RATE = 16000
        private const val SIZE = 4096
        private const val WIN = 720f              // 45 ms
        private const val ECHO = 3520             // 220 ms
        val PRESETS = linkedMapOf(
            "normal" to Preset(1f),
            "monster" to Preset(0.58f, growl = 34f, gain = 1.5f),
            "deep" to Preset(0.8f, gain = 1.2f),
            "woman" to Preset(1.26f),
            "child" to Preset(1.5f),
            "chipmunk" to Preset(1.95f),
            "robot" to Preset(1f, ring = 62f, gain = 1.6f),
            "alien" to Preset(1.3f, ring = 150f, gain = 1.6f),
            "cave" to Preset(1f, echo = 0.5f))
    }
}
