package com.mytv.launcher

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 12/24-hour display rules shared by the clock and the prayer times. */
object TimeFmt {

    fun is24(context: Context, prefs: Prefs): Boolean = when (prefs.timeFormat) {
        "12" -> false
        "24" -> true
        else -> DateFormat.is24HourFormat(context)
    }

    /** Formats a 24-hour "HH:mm" string (as Aladhan returns it) for display. */
    fun display(hhmm: String?, is24: Boolean, locale: Locale = Locale.getDefault()): String {
        if (hhmm == null) return "--:--"
        if (is24) return hhmm
        return try {
            LocalTime.parse(hhmm.trim().take(5)).format(DateTimeFormatter.ofPattern("h:mm a", locale))
        } catch (e: Exception) {
            hhmm
        }
    }
}
