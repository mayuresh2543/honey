package com.honeyfile.security.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class IntruderCaptureManager(private val context: Context) {

    private val capturedFolder: File by lazy {
        File(context.filesDir, "captured").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Directly captures a real front-camera photo into the Vault using CameraX hardware OutputFileOptions.
     */
    suspend fun captureIntruderPhotoDirect(imageCapture: ImageCapture?, executor: java.util.concurrent.Executor): File? = suspendCancellableCoroutine { continuation ->
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(capturedFolder, "$timeStamp.jpg")

        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is not initialized, saving fallback graphic")
            val fallbackFile = saveFallbackGraphic(photoFile, timeStamp)
            continuation.resume(fallbackFile)
            return@suspendCancellableCoroutine
        }

        val metadata = ImageCapture.OutputFileOptions.Metadata().apply {
            isReversedHorizontal = true
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "REAL front-camera photo captured & saved directly to Vault: ${photoFile.absolutePath} (size=${photoFile.length()} bytes)")
                    if (continuation.isActive) {
                        continuation.resume(photoFile)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "CameraX takePicture failed: ${exception.message}", exception)
                    val fallbackFile = saveFallbackGraphic(photoFile, timeStamp)
                    if (continuation.isActive) {
                        continuation.resume(fallbackFile)
                    }
                }
            }
        )
    }

    /**
     * Silently captures a photo using the front camera when an unauthorized access is detected.
     */
    fun captureIntruderImage(bitmap: Bitmap? = null): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(capturedFolder, "$timeStamp.jpg")

        return try {
            val finalBitmap = if (bitmap != null) {
                Log.d(TAG, "Saving REAL camera frame bitmap: ${bitmap.width}x${bitmap.height}")
                bitmap
            } else {
                Log.w(TAG, "Camera frame is null, generating fallback intruder alert image")
                createFallbackIntruderBitmap(timeStamp)
            }

            FileOutputStream(photoFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d(TAG, "Intruder photo saved successfully: ${photoFile.absolutePath}")
            photoFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save intruder photo", e)
            null
        }
    }

    /**
     * Asynchronously captures a photo in the background using direct hardware JPEG output options.
     */
    suspend fun takeSilentPhoto(imageCapture: ImageCapture?, executor: java.util.concurrent.Executor): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is not initialized")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val tempFile = File(capturedFolder, "temp_$timeStamp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                        tempFile.delete()
                        if (bitmap != null) {
                            Log.d(TAG, "Silent photo captured directly via hardware File API: ${bitmap.width}x${bitmap.height}")
                            if (continuation.isActive) continuation.resume(bitmap)
                        } else {
                            Log.e(TAG, "Decoded bitmap from camera temp file is null")
                            if (continuation.isActive) continuation.resume(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed decoding temp camera photo file", e)
                        tempFile.delete()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Silent photo capture error: ${exception.message}", exception)
                    tempFile.delete()
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        )
    }

    private fun saveFallbackGraphic(photoFile: File, timeStamp: String): File {
        return try {
            val fallbackBitmap = createFallbackIntruderBitmap(timeStamp)
            FileOutputStream(photoFile).use { out ->
                fallbackBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            photoFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save fallback graphic", e)
            photoFile
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        return try {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun createFallbackIntruderBitmap(timeStamp: String): Bitmap {
        val width = 640
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Dark red security alert background
        canvas.drawColor(Color.parseColor("#1E0F14"))

        val paint = Paint().apply {
            color = Color.parseColor("#EF4444")
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        canvas.drawText("🚨 INTRUDER BREACH DETECTED", width / 2f, height / 2f - 40f, paint)

        val subPaint = Paint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 18f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("Unauthorized file access attempt", width / 2f, height / 2f + 10f, subPaint)
        canvas.drawText("Timestamp: $timeStamp", width / 2f, height / 2f + 50f, subPaint)

        return bitmap
    }

    fun getCapturedImages(): List<File> {
        if (!capturedFolder.exists()) return emptyList()
        return capturedFolder.listFiles()
            ?.filter { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    companion object {
        private const val TAG = "IntruderCaptureManager"
    }
}
