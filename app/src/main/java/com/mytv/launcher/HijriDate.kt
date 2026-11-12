package com.mytv.launcher

import android.content.Context
import java.text.NumberFormat
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.util.Locale

/** Hijri (Umm al-Qura) date text for the home screen, with an optional +/- day correction. */
object HijriDate {

    private val AR = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )
    private val EN = listOf(
        "Muharram", "Safar", "Rabi\u2019 I", "Rabi\u2019 II", "Jumada I", "Jumada II",
        "Rajab", "Sha\u2019ban", "Ramadan", "Shawwal", "Dhu al-Qi\u2019dah", "Dhu al-Hijjah"
    )

    fun format(context: Context, today: LocalDate, locale: Locale): String {
        return try {
            val h = HijrahDate.from(today.plusDays(Prefs(context).hijriAdjust.toLong()))
            val day = h.get(ChronoField.DAY_OF_MONTH)
            val month = h.get(ChronoField.MONTH_OF_YEAR)
            val year = h.get(ChronoField.YEAR)
            val ar = locale.language == "ar"
            val nf = NumberFormat.getInstance(locale).apply { isGroupingUsed = false }
            val name = (if (ar) AR else EN)[(month - 1).coerceIn(0, 11)]
            "${nf.format(day)} $name ${nf.format(year)}" + if (ar) " هـ" else " AH"
        } catch (e: Exception) {
            ""
        }
    }
}
