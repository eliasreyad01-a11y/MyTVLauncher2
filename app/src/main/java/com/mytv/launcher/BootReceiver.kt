package com.mytv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the Adhan alarm after reboot, app update, or a time / time zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The receiver is exported so the system can reach it; ignore anything that isn't one of its broadcasts.
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                AdhanScheduler.rescheduleBlocking(appContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
