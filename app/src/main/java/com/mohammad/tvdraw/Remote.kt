package com.mohammad.tvdraw

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.io.File
import kotlin.concurrent.thread

/**
 * The phone as a remote. Inside Lamma's own screens the keys are sent as real key presses. Android lets an app
 * press keys only in its own windows, so in other apps Back and Home (and the arrows on Android 13 and later) go
 * through an accessibility service, when the owner has switched it on.
 */
object Remote {
    private val KEYS = mapOf("up" to KeyEvent.KEYCODE_DPAD_UP, "down" to KeyEvent.KEYCODE_DPAD_DOWN, "left" to KeyEvent.KEYCODE_DPAD_LEFT,
        "right" to KeyEvent.KEYCODE_DPAD_RIGHT, "ok" to KeyEvent.KEYCODE_DPAD_CENTER, "hold" to KeyEvent.KEYCODE_DPAD_CENTER, "back" to KeyEvent.KEYCODE_BACK)
    fun known(k: String) = k == "home" || k in KEYS

    fun press(ctx: Context, k: String) {
        if (k == "home") { if (KeysService.press("home")) return
            try { ctx.startActivity(Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { }; return }
        val code = KEYS[k] ?: return
        thread {
            try {
                val ins = Instrumentation()
                if (k == "hold") { val t = SystemClock.uptimeMillis()       // held long enough for a long press
                    ins.sendKeySync(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); Thread.sleep(900)
                    ins.sendKeySync(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0)) }
                else ins.sendKeyDownUpSync(code)
            } catch (e: Throwable) { Handler(Looper.getMainLooper()).post { KeysService.press(k) } }      // another app is in front
        }
    }
}

/** Presses Back, Home and, where Android allows it, the arrows in other apps. It reads nothing from the screen. */
class KeysService : AccessibilityService() {
    override fun onAccessibilityEvent(e: AccessibilityEvent?) { }
    override fun onInterrupt() { }
    override fun onServiceConnected() { live = this }
    override fun onDestroy() { if (live === this) live = null; super.onDestroy() }
    companion object {
        @Volatile private var live: KeysService? = null
        val on get() = live != null
        fun press(k: String): Boolean {
            val s = live ?: return false
            val a = when (k) { "back" -> GLOBAL_ACTION_BACK; "home" -> GLOBAL_ACTION_HOME
                // the arrow actions exist from Android 13 (their numbers are 16 to 20)
                "up" -> 16; "down" -> 17; "left" -> 18; "right" -> 19; "ok", "hold" -> 20; else -> return false }
            if (a >= 16 && android.os.Build.VERSION.SDK_INT < 33) return false
            return try { s.performGlobalAction(a) } catch (e: Throwable) { false }
        }
    }
}

/** Records what the TV shows into one video file, for as long as three minutes. Android asks on the TV before each recording. */
object Recorder {
    const val IDLE = 0; const val ASKING = 1; const val ON = 2; const val READY = 3
    const val MAX_MS = 180000L
    @Volatile var state = IDLE; private set
    @Volatile var error = ""; private set
    private var startedAt = 0L
    private var rec: MediaRecorder? = null; private var display: VirtualDisplay? = null; private var projection: MediaProjection? = null
    private val ui = Handler(Looper.getMainLooper())
    private val limit = Runnable { stop() }
    fun file(c: Context) = File(c.filesDir, "rec.mp4")
    val seconds get() = if (state == ON) ((SystemClock.uptimeMillis() - startedAt) / 1000).toInt() else 0

    /** Opens the question "allow Lamma to record the screen?" on the TV. */
    fun ask(c: Context) {
        if (state == ON || state == ASKING) return
        state = ASKING; error = ""
        try { c.startActivity(Intent(c, RecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { state = IDLE; error = e.javaClass.simpleName }
        ui.postDelayed({ if (state == ASKING) state = IDLE }, 60000)
    }
    fun refused() { if (state == ASKING) state = IDLE }

    fun start(c: Context, code: Int, data: Intent) {
        val app = c.applicationContext
        try {
            val m = app.resources.displayMetrics
            val wide = m.widthPixels >= m.heightPixels; val w = if (wide) 1280 else 720; val h = if (wide) 720 else 1280
            file(app).delete()
            val p = (app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data) ?: throw IllegalStateException("no projection")
            projection = p
            p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { ui.post { stop() } } }, ui)
            @Suppress("DEPRECATION") val r = MediaRecorder(); rec = r
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264); r.setVideoSize(w, h); r.setVideoFrameRate(30); r.setVideoEncodingBitRate(5_000_000)
            r.setOutputFile(file(app).path); r.prepare()
            display = p.createVirtualDisplay("lamma", w, h, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
            r.start(); startedAt = SystemClock.uptimeMillis(); state = ON
            ui.removeCallbacks(limit); ui.postDelayed(limit, MAX_MS)
        } catch (e: Throwable) { error = (e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")).take(120); release(); state = IDLE }
    }
    private fun release() {
        try { rec?.release() } catch (e: Throwable) { }; rec = null
        try { display?.release() } catch (e: Throwable) { }; display = null
        try { projection?.stop() } catch (e: Throwable) { }; projection = null
    }
    fun stop() {
        if (state != ON) return
        ui.removeCallbacks(limit); state = IDLE
        val ok = try { rec?.stop(); true } catch (e: Throwable) { error = "nothing was recorded"; false }
        release()
        if (ok) state = READY
    }
    /** A recording made before the app was restarted or updated is still there to download. */
    fun restore(c: Context) { if (state == IDLE && file(c).length() > 10000) state = READY }
    fun forget(c: Context) { if (state == READY) { file(c).delete(); state = IDLE } }
}

/** Shows Android's own question before a recording and hands the answer to the recorder. It has no screen of its own. */
class RecordActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { startActivityForResult((getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(), 7) }
        catch (e: Throwable) { Recorder.refused(); finish() }
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 7 && res == RESULT_OK && data != null) Recorder.start(this, res, data) else Recorder.refused()
        finish()
    }
}
