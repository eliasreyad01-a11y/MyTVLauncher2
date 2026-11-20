package com.mytv.tvos

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 12/24-hour display rules shared by the clock and the prayer times. */
/** Which digits are shown: "auto" leaves them as the language gives them, "latin" = 0123456789, "arabic" = ٠١٢٣٤٥٦٧٨٩. One setting for the whole launcher. */
object Digits {
    @Volatile var mode = "auto"

    fun load(prefs: Prefs) { mode = prefs.numerals }

    fun fix(s: String): String = convert(s, mode)

    fun convert(s: String, m: String): String {
        if (m == "auto") return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when {
                    m == "arabic" && ch in '0'..'9' -> '\u0660' + (ch - '0')
                    m == "latin" && ch in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    m == "latin" && ch in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}

object TimeFmt {

    fun is24(context: Context, prefs: Prefs): Boolean = when (prefs.timeFormat) {
        "12" -> false
        "24" -> true
        else -> DateFormat.is24HourFormat(context)
    }

    /** Formats a 24-hour "HH:mm" string (as Aladhan returns it) for display. */
    fun display(hhmm: String?, is24: Boolean, locale: Locale = Locale.getDefault()): String {
        if (hhmm == null) return "--:--"
        return Digits.fix(displayRaw(hhmm, is24, locale))
    }

    private fun displayRaw(hhmm: String, is24: Boolean, locale: Locale): String {
        if (is24) return hhmm
        return try {
            LocalTime.parse(hhmm.trim().take(5)).format(DateTimeFormatter.ofPattern("h:mm a", locale))
        } catch (e: Exception) {
            hhmm
        }
    }
}
