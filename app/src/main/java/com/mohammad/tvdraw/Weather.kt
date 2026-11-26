package com.mohammad.tvdraw

import org.json.JSONObject

/** The weather now and today's high and low, read from the answer of Open-Meteo (open-meteo.com, CC BY 4.0). */
object Weather {
    class Now(val temp: Int, val code: Int, val day: Boolean, val high: Int, val low: Int)

    fun url(lat: Double, lon: Double) = "https://api.open-meteo.com/v1/forecast?latitude=" + String.format(java.util.Locale.US, "%.4f", lat) + "&longitude=" +
        String.format(java.util.Locale.US, "%.4f", lon) + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"

    /** Null when the answer is not a forecast. */
    fun parse(json: String): Now? = try {
        val o = JSONObject(json); val c = o.getJSONObject("current"); val d = o.getJSONObject("daily")
        Now(Math.round(c.getDouble("temperature_2m")).toInt(), c.getInt("weather_code"), c.optInt("is_day", 1) == 1,
            Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)).toInt(), Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)).toInt())
    } catch (e: Throwable) { null }

    /** A small picture for a WMO weather code. */
    fun icon(code: Int, day: Boolean) = when (code) {
        0 -> if (day) "☀️" else "🌙"; 1, 2 -> if (day) "🌤️" else "☁️"; 3 -> "☁️"; 45, 48 -> "🌫️"
        in 51..57 -> "🌦️"; in 61..67, in 80..82 -> "🌧️"; in 71..77, 85, 86 -> "❄️"; in 95..99 -> "⛈️"; else -> "🌡️" }
    /** Which of the weather words fits a WMO code: 0 clear, 1 some cloud, 2 cloudy, 3 fog, 4 drizzle, 5 rain, 6 snow, 7 thunder. */
    fun kind(code: Int) = when (code) { 0 -> 0; 1, 2 -> 1; 3 -> 2; 45, 48 -> 3; in 51..57 -> 4; in 61..67, in 80..82 -> 5; in 71..77, 85, 86 -> 6; in 95..99 -> 7; else -> 2 }
}
