package com.honeyfile.security.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.camera.IntruderCaptureManager
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

    private val cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

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

                // ImageAnalysis provides the repeating-request surface that primes the
                // capture pipeline so that ImageCapture.takePicture() actually works.
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
            val intruderCaptureManager = IntruderCaptureManager(this@HoneyMonitoringService)
            val cameraExecutor = Executors.newSingleThreadExecutor()

            // Silently capture real camera photo from background CameraX service binding
            val frame = intruderCaptureManager.takeSilentPhoto(imageCapture, cameraExecutor)
            val photoFile = intruderCaptureManager.captureIntruderImage(frame)
            cameraExecutor.shutdown()

            try {
                val actionTag = when (actionType.uppercase()) {
                    "DELETED", "DELETE", "DELETE_SELF" -> "DELETED"
                    "MODIFIED", "EDITED", "MODIFY" -> "EDITED"
                    "CREATED", "CREATE", "COPIED_PASTED", "NEW" -> "CREATED"
                    "RENAMED", "MOVED_FROM", "MOVED_TO" -> "RENAMED"
                    else -> actionType
                }

                val db = AppDatabase.getDatabase(this@HoneyMonitoringService)
                db.logDao().insertLog(
                    AccessLog(
                        file = fileName,
                        user = "Intruder",
                        action = actionTag,
                        details = "UNAUTHORIZED BACKGROUND BREACH: File '$fileName' $actionTag in monitored folder while app was closed.",
                        timestamp = timestamp
                    )
                )

                emailAlertManager.sendAlert(
                    subject = "🚨 Background Intruder File Alteration: $fileName",
                    body = "Unauthorized background file modification detected at $timestamp.\n\nFile: $fileName\nAction: $actionType",
                    imageFile = photoFile
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error logging background breach", e)
            }
        }

        sendAlterationNotification(fileName, actionType)
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

    override fun onDestroy() {
        super.onDestroy()
        fileObserver?.stopWatching()
        fileObserver = null
        folderScannerManager.stopScanning()
        cameraExecutor.shutdown()
        serviceScope.cancel()
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
