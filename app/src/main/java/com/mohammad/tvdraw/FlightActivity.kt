package com.mohammad.tvdraw

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/** The flight simulator's screen: a full-screen web view that runs the 3D page from the app's own files.
    The page is served under a made-up secure address, so it needs no local web server and may fetch map tiles from the internet. */
class FlightActivity : Activity() {
    private var web: WebView? = null
    private val ui = Handler(Looper.getMainLooper())
    private val watch = object : Runnable { override fun run() { if (!Flight.active || Flight.pc) finish() else ui.postDelayed(this, 400) } }

    inner class Bridge {
        @JavascriptInterface fun conf(): String = Flight.conf(this@FlightActivity)
        @JavascriptInterface fun poll(): String = Flight.poll()
        @JavascriptInterface fun tell(s: String?) { Flight.tell(s ?: "") }
        @JavascriptInterface fun quit() { Flight.stop() }
        @JavascriptInterface fun alt(): String = if (Flight.port > 0) "http://127.0.0.1:${Flight.port}/fly/tv.html" else ""
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            val w = WebView(this); web = w
            w.setBackgroundColor(Color.rgb(10, 18, 32))
            w.settings.javaScriptEnabled = true; w.settings.domStorageEnabled = true; w.settings.mediaPlaybackRequiresUserGesture = false
            w.settings.allowFileAccess = false; w.settings.allowContentAccess = false
            w.addJavascriptInterface(Bridge(), "Lamma")
            w.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? = if (req.url.host == HOST) open(req.url) else null
                // the bridge is for our own page only: the view never leaves it
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean = req.url.host != HOST && !(req.url.host == "127.0.0.1" && req.url.port == Flight.port)
            }
            setContentView(w)
            w.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            w.isFocusable = true; w.isFocusableInTouchMode = true; w.requestFocus()
            w.loadUrl("https://$HOST/fly/tv.html")
        } catch (e: Throwable) { Flight.tell("err|noweb"); Flight.stop(); finish(); return }      // a TV without a system web view
        ui.postDelayed(watch, 400)
    }

    /** The remote flies the plane when no phone does: the arrows and OK go to the page by name, and the volume keys are the throttle. */
    override fun dispatchKeyEvent(e: android.view.KeyEvent): Boolean {
        val name = when (e.keyCode) { android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "ArrowLeft"; android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"; android.view.KeyEvent.KEYCODE_DPAD_UP -> "ArrowUp"
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "ArrowDown"; android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> "Enter"; else -> null }
        if (name != null) { if (e.repeatCount == 0) web?.evaluateJavascript("window.flyKey&&flyKey('$name'," + (if (e.action == android.view.KeyEvent.ACTION_DOWN) 1 else 0) + ")", null); return true }
        if (e.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || e.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (e.action == android.view.KeyEvent.ACTION_DOWN) Flight.nudge(if (e.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) 4 else -4); return true }
        return super.dispatchKeyEvent(e)
    }

    /** A file of the page, straight from the app's assets. */
    private fun open(u: Uri): WebResourceResponse {
        val path = (u.path ?: "").removePrefix("/")
        val name = if (path == "fly/font.ttf") "fonts/BalooBhaijaan2-Bold.ttf" else path
        val ok = path.startsWith("fly/") && !path.contains("..") && path.all { it.isLetterOrDigit() || it in "/._-" }
        val stream = if (ok) try { assets.open(name) } catch (e: Throwable) { null } else null
        if (stream == null) return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        return WebResourceResponse(mime(path), if (path.endsWith(".html") || path.endsWith(".js") || path.endsWith(".css") || path.endsWith(".json")) "utf-8" else null, 200, "OK",
            mapOf("Cache-Control" to "no-cache", "Access-Control-Allow-Origin" to "*"), stream)
    }

    override fun onPause() { super.onPause(); if (!isChangingConfigurations) { Flight.stop(); finish() } }       // leaving the screen ends the flight and frees the graphics memory
    override fun onDestroy() { ui.removeCallbacks(watch); Flight.stop()
        web?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }; web = null; super.onDestroy() }

    companion object {
        const val HOST = "appassets.androidplatform.net"
        fun mime(p: String): String = when (p.substringAfterLast('.', "")) {
            "html" -> "text/html"; "js" -> "text/javascript"; "css" -> "text/css"; "json" -> "application/json"; "wasm" -> "application/wasm"; "glb" -> "model/gltf-binary"
            "png" -> "image/png"; "jpg" -> "image/jpeg"; "svg" -> "image/svg+xml"; "gif" -> "image/gif"; "xml" -> "application/xml"; "ttf" -> "font/ttf"; else -> "application/octet-stream" }
    }
}
