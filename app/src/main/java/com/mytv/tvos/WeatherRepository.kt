package com.mytv.tvos

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One hour of the forecast: "HH:mm" in the city's time, temperature, weather code, rain chance, and whether the sun is up. */
data class HourPoint(val hhmm: String, val tempC: Int, val code: Int, val rainPct: Int, val isDay: Boolean)

/** One day of the forecast; [date] is "yyyy-MM-dd". */
data class DayDetail(val date: String, val code: Int, val maxC: Int, val minC: Int, val rainPct: Int)

data class DayForecast(val code: Int, val maxC: Int, val minC: Int, val rainPct: Int)

data class Weather(
    val tempC: Int,
    val code: Int,
    val isDay: Boolean,
    val feelsC: Int,
    val humidity: Int,
    val windKmh: Int,
    val today: DayForecast,
    val tomorrow: DayForecast,
    val pressureHpa: Int = 0,
    val windDirDeg: Int = -1,
    val uvIndex: Int = -1,
    val sunrise: String = "",
    val sunset: String = "",
    val hourly: List<HourPoint> = emptyList(),
    val days: List<DayDetail> = emptyList()
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
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,surface_pressure" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
            "&forecast_days=5&timezone=auto"
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
            val dates = daily.getJSONArray("time")
            val days = (0 until dates.length()).map { i -> day(i).let { DayDetail(dates.getString(i), it.code, it.maxC, it.minC, it.rainPct) } }
            val hourly = ArrayList<HourPoint>()
            try {
                val h = root.getJSONObject("hourly")
                val times = h.getJSONArray("time")
                val now = cur.optString("time", "")
                var start = 0
                for (i in 0 until times.length()) { if (times.getString(i) >= now) { start = i; break } }
                for (i in start until minOf(times.length(), start + 10)) {
                    hourly.add(HourPoint(times.getString(i).substringAfter('T'), Math.round(h.getJSONArray("temperature_2m").getDouble(i)).toInt(),
                        h.getJSONArray("weather_code").getInt(i), h.getJSONArray("precipitation_probability").optInt(i, 0), h.getJSONArray("is_day").optInt(i, 1) == 1))
                }
            } catch (_: Exception) {}
            Weather(
                tempC = Math.round(cur.getDouble("temperature_2m")).toInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                feelsC = Math.round(cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m"))).toInt(),
                humidity = cur.optInt("relative_humidity_2m", 0),
                windKmh = Math.round(cur.optDouble("wind_speed_10m", 0.0)).toInt(),
                today = day(0),
                tomorrow = day(1),
                pressureHpa = Math.round(cur.optDouble("surface_pressure", 0.0)).toInt(),
                windDirDeg = if (cur.has("wind_direction_10m")) Math.round(cur.optDouble("wind_direction_10m", -1.0)).toInt() else -1,
                uvIndex = try { Math.round(daily.getJSONArray("uv_index_max").getDouble(0)).toInt() } catch (_: Exception) { -1 },
                sunrise = try { daily.getJSONArray("sunrise").getString(0).substringAfter('T') } catch (_: Exception) { "" },
                sunset = try { daily.getJSONArray("sunset").getString(0).substringAfter('T') } catch (_: Exception) { "" },
                hourly = hourly,
                days = days
            )
        } catch (e: Exception) {
            null
        }
    }
}
