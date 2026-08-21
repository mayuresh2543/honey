package com.honeyfile.security.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.honeyfile.security.R
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.alert.TelemetryManager
import com.honeyfile.security.camera.IntruderCaptureManager
import com.honeyfile.security.cloud.FirebaseCloudVaultManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.scanner.FolderScannerManager
import com.honeyfile.security.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class HoneyMonitoringService : LifecycleService() {

    private lateinit var folderScannerManager: FolderScannerManager
    private lateinit var intruderCaptureManager: IntruderCaptureManager
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Atomic debounce — only one background capture per 5-second window.
    private val lastBreachTimeMs = AtomicLong(0L)
    private val BREACH_DEBOUNCE_MS = 5000L

    override fun onCreate() {
        super.onCreate()
        folderScannerManager = FolderScannerManager(this)
        intruderCaptureManager = IntruderCaptureManager(this)
        createNotificationChannel()
        initializeServiceCamera()
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

        // Initialize background camera if not yet primed
        initializeServiceCamera()

        // Unified folder monitor: SAF polling (writes) + Linux inotify (reads/opens)
        folderScannerManager.startContinuousScanning(uri)
        serviceScope.launch {
            folderScannerManager.fileChangeEvents.collect { event ->
                Log.w(TAG, "Honeyfile event [${event.eventType}]: ${event.fileName}")
                handleBackgroundFileBreach(event.fileName, event.eventType)
            }
        }

        Log.d(TAG, "HoneyMonitoringService started monitoring: $folderUriStr")
        return START_STICKY
    }

    private fun initializeServiceCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Camera permission not granted for HoneyMonitoringService")
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor) { proxy -> proxy.close() } }

                this.imageCapture = capture

                val cameraSelector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this@HoneyMonitoringService, cameraSelector, capture, analysis)
                Log.d(TAG, "CameraX bound to HoneyMonitoringService in background (0 UI, completely silent)")
            } catch (e: Exception) {
                Log.e(TAG, "HoneyMonitoringService camera binding error", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Handles background file breaches:
     * Completely silent execution in background service — ZERO UI, NO ACTIVITY LAUNCH, NO APP POPUP.
     */
    private fun handleBackgroundFileBreach(
        fileName: String,
        actionType: Any
    ) {
        val actionStr = when (actionType) {
            is com.honeyfile.security.integrity.FileAlterationType -> actionType.name
            is String -> actionType
            else -> actionType.toString()
        }

        if (FolderScannerManager.isDeploymentInProgress ||
            com.honeyfile.security.integrity.HoneyFileObserver.isDeploymentInProgress ||
            (actionStr.uppercase() == "CREATED" && com.honeyfile.security.decoy.DecoyGeneratorEngine.isDecoyFileName(fileName))
        ) {
            Log.d(TAG, "Decoy deployment/file creation ignored for background breach: $fileName ($actionStr)")
            return
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

        Log.w(TAG, "Silent background breach detected: $fileName ($actionStr) — ZERO UI, NO APP LAUNCH")
        sendAlterationNotification(fileName, actionStr)

        serviceScope.launch {
            processSilentBackgroundBreach(fileName, actionStr)
        }
    }

    private suspend fun processSilentBackgroundBreach(fileName: String, actionType: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val actionTag = when (actionType.uppercase()) {
            "DELETED", "DELETE" -> "DELETED"
            "MODIFIED", "EDITED", "MODIFY" -> "EDITED"
            "CREATED", "CREATE", "COPIED_PASTED", "NEW" -> "CREATED"
            "RENAMED", "MOVED_FROM", "MOVED_TO" -> "RENAMED"
            "ACCESSED", "OPENED" -> "ACCESSED"
            else -> actionType
        }

        val actionVerb = when (actionTag) {
            "ACCESSED" -> "opened/accessed"
            "DELETED" -> "deleted"
            "EDITED" -> "edited"
            "CREATED" -> "created"
            "RENAMED" -> "renamed"
            else -> actionTag.lowercase()
        }

        // Silent camera capture in background — no activity, no window, zero popup
        if (imageCapture == null) {
            initializeServiceCamera()
            delay(500L)
        }

        var frame = intruderCaptureManager.takeSilentPhoto(imageCapture, cameraExecutor)
        if (frame == null && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeServiceCamera()
            delay(600L)
            frame = intruderCaptureManager.takeSilentPhoto(imageCapture, cameraExecutor)
        }

        val photoFile = intruderCaptureManager.captureIntruderImage(frame)

        // Check if admin face matches
        val faceAuthManager = com.honeyfile.security.auth.FaceAuthManager(this)
        val authResult = frame?.let { faceAuthManager.authenticateFace(it) }
        val isAuthenticated = authResult?.isAuthenticated ?: false
        val adminName = authResult?.adminName ?: "Admin"

        val telemetry = TelemetryManager(this).getDeviceTelemetry()

        if (isAuthenticated) {
            Log.d(TAG, "Background file access verified by $adminName ✅")
            AppDatabase.getDatabase(this).logDao().insertLog(
                AccessLog(
                    file = fileName,
                    user = adminName,
                    action = actionTag,
                    details = "Authorized background access: File '$fileName' $actionVerb by $adminName at $timestamp.",
                    timestamp = timestamp
                )
            )
        } else {
            Log.w(TAG, "Unauthorized background breach by Intruder: $fileName ($actionTag) 🚨")
            AppDatabase.getDatabase(this).logDao().insertLog(
                AccessLog(
                    file = fileName,
                    user = "Intruder",
                    action = actionTag,
                    details = "BACKGROUND INTRUSION: File '$fileName' $actionVerb while app was closed at $timestamp.\n${telemetry.formattedSummary}",
                    timestamp = timestamp
                )
            )

            EmailAlertManager().sendAlert(
                context = this,
                subject = "🚨 Background Intruder Alert: $fileName",
                body = "Unauthorized file activity detected at $timestamp.\n\nFile: $fileName\nAction: $actionTag ($actionVerb)\n\n${telemetry.formattedSummary}",
                imageFile = photoFile,
                telemetry = telemetry
            )

            FirebaseCloudVaultManager(this).syncBreachIncidentToCloud(
                fileName = fileName,
                actionType = actionTag,
                timestamp = timestamp,
                details = "BACKGROUND INTRUSION: File '$fileName' $actionVerb by Intruder at $timestamp.",
                imageFile = photoFile,
                telemetry = telemetry
            )
        }
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
        serviceScope.cancel()
        cameraExecutor.shutdown()
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
        cameraExecutor.shutdown()
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
