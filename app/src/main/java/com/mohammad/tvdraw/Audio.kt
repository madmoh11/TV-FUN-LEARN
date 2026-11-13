package com.mohammad.tvdraw

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.concurrent.thread

/**
 * All TV sound goes through one 48 kHz stereo stream fed by a small software mixer:
 * one-shot effects, the looping fly buzz and the live microphone (16 kHz from the phone).
 */
class Sfx(ctx: Context) {
    @Volatile var muted = false
    @Volatile var lastError = ""
    @Volatile private var trackInfo = "not started"
    @Volatile private var framesOut = 0L
    @Volatile private var voiceIn = 0L
    private val am = ctx.getSystemService(AudioManager::class.java)

    private class Shot(val pcm: ShortArray, val gain: Float) { var pos = 0 }
    private val lock = Object()
    private val shots = ArrayList<Shot>()
    private val sounds = HashMap<String, ShortArray>()
    @Volatile private var buzzTarget = 0f
    private var started = false

    init {
        thread(isDaemon = true) {
            try {
                val made = mapOf("glass" to Synth.glass(), "buzz" to Synth.buzz(), "beep" to Synth.beep(),
                    "thump" to Synth.thump(), "roar" to Synth.roar(), "chime" to Synth.chime(), "swish" to Synth.swish(), "blip" to Synth.blip(), "buzzer" to Synth.buzzer(), "clack" to Synth.clack())
                synchronized(lock) { sounds.putAll(made) }
                for (name in arrayOf("glass1", "glass2")) try {          // real recordings (Gravity Sound, CC BY 4.0)
                    val b = ctx.assets.open("$name.pcm").use { it.readBytes() }
                    val pcm = ShortArray(b.size / 2) { ((b[2 * it + 1].toInt() shl 8) or (b[2 * it].toInt() and 0xff)).toShort() }
                    synchronized(lock) { sounds[name] = pcm }
                } catch (e: Throwable) { }
            } catch (e: Throwable) { fail("synth", e) }
        }
    }

    private fun fail(where: String, e: Throwable) { lastError = "$where: $e"; Log.e("TVDraw", where, e) }

    fun status(): String {
        var vol = "?"
        try { vol = "" + am.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) +
            (if (am.isStreamMute(AudioManager.STREAM_MUSIC)) " MUTED" else "") + (if (am.isVolumeFixed) " FIXED" else "") } catch (e: Throwable) { }
        return "out=$trackInfo played=${framesOut / 4800 / 10.0}s voiceIn=${voiceIn / 1600 / 10.0}s vol=$vol" +
            (if (muted) " sfxOff" else "") + (if (lastError.isEmpty()) "" else " ERR $lastError")
    }

    /** Volume of the TV box itself, from the phone: up, down or mute. Shows the system volume bar on the TV. */
    fun volume(what: String) {
        try {
            val dir = when (what) { "up" -> AudioManager.ADJUST_RAISE; "down" -> AudioManager.ADJUST_LOWER; "mute" -> AudioManager.ADJUST_TOGGLE_MUTE; else -> return }
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        } catch (e: Throwable) { fail("volume", e) }
    }

    /** name: glass, beep, thump, roar. force = play even when prank sounds are switched off. */
    fun play(name: String, gain: Float = 1f, force: Boolean = false) {
        if (muted && !force) return
        synchronized(lock) {
            var pcm = sounds[name]
            if (name == "glass") pcm = sounds[if (Math.random() < 0.5) "glass1" else "glass2"] ?: sounds["glass1"] ?: pcm
            if (pcm == null) return
            if (shots.size < 12) shots.add(Shot(pcm, gain))
            wake()
        }
    }

    fun buzz(on: Boolean, loud: Boolean) {
        val g = if (on && !muted) (if (loud) 0.85f else 0.38f) else 0f
        if (g == buzzTarget) return
        buzzTarget = g
        if (g > 0f) synchronized(lock) { wake() }
    }

    // ---- live voice ----
    private val voice = java.util.ArrayDeque<ShortArray>()
    private var voiceHead: ShortArray? = null
    private var voicePos = 0
    private var voiceBuffered = 0
    private val fx = VoiceFx()
    /** Voice changer for the microphone: one of VoiceFx.PRESETS. */
    fun voiceEffect(name: String) { fx.set(name) }

    fun voice(b: ByteArray) {
        val n = b.size / 2; if (n == 0) return
        val s = fx.process(ShortArray(n) { ((b[2 * it + 1].toInt() shl 8) or (b[2 * it].toInt() and 0xff)).toShort() })
        synchronized(lock) {
            if (voiceBuffered > 14000) { voice.clear(); voiceHead = null; voicePos = 0; voiceBuffered = 0 }  // too far behind: skip ahead
            voice.add(s); voiceBuffered += n; voiceIn += n
            wake()
        }
    }

    private fun wake() {            // call with lock held
        if (!started) { started = true; thread(isDaemon = true) { loop() } }
        lock.notifyAll()
    }

    private fun open(): AudioTrack? {
        for (rate in intArrayOf(48000, 44100)) try {
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val t = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(maxOf(min, rate / 10 * 4)).setTransferMode(AudioTrack.MODE_STREAM).build()
            if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); throw IllegalStateException("state " + t.state) }
            t.play()
            trackInfo = "ok@$rate"
            return t
        } catch (e: Throwable) { fail("open $rate", e) }
        trackInfo = "FAILED"
        return null
    }

    private fun loop() {
        val t = open() ?: run { synchronized(lock) { started = false }; return }
        val frames = 960
        val mix = FloatArray(frames)
        val out = ShortArray(frames * 2)
        var buzzPos = 0; var buzzGain = 0f
        var vPrev = 0f; var vCur = 0f; var vPhase = 3; var vPlaying = false
        try {
            while (true) {
                var active: Boolean
                var buzzPcm: ShortArray? = null
                synchronized(lock) {
                    java.util.Arrays.fill(mix, 0f)
                    val it = shots.iterator()
                    while (it.hasNext()) {
                        val s = it.next()
                        val n = minOf(frames, s.pcm.size - s.pos)
                        for (i in 0 until n) mix[i] += s.pcm[s.pos + i] * s.gain
                        s.pos += n; if (s.pos >= s.pcm.size) it.remove()
                    }
                    buzzPcm = sounds["buzz"]
                    if (!vPlaying && voiceBuffered >= 1600) vPlaying = true
                    if (vPlaying) for (i in 0 until frames) {               // 16 kHz -> 48 kHz, linear
                        if (vPhase == 3) {
                            vPhase = 0; vPrev = vCur
                            var h = voiceHead
                            if (h == null || voicePos >= h.size) { h = voice.poll(); voiceHead = h; voicePos = 0 }
                            if (h == null) { vCur = 0f; vPlaying = false } else { vCur = h[voicePos++].toFloat(); voiceBuffered-- }
                        }
                        mix[i] += vPrev + (vCur - vPrev) * vPhase / 3f
                        vPhase++
                    }
                    active = shots.isNotEmpty() || vPlaying || voiceBuffered > 0 || buzzTarget > 0f || buzzGain > 0.002f
                    if (!active) { lock.wait(300) }
                }
                val bz = buzzPcm
                if (bz != null && (buzzTarget > 0f || buzzGain > 0.002f)) for (i in 0 until frames) {
                    buzzGain += (buzzTarget - buzzGain) * 0.002f
                    mix[i] += bz[buzzPos] * buzzGain
                    buzzPos++; if (buzzPos >= bz.size) buzzPos = 0
                }
                if (!active) continue
                for (i in 0 until frames) {
                    val v = mix[i].coerceIn(-32767f, 32767f).toInt().toShort()
                    out[2 * i] = v; out[2 * i + 1] = v
                }
                val w = t.write(out, 0, out.size)
                if (w < 0) throw IllegalStateException("write $w")
                framesOut += frames
            }
        } catch (e: Throwable) { fail("mixer", e); synchronized(lock) { started = false }; try { t.release() } catch (x: Throwable) { } }
    }
}
