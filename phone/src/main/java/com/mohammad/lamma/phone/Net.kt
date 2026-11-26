package com.mohammad.lamma.phone

/** Small pieces of the search for the TV that need no Android, so they can be tested on their own. */
object Net {
    class Tv(val ip: String, val version: String, val http: Int, val https: Int)

    /** Every other address of the phone's own /24 network, nearest numbers first. */
    fun neighbours(own: String): List<String> {
        val p = own.split('.'); if (p.size != 4 || p.any { it.toIntOrNull() == null }) return emptyList()
        val me = p[3].toInt(); val base = p.take(3).joinToString(".")
        return (1..254).filter { it != me }.sortedBy { Math.abs(it - me) }.map { "$base.$it" }
    }

    /** Reads the TV's status line ("ver=3.30|...|http=8765|https=8444|..."). Null when it is not Lamma answering. */
    fun parse(ip: String, body: String, askedPort: Int): Tv? {
        if (!body.contains("clock=") || !body.contains("times=")) return null
        val o = HashMap<String, String>(); for (x in body.trim().split('|')) { val i = x.indexOf('='); if (i > 0) o[x.substring(0, i)] = x.substring(i + 1) }
        return Tv(ip, o["ver"] ?: "", o["http"]?.toIntOrNull()?.takeIf { it > 0 } ?: askedPort, o["https"]?.toIntOrNull() ?: 0)
    }

    /** Only addresses of a home network may be opened with a certificate nobody signed. */
    fun isHome(host: String): Boolean {
        val p = host.split('.').map { it.toIntOrNull() ?: return false }; if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }
}
