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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.honeyfile.security.R
import com.honeyfile.security.alert.EmailAlertManager
import com.honeyfile.security.alert.TelemetryManager
import com.honeyfile.security.cloud.FirebaseCloudVaultManager
import com.honeyfile.security.data.AccessLog
import com.honeyfile.security.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Transparent activity launched by HoneyMonitoringService on breach detection.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. NOT windowIsFloating: We removed this from the theme because floating windows
 *    are treated as dialogs by Android. Dialog-mode activities are NOT considered
 *    the foreground activity for camera access purposes on some devices/OS versions.
 *    Full-screen translucent = properly foreground = guaranteed camera access.
 *
 * 2. Has a real PreviewView (1dp, alpha=0): Without a Preview use case surface,
 *    many camera HALs enter a degraded initialization mode where the imaging pipeline
 *    isn't fully warmed up. takePicture() then silently times out because the
 *    repeating capture session hasn't converged. The PreviewView is invisible to the
 *    user (alpha=0, 1dp) but gives the HAL a real surface to stream frames to.
 *
 * 3. Camera binding in onResume(): Guarantees lifecycle is at RESUMED state before
 *    the camera is asked to open. bindToLifecycle() in onCreate() can result in the
 *    camera warming up before onResume is called, meaning our 4s delay starts from
 *    the wrong reference point.
 *
 * 4. File-based capture as primary: OutputFileOptions.Builder(File) is more reliable
 *    than in-memory OnImageCapturedCallback in background/overlay contexts because
 *    it uses the file I/O path instead of the shared memory path, which is less prone
 *    to buffer allocation failures under memory pressure.
 */
class OverlayCaptureActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var intruderCaptureManager: IntruderCaptureManager
    private lateinit var previewView: PreviewView
    private var captureTriggered = false

    private var fileName = "unknown_file"
    private var actionType = "UNKNOWN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlay_capture)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        previewView = findViewById(R.id.previewView)
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
            Log.e(TAG, "Camera permission not granted — fallback")
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

                // Preview use case — gives the camera HAL a real surface.
                // This is critical: without Preview, the camera pipeline on many devices
                // never fully initializes and takePicture() times out.
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                cameraProvider.unbindAll()
                // Bind Preview + ImageCapture together. Both will start immediately
                // because the lifecycle is at RESUMED when this listener fires from onResume().
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, capture)
                Log.d(TAG, "CameraX bound (Preview + ImageCapture) in RESUMED state")

                lifecycleScope.launch { waitAndCapture() }

            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed: ${e.message}", e)
                saveFallbackAndFinish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private suspend fun waitAndCapture() {
        // Wait for the camera pipeline to fully warm up:
        //   ~500ms  sensor open
        //   ~500ms  session start + first repeating frame
        //   ~1000ms AE/AF/AWB convergence (first few frames)
        //   ~1000ms buffer to account for slow devices
        // Total: 3000ms conservative baseline.
        delay(3000L)

        val capture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture null — fallback")
            saveFallbackAndFinish()
            return
        }

        // File-based capture: write directly to a temp file, then read back as Bitmap.
        // This path is more reliable in overlay/background contexts than the shared-memory
        // in-memory path used by OnImageCapturedCallback.
        val tempFile = File(cacheDir, "overlay_capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        Log.d(TAG, "Calling takePicture() for: $fileName")

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "takePicture() onImageSaved — file: ${tempFile.absolutePath}")
                    lifecycleScope.launch {
                        try {
                            var bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                            // Correct rotation using EXIF data
                            try {
                                val exif = ExifInterface(tempFile.absolutePath)
                                val orientation = exif.getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL
                                )
                                val degrees = when (orientation) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                    else -> 0
                                }
                                if (degrees != 0 && bitmap != null) {
                                    val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
                                    bitmap = android.graphics.Bitmap.createBitmap(
                                        bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                                    )
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "EXIF rotation failed", e)
                            }
                            tempFile.delete()

                            val photoFile = intruderCaptureManager.captureIntruderImage(bitmap)
                            Log.d(TAG, "Real intruder photo saved: ${photoFile?.name}")
                            persistAndAlert(photoFile)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing captured image", e)
                            tempFile.delete()
                            saveFallbackAndFinish()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "takePicture() onError code=${exception.imageCaptureError}: ${exception.message}", exception)
                    tempFile.delete()
                    lifecycleScope.launch { saveFallbackAndFinish() }
                }
            }
        )
    }

    private suspend fun saveFallbackAndFinish() {
        val photoFile = withContext(Dispatchers.IO) {
            intruderCaptureManager.captureIntruderImage(null)
        }
        persistAndAlert(photoFile)
    }

    private suspend fun persistAndAlert(photoFile: File?) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val actionTag = when (actionType.uppercase()) {
            "DELETED", "DELETE" -> "DELETED"
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
