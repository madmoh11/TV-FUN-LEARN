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
    const val SNAKE = "snake"; const val KIDS = "kids"; const val RPS = "rps"
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
        val v = view; val g = game; game = ""; ui.removeCallbacks(tick)
        if (v != null) { when (g) { SNAKE -> v.handle("j stop", CID); KIDS -> v.handle("kd close", CID); RPS -> v.handle("rps stop", CID) }; v.invalidate() }
    }

    /** A key was pressed. Returns true: while a game is on, every key is the game's. */
    fun key(code: Int): Boolean {
        val v = view ?: return false
        if (game == "") return false
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) { stop(); return true }
        val ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER
        val left = code == KeyEvent.KEYCODE_DPAD_LEFT; val right = code == KeyEvent.KEYCODE_DPAD_RIGHT; val up = code == KeyEvent.KEYCODE_DPAD_UP; val down = code == KeyEvent.KEYCODE_DPAD_DOWN
        when (game) {
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
            SNAKE -> c.getString(if (v.snake.phase == SnakeGame.PLAY || v.snake.phase == SnakeGame.COUNT) R.string.pad_snake_play else R.string.pad_snake_start)
            RPS -> c.getString(if (v.rps.phase == RpsGame.PICK) R.string.pad_rps_pick else if (v.rps.phase == RpsGame.REVEAL) R.string.pad_wait else R.string.pad_rps_start)
            KIDS -> c.getString(when (v.kids.phase) { KidsGame.LEARN -> R.string.pad_kids_learn; KidsGame.ASK -> R.string.pad_kids_ask; KidsGame.RIGHT -> R.string.pad_wait; else -> R.string.pad_kids_done })
            else -> "" } }
}
