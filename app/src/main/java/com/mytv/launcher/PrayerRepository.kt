package com.mytv.launcher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class City(
    val name: String,
    val admin: String?,
    val country: String?,
    val lat: Double,
    val lon: Double
) {
    val display: String
        get() = listOfNotNull(name, admin, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

data class DayTimings(val zone: ZoneId, val times: Map<String, String>)

data class NextPrayer(val name: String, val time: ZonedDateTime)

/**
 * Prayer times from the Aladhan API (aladhan.com) and city search from the
 * Open-Meteo geocoding API. Everything here is blocking: call from a background thread.
 */
object PrayerRepository {

    val PRAYERS = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")

    private val TIMING_KEYS = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")

    fun searchCities(query: String, lang: String): List<City> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query, "UTF-8") + "&count=10&language=$lang&format=json"
        val body = http(url) ?: return emptyList()
        return try {
            val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                City(
                    name = o.getString("name"),
                    admin = o.optString("admin1").ifBlank { null },
                    country = o.optString("country").ifBlank { null },
                    lat = o.getDouble("latitude"),
                    lon = o.getDouble("longitude")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Today's date in the chosen city (fixed UTC offset, independent of the TV's time-zone data). */
    fun cityToday(context: Context): LocalDate = LocalDate.now(CityClock.zone(context))

    private fun withOffsets(prefs: Prefs, t: DayTimings, zone: ZoneId): DayTimings {
        val adjusted = HashMap<String, String>()
        for ((k, v) in t.times) {
            val off = prefs.prayerOffset(k)
            adjusted[k] = if (off == 0) v else try {
                LocalTime.parse(v).plusMinutes(off.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: Exception) { v }
        }
        return DayTimings(zone, adjusted)
    }

    fun getTimings(context: Context, date: LocalDate): DayTimings? {
        val raw = getRawTimings(context, date) ?: return null
        return withOffsets(Prefs(context), raw, CityClock.zone(context))
    }

    private fun getRawTimings(context: Context, date: LocalDate): DayTimings? {
        val prefs = Prefs(context)
        val key = "$date|${prefs.latitude}|${prefs.longitude}|${prefs.method}"

        val cache = loadCache(prefs)
        cache.optString(key, "").takeIf { it.isNotEmpty() }?.let { cached ->
            parse(cached)?.let { return it }
        }

        val dateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val url = "https://api.aladhan.com/v1/timings/$dateStr" +
            "?latitude=${prefs.latitude}&longitude=${prefs.longitude}&method=${prefs.method}"
        val body = http(url) ?: return null
        val parsed = parse(body) ?: return null

        if (cache.length() > 6) {
            val keys = cache.keys().asSequence().toList()
            keys.forEach { cache.remove(it) }
        }
        cache.put(key, body)
        prefs.timingsCache = cache.toString()
        return parsed
    }

    /** The next prayer from now. With [onlyEnabled] only prayers the user switched on are considered. */
    fun nextPrayer(context: Context, onlyEnabled: Boolean): NextPrayer? {
        val prefs = Prefs(context)
        if (onlyEnabled && PRAYERS.none { prefs.isPrayerEnabled(it) }) return null
        CityClock.ensure(context)
        val now = java.time.Instant.now()
        val today = cityToday(context)
        for (offset in 0..2) {
            val date = today.plusDays(offset.toLong())
            val timings = getTimings(context, date) ?: return null
            for (name in PRAYERS) {
                if (onlyEnabled && !prefs.isPrayerEnabled(name)) continue
                val hhmm = timings.times[name] ?: continue
                val time = date.atTime(LocalTime.parse(hhmm)).atZone(timings.zone)
                if (time.toInstant().isAfter(now)) return NextPrayer(name, time)
            }
        }
        return null
    }

    private fun loadCache(prefs: Prefs): JSONObject = try {
        JSONObject(prefs.timingsCache ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    private fun parse(body: String): DayTimings? = try {
        val data = JSONObject(body).getJSONObject("data")
        val timings = data.getJSONObject("timings")
        val zone = ZoneId.of(data.getJSONObject("meta").getString("timezone"))
        val times = HashMap<String, String>()
        for (k in TIMING_KEYS) {
            // Values look like "04:55" or "04:55 (EET)"; keep the first five characters.
            timings.optString(k, "").takeIf { it.length >= 5 }?.let { times[k] = it.substring(0, 5) }
        }
        DayTimings(zone, times)
    } catch (e: Exception) {
        null
    }

    private fun http(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "MyTVLauncher/1.0")
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
