package com.honeyfile.security.camera

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Fully invisible activity launched by HoneyMonitoringService on breach detection.
 *
 * DESIGN DECISIONS:
 *
 * 1. No PreviewView / no layout: A PreviewView renders its camera texture directly
 *    onto the display surface, bypassing the window's translucency — it would be
 *    visible to the user even with a transparent theme. We use ImageAnalysis instead
 *    to prime the camera HAL without any visible surface.
 *
 * 2. suspendCancellableCoroutine for takePicture: takePicture() with callbacks is NOT
 *    a suspend function — it returns immediately. Without this wrapper, waitAndCapture()
 *    would finish before the photo is taken, the lifecycleScope coroutine would end,
 *    and the onImageSaved callback would fire into a dead/cancelled scope → no photo saved.
 *    suspendCancellableCoroutine keeps the coroutine alive until the callback fires.
 *
 * 3. Camera binding in onResume(): ensures lifecycle is at RESUMED when bindToLifecycle
 *    is called, so CameraX opens the camera hardware immediately.
 *
 * 4. 4000ms warmup delay: gives the camera HAL time to open the sensor, start the
 *    repeating session (driven by ImageAnalysis), and converge AE/AF/AWB.
 */
class OverlayCaptureActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var intruderCaptureManager: IntruderCaptureManager
    private var captureTriggered = false

    private var fileName = "unknown_file"
    private var actionType = "UNKNOWN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView — zero visible UI

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        intruderCaptureManager = IntruderCaptureManager(this)
        fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "unknown_file"
        actionType = intent.getStringExtra(EXTRA_ACTION_TYPE) ?: "UNKNOWN"
        Log.d(TAG, "OverlayCaptureActivity created for: $fileName ($actionType)")
    }

    override fun onResume() {
        super.onResume()
        if (captureTriggered) return
        captureTriggered = true

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Camera permission denied — saving fallback")
            saveFallbackAndFinish()
            return
        }

        bindCameraAndCapture()
    }

    private fun bindCameraAndCapture() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val cameraProvider = future.get()

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCapture = capture

                // ImageAnalysis starts the repeating capture session without any visible surface.
                // This primes the camera HAL so that takePicture() has a live pipeline to work with.
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(cameraExecutor) { proxy -> proxy.close() } }

                val cameraSelector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, capture, analysis)
                Log.d(TAG, "CameraX bound (ImageAnalysis + ImageCapture) in RESUMED state")

                // Now wait for the pipeline to warm up, then capture.
                // The coroutine stays alive until persistAndAlert() calls finish().
                lifecycleScope.launch { waitAndCapture() }

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed: ${e.message}", e)
                saveFallbackAndFinish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun waitAndCapture() {
        // Allow the camera HAL to fully initialize before we try to capture.
        delay(4000L)

        val capture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture null after binding — fallback")
            saveFallbackAndFinish()
            return
        }

        val tempFile = File(cacheDir, "overlay_${System.currentTimeMillis()}.jpg")
        Log.d(TAG, "Calling takePicture for: $fileName")

        // suspendCancellableCoroutine converts the callback API into a suspend call.
        // The coroutine WAITS HERE until onImageSaved or onError fires.
        // Without this, the coroutine would finish before the photo is taken.
        val success = suspendCancellableCoroutine { cont ->
            val options = ImageCapture.OutputFileOptions.Builder(tempFile).build()
            capture.takePicture(
                options,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                        Log.d(TAG, "takePicture succeeded: ${tempFile.name}")
                        cont.resume(true)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "takePicture failed [${exception.imageCaptureError}]: ${exception.message}")
                        tempFile.delete()
                        cont.resume(false)
                    }
                }
            )
            cont.invokeOnCancellation { tempFile.delete() }
        }

        if (success && tempFile.exists()) {
            // Decode captured JPEG, apply EXIF rotation correction
            var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            try {
                val exif = ExifInterface(tempFile.absolutePath)
                val degrees = when (
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
                if (degrees != 0 && bitmap != null) {
                    val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
                    bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "EXIF correction failed", e)
            }
            tempFile.delete()

            val photoFile = withContext(Dispatchers.IO) {
                intruderCaptureManager.captureIntruderImage(bitmap)
            }
            Log.d(TAG, "Real intruder photo saved: ${photoFile?.name}")
            persistAndAlert(photoFile)
        } else {
            Log.w(TAG, "takePicture returned false — saving fallback evidence")
            val photoFile = withContext(Dispatchers.IO) {
                intruderCaptureManager.captureIntruderImage(null)
            }
            persistAndAlert(photoFile)
        }
    }

    // Non-suspend: can be called from callbacks and catch blocks.
    private fun saveFallbackAndFinish() {
        lifecycleScope.launch {
            val photoFile = withContext(Dispatchers.IO) {
                intruderCaptureManager.captureIntruderImage(null)
            }
            persistAndAlert(photoFile)
        }
    }

    private suspend fun persistAndAlert(photoFile: File?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val actionTag = when (actionType.uppercase()) {
            "DELETED", "DELETE" -> "DELETED"
            "MODIFIED", "EDITED", "MODIFY" -> "EDITED"
            "CREATED", "CREATE", "COPIED_PASTED", "NEW" -> "CREATED"
            "RENAMED", "MOVED_FROM", "MOVED_TO" -> "RENAMED"
            "ACCESSED" -> "ACCESSED"
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
                        details = "BACKGROUND BREACH: '$fileName' $actionTag while app closed.\n${telemetry.formattedSummary}",
                        timestamp = timestamp
                    )
                )

                EmailAlertManager().sendAlert(
                    context = this@OverlayCaptureActivity,
                    subject = "🚨 Background Intruder: $fileName",
                    body = "Unauthorized file access at $timestamp.\n\nFile: $fileName\nAction: $actionTag",
                    imageFile = photoFile,
                    telemetry = telemetry
                )

                FirebaseCloudVaultManager(this@OverlayCaptureActivity).syncBreachIncidentToCloud(
                    fileName = fileName,
                    actionType = actionTag,
                    timestamp = timestamp,
                    details = "BACKGROUND BREACH: '$fileName' $actionTag while app closed.",
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
