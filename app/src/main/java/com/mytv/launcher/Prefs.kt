package com.mytv.launcher

import android.content.Context
import org.json.JSONArray

/** Thin wrapper over SharedPreferences for every setting the launcher stores. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    private fun readList(key: String): List<String> {
        val raw = sp.getString(key, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeList(key: String, list: List<String>) {
        sp.edit().putString(key, JSONArray(list).toString()).apply()
    }

    private fun putString(key: String, value: String?) {
        sp.edit().putString(key, value).apply()
    }

    // ---- Apps ----
    var favorites: List<String>
        get() = readList("favorites")
        set(value) = writeList("favorites", value)

    /** True once the dock was filled automatically (first five apps) or edited by the user. */
    var favoritesInit: Boolean
        get() = sp.getBoolean("favorites_init", false)
        set(value) {
            sp.edit().putBoolean("favorites_init", value).apply()
        }

    /** Home module card: current weather next to the date. */
    var showWeather: Boolean
        get() = sp.getBoolean("card_weather", true)
        set(value) {
            sp.edit().putBoolean("card_weather", value).apply()
        }

    /** Apps per row on the home grid and in the library (4..7). */
    var gridSpan: Int
        get() = sp.getInt("grid_span", 7)
        set(value) {
            sp.edit().putInt("grid_span", value).apply()
        }

    /** Rows of apps shown under the favorites row (0..3); the rest live in the library. */
    var homeRows: Int
        get() = sp.getInt("home_rows", 2)
        set(value) {
            sp.edit().putInt("home_rows", value).apply()
        }

    var appOrder: List<String>
        get() = readList("app_order")
        set(value) = writeList("app_order", value)

    var hidden: List<String>
        get() = readList("hidden")
        set(value) = writeList("hidden", value)

    /** Home module card: today's prayer times. */
    var showPrayerCard: Boolean
        get() = sp.getBoolean("card_prayer", true)
        set(value) {
            sp.edit().putBoolean("card_prayer", value).apply()
        }

    // ---- Prayer / Adhan ----
    var adhanEnabled: Boolean
        get() = sp.getBoolean("adhan_enabled", false)
        set(value) {
            sp.edit().putBoolean("adhan_enabled", value).apply()
        }

    fun isPrayerEnabled(name: String): Boolean = sp.getBoolean("prayer_$name", true)

    fun setPrayerEnabled(name: String, value: Boolean) {
        sp.edit().putBoolean("prayer_$name", value).apply()
    }

    var cityName: String
        get() = sp.getString("city_name", "Amman, Jordan") ?: "Amman, Jordan"
        set(value) = putString("city_name", value)

    var latitude: Double
        get() = (sp.getString("lat", null) ?: "31.9539").toDoubleOrNull() ?: 31.9539
        set(value) = putString("lat", value.toString())

    var longitude: Double
        get() = (sp.getString("lon", null) ?: "35.9106").toDoubleOrNull() ?: 35.9106
        set(value) = putString("lon", value.toString())

    /** Aladhan calculation method id. 23 = Jordan (Ministry of Awqaf). */
    var method: Int
        get() = sp.getInt("method", 23)
        set(value) {
            sp.edit().putInt("method", value).apply()
        }

    /** Adhan playback volume, 10..100 percent. */
    var adhanVolume: Int
        get() = sp.getInt("adhan_volume", 100)
        set(value) {
            sp.edit().putInt("adhan_volume", value.coerceIn(10, 100)).apply()
        }

    /** Manual per-prayer correction in minutes (-15..+15). */
    fun prayerOffset(name: String): Int = sp.getInt("offset_$name", 0)

    fun setPrayerOffset(name: String, minutes: Int) {
        sp.edit().putInt("offset_$name", minutes.coerceIn(-15, 15)).apply()
    }

    /** "auto" (offset of the chosen city, from Open-Meteo) or a fixed UTC offset in seconds, e.g. "10800". */
    var tzMode: String
        get() = sp.getString("tz_mode", "auto") ?: "auto"
        set(value) = putString("tz_mode", value)

    /** Last known UTC offset (seconds) of the chosen city, its key ("lat,lon") and when it was fetched. */
    var cityOffsetSec: Int
        get() = sp.getInt("city_offset_sec", Int.MIN_VALUE)
        set(value) { sp.edit().putInt("city_offset_sec", value).apply() }

    var cityOffsetKey: String?
        get() = sp.getString("city_offset_key", null)
        set(value) = putString("city_offset_key", value)

    var cityOffsetAt: Long
        get() = sp.getLong("city_offset_at", 0L)
        set(value) { sp.edit().putLong("city_offset_at", value).apply() }

    /** Hijri date correction in days (-2..+2) to match the local moon-sighting announcement. */
    var hijriAdjust: Int
        get() = sp.getInt("hijri_adjust", 0)
        set(value) { sp.edit().putInt("hijri_adjust", value.coerceIn(-2, 2)).apply() }

    /** Minutes of no key presses before the clock screensaver starts (0 = off). */
    var screensaverMinutes: Int
        get() = sp.getInt("screensaver_min", 10)
        set(value) { sp.edit().putInt("screensaver_min", value).apply() }

    /** Seconds between photos in the slideshows. */
    var slideSeconds: Int
        get() = sp.getInt("slide_seconds", 30)
        set(value) { sp.edit().putInt("slide_seconds", value).apply() }

    /** "prayer|epoch-minute" of the last Adhan that actually played, to prevent repeats. */
    var lastAdhan: String?
        get() = sp.getString("last_adhan", null)
        set(value) = putString("last_adhan", value)

    var adhanUri: String?
        get() = sp.getString("adhan_uri", null)
        set(value) = putString("adhan_uri", value)

    var adhanName: String?
        get() = sp.getString("adhan_name", null)
        set(value) = putString("adhan_name", value)

    var timingsCache: String?
        get() = sp.getString("timings_cache", null)
        set(value) = putString("timings_cache", value)

    // ---- Wallpaper ----
    var wallpaperUri: String?
        get() = sp.getString("wallpaper_uri", null)
        set(value) = putString("wallpaper_uri", value)

    /** "none" (built-in preset), "image", "video" or "slideshow". */
    var wallpaperType: String
        get() = sp.getString("wallpaper_type", "none") ?: "none"
        set(value) = putString("wallpaper_type", value)

    /** "kirkjufell", "photo1", "photo3" or "photo6". */
    var wallpaperPreset: String
        get() = sp.getString("wallpaper_preset", "kirkjufell") ?: "kirkjufell"
        set(value) = putString("wallpaper_preset", value)

    /** Bumped whenever slideshow photos change so the home screen reloads them. */
    var wallpaperStamp: Long
        get() = sp.getLong("wallpaper_stamp", 0L)
        set(value) {
            sp.edit().putLong("wallpaper_stamp", value).apply()
        }

    var dim: Int
        get() = sp.getInt("dim", 25)
        set(value) {
            sp.edit().putInt("dim", value).apply()
        }

    // ---- Customization ----
    var glossy: Boolean
        get() = sp.getBoolean("glossy", true)
        set(value) { sp.edit().putBoolean("glossy", value).apply() }

    var animations: Boolean
        get() = sp.getBoolean("animations", true)
        set(value) { sp.edit().putBoolean("animations", value).apply() }

    var showGreeting: Boolean
        get() = sp.getBoolean("show_greeting", true)
        set(value) { sp.edit().putBoolean("show_greeting", value).apply() }

    var showWifiButton: Boolean
        get() = sp.getBoolean("show_wifi_btn", true)
        set(value) { sp.edit().putBoolean("show_wifi_btn", value).apply() }

    // ---- General ----
    /** "system", "12" or "24". */
    var timeFormat: String
        get() = sp.getString("time_format", "24") ?: "24"
        set(value) = putString("time_format", value)

    /** "system", "ar" or "en". */
    var language: String
        get() = sp.getString("language", "system") ?: "system"
        set(value) = putString("language", value)
}
