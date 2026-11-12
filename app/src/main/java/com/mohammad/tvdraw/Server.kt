package com.mohammad.tvdraw

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Tiny HTTP + HTTPS server for the phone page.
 * GET / = the page, POST /e = command lines, POST /a = 16 kHz mono PCM16 voice, POST /i = picture to show,
 * POST /f = selfie for the snake, POST /v = live camera frame, GET /s /p /g /h /j = sound, prayer and game status.
 */
class Server(private val page: () -> String, private val keyDir: java.io.File, private val onLines: (List<String>, String) -> Unit, private val onAudio: (ByteArray) -> Unit,
             private val onImage: (ByteArray) -> Unit, private val onFace: (ByteArray, String) -> Unit, private val onVideo: (ByteArray, String) -> Unit, private val status: (String) -> String) {
    var httpPort = 0; private set
    var httpsPort = 0; private set

    fun start() {
        bind(listOf(8765, 8766, 8767, 9321)) { ServerSocket(it) }?.let { httpPort = it.localPort; listen(it) }
        thread(isDaemon = true) {            // the first run creates this TV's own key, which can take a few seconds
            try {
                val ctx = Tls.context(keyDir)
                bind(listOf(8443, 8444, 9443, 9444)) { ctx.serverSocketFactory.createServerSocket(it) }
                    ?.let { httpsPort = it.localPort; listen(it) }
            } catch (e: Exception) { }
        }
    }

    private fun bind(ports: List<Int>, make: (Int) -> ServerSocket): ServerSocket? {
        for (p in ports) try { return make(p) } catch (e: Exception) { }
        return null
    }

    private fun listen(ss: ServerSocket) {
        thread(isDaemon = true) {
            while (true) try { val s = ss.accept(); thread(isDaemon = true) { serve(s) } } catch (e: Exception) { }
        }
    }

    private fun serve(s: Socket) {
        try {
            s.soTimeout = 30000
            val inp = BufferedInputStream(s.getInputStream())
            val out = BufferedOutputStream(s.getOutputStream())
            while (true) {
                val req = readLine(inp) ?: break
                if (req.isEmpty()) continue
                var len = 0; var close = false
                while (true) {
                    val h = (readLine(inp) ?: break).lowercase(); if (h.isEmpty()) break
                    if (h.startsWith("content-length:")) len = h.substring(15).trim().toInt()
                    if (h.startsWith("connection:") && h.contains("close")) close = true
                }
                if (len < 0 || len > 12_000_000) break
                val body = ByteArray(len); var r = 0
                while (r < len) { val n = inp.read(body, r, len - r); if (n < 0) break; r += n }
                if (r < len) break
                val parts = req.split(" ")
                val path = if (parts.size > 1) parts[1] else "/"
                var type = "text/plain"; var resp = ByteArray(0)
                if (parts[0] == "POST" && path.startsWith("/a")) onAudio(body)
                else if (parts[0] == "POST" && path.startsWith("/i")) onImage(body)
                else if (parts[0] == "POST" && path.startsWith("/f")) onFace(body, path.substringAfter("c=", "x").filter { it.isLetterOrDigit() }.take(12).ifEmpty { "x" })
                else if (parts[0] == "POST" && path.startsWith("/v")) onVideo(body, path.substringAfter("c=", "x").filter { it.isLetterOrDigit() }.take(12).ifEmpty { "x" })
                else if (path.startsWith("/c") || path.startsWith("/s") || path.startsWith("/p") || path.startsWith("/g") || path.startsWith("/h") || path.startsWith("/j")) resp = status(path).toByteArray(Charsets.UTF_8)
                else if (parts[0] == "POST") onLines(String(body, Charsets.UTF_8).split("\n").filter { it.isNotBlank() },
                    path.substringAfter("c=", "x").filter { it.isLetterOrDigit() }.take(12).ifEmpty { "x" })
                else { type = "text/html; charset=utf-8"; resp = page().replace("__HTTPS__", httpsPort.toString()).toByteArray(Charsets.UTF_8) }
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${resp.size}\r\n" +
                    "Cache-Control: no-store\r\n\r\n").toByteArray())
                out.write(resp); out.flush()
                if (close) break
            }
        } catch (e: Exception) { }
        try { s.close() } catch (e: Exception) { }
    }

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder(); var any = false
        while (true) {
            val c = i.read(); if (c < 0) return if (any) sb.toString() else null
            any = true
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
            if (sb.length > 8192) return null
        }
        return sb.toString()
    }
}
