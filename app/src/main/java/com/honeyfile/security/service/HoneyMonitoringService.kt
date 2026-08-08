package com.honeyfile.security.service

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
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.honeyfile.security.R
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.alert.TelemetryManager
import com.honeyfile.security.auth.FaceAuthManager
import com.honeyfile.security.camera.IntruderCaptureManager
import com.honeyfile.security.cloud.FirebaseCloudVaultManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import com.honeyfile.security.integrity.HoneyFileObserver
import com.honeyfile.security.scanner.FolderScannerManager
import com.honeyfile.security.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class HoneyMonitoringService : LifecycleService() {

    private lateinit var folderScannerManager: FolderScannerManager
    private var fileObserver: HoneyFileObserver? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val emailAlertManager = EmailAlertManager()
    private var imageCapture: ImageCapture? = null

    override fun onCreate() {
        super.onCreate()
        folderScannerManager = FolderScannerManager(this)
        createNotificationChannel()
        initializeBackgroundCamera()
        Log.d(TAG, "HoneyMonitoringService created")
    }

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private fun initializeBackgroundCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
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

                val analysis = androidx.camera.core.ImageAnalysis.Builder()
                    .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor) { proxy -> proxy.close() } }

                this.imageCapture = capture

                val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, capture, analysis)
                Log.d(TAG, "Background CameraX bound to HoneyMonitoringService with ImageCapture + ImageAnalysis")
            } catch (e: Exception) {
                Log.e(TAG, "Background camera binding failed in HoneyMonitoringService", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
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
                handleBackgroundFileBreach(event.fileName, event.eventType.name)
            }.also {
                it.startWatching()
            }
            Log.d(TAG, "HoneyFileObserver watching path: $path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HoneyFileObserver on path $path", e)
        }
    }

    private fun handleBackgroundFileBreach(fileName: String, actionType: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        serviceScope.launch {
            if (imageCapture == null) {
                initializeBackgroundCamera()
                kotlinx.coroutines.delay(800)
            }

            val intruderCaptureManager = IntruderCaptureManager(this@HoneyMonitoringService)
            val cameraExec = Executors.newSingleThreadExecutor()

            val frame = intruderCaptureManager.takeSilentPhoto(imageCapture, cameraExec)
            val photoFile = intruderCaptureManager.captureIntruderImage(frame)
            cameraExec.shutdown()

            try {
                val actionTag = when (actionType.uppercase()) {
                    "DELETED", "DELETE", "DELETE_SELF" -> "DELETED"
                    "MODIFIED", "EDITED", "MODIFY" -> "EDITED"
                    "CREATED", "CREATE", "COPIED_PASTED", "NEW" -> "CREATED"
                    "RENAMED", "MOVED_FROM", "MOVED_TO" -> "RENAMED"
                    else -> actionType
                }

                val telemetryManager = TelemetryManager(this@HoneyMonitoringService)
                val telemetry = telemetryManager.getDeviceTelemetry()

                val db = AppDatabase.getDatabase(this@HoneyMonitoringService)
                db.logDao().insertLog(
                    AccessLog(
                        file = fileName,
                        user = "Intruder",
                        action = actionTag,
                        details = "UNAUTHORIZED BACKGROUND BREACH: File '$fileName' $actionTag in monitored folder while app was closed.\n${telemetry.formattedSummary}",
                        timestamp = timestamp
                    )
                )

                emailAlertManager.sendAlert(
                    context = this@HoneyMonitoringService,
                    subject = "🚨 Background Intruder File Alteration: $fileName",
                    body = "Unauthorized background file modification detected at $timestamp.\n\nFile: $fileName\nAction: $actionType",
                    imageFile = photoFile,
                    telemetry = telemetry
                )

                // Real-time sub-second off-device backup to Firebase Cloud Vault
                FirebaseCloudVaultManager(this@HoneyMonitoringService).syncBreachIncidentToCloud(
                    fileName = fileName,
                    actionType = actionTag,
                    timestamp = timestamp,
                    details = "UNAUTHORIZED BACKGROUND BREACH: File '$fileName' $actionTag in monitored folder while app was closed.",
                    imageFile = photoFile,
                    telemetry = telemetry
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error logging background breach", e)
            }
        }

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
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, HoneyMonitoringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Honeyfile Deception Engine Active")
            .setContentText("Monitoring folder '$folderName' for unauthorized access attempts")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Monitoring", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Honeyfile Security Monitoring"
            val descriptionText = "Continuous background file modification & decoy security monitoring"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        folderScannerManager.stopScanning()
        fileObserver?.stopWatching()
        fileObserver = null
        cameraExecutor.shutdown()
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
        cameraExecutor.shutdown()
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
