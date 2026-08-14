package com.honeyfile.security.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.honeyfile.security.R
import com.honeyfile.security.camera.OverlayCaptureActivity
import com.honeyfile.security.scanner.FolderScannerManager
import com.honeyfile.security.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class HoneyMonitoringService : LifecycleService() {

    private lateinit var folderScannerManager: FolderScannerManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Atomic debounce: only one OverlayCaptureActivity per 5-second window.
    // AtomicLong.compareAndSet makes the read-check-write atomic, preventing the race
    // condition where two coroutine threads both see the old timestamp and both launch
    // a capture activity when multiple files change simultaneously.
    private val lastBreachTimeMs = AtomicLong(0L)
    private val BREACH_DEBOUNCE_MS = 5000L

    override fun onCreate() {
        super.onCreate()
        folderScannerManager = FolderScannerManager(this)
        createNotificationChannel()
        Log.d(TAG, "HoneyMonitoringService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        val folderUriStr = intent?.getStringExtra(EXTRA_FOLDER_URI)
        if (folderUriStr != null) {
            val uri = Uri.parse(folderUriStr)
            startForeground(NOTIFICATION_ID, buildNotification(uri.lastPathSegment ?: "Monitored Folder"))

            // Start SAF-based polling scanner (works with content:// URIs picked via folder picker)
            folderScannerManager.startContinuousScanning(uri)

            // Subscribe to file change events emitted by the scanner
            // This is the CORRECT way: FolderScannerManager uses DocumentFile (SAF) to poll
            // the folder every 500ms and emits events via SharedFlow when changes are detected.
            serviceScope.launch {
                folderScannerManager.fileChangeEvents.collect { event ->
                    Log.w(TAG, "Breach detected in background: ${event.fileName} (${event.eventType})")
                    handleBackgroundFileBreach(event.fileName, event.eventType)
                }
            }

            Log.d(TAG, "Started background SAF folder monitoring for: $folderUriStr")
        }

        return START_STICKY
    }

    /**
     * Called when FolderScannerManager detects a file change while the app is in background.
     *
     * CAMERA STRATEGY:
     * Android's cameraserver revokes camera access from any process that has no visible UI.
     * We launch OverlayCaptureActivity — a fully transparent 1x1 pixel Activity — which
     * briefly becomes the "visible" foreground element that lets us access the camera for
     * ~2-3 seconds to capture the intruder, then finishes itself automatically.
     */
    private fun handleBackgroundFileBreach(fileName: String, actionType: String) {
        // GUARD 1: If the app is in the foreground, MainActivity already has the camera
        // bound and its own observeFolderScanner() will handle breach capture.
        // Launching OverlayCaptureActivity on top would call unbindAll() and kill
        // MainActivity's camera session, causing the fallback image to be saved instead.
        if (MainActivity.isInForeground) {
            Log.d(TAG, "App is in foreground — MainActivity handles breach for $fileName, skipping overlay")
            sendAlterationNotification(fileName, actionType)
            return
        }

        // GUARD 2: Atomic debounce — only one capture per BREACH_DEBOUNCE_MS window.
        // compareAndSet makes the "check last time, update if old enough" operation atomic,
        // preventing two coroutine threads from both getting through when files change rapidly.
        val now = System.currentTimeMillis()
        val last = lastBreachTimeMs.get()
        if (now - last < BREACH_DEBOUNCE_MS || !lastBreachTimeMs.compareAndSet(last, now)) {
            Log.d(TAG, "Breach debounced ($fileName) — within ${BREACH_DEBOUNCE_MS}ms window")
            return
        }

        Log.w(TAG, "App in background — launching OverlayCaptureActivity for: $fileName ($actionType)")
        val captureIntent = OverlayCaptureActivity.createLaunchIntent(this, fileName, actionType)
        startActivity(captureIntent)
        sendAlterationNotification(fileName, actionType)
    }

    private fun sendAlterationNotification(fileName: String, actionType: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 Honeyfile Alteration Detected!")
            .setContentText("File '$fileName' was $actionType in monitored folder.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun buildNotification(folderName: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, HoneyMonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Honeyfile Deception Engine Active")
            .setContentText("Monitoring '$folderName' for unauthorized access")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Monitoring", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Honeyfile Security Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous background file modification & decoy security monitoring"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        folderScannerManager.stopScanning()
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        folderScannerManager.stopScanning()
        serviceScope.cancel()
        Log.d(TAG, "HoneyMonitoringService destroyed")
    }

    companion object {
        private const val TAG = "HoneyMonitoringService"
        const val CHANNEL_ID = "honeyfile_monitoring_channel"
        const val NOTIFICATION_ID = 9901
        const val EXTRA_FOLDER_URI = "extra_folder_uri"
        const val ACTION_STOP = "com.honeyfile.security.ACTION_STOP"

        fun startService(context: Context, folderUri: Uri) {
            val intent = Intent(context, HoneyMonitoringService::class.java).apply {
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
