package com.honeyfile.security.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class IntruderCaptureManager(private val context: Context) {

    private val captureMutex = Mutex()

    private val capturedFolder: File by lazy {
        File(context.filesDir, "captured").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Saves a real front-camera photo to the Vault if a frame was captured.
     * If bitmap is null (camera frame dropped/failed), generates a structured Intruder Evidence bitmap
     * so an image file is ALWAYS saved and available in the Vault directory.
     */
    fun captureIntruderImage(bitmap: Bitmap?): File? {
        val isRealCapture = bitmap != null
        val finalBitmap = bitmap ?: createFallbackEvidenceBitmap()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(capturedFolder, "$timeStamp.jpg")

        return try {
            FileOutputStream(photoFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d(TAG, "Intruder photo (${if (isRealCapture) "REAL CAMERA" else "EVIDENCE LOG"}) saved successfully: ${photoFile.absolutePath} (${finalBitmap.width}x${finalBitmap.height})")
            photoFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save intruder photo", e)
            null
        }
    }

    private fun createFallbackEvidenceBitmap(): Bitmap {
        val width = 640
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Dark red alert background
        val bgPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(35, 10, 10)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Red alert border
        val borderPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 12f
        }
        canvas.drawRect(12f, 12f, width - 12f, height - 12f, borderPaint)

        val headerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
            textSize = 34f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val subTextPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.YELLOW
            textSize = 20f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        canvas.drawText("🚨 INTRUDER BREACH LOGGED 🚨", width / 2f, 140f, headerPaint)
        canvas.drawText("Unauthorized Honeyfile Access Detected", width / 2f, 210f, textPaint)
        canvas.drawText("Timestamp: $timeStamp", width / 2f, 270f, subTextPaint)
        canvas.drawText("Security Audit Evidence Recorded", width / 2f, 320f, subTextPaint)

        return bitmap
    }

    suspend fun takeSilentPhoto(imageCapture: ImageCapture?, executor: java.util.concurrent.Executor): Bitmap? = captureMutex.withLock {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is not initialized")
            return@withLock null
        }

        // 1. Primary Strategy: In-memory ImageProxy capture (fastest, direct byte buffer parsing)
        for (attempt in 1..2) {
            val bitmap = attemptMemoryCapture(imageCapture, executor)
            if (bitmap != null) {
                Log.d(TAG, "In-memory camera photo captured successfully on attempt $attempt")
                return@withLock bitmap
            }
            Log.w(TAG, "Memory capture attempt $attempt failed, retrying...")
            kotlinx.coroutines.delay(200L * attempt)
        }

        // 2. Fallback Strategy: File-based capture output options
        Log.w(TAG, "Memory capture failed, trying file-based capture fallback...")
        val fileBitmap = attemptFileCapture(imageCapture, executor)
        if (fileBitmap != null) {
            Log.d(TAG, "File-based camera photo captured successfully")
            return@withLock fileBitmap
        }

        Log.e(TAG, "All photo capture strategies failed")
        null
    }

    private suspend fun attemptMemoryCapture(imageCapture: ImageCapture, executor: java.util.concurrent.Executor): Bitmap? {
        return kotlinx.coroutines.withTimeoutOrNull(4000L) {
            suspendCancellableCoroutine { continuation ->
                imageCapture.takePicture(
                    executor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val rotationDegrees = image.imageInfo.rotationDegrees
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    val finalBitmap = rotateBitmap(bitmap, rotationDegrees)
                                    Log.d(TAG, "ImageProxy decode success: ${finalBitmap.width}x${finalBitmap.height}, rot=$rotationDegrees")
                                    if (continuation.isActive) continuation.resume(finalBitmap)
                                } else {
                                    Log.e(TAG, "BitmapFactory.decodeByteArray returned null")
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decoding ImageProxy", e)
                                if (continuation.isActive) continuation.resume(null)
                            } finally {
                                image.close()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "OnImageCapturedCallback onError (code=${exception.imageCaptureError}): ${exception.message}", exception)
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            }
        }
    }

    private suspend fun attemptFileCapture(imageCapture: ImageCapture, executor: java.util.concurrent.Executor): Bitmap? {
        return kotlinx.coroutines.withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine { continuation ->
                val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

                imageCapture.takePicture(
                    outputOptions,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            try {
                                val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                                if (bitmap != null) {
                                    var finalBitmap = bitmap
                                    try {
                                        val exif = android.media.ExifInterface(tempFile.absolutePath)
                                        val rotation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
                                        val rotationInDegrees = exifToDegrees(rotation)
                                        finalBitmap = rotateBitmap(bitmap, rotationInDegrees)
                                    } catch (exifError: Exception) {
                                        Log.w(TAG, "EXIF rotation failed, using default", exifError)
                                    }
                                    tempFile.delete()
                                    if (continuation.isActive) continuation.resume(finalBitmap)
                                } else {
                                    tempFile.delete()
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            } catch (e: Exception) {
                                tempFile.delete()
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            tempFile.delete()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            }
        }
    }

    private fun exifToDegrees(exifOrientation: Int): Int {
        return when (exifOrientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
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
