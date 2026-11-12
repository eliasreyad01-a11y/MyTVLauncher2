package com.mytv.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class DayForecast(val code: Int, val maxC: Int, val minC: Int, val rainPct: Int)

data class Weather(
    val tempC: Int,
    val code: Int,
    val isDay: Boolean,
    val feelsC: Int,
    val humidity: Int,
    val windKmh: Int,
    val today: DayForecast,
    val tomorrow: DayForecast
) {
    val emoji: String get() = emojiFor(code, isDay)

    companion object {
        fun emojiFor(code: Int, isDay: Boolean = true): String = when (code) {
            0 -> if (isDay) "\u2600\uFE0F" else "\uD83C\uDF19"
            1, 2 -> if (isDay) "\uD83C\uDF24\uFE0F" else "\u2601\uFE0F"
            3 -> "\u2601\uFE0F"
            45, 48 -> "\uD83C\uDF2B\uFE0F"
            in 51..67, in 80..82 -> "\uD83C\uDF27\uFE0F"
            in 71..77, 85, 86 -> "\u2744\uFE0F"
            in 95..99 -> "\u26C8\uFE0F"
            else -> "\uD83C\uDF21\uFE0F"
        }

        /** String resource describing a WMO weather code. */
        fun labelRes(code: Int): Int = when (code) {
            0 -> R.string.wx_clear
            1, 2 -> R.string.wx_partly
            3 -> R.string.wx_cloudy
            45, 48 -> R.string.wx_fog
            in 51..67, in 80..82 -> R.string.wx_rain
            in 71..77, 85, 86 -> R.string.wx_snow
            in 95..99 -> R.string.wx_storm
            else -> R.string.wx_clear
        }
    }
}

/** Weather from Open-Meteo (open-meteo.com, no API key). Blocking: call off the main thread. */
object WeatherRepository {

    fun fetch(lat: Double, lon: Double): Weather? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=2&timezone=auto"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val body = try {
                if (conn.responseCode != 200) return null
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val root = JSONObject(body)
            val cur = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")
            fun day(i: Int) = DayForecast(
                code = daily.getJSONArray("weather_code").getInt(i),
                maxC = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(i)).toInt(),
                minC = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(i)).toInt(),
                rainPct = daily.getJSONArray("precipitation_probability_max").optInt(i, 0)
            )
            Weather(
                tempC = Math.round(cur.getDouble("temperature_2m")).toInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                feelsC = Math.round(cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m"))).toInt(),
                humidity = cur.optInt("relative_humidity_2m", 0),
                windKmh = Math.round(cur.optDouble("wind_speed_10m", 0.0)).toInt(),
                today = day(0),
                tomorrow = day(1)
            )
        } catch (e: Exception) {
            null
        }
    }
}
