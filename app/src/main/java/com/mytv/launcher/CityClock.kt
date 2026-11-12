package com.mytv.launcher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

/**
 * The clock of the chosen city. Some TVs ship outdated time-zone data (for example Amman as UTC+2
 * instead of UTC+3), so the launcher does not trust the device zone: the UTC offset comes from
 * Open-Meteo (cached), or from a fixed value the user picked in the settings.
 */
object CityClock {

    private const val REFRESH_MS = 6 * 60 * 60_000L

    private fun key(p: Prefs) = "${"%.2f".format(Locale.US, p.latitude)},${"%.2f".format(Locale.US, p.longitude)}"

    /** Current UTC offset of the city in seconds. */
    fun offsetSeconds(context: Context): Int {
        val p = Prefs(context)
        val mode = p.tzMode
        if (mode != "auto") mode.toIntOrNull()?.let { return it }
        if (p.cityOffsetSec != Int.MIN_VALUE && p.cityOffsetKey == key(p)) return p.cityOffsetSec
        return TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
    }

    fun zone(context: Context): ZoneOffset =
        ZoneOffset.ofTotalSeconds(offsetSeconds(context).coerceIn(-18 * 3600, 18 * 3600))

    /** An id such as "GMT+03:00" that TextClock and SimpleDateFormat understand. */
    fun gmtId(context: Context): String = gmtId(offsetSeconds(context))

    fun gmtId(seconds: Int): String {
        val abs = Math.abs(seconds)
        return String.format(Locale.US, "GMT%s%02d:%02d", if (seconds < 0) "-" else "+", abs / 3600, (abs % 3600) / 60)
    }

    fun timeZone(context: Context): TimeZone = TimeZone.getTimeZone(gmtId(context))

    /** "UTC+3" style label. */
    fun label(seconds: Int): String {
        val abs = Math.abs(seconds)
        val h = abs / 3600
        val m = (abs % 3600) / 60
        return "UTC" + (if (seconds < 0) "-" else "+") + h + if (m != 0) ":" + String.format(Locale.US, "%02d", m) else ""
    }

    /** Refreshes the cached offset when it is old or the city changed. Blocking: call off the main thread. */
    fun ensure(context: Context): Boolean {
        val p = Prefs(context)
        if (p.tzMode != "auto") return false
        val k = key(p)
        val fresh = p.cityOffsetKey == k && p.cityOffsetSec != Int.MIN_VALUE &&
            System.currentTimeMillis() - p.cityOffsetAt < REFRESH_MS
        if (fresh) return false
        val sec = fetchOffset(p.latitude, p.longitude) ?: return false
        val changed = p.cityOffsetSec != sec || p.cityOffsetKey != k
        p.cityOffsetSec = sec
        p.cityOffsetKey = k
        p.cityOffsetAt = System.currentTimeMillis()
        return changed
    }

    private fun fetchOffset(lat: Double, lon: Double): Int? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m&forecast_days=1&timezone=auto"
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            if (o.has("utc_offset_seconds")) o.getInt("utc_offset_seconds") else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
