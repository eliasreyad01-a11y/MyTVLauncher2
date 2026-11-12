package com.mytv.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/** Plays the Adhan as a foreground service so it keeps running with the screen off. */
class AdhanService : Service() {

    companion object {
        const val ACTION_PLAY = "com.mytv.launcher.PLAY_ADHAN"
        const val ACTION_TEST = "com.mytv.launcher.TEST_ADHAN"
        const val ACTION_STOP = "com.mytv.launcher.STOP_ADHAN"
        private const val CHANNEL_ID = "adhan_alert"
        private const val NOTIFICATION_ID = 77

        @Volatile
        var instance: AdhanService? = null

        /** Called when the Adhan starts or stops, so the home screen can show or hide its Stop button. */
        @Volatile
        var onChanged: (() -> Unit)? = null

        val isPlaying: Boolean
            get() = instance != null

        fun stopNow() {
            instance?.finishPlayback()
        }
    }

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    /** Takes transient audio focus: YouTube and other players pause while the Adhan plays and resume when it ends. */
    private fun takeAudioFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
    }

    private fun dropAudioFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Tapping the notification opens the home screen with the Stop button (nothing is opened automatically). */
    private fun stopScreenIntent(prayer: String): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(AdhanScheduler.EXTRA_PRAYER, prayer)
            .putExtra("adhan_stop", true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(this, 5, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        onChanged?.invoke()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finishPlayback()
            return START_NOT_STICKY
        }

        val prayer = intent?.getStringExtra(AdhanScheduler.EXTRA_PRAYER) ?: "Fajr"
        val screen = stopScreenIntent(prayer)
        val notification = buildNotification(prayer, screen)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        play()

        // Chain the next alarm right away so a playback failure never breaks the schedule.
        if (intent?.action == ACTION_PLAY) AdhanScheduler.reschedule(this)
        return START_NOT_STICKY
    }

    private fun play() {
        releasePlayer()
        takeAudioFocus()
        val prefs = Prefs(this)
        val rawId = resources.getIdentifier("adhan", "raw", packageName)

        val sources = ArrayList<(MediaPlayer) -> Unit>()
        prefs.adhanUri?.let { custom ->
            sources.add { it.setDataSource(this, Uri.parse(custom)) }
        }
        if (rawId != 0) {
            sources.add {
                val afd = resources.openRawResourceFd(rawId)
                it.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
        }
        sources.add { it.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) }

        for (source in sources) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                source(mp)
                val vol = prefs.adhanVolume / 100f
                mp.setVolume(vol, vol)
                mp.setOnCompletionListener { finishPlayback() }
                mp.setOnErrorListener { _, _, _ ->
                    finishPlayback()
                    true
                }
                mp.setOnPreparedListener { it.start() }
                mp.prepareAsync()
                player = mp
                return
            } catch (e: Exception) {
                mp.release()
            }
        }
        finishPlayback()
    }

    private fun buildNotification(prayer: String, screen: PendingIntent): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val label = getString(
            when (prayer) {
                "Fajr" -> R.string.prayer_fajr
                "Dhuhr" -> R.string.prayer_dhuhr
                "Asr" -> R.string.prayer_asr
                "Maghrib" -> R.string.prayer_maghrib
                else -> R.string.prayer_isha
            }
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.adhan_notif_title_fmt, label))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(screen)
            .addAction(0, getString(R.string.stop), stopIntent)
            .build()
    }

    private fun releasePlayer() {
        try {
            player?.release()
        } catch (e: Exception) {
            // ignore
        }
        player = null
    }

    fun finishPlayback() {
        releasePlayer()
        dropAudioFocus()
        onChanged?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releasePlayer()
        if (instance === this) instance = null
        onChanged?.invoke()
        super.onDestroy()
    }
}
