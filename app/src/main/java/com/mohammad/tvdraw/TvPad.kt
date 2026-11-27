package com.mohammad.tvdraw

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import java.net.URLDecoder
import java.net.URLEncoder

/** Playing with the TV's own remote, with no phone: the remote joins a game as one more player and its keys become that game's commands.
    The home screen starts it and hands it the keys; the games themselves do not know the difference. */
object TvPad {
    const val CID = "tvpad"
    const val SNAKE = "snake"; const val KIDS = "kids"; const val RPS = "rps"; const val HIFZ = "hifz"
    var view: DrawView? = null
    var speak: ((String, String) -> Unit)? = null              // words to say aloud, and their language
    var game = ""; private set
    private var track = 0; private var spoke = ""
    private val ui = Handler(Looper.getMainLooper())
    /** Keeps the remote's seat in the game, notices when the game has ended, and reads the letters aloud. */
    private val tick = object : Runnable { override fun run() {
        val v = view; if (v == null || game == "") return
        when (game) {
            SNAKE -> if (!v.snake.active) game = ""
            HIFZ -> if (!v.hifz.active) { game = ""; deaf() } else if (v.hifz.phase != Hifz.READ && ears == "on") deaf()
            RPS -> if (!v.rps.active) game = "" else v.rps.status(CID)
            KIDS -> if (!v.kids.active) game = "" else { val f = v.kids.status().split("|")
                val sig = f[0] + "|" + f[2] + "|" + f[5]; val st = f[0].toIntOrNull() ?: 0
                if (sig != spoke && (st == KidsGame.LEARN || st == KidsGame.ASK)) { spoke = sig; try { speak?.invoke(URLDecoder.decode(f[10], "UTF-8"), f[14]) } catch (e: Throwable) { } } }
        }
        v.invalidate(); if (game != "") ui.postDelayed(this, 600)
    } }

    /** Starts a game for the remote. track: which cards, for the letters game. */
    fun start(c: Context, g: String, t: Int = 0) {
        val v = view ?: return
        stop(); game = g; track = t; spoke = ""
        when (g) { SNAKE -> v.handle("j join g", CID); KIDS -> v.handle("kd open $t", CID); RPS -> v.handle("rps join " + URLEncoder.encode(c.getString(R.string.pad_name), "UTF-8"), CID) }
        ui.removeCallbacks(tick); ui.postDelayed(tick, 300); v.invalidate()
    }
    fun stop() {
        val v = view; val g = game; game = ""; ui.removeCallbacks(tick); deaf()
        if (v != null) { when (g) { HIFZ -> v.handle("hz close", CID); SNAKE -> v.handle("j stop", CID); KIDS -> v.handle("kd close", CID); RPS -> v.handle("rps stop", CID) }; v.invalidate() }
    }

    /** Opens a stretch of the Quran for the remote: to listen and learn, to recite to the remote's microphone, or to test oneself. */
    fun startHifz(c: Context, partId: String, mode: String) {
        val v = view ?: return
        stop(); game = HIFZ; v.handle("hz part $partId $mode", CID)
        if (mode == "read") listen(c)
        ui.removeCallbacks(tick); ui.postDelayed(tick, 300); v.invalidate()
    }

    /* ---- the TV's own speech recogniser, which on a TV listens through the remote's microphone. What it hears goes to the same follower the phone's words go to. ---- */
    var ears = "off"; private set               // off, on, none (this TV cannot recognise speech), denied (no permission to listen)
    private var sr: android.speech.SpeechRecognizer? = null; private var sid = 1000; private var wantEars = false; private var fails = 0
    private fun enc(t: String) = URLEncoder.encode(t, "UTF-8").replace("+", "%20").ifEmpty { "%20" }
    /** The command that reports what was heard, as the phone would send it. */
    fun heardLine(session: Int, final: Boolean, best: String, alts: List<String>): String =
        "hz heard " + session + " " + (if (final) 1 else 0) + " 0 " + enc(best.trim()) + (if (alts.isNotEmpty()) " " + enc(alts.joinToString("|") { it.trim() }) else "")
    fun listen(c: Context) {
        wantEars = true
        try {
            if (!android.speech.SpeechRecognizer.isRecognitionAvailable(c)) { ears = "none"; return }
            if (c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { ears = "denied"; return }
            if (sr == null) sr = android.speech.SpeechRecognizer.createSpeechRecognizer(c.applicationContext).also { it.setRecognitionListener(object : android.speech.RecognitionListener {
                private fun words(b: android.os.Bundle?) = b?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                override fun onPartialResults(b: android.os.Bundle?) { val w = words(b); if (w.isNotEmpty() && w[0].isNotBlank()) { fails = 0; view?.handle(heardLine(sid, false, w[0], emptyList()), CID) } }
                override fun onResults(b: android.os.Bundle?) { val w = words(b); if (w.isNotEmpty() && w[0].isNotBlank()) { fails = 0; view?.handle(heardLine(sid, true, w[0], w.drop(1).take(3)), CID) }; again(c, 150) }
                override fun onError(e: Int) { if (e == android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { ears = "denied"; wantEars = false; return }
                    // silence and "nothing understood" are ordinary: listen again. Anything that keeps failing means this TV cannot do it
                    if (e != android.speech.SpeechRecognizer.ERROR_NO_MATCH && e != android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) fails++
                    if (fails > 8) { ears = "none"; wantEars = false } else again(c, if (e == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900 else 350) }
                override fun onReadyForSpeech(p: android.os.Bundle?) {}; override fun onBeginningOfSpeech() {}; override fun onRmsChanged(r: Float) {}; override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}; override fun onEvent(t: Int, p: android.os.Bundle?) {} }) }
            begin(c)
        } catch (e: Throwable) { ears = "none" }
    }
    private fun begin(c: Context) { sid++
        val i = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ar-SA").putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true).putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 4)
            .putExtra(android.speech.RecognizerIntent.EXTRA_CALLING_PACKAGE, c.packageName)
        try { sr?.startListening(i); ears = "on" } catch (e: Throwable) { ears = "none" } }
    private fun again(c: Context, ms: Long) { ui.postDelayed({ if (wantEars && game == HIFZ && view?.hifz?.phase == Hifz.READ) begin(c) else if (ears == "on") ears = "off" }, ms) }
    fun deaf() { wantEars = false; try { sr?.cancel() } catch (e: Throwable) { }; if (ears == "on") ears = "off" }

    /** A key was pressed. Returns true: while a game is on, every key is the game's. */
    fun key(code: Int): Boolean {
        val v = view ?: return false
        if (game == "") return false
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) { stop(); return true }
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val left = code == KeyEvent.KEYCODE_DPAD_LEFT; val right = code == KeyEvent.KEYCODE_DPAD_RIGHT; val up = code == KeyEvent.KEYCODE_DPAD_UP; val down = code == KeyEvent.KEYCODE_DPAD_DOWN
        when (game) {
            HIFZ -> { val f = v.hifz.status().substringBefore('\n').split("|")
                when (v.hifz.phase) {
                    Hifz.LISTEN -> when { left -> v.handle("hz next", CID); right -> v.handle("hz prev", CID); ok -> v.handle("hz say", CID)
                        up -> v.handle("hz hide " + ((f.getOrNull(5)?.toIntOrNull() ?: 0) + 1) % 4, CID); down -> v.handle("hz auto " + (if (f.getOrNull(6) == "1") 0 else 1), CID) }
                    Hifz.TEST -> when { ok || up -> v.handle("hz mark 1", CID); down -> v.handle("hz mark 0", CID) }
                    Hifz.READ -> when { ok -> v.handle("hz ok", CID); down -> v.handle("hz reveal", CID); up -> if (ears == "on") deaf() else listen(v.context) }
                    else -> if (ok) stop()
                } }
            SNAKE -> when { ok -> v.handle("j start", CID); left -> v.handle("r -1 0", CID); right -> v.handle("r 1 0", CID); up -> v.handle("r 0 -1", CID); down -> v.handle("r 0 1", CID) }
            RPS -> { val f = v.rps.status(CID).split("|"); val st = f[0].toIntOrNull() ?: 0
                if (st == RpsGame.PICK) { val hand = if (left) RpsCore.ROCK else if (up || ok) RpsCore.PAPER else if (right) RpsCore.SCISSORS else -1; if (hand >= 0) v.handle("rps pick " + f[1] + " " + hand, CID) }
                else if (ok && (st == RpsGame.LOBBY || st == RpsGame.END)) v.handle("rps start", CID) }
            KIDS -> { val st = v.kids.status().split("|")[0].toIntOrNull() ?: 0; val en = track == KidsCore.EN
                when (st) {
                    KidsGame.LEARN -> when { left -> v.handle(if (en) "kd prev" else "kd next", CID); right -> v.handle(if (en) "kd next" else "kd prev", CID); up -> v.handle("kd play", CID); ok -> spoke = "" }
                    // the three choices are drawn in a row: the left key takes the left one, OK or up the middle one, the right key the right one
                    KidsGame.ASK -> when { left -> v.handle("kd ans " + (if (en) 0 else 2), CID); ok || up -> v.handle("kd ans 1", CID); right -> v.handle("kd ans " + (if (en) 2 else 0), CID); down -> v.handle("kd learn", CID) }
                    else -> when { ok || up -> v.handle("kd play", CID); down -> v.handle("kd learn", CID) }
                } }
        }
        return true
    }

    /** One line telling the player what the keys do now. */
    fun hint(c: Context): String { val v = view ?: return ""
        return when (game) {
            HIFZ -> c.getString(when (v.hifz.phase) { Hifz.LISTEN -> R.string.pad_hz_listen; Hifz.TEST -> R.string.pad_hz_test
                Hifz.READ -> when (ears) { "on" -> R.string.pad_hz_read_on; "none" -> R.string.pad_hz_read_none; "denied" -> R.string.pad_hz_read_denied; else -> R.string.pad_hz_read_off }; else -> R.string.pad_hz_done })
            SNAKE -> c.getString(if (v.snake.phase == SnakeGame.PLAY || v.snake.phase == SnakeGame.COUNT) R.string.pad_snake_play else R.string.pad_snake_start)
            RPS -> c.getString(if (v.rps.phase == RpsGame.PICK) R.string.pad_rps_pick else if (v.rps.phase == RpsGame.REVEAL) R.string.pad_wait else R.string.pad_rps_start)
            KIDS -> c.getString(when (v.kids.phase) { KidsGame.LEARN -> R.string.pad_kids_learn; KidsGame.ASK -> R.string.pad_kids_ask; KidsGame.RIGHT -> R.string.pad_wait; else -> R.string.pad_kids_done })
            else -> "" } }
}
