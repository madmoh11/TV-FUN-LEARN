package com.mohammad.lamma.phone

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Finds the TV that runs Lamma on the home Wi-Fi and shows its control page as an app. The page is the same one a
 * browser gets; here the microphone, the camera and speech recognition work without the secure-link step.
 */
class MainActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private val sp by lazy { getSharedPreferences("tv", 0) }
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var finder: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private var tv: Net.Tv? = null
    private var searching = false
    private var d = 1f

    private fun dp(v: Int) = (v * d).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        d = resources.displayMetrics.density
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root = FrameLayout(this); root.setBackgroundColor(0xFF140C2E.toInt()); setContentView(root)

        web = WebView(this); web.setBackgroundColor(0xFF140C2E.toInt()); web.visibility = View.GONE
        web.settings.javaScriptEnabled = true; web.settings.domStorageEnabled = true; web.settings.mediaPlaybackRequiresUserGesture = false
        web.addJavascriptInterface(Bridge(), "LammaNative")
        web.webViewClient = object : WebViewClient() {
            // The TV signs its own certificate. It is accepted only for the TV that was chosen, and only on a home network.
            override fun onReceivedSslError(v: WebView, h: SslErrorHandler, e: SslError) {
                val host = Uri.parse(e.url).host ?: ""
                if (host == tv?.ip && Net.isHome(host)) h.proceed() else h.cancel()
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) { if (r.isForMainFrame) ui.post { lost() } }
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                if (r.url.host == tv?.ip) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, r.url)) } catch (e: Throwable) { }; return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(r: PermissionRequest) { ui.post { grant(r) } }
            override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                picking?.onReceiveValue(null); picking = cb
                return try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), PICK); true }
                catch (e: Throwable) { picking = null; false }
            }
        }
        // A recording of the TV screen: saved through the phone's download manager, over plain http, which it can read
        web.setDownloadListener { _, _, _, _, _ -> val t = tv ?: return@setDownloadListener
            try { val q = DownloadManager.Request(Uri.parse("http://${t.ip}:${t.http}/rec.mp4")).setTitle("lamma-tv.mp4").setMimeType("video/mp4")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "lamma-tv-" + System.currentTimeMillis() / 1000 + ".mp4")
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(q)
                Toast.makeText(this, R.string.saving, Toast.LENGTH_LONG).show() } catch (e: Throwable) { } }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))

        // the search screen
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL; col.gravity = Gravity.CENTER_HORIZONTAL; col.setPadding(dp(24), dp(56), dp(24), dp(24))
        col.addView(text(getString(R.string.app_name), 40, 0xFFFFD60A.toInt()))
        status = text("", 17, Color.WHITE); status.setPadding(0, dp(18), 0, dp(18)); col.addView(status)
        list = LinearLayout(this); list.orientation = LinearLayout.VERTICAL; col.addView(list, LinearLayout.LayoutParams(-1, -2))
        col.addView(button(getString(R.string.again), 0xFF3B2F73.toInt()) { search() })
        col.addView(text(getString(R.string.manual), 15, 0xFFA59FC4.toInt()).apply { setPadding(0, dp(28), 0, dp(8)) })
        val ip = EditText(this); ip.hint = "192.168.1.20"; ip.inputType = InputType.TYPE_CLASS_PHONE; ip.setTextColor(Color.WHITE); ip.setHintTextColor(0xFF6F6A8C.toInt()); ip.gravity = Gravity.CENTER
        ip.setText(sp.getString("ip", "")); ip.layoutDirection = View.LAYOUT_DIRECTION_LTR
        col.addView(ip, LinearLayout.LayoutParams(-1, -2))
        col.addView(button(getString(R.string.go), 0xFF7A3FD8.toInt()) { val a = ip.text.toString().trim(); if (a.isNotEmpty()) tryOne(a) })
        finder = ScrollView(this); finder.addView(col); root.addView(finder, FrameLayout.LayoutParams(-1, -1))

        val last = sp.getString("ip", "") ?: ""
        if (last.isNotEmpty()) tryOne(last, thenSearch = true) else search()
    }

    private fun text(t: String, size: Int, color: Int) = TextView(this).apply { text = t; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER }
    private fun button(t: String, color: Int, run: () -> Unit) = Button(this).apply { text = t; textSize = 17f; isAllCaps = false; setTextColor(Color.WHITE)
        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(color) }
        setOnClickListener { run() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(10) } }

    // ---- finding the TV ----
    private fun ownAddress(): String? {
        try { for (ni in NetworkInterface.getNetworkInterfaces()) { if (!ni.isUp || ni.isLoopback) continue
            for (a in ni.inetAddresses) if (a is Inet4Address && !a.isLoopbackAddress && Net.isHome(a.hostAddress ?: "")) return a.hostAddress } } catch (e: Throwable) { }
        return null
    }
    /** Asks one address whether Lamma answers there. Runs off the UI thread. */
    private fun ask(ip: String, wait: Int): Net.Tv? {
        for (port in intArrayOf(8765, 8766, 8767, 9321)) {
            try { Socket().use { it.connect(InetSocketAddress(ip, port), wait) } } catch (e: Throwable) { continue }
            try { val c = URL("http://$ip:$port/p").openConnection() as HttpURLConnection; c.connectTimeout = 1500; c.readTimeout = 2500
                try { val body = c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }; Net.parse(ip, body, port)?.let { return it } } finally { c.disconnect() }
            } catch (e: Throwable) { }
        }
        return null
    }
    private fun tryOne(ip: String, thenSearch: Boolean = false) {
        status.text = getString(R.string.looking)
        Thread { val t = ask(ip, 1200); ui.post { if (t != null) open(t) else if (thenSearch) search() else status.text = getString(R.string.lost) } }.start()
    }
    private fun search() {
        if (searching) return
        val own = ownAddress(); list.removeAllViews()
        if (own == null) { status.text = getString(R.string.no_wifi); return }
        searching = true; status.text = getString(R.string.looking)
        val found = java.util.Collections.synchronizedList(ArrayList<Net.Tv>())
        Thread {
            val pool = Executors.newFixedThreadPool(48)
            for (ip in Net.neighbours(own)) pool.execute { try { Socket().use { it.connect(InetSocketAddress(ip, 8765), 450) }; ask(ip, 600)?.let { found.add(it) } } catch (e: Throwable) { } }
            pool.shutdown(); pool.awaitTermination(25, TimeUnit.SECONDS)
            ui.post { searching = false
                if (found.size == 1) open(found[0])
                else if (found.isEmpty()) status.text = getString(R.string.none)
                else { status.text = getString(R.string.found)
                    for (t in found.sortedBy { it.ip }) list.addView(button(getString(R.string.tv_at, t.version, t.ip), 0xFF1A6F55.toInt()) { open(t) }) } }
        }.start()
    }
    private fun open(t: Net.Tv) {
        tv = t; sp.edit().putString("ip", t.ip).apply()
        finder.visibility = View.GONE; web.visibility = View.VISIBLE
        web.loadUrl(if (t.https > 0) "https://${t.ip}:${t.https}/" else "http://${t.ip}:${t.http}/")
    }
    private fun lost() { stopListening(); web.visibility = View.GONE; web.loadUrl("about:blank"); finder.visibility = View.VISIBLE; status.text = getString(R.string.lost) }

    // ---- the microphone and the camera for the page ----
    private var asking: PermissionRequest? = null
    private var picking: ValueCallback<Array<Uri>>? = null
    private var listenAfter: String? = null
    private fun needs(r: PermissionRequest) = r.resources.mapNotNull { when (it) { PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA; else -> null } }
    private fun grant(r: PermissionRequest) {
        if (Uri.parse(r.origin.toString()).host != tv?.ip) { r.deny(); return }
        val missing = needs(r).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) r.grant(r.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }.toTypedArray())
        else { asking?.deny(); asking = r; requestPermissions(missing.toTypedArray(), ASK) }
    }
    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, p, res)
        if (code == ASK) { val r = asking; asking = null; if (r != null) { if (res.isNotEmpty() && res.all { it == PackageManager.PERMISSION_GRANTED }) grant(r) else r.deny() } }
        if (code == ASK_MIC) { val lang = listenAfter; listenAfter = null
            if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED && lang != null) listen(lang) else js("error", "not-allowed") }
    }
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == PICK) { val cb = picking; picking = null; cb?.onReceiveValue(if (res == RESULT_OK && data?.data != null) arrayOf(data.data!!) else null) }
    }

    // ---- speech recognition: the page's SpeechRecognition is served by the phone's own recogniser ----
    private var sr: SpeechRecognizer? = null
    private var quiet = false
    private val am by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private fun js(kind: String, arg: Any) {
        val a = if (arg is List<*>) JSONArray(arg).toString() else JSONArray(listOf(arg)).toString()
        ui.post { web.evaluateJavascript("window.__lammaSpeech&&window.__lammaSpeech('$kind',$a)", null) }
    }
    /** The recogniser beeps each time it starts; a recitation restarts it after every pause, so the beeps are hushed meanwhile. */
    private fun hush(on: Boolean) {
        if (quiet == on) return; quiet = on
        for (s in intArrayOf(AudioManager.STREAM_MUSIC, AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM))
            try { am.adjustStreamVolume(s, if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0) } catch (e: Throwable) { }
    }
    private val unhush = Runnable { hush(false) }
    private fun listen(lang: String) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { listenAfter = lang; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), ASK_MIC); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { js("error", "service-not-allowed"); js("end", ""); return }
        try { sr?.destroy() } catch (e: Throwable) { }
        val r = SpeechRecognizer.createSpeechRecognizer(this); sr = r
        r.setRecognitionListener(object : RecognitionListener {
            private fun words(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.filter { it.isNotBlank() } ?: emptyList()
            override fun onPartialResults(b: Bundle?) { if (sr === r) words(b).firstOrNull()?.let { js("partial", listOf(it)) } }
            override fun onResults(b: Bundle?) { if (sr !== r) return; val w = words(b); if (w.isNotEmpty()) js("final", w.take(3)); js("end", "") }
            override fun onError(code: Int) { if (sr !== r) return
                when (code) { SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> js("error", "not-allowed")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> js("error", "network") }
                js("end", "") }
            override fun onReadyForSpeech(b: Bundle?) { }
            override fun onBeginningOfSpeech() { }
            override fun onRmsChanged(v: Float) { }
            override fun onBufferReceived(b: ByteArray?) { }
            override fun onEndOfSpeech() { }
            override fun onEvent(t: Int, b: Bundle?) { }
        })
        ui.removeCallbacks(unhush); hush(true)
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName))
        ui.postDelayed(unhush, 60000)
    }
    private fun stopListening() { try { sr?.destroy() } catch (e: Throwable) { }; sr = null; ui.removeCallbacks(unhush); ui.postDelayed(unhush, 1500) }

    // ---- a voice for the page: letters and words are read aloud by the phone ----
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private fun speak(text: String, lang: String) {
        fun now() { try { val t = tts ?: return; t.language = java.util.Locale.forLanguageTag(lang); t.setSpeechRate(0.85f); t.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "w") } catch (e: Throwable) { } }
        if (tts == null) tts = android.speech.tts.TextToSpeech(this) { st -> if (st == android.speech.tts.TextToSpeech.SUCCESS) { ttsReady = true; now() } }
        else if (ttsReady) now()
    }

    /** What the page can ask of the phone. Every call comes from the page's own thread, so the work is passed to the UI thread. */
    inner class Bridge {
        @JavascriptInterface fun listen(lang: String) { ui.post { this@MainActivity.listen(lang.take(12).ifEmpty { "ar-SA" }) } }
        @JavascriptInterface fun stopListen() { ui.post { stopListening() } }
        @JavascriptInterface fun pickTv() { ui.post { tv = null; sp.edit().remove("ip").apply(); lost(); status.text = ""; search() } }
        @JavascriptInterface fun speak(text: String, lang: String) { ui.post { this@MainActivity.speak(text.take(200), lang) } }
        @JavascriptInterface fun version(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Throwable) { "" }
    }

    private var backAt = 0L
    override fun onBackPressed() {
        if (web.visibility != View.VISIBLE) { super.onBackPressed(); return }
        val now = System.currentTimeMillis()
        if (now - backAt < 2000) moveTaskToBack(true) else { backAt = now; Toast.makeText(this, R.string.exit_hint, Toast.LENGTH_SHORT).show() }
    }
    override fun onPause() {
        super.onPause()
        if (sr != null) { stopListening(); web.evaluateJavascript("try{hzdeaf()}catch(e){}", null) }      // the page stops asking for the recogniser while the app is away
        ui.removeCallbacks(unhush); hush(false)
    }
    override fun onDestroy() { try { tts?.shutdown() } catch (e: Throwable) { }; try { sr?.destroy() } catch (e: Throwable) { }; hush(false); web.destroy(); super.onDestroy() }

    companion object { const val ASK = 11; const val ASK_MIC = 12; const val PICK = 13 }
}
