/**
 * SPDX-FileCopyrightText: 2026 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.findmydevice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import com.google.android.gms.R
import org.microg.gms.profile.Build
import org.microg.gms.wearable.WearableService

private const val CHANNEL_ID = "find_my_device_ring"
private const val NOTIFICATION_ID = 76182
private const val RING_TIMEOUT_MS = 5 * 60 * 1000L
private const val PATH_RING_STARTED = "com.google.android.gms.mdm.RING_STARTED"
private const val PATH_RING_CANCELED = "com.google.android.gms.mdm.RING_CANCELED"

class AlarmRingService : Service() {

    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var requestorNodeId: String? = null
    private var originalAlarmVolume = -1
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopRing() }

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                requestorNodeId = intent.getStringExtra(EXTRA_REQUESTOR_NODE_ID)
                startRing()
                requestorNodeId?.let { WearableService.sendGmsMessage(it, PATH_RING_STARTED) }
            }
            ACTION_STOP -> stopRing()
        }
        return START_NOT_STICKY
    }

    private fun startRing() {
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RING_TIMEOUT_MS)
        if (ringtone?.isPlaying == true) return

        raiseAlarmVolume()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        ringtone = RingtoneManager.getRingtone(this, uri).apply {
            if (Build.VERSION.SDK_INT >= 21) {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= 28) {
                isLooping = true
            }
            play()
        }
    }

    private fun stopRing() {
        ringtone?.let {
            if (it.isPlaying) it.stop()
        }
        ringtone = null
        handler.removeCallbacks(timeout)
        restoreAlarmVolume()
        requestorNodeId?.let { WearableService.sendGmsMessage(it, PATH_RING_CANCELED) }
        requestorNodeId = null
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun raiseAlarmVolume() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
            val volume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            originalAlarmVolume = volume
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not raise alarm volume", e)
        }
    }

    private fun restoreAlarmVolume() {
        if (originalAlarmVolume == -1) return
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not restore alarm volume", e)
        }
        originalAlarmVolume = -1
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.ring_notification_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val stopIntent = PendingIntentCompat.getService(
            this, 0, Intent(this, AlarmRingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT, false
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.ring_notification_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(stopIntent)
            .addAction(0, getString(R.string.ring_notification_stop), stopIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AlarmRingService::WakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        stopRing()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AlarmRingService"
        private const val ACTION_START = "alarm_ring_start"
        private const val ACTION_STOP = "alarm_ring_stop"
        private const val EXTRA_REQUESTOR_NODE_ID = "requestor_node_id"

        fun stopRing(context: Context) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not stop ringing", e)
            }
        }

        fun startRing(context: Context, requestorNodeId: String? = null) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REQUESTOR_NODE_ID, requestorNodeId)
            }
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}