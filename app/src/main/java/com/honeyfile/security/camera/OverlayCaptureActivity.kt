package com.honeyfile.security.camera

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.alert.TelemetryManager
import com.honeyfile.security.cloud.FirebaseCloudVaultManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Transparent, no-UI activity launched by HoneyMonitoringService when a breach is detected
 * while the app is in the background.
 *
 * WHY THIS EXISTS:
 * Android's cameraserver revokes camera access from any process with no visible Activity.
 * This activity is transparent (invisible to the user) but technically visible to the OS,
 * which grants the process camera access for ~3-4 seconds to capture the intruder.
 *
 * CAMERA BINDING STRATEGY:
 * Camera binding happens in onResume() — NOT onCreate() — because CameraX requires the
 * lifecycle owner to be in at least STARTED state before the camera hardware actually
 * opens. Binding in onCreate() causes the camera HAL to still be initializing when we
 * try to capture, resulting in timeouts.
 */
class OverlayCaptureActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var intruderCaptureManager: IntruderCaptureManager
    private var captureTriggered = false  // Guard: only run capture once per lifecycle

    private var fileName = "unknown_file"
    private var actionType = "UNKNOWN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the window fully transparent and non-interactive.
        // We do NOT call setLayout(1,1) — that can confuse the window manager on some
        // devices and cause the activity to not be considered "properly visible".
        // The transparent theme handles invisibility at the rendering level.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        // No setContentView — this activity has zero UI

        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "unknown_file"
        actionType = intent.getStringExtra(EXTRA_ACTION_TYPE) ?: "UNKNOWN"
        intruderCaptureManager = IntruderCaptureManager(this)

        Log.d(TAG, "OverlayCaptureActivity created for breach: $fileName ($actionType)")
    }

    override fun onResume() {
        super.onResume()

        // Only trigger once per instance
        if (captureTriggered) return
        captureTriggered = true

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Camera permission not granted — saving fallback evidence")
            performFallbackCapture()
            return
        }

        // Now the Activity is in RESUMED state — the camera lifecycle will be active
        // and the OS considers this activity "visible" for camera access purposes.
        bindCameraAndCapture()
    }

    private fun bindCameraAndCapture() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // ImageAnalysis primes the repeating capture session.
                // Without an active repeating use case, ImageCapture.takePicture()
                // silently fails because no repeating capture request exists in the HAL.
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor) { proxy -> proxy.close() } }

                imageCapture = capture

                val cameraSelector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    else -> {
                        Log.e(TAG, "No camera found on device")
                        performFallbackCapture()
                        return@addListener
                    }
                }

                // Unbind any stale bindings, then bind to THIS activity's lifecycle.
                // Since we're called from onResume, the lifecycle is at RESUMED — CameraX
                // will immediately begin opening the camera hardware.
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, capture, analysis)
                Log.d(TAG, "CameraX bound to OverlayCaptureActivity in RESUMED state")

                lifecycleScope.launch { waitAndCapture() }

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed: ${e.message}", e)
                performFallbackCapture()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun waitAndCapture() {
        // Wait for camera hardware to fully initialize and for AE/AF to settle.
        // 3000ms is conservative but reliable across devices. The camera HAL needs:
        //   ~500-1000ms to open the sensor
        //   ~500-1000ms to start the repeating session
        //   ~500-1000ms for auto-exposure to converge in the first few frames
        delay(3000L)

        val capture = imageCapture
        if (capture == null) {
            Log.e(TAG, "ImageCapture is null after binding — saving fallback")
            persistAndAlert(photoFile = intruderCaptureManager.captureIntruderImage(null))
            return
        }

        Log.d(TAG, "Attempting camera capture for: $fileName")
        val bitmap = intruderCaptureManager.takeSilentPhoto(capture, cameraExecutor)

        if (bitmap != null) {
            Log.d(TAG, "Real intruder photo captured successfully (${bitmap.width}x${bitmap.height})")
        } else {
            Log.w(TAG, "Camera capture returned null — saving fallback evidence image")
        }

        val photoFile = intruderCaptureManager.captureIntruderImage(bitmap)
        persistAndAlert(photoFile)
    }

    private fun performFallbackCapture() {
        lifecycleScope.launch {
            val photoFile = intruderCaptureManager.captureIntruderImage(null)
            persistAndAlert(photoFile)
        }
    }

    private suspend fun persistAndAlert(photoFile: java.io.File?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val actionTag = when (actionType.uppercase()) {
            "DELETED", "DELETE", "DELETE_SELF" -> "DELETED"
            "MODIFIED", "EDITED", "MODIFY" -> "EDITED"
            "CREATED", "CREATE", "COPIED_PASTED", "NEW" -> "CREATED"
            "RENAMED", "MOVED_FROM", "MOVED_TO" -> "RENAMED"
            else -> actionType
        }

        try {
            withContext(Dispatchers.IO) {
                val telemetry = TelemetryManager(this@OverlayCaptureActivity).getDeviceTelemetry()

                AppDatabase.getDatabase(this@OverlayCaptureActivity).logDao().insertLog(
                    AccessLog(
                        file = fileName,
                        user = "Intruder",
                        action = actionTag,
                        details = "BACKGROUND BREACH: '$fileName' $actionTag while app was closed.\n${telemetry.formattedSummary}",
                        timestamp = timestamp
                    )
                )

                EmailAlertManager().sendAlert(
                    context = this@OverlayCaptureActivity,
                    subject = "🚨 Background Intruder: $fileName",
                    body = "Unauthorized file modification at $timestamp.\n\nFile: $fileName\nAction: $actionTag",
                    imageFile = photoFile,
                    telemetry = telemetry
                )

                FirebaseCloudVaultManager(this@OverlayCaptureActivity).syncBreachIncidentToCloud(
                    fileName = fileName,
                    actionType = actionTag,
                    timestamp = timestamp,
                    details = "BACKGROUND BREACH: '$fileName' $actionTag while app was closed.",
                    imageFile = photoFile,
                    telemetry = telemetry
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in persistAndAlert", e)
        } finally {
            withContext(Dispatchers.Main) {
                Log.d(TAG, "OverlayCaptureActivity finishing")
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "OverlayCaptureActivity"
        const val EXTRA_FILE_NAME = "extra_file_name"
        const val EXTRA_ACTION_TYPE = "extra_action_type"

        fun createLaunchIntent(context: Context, fileName: String, actionType: String): Intent {
            return Intent(context, OverlayCaptureActivity::class.java).apply {
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_ACTION_TYPE, actionType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }
    }
}
