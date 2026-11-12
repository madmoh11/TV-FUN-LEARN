package com.mohammad.tvdraw

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** Sound effects generated in code (no audio files). Everything is mono, 48 kHz, 16-bit. */
object Synth {
    const val RATE = 48000

    private fun norm(f: FloatArray, level: Float): ShortArray {
        var peak = 0.0001f
        for (v in f) if (abs(v) > peak) peak = abs(v)
        return ShortArray(f.size) { (f[it] / peak * level * 32767f).toInt().toShort() }
    }

    /** Glass shattering: a broadband crash, a spray of very short bright clinks, then falling debris. */
    fun glass(rnd: Random = Random.Default): ShortArray {
        val n = (RATE * 1.5).toInt()
        val f = FloatArray(n)
        fun grain(onset: Float, decay: Float, amp: Float) {          // bright noise burst
            val s = (onset * RATE).toInt(); val len = minOf(n - s, (decay * 7 * RATE).toInt())
            var p1 = 0f; var p2 = 0f
            for (i in 0 until len) {
                val w = rnd.nextFloat() * 2f - 1f
                val hp = w - 1.6f * p1 + 0.64f * p2; p2 = p1; p1 = w    // steep high-pass
                f[s + i] += amp * hp * exp(-(i / RATE.toFloat()) / decay)
            }
        }
        fun clink(freq: Float, onset: Float, decay: Float, amp: Float) {
            val s = (onset * RATE).toInt(); val len = minOf(n - s, (decay * 7 * RATE).toInt())
            val w1 = 2 * PI.toFloat() * freq / RATE; val w2 = w1 * 1.0113f
            for (i in 0 until len) f[s + i] += amp * (sin(w1 * i) + 0.7f * sin(w2 * i)) * exp(-(i / RATE.toFloat()) / decay)
        }
        // 1) the hit: loud broadband crash that dies in a quarter of a second
        grain(0f, 0.03f, 1.0f); grain(0.002f, 0.14f, 0.7f); grain(0.03f, 0.22f, 0.35f)
        repeat(7) { grain(0.02f + rnd.nextFloat() * 0.2f, 0.03f + rnd.nextFloat() * 0.06f, 0.25f + rnd.nextFloat() * 0.3f) }
        // 2) pieces ringing as they break apart: irregular, each with 2-3 inharmonic modes
        repeat(46) { val r = rnd.nextFloat(); val t = 0.01f + 0.75f * r * r
            val base = 2200f + rnd.nextFloat() * 6500f; val dec = 0.025f + rnd.nextFloat() * 0.1f; val amp = (0.05f + rnd.nextFloat() * 0.12f) * (1f - 0.6f * r)
            clink(base, t, dec, amp); clink(base * (1.37f + rnd.nextFloat() * 0.5f), t, dec * 0.6f, amp * 0.5f)
            grain(t, 0.003f + rnd.nextFloat() * 0.004f, amp * 1.6f) }                 // each piece starts with a tick
        // 3) debris landing
        repeat(12) { val t = 0.55f + rnd.nextFloat() * 0.8f
            grain(t, 0.003f + rnd.nextFloat() * 0.006f, 0.08f + rnd.nextFloat() * 0.1f)
            clink(3500f + rnd.nextFloat() * 5000f, t, 0.03f + rnd.nextFloat() * 0.06f, 0.03f + rnd.nextFloat() * 0.05f) }
        return norm(f, 0.92f)
    }

    /** One second of fly buzz that loops seamlessly: a thin, nasal, fluttering whine. */
    fun buzz(): ShortArray {
        val f = FloatArray(RATE)
        var p1 = 0f
        for (i in 0 until RATE) {
            val t = i / RATE.toDouble()
            val ph = 2 * PI * 210 * t + 2.2 * sin(2 * PI * 9 * t) + 0.8 * sin(2 * PI * 31 * t)
            var v = 0.0
            for (k in 1..24) v += sin(k * ph) / Math.sqrt(k.toDouble())      // bright, lots of upper harmonics
            val x = (v * (0.62 + 0.38 * sin(2 * PI * 27 * t))).toFloat()
            f[i] = x - 0.93f * p1; p1 = x                                    // thin it: cut the low body
        }
        f[0] = f[RATE - 1] * 0.5f + f[1] * 0.5f
        return norm(f, 0.8f)
    }

    /** Three rising notes: the "is the TV sound working" test. */
    fun beep(): ShortArray {
        val notes = floatArrayOf(660f, 880f, 1320f)
        val len = (RATE * 0.22).toInt()
        val f = FloatArray(len * notes.size)
        for ((k, hz) in notes.withIndex()) for (i in 0 until len) {
            val env = minOf(1f, i / 400f) * minOf(1f, (len - i) / 2400f)
            f[k * len + i] = sin(2 * PI.toFloat() * hz * i / RATE) * env
        }
        return norm(f, 0.6f)
    }

    /** Soft two-note chime for notices. */
    fun chime(): ShortArray {
        val n = (RATE * 1.9).toInt()
        val f = FloatArray(n)
        fun note(hz: Float, at: Float) {
            val s0 = (at * RATE).toInt()
            for (i in 0 until n - s0) {
                val t = i / RATE.toFloat(); val w = 2 * PI.toFloat() * hz * t
                f[s0 + i] += (sin(w) + 0.35f * sin(2 * w) + 0.12f * sin(3 * w)) * exp(-t / 0.5f) * minOf(1f, i / 240f)
            }
        }
        note(659.25f, 0f); note(880f, 0.38f)
        return norm(f, 0.7f)
    }

    /** Ball dropping through the net: a soft breathy "fssh" that falls in pitch. */
    fun swish(rnd: Random = Random.Default): ShortArray {
        val n = (RATE * 0.55).toInt(); val f = FloatArray(n)
        var lp = 0f; var bpS = 0f
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            val cut = 0.55f - 0.42f * (t / 0.55f)                      // filter closes as the ball passes
            lp += cut * ((rnd.nextFloat() * 2f - 1f) - lp); val hp = lp - bpS; bpS += 0.05f * (lp - bpS)
            f[i] = hp * minOf(1f, t / 0.02f) * exp(-t / 0.16f)
        }
        val ding = (RATE * 0.09).toInt()                              // tiny bright accent so it reads as a score
        for (i in 0 until n - ding) { val t = i / RATE.toFloat(); f[ding + i] += 0.25f * sin(2 * PI.toFloat() * 1318f * t) * exp(-t / 0.18f) + 0.18f * sin(2 * PI.toFloat() * 1976f * t) * exp(-t / 0.12f) }
        return norm(f, 0.85f)
    }

    /** Hard plastic knock: puck against a striker or the rail. */
    fun clack(rnd: Random = Random.Default): ShortArray {
        val n = (RATE * 0.09).toInt(); val f = FloatArray(n); var prev = 0f
        for (i in 0 until n) { val t = i / RATE.toFloat(); val wn = rnd.nextFloat() * 2f - 1f; val hp = wn - prev; prev = wn
            f[i] = hp * 0.6f * exp(-t / 0.004f) + sin(2 * PI.toFloat() * 1750f * t) * 0.8f * exp(-t / 0.012f) + sin(2 * PI.toFloat() * 3120f * t) * 0.4f * exp(-t / 0.007f) }
        return norm(f, 0.85f)
    }

    /** Short click-like note for countdowns and dashes. */
    fun blip(): ShortArray {
        val n = (RATE * 0.12).toInt(); val f = FloatArray(n)
        for (i in 0 until n) { val t = i / RATE.toFloat(); f[i] = sin(2 * PI.toFloat() * 980f * t) * exp(-t / 0.035f) * minOf(1f, i / 60f) }
        return norm(f, 0.6f)
    }

    /** End-of-match horn. */
    fun buzzer(): ShortArray {
        val n = (RATE * 1.1).toInt(); val f = FloatArray(n)
        for (i in 0 until n) { val t = i / RATE.toFloat()
            var v = 0f; for (hz in floatArrayOf(196f, 247f, 294f)) { val ph = hz * t; v += 2f * (ph - Math.floor(ph.toDouble()).toFloat()) - 1f }
            f[i] = v * minOf(1f, t / 0.02f) * minOf(1f, (1.1f - t) / 0.12f) }
        return norm(f, 0.6f)
    }

    /** One heavy paw landing: a deep, dull body hit. */
    fun thump(rnd: Random = Random.Default): ShortArray {
        val n = (RATE * 0.45).toInt()
        val f = FloatArray(n)
        var ph = 0.0; var lp = 0f; var lp2 = 0f
        for (i in 0 until n) {
            val t = i / RATE.toFloat()
            ph += 2 * PI * (42.0 + 55.0 * exp(-t / 0.035)) / RATE
            lp += 0.02f * ((rnd.nextFloat() * 2f - 1f) - lp); lp2 += 0.02f * (lp - lp2)   // dull rumble only
            f[i] = (sin(ph).toFloat() + 0.4f * sin(2 * ph).toFloat()) * exp(-t / 0.11f) * minOf(1f, i / 90f) + lp2 * 14f * exp(-t / 0.05f)
        }
        return norm(f, 0.95f)
    }

    /** Lion roar: rough low voice through throat-like resonances, swelling then dying away. */
    fun roar(rnd: Random = Random.Default): ShortArray {
        val n = (RATE * 2.8).toInt()
        val src = FloatArray(n)
        var ph = 0.0; var jit = 0.0; var rasp = 0f; var breath = 0f
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val f0 = (if (t < 0.45) 78 + 60 * (t / 0.45) else 138 - 62 * minOf(1.0, (t - 0.45) / 2.0))
            jit += 0.002 * (rnd.nextDouble() - 0.5) - 0.0005 * jit
            ph += (f0 * (1 + 3.0 * jit)) / RATE
            val p = ph - Math.floor(ph)
            val q = ph / 2 - Math.floor(ph / 2)
            val pulse = (1 - 2 * p) + 0.7 * (1 - 2 * q)                    // voice + sub-octave roughness
            rasp += 0.006f * ((rnd.nextFloat() * 2f - 1f) - rasp)          // slow random flutter
            breath += 0.35f * ((rnd.nextFloat() * 2f - 1f) - breath)
            val env = (minOf(1.0, t / 0.3) * minOf(1.0, maxOf(0.0, (2.8 - t) / 1.3))).toFloat()
            src[i] = ((pulse * (0.55 + 9.0 * abs(rasp))).toFloat() + breath * 1.1f) * env
        }
        fun band(inp: FloatArray, fc: Double, qf: Double, gain: Float, out: FloatArray) {
            val w = 2 * PI * fc / RATE; val alpha = sin(w) / (2 * qf)
            val a0 = 1 + alpha; val b0 = (alpha / a0).toFloat(); val b2 = (-alpha / a0).toFloat()
            val a1 = (-2 * cos(w) / a0).toFloat(); val a2 = ((1 - alpha) / a0).toFloat()
            var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
            for (i in inp.indices) {
                val x = inp[i]; val y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1; x1 = x; y2 = y1; y1 = y; out[i] += y * gain
            }
        }
        val out = FloatArray(n)
        band(src, 210.0, 2.0, 1.2f, out); band(src, 520.0, 3.0, 1.0f, out)
        band(src, 1050.0, 4.0, 0.5f, out); band(src, 2300.0, 5.0, 0.22f, out)
        for (i in 0 until n) out[i] = tanh(out[i] * 2.2f)
        return norm(out, 0.95f)
    }
}
