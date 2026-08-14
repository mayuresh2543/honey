package com.honeyfile.security.camera

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
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
 * Transparent, no-UI activity launched by HoneyMonitoringService when a breach is detected.
 *
 * WHY THIS EXISTS:
 * Android revokes camera access from any process that has no visible Activity or active PiP window.
 * This is a kernel-level privacy policy (enforced since Android 9, stricter in Android 11+/14+).
 * The ONLY way to access the camera when the app is "closed" is to briefly make an Activity visible.
 * This Activity is fully transparent — the user sees no UI change — and it finishes in ~2-3 seconds.
 *
 * FLOW:
 * 1. Breach detected by HoneyMonitoringService → starts this activity with breach metadata.
 * 2. Activity opens transparently, binds CameraX to its OWN lifecycle.
 * 3. Waits ~1.5s for camera to warm up, captures front-camera photo silently.
 * 4. Logs breach, uploads to Firebase, sends email alert.
 * 5. Finishes itself. Total lifecycle: ~2-3 seconds.
 */
class OverlayCaptureActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var intruderCaptureManager: IntruderCaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Make the window fully transparent — no title bar, no background, no visible UI
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        window.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
            // Make it a 1x1 pixel window so it's technically visible but invisible to the user
            setLayout(1, 1)
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        // Empty content view — no layout inflation needed
        setContentView(android.R.layout.simple_list_item_1) // smallest possible layout

        intruderCaptureManager = IntruderCaptureManager(this)

        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "unknown_file"
        val actionType = intent.getStringExtra(EXTRA_ACTION_TYPE) ?: "UNKNOWN"

        Log.d(TAG, "OverlayCaptureActivity started for breach: $fileName ($actionType)")

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Camera permission not granted — aborting overlay capture")
            finish()
            return
        }

        initCameraAndCapture(fileName, actionType)
    }

    private fun initCameraAndCapture(fileName: String, actionType: String) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // ImageAnalysis is REQUIRED to prime the repeating capture session.
                // Without it, takePicture() silently fails because no repeating
                // capture request has been submitted to the camera HAL.
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
                        Log.e(TAG, "No camera available — finishing overlay activity")
                        finish()
                        return@addListener
                    }
                }

                // Bind to THIS activity's lifecycle — this is the key: the Activity being
                // visible is what grants camera access to the process.
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, capture, analysis)
                Log.d(TAG, "CameraX bound successfully in OverlayCaptureActivity")

                // Now do the actual capture in a coroutine
                lifecycleScope.launch {
                    performCapture(fileName, actionType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed in OverlayCaptureActivity", e)
                performFallbackCapture(fileName, actionType)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun performCapture(fileName: String, actionType: String) {
        // Give camera hardware time to warm up (open, AE/AF settle)
        delay(1500L)

        val capture = imageCapture
        val bitmap = if (capture != null) {
            intruderCaptureManager.takeSilentPhoto(capture, cameraExecutor)
        } else null

        val photoFile = intruderCaptureManager.captureIntruderImage(bitmap)
        Log.d(TAG, "Overlay capture complete. Real bitmap: ${bitmap != null}, file: ${photoFile?.name}")

        persistAndAlert(fileName, actionType, photoFile)
    }

    private fun performFallbackCapture(fileName: String, actionType: String) {
        lifecycleScope.launch {
            val photoFile = intruderCaptureManager.captureIntruderImage(null)
            persistAndAlert(fileName, actionType, photoFile)
        }
    }

    private suspend fun persistAndAlert(
        fileName: String,
        actionType: String,
        photoFile: java.io.File?
    ) {
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
                val telemetryManager = TelemetryManager(this@OverlayCaptureActivity)
                val telemetry = telemetryManager.getDeviceTelemetry()

                // 1. Log the breach to local DB
                AppDatabase.getDatabase(this@OverlayCaptureActivity).logDao().insertLog(
                    AccessLog(
                        file = fileName,
                        user = "Intruder",
                        action = actionTag,
                        details = "BACKGROUND BREACH: File '$fileName' was $actionTag while app was closed.\n${telemetry.formattedSummary}",
                        timestamp = timestamp
                    )
                )

                // 2. Send email alert
                EmailAlertManager().sendAlert(
                    context = this@OverlayCaptureActivity,
                    subject = "🚨 Background Intruder File Alteration: $fileName",
                    body = "Unauthorized background file modification detected at $timestamp.\n\nFile: $fileName\nAction: $actionTag",
                    imageFile = photoFile,
                    telemetry = telemetry
                )

                // 3. Sync to Firebase Cloud Vault
                FirebaseCloudVaultManager(this@OverlayCaptureActivity).syncBreachIncidentToCloud(
                    fileName = fileName,
                    actionType = actionTag,
                    timestamp = timestamp,
                    details = "BACKGROUND BREACH: File '$fileName' $actionTag while app was closed.",
                    imageFile = photoFile,
                    telemetry = telemetry
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during persist & alert in OverlayCaptureActivity", e)
        } finally {
            // Always finish — do not stay open
            Log.d(TAG, "OverlayCaptureActivity finishing")
            withContext(Dispatchers.Main) {
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

        /**
         * Creates the intent to launch this transparent capture activity from a Service or
         * BroadcastReceiver context. Uses FLAG_ACTIVITY_NEW_TASK which is required when
         * starting an Activity from a non-Activity context.
         */
        fun createLaunchIntent(context: Context, fileName: String, actionType: String): Intent {
            return Intent(context, OverlayCaptureActivity::class.java).apply {
                putExtra(EXTRA_FILE_NAME, fileName)
                putExtra(EXTRA_ACTION_TYPE, actionType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }
        }
    }
}
