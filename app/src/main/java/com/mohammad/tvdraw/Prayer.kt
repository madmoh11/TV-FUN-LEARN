package com.mohammad.tvdraw

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Prayer time calculation (standard astronomical method), no internet needed. */
object Prayer {

    /** ishaMin > 0 means "Isha = Maghrib + ishaMin minutes" (Umm al-Qura style) instead of an angle. */
    class Method(val fajrAngle: Double, val ishaAngle: Double, val ishaMin: Int, val maghribMin: Int)

    private fun rad(d: Double) = d * Math.PI / 180
    private fun deg(r: Double) = r * 180 / Math.PI
    private fun fix(a: Double, m: Double): Double { val x = a - m * floor(a / m); return if (x < 0) x + m else x }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0); val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** returns [declination in degrees, equation of time in hours] */
    private fun sun(jd: Double): DoubleArray {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15
        return doubleArrayOf(deg(asin(sin(rad(e)) * sin(rad(l)))), q / 15 - fix(ra, 24.0))
    }

    /**
     * Times for one calendar day, in hours of local clock time: fajr, dhuhr, asr, maghrib, isha.
     * tz = hours ahead of UTC on that day.
     */
    fun times(year: Int, month: Int, day: Int, lat: Double, lon: Double, tz: Double, m: Method): DoubleArray {
        val jd = julian(year, month, day) - lon / (15 * 24)
        fun midDay(t: Double) = fix(12 - sun(jd + t)[1], 24.0)
        fun angleTime(angle: Double, t: Double, before: Boolean): Double {
            val decl = sun(jd + t)[0]
            val x = (-sin(rad(angle)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            val h = deg(acos(x.coerceIn(-1.0, 1.0))) / 15
            return midDay(t) + if (before) -h else h
        }
        fun asr(t: Double): Double {
            val decl = sun(jd + t)[0]
            val angle = -deg(atan(1 / (1 + tan(rad(abs(lat - decl))))))      // shadow = object length + noon shadow
            return angleTime(angle, t, false)
        }
        val shift = tz - lon / 15
        val fajr = angleTime(m.fajrAngle, 5 / 24.0, true) + shift
        val dhuhr = midDay(12 / 24.0) + shift
        val asrT = asr(13 / 24.0) + shift
        val maghrib = angleTime(0.833, 18 / 24.0, false) + shift + m.maghribMin / 60.0
        val isha = if (m.ishaMin > 0) maghrib + m.ishaMin / 60.0 else angleTime(m.ishaAngle, 18 / 24.0, false) + shift
        return doubleArrayOf(fajr, dhuhr, asrT, maghrib, isha)
    }

    fun hhmm(h: Double): String {
        val mins = Math.round(fix(h, 24.0) * 60).toInt() % 1440
        return (mins / 60).toString() + ":" + (mins % 60).toString().padStart(2, '0')
    }
}
