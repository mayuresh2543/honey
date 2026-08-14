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
import com.honeyfile.security.R
import com.honeyfile.security.camera.OverlayCaptureActivity
import com.honeyfile.security.integrity.HoneyFileObserver
import com.honeyfile.security.integrity.UriPathResolver
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

    // inotify-based file observer — watches for CLOSE_NOWRITE (file read/accessed)
    // and write events (CREATE/MODIFY/DELETE/MOVE) in real-time via Linux kernel events.
    // Runs alongside FolderScannerManager which handles SAF polling for write events.
    private var fileObserver: HoneyFileObserver? = null

    // Atomic debounce — only one OverlayCaptureActivity per 5-second window.
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

        val folderUriStr = intent?.getStringExtra(EXTRA_FOLDER_URI) ?: return START_STICKY

        // Guard: At least 1 administrator must be enrolled before monitoring can run
        val faceAuthManager = com.honeyfile.security.auth.FaceAuthManager(this)
        if (!faceAuthManager.hasAtLeastOneAdmin()) {
            Log.w(TAG, "Cannot start HoneyMonitoringService: No enrolled administrator found.")
            stopForegroundService()
            return START_NOT_STICKY
        }

        val uri = Uri.parse(folderUriStr)
        startForeground(NOTIFICATION_ID, buildNotification(uri.lastPathSegment ?: "Monitored Folder"))

        // 1. SAF polling — detects write events (create/modify/delete) via DocumentFile.
        //    Works with any content:// URI. Polls every 500ms.
        folderScannerManager.startContinuousScanning(uri)
        serviceScope.launch {
            folderScannerManager.fileChangeEvents.collect { event ->
                Log.w(TAG, "SAF breach [${event.eventType}]: ${event.fileName}")
                handleBackgroundFileBreach(event.fileName, event.eventType)
            }
        }

        // 2. inotify observer — detects file READ events (CLOSE_NOWRITE) in real-time.
        //    Requires a real filesystem path (not SAF URI). Try to resolve the path.
        //    If resolution fails (custom ROM etc.), falls back gracefully — write detection
        //    via SAF polling still works, only read detection is unavailable.
        val realPath = UriPathResolver.toRealPath(this, uri)
        if (realPath != null) {
            startFileObserver(realPath)
            Log.d(TAG, "inotify read detection active for: $realPath")
        } else {
            Log.w(TAG, "Could not resolve real path from URI — read detection unavailable for: $uri")
        }

        Log.d(TAG, "HoneyMonitoringService started monitoring: $folderUriStr")
        return START_STICKY
    }

    private fun startFileObserver(path: String) {
        fileObserver?.stopWatching()
        fileObserver = HoneyFileObserver(path) { event ->
            Log.w(TAG, "inotify event [${event.eventType}]: ${event.fileName}")
            // Only handle ACCESSED here — write events are handled by SAF scanner above.
            // Handling both would cause double captures for write events.
            if (event.eventType == com.honeyfile.security.integrity.FileAlterationType.ACCESSED) {
                handleBackgroundFileBreach(event.fileName, event.eventType)
            }
        }.also { it.startWatching() }
    }

    /**
     * Handles a breach event detected by either the SAF scanner (writes) or the
     * inotify observer (reads/accesses).
     *
     * CAMERA STRATEGY: When app is closed, launches OverlayCaptureActivity — a fully
     * transparent Activity — to briefly gain camera access (Android denies camera to
     * background processes with no visible window).
     */
    private fun handleBackgroundFileBreach(
        fileName: String,
        actionType: Any  // String from SAF scanner or FileAlterationType from observer
    ) {
        val actionStr = when (actionType) {
            is com.honeyfile.security.integrity.FileAlterationType -> actionType.name
            is String -> actionType
            else -> actionType.toString()
        }

        if (MainActivity.isInForeground) {
            Log.d(TAG, "App foreground — MainActivity handles $fileName ($actionStr)")
            sendAlterationNotification(fileName, actionStr)
            return
        }

        val now = System.currentTimeMillis()
        val last = lastBreachTimeMs.get()
        if (now - last < BREACH_DEBOUNCE_MS || !lastBreachTimeMs.compareAndSet(last, now)) {
            Log.d(TAG, "Breach debounced ($fileName) within ${BREACH_DEBOUNCE_MS}ms window")
            return
        }

        Log.w(TAG, "Launching OverlayCaptureActivity: $fileName ($actionStr)")
        startActivity(OverlayCaptureActivity.createLaunchIntent(this, fileName, actionStr))
        sendAlterationNotification(fileName, actionStr)
    }

    private fun sendAlterationNotification(fileName: String, actionType: String) {
        val title = when (actionType.uppercase()) {
            "ACCESSED" -> "👁️ Honeyfile Accessed!"
            else -> "🚨 Honeyfile Tampered!"
        }
        val body = when (actionType.uppercase()) {
            "ACCESSED" -> "File '$fileName' was opened and read in monitored folder."
            else -> "File '$fileName' was $actionType in monitored folder."
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
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
            .setContentText("Monitoring '$folderName' for access & tampering")
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
                description = "Monitors for unauthorized file access and tampering"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        folderScannerManager.stopScanning()
        fileObserver?.stopWatching()
        fileObserver = null
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
        fileObserver?.stopWatching()
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
