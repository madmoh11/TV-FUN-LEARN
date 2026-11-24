package com.mohammad.tvdraw

import android.content.Context
import java.util.Calendar
import java.util.SimpleTimeZone
import java.util.TimeZone

/**
 * The time of day as the family reads it. Old TV boxes carry time-zone rules from before Jordan and Syria stopped
 * changing their clocks in 2022, so in winter they show an hour less than the real time; those two zones are held at
 * three hours ahead of UTC all year. A shift of whole hours can also be set from the phone for any other wrong clock.
 */
object TvClock {
    private var app: Context? = null
    fun init(c: Context) { app = c.applicationContext }

    /** Minutes ahead of UTC that a zone id should have right now, before any shift from the phone. */
    fun baseMinutes(id: String, defaultOffsetMs: Int) = if (id == "Asia/Amman" || id == "Asia/Damascus") 180 else defaultOffsetMs / 60000

    fun shift(): Int = app?.getSharedPreferences("prayer", 0)?.getInt("shift", 0) ?: 0
    fun zone(): TimeZone {
        val d = TimeZone.getDefault()
        return SimpleTimeZone((baseMinutes(d.id, d.getOffset(System.currentTimeMillis())) + 60 * shift()) * 60000, "tv")
    }
    fun now(): Calendar = Calendar.getInstance(zone())
}
