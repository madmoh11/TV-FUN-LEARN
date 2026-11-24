package com.mohammad.tvdraw

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Plays one recitation file at a time. A file is saved first and then played, because the players of some TV boxes
 * cannot stream it; a file heard once plays again without the internet.
 */
class AyahAudio(private val ctx: Context) {
    private val ui = Handler(Looper.getMainLooper())
    private var media: MediaPlayer? = null
    private var turn = 0
    /** Why the last file could not be played, or empty. */
    var error = ""; private set
    var onError: (() -> Unit)? = null

    private fun file(url: String) = File(ctx.cacheDir, "quran/" + url.substringAfter("/data/").replace('/', '_'))

    private fun fetch(url: String) {
        val f = file(url)
        if (f.exists() && f.length() >= 2000) return
        f.parentFile?.mkdirs()
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 20000; c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Lamma")
        try { if (c.responseCode != 200) throw IOException("HTTP " + c.responseCode)
            val part = File(f.path + ".part" + Thread.currentThread().id); c.inputStream.use { i -> part.outputStream().use { o -> i.copyTo(o) } }
            if (!f.exists() && !part.renameTo(f)) throw IOException("cannot save")
            part.delete()
        } finally { c.disconnect() }
    }

    /** Saves a file ahead of time, so the next ayah starts without a gap. Failures are silent. */
    fun prepare(url: String) { thread { try { fetch(url) } catch (e: Throwable) { } } }

    fun stop() { turn++; try { media?.release() } catch (e: Throwable) { }; media = null }
    fun pause() { try { media?.pause() } catch (e: Throwable) { } }
    fun resume() { try { media?.start() } catch (e: Throwable) { } }

    private fun failed(what: String, done: () -> Unit) { error = what.take(120); onError?.invoke(); done() }

    /** done runs on the UI thread when the file has ended, or at once when it could not be played. */
    fun play(url: String, done: () -> Unit) {
        stop(); val mine = turn
        thread {
            var err = ""
            try { fetch(url) } catch (e: Throwable) { err = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "") }
            ui.post {
                if (mine != turn) return@post
                if (err.isNotEmpty()) { failed(err, done); return@post }
                val f = file(url)
                try {
                    val m = MediaPlayer(); media = m
                    m.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    m.setDataSource(f.path)
                    m.setOnCompletionListener { ui.post { if (mine == turn) done() } }
                    m.setOnErrorListener { _, what, extra -> f.delete(); ui.post { if (mine == turn) failed("player $what/$extra", done) }; true }
                    m.prepare(); m.start(); error = ""
                } catch (e: Throwable) { f.delete(); failed(e.javaClass.simpleName + (e.message?.let { ": $it" } ?: ""), done) }
            }
        }
    }
}
