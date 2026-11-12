package com.mytv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra(AdhanScheduler.EXTRA_PRAYER) ?: "Fajr"
        val scheduled = intent.getLongExtra(AdhanScheduler.EXTRA_WHEN, 0L)
        val now = System.currentTimeMillis()

        // An alarm that fires far from its scheduled time (the TV clock jumped after boot or a network
        // time sync) must not play the Adhan. Re-arm for the real next prayer instead.
        val onTime = scheduled > 0L && Math.abs(now - scheduled) <= 3 * 60_000L
        val prefs = Prefs(context)
        val key = "$prayer|${scheduled / 60_000L}"
        if (!onTime || prefs.lastAdhan == key || !prefs.adhanEnabled) {
            AdhanScheduler.reschedule(context)
            return
        }
        prefs.lastAdhan = key

        val service = Intent(context, AdhanService::class.java)
            .setAction(AdhanService.ACTION_PLAY)
            .putExtra(AdhanScheduler.EXTRA_PRAYER, prayer)
        ContextCompat.startForegroundService(context, service)
    }
}
