package com.mytv.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Schedules one alarm for the next enabled prayer; the receiver re-schedules after it fires. */
object AdhanScheduler {

    const val ACTION_ADHAN = "com.mytv.launcher.ACTION_ADHAN"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_WHEN = "when_ms"

    fun reschedule(context: Context) {
        val appContext = context.applicationContext
        Thread { rescheduleBlocking(appContext) }.start()
    }

    fun rescheduleBlocking(context: Context) {
        val ctx = context.applicationContext
        val prefs = Prefs(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        am.cancel(alarmIntent(ctx, "Fajr", 0L))
        if (!prefs.adhanEnabled) return

        val next = PrayerRepository.nextPrayer(ctx, onlyEnabled = true) ?: return
        val showIntent = PendingIntent.getActivity(
            ctx, 1002, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock is exact and does not need the "exact alarms" permission.
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(next.time.toInstant().toEpochMilli(), showIntent),
            alarmIntent(ctx, next.name, next.time.toInstant().toEpochMilli())
        )
    }

    private fun alarmIntent(ctx: Context, prayer: String, whenMs: Long): PendingIntent {
        val intent = Intent(ctx, AdhanReceiver::class.java)
            .setAction(ACTION_ADHAN)
            .putExtra(EXTRA_PRAYER, prayer)
            .putExtra(EXTRA_WHEN, whenMs)
        return PendingIntent.getBroadcast(
            ctx, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
