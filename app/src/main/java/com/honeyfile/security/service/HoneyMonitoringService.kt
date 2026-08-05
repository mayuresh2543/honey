package com.honeyfile.security.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.honeyfile.security.integrity.HoneyFileObserver
import com.honeyfile.security.scanner.FolderScannerManager
import com.honeyfile.security.ui.MainActivity

class HoneyMonitoringService : Service() {

    private lateinit var folderScannerManager: FolderScannerManager
    private var fileObserver: HoneyFileObserver? = null

    override fun onCreate() {
        super.onCreate()
        folderScannerManager = FolderScannerManager(this)
        createNotificationChannel()
        Log.d(TAG, "HoneyMonitoringService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        val folderUriStr = intent?.getStringExtra(EXTRA_FOLDER_URI)
        if (folderUriStr != null) {
            val uri = Uri.parse(folderUriStr)
            startForeground(NOTIFICATION_ID, buildNotification(uri.lastPathSegment ?: "Monitored Folder"))
            folderScannerManager.startContinuousScanning(uri)

            val folderPath = uri.path
            if (folderPath != null) {
                startFileObserver(folderPath)
            }

            Log.d(TAG, "Started continuous background folder monitoring for: $folderUriStr")
        }

        return START_STICKY
    }

    private fun startFileObserver(path: String) {
        try {
            fileObserver?.stopWatching()
            fileObserver = HoneyFileObserver(path) { event ->
                Log.w(TAG, "Background File Alteration Event: ${event.fileName} (${event.eventType})")
                sendAlterationNotification(event.fileName, event.eventType.name)
            }.also {
                it.startWatching()
            }
            Log.d(TAG, "HoneyFileObserver watching path: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HoneyFileObserver on path $path", e)
        }
    }

    private fun sendAlterationNotification(fileName: String, actionType: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 File Alteration Alert: $actionType")
            .setContentText("File '$fileName' was $actionType in monitored folder!")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun stopForegroundService() {
        fileObserver?.stopWatching()
        fileObserver = null
        folderScannerManager.stopScanning()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Honeyfile Endpoint Protection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Continuous background folder scanning and file alteration alerts"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(folderName: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Honeyfile Endpoint Protection Active")
            .setContentText("Monitoring folder '$folderName' in background")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
        fileObserver = null
        folderScannerManager.stopScanning()
        Log.d(TAG, "HoneyMonitoringService destroyed")
    }

    companion object {
        private const val TAG = "HoneyMonitoringService"
        private const val CHANNEL_ID = "honey_monitoring_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.honeyfile.security.START_MONITORING"
        const val ACTION_STOP = "com.honeyfile.security.STOP_MONITORING"
        const val EXTRA_FOLDER_URI = "extra_folder_uri"

        fun startService(context: Context, folderUri: Uri) {
            val intent = Intent(context, HoneyMonitoringService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FOLDER_URI, folderUri.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, HoneyMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
