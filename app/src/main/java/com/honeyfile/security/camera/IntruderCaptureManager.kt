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
     * Returns null if no camera frame was captured.
     */
    fun captureIntruderImage(bitmap: Bitmap?): File? {
        if (bitmap == null) return null
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(capturedFolder, "$timeStamp.jpg")

        return try {
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Log.d(TAG, "Intruder photo saved successfully: ${photoFile.absolutePath} (${bitmap.width}x${bitmap.height})")
            photoFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save intruder photo", e)
            null
        }
    }

    suspend fun takeSilentPhoto(imageCapture: ImageCapture?, executor: java.util.concurrent.Executor): Bitmap? = captureMutex.withLock {
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is not initialized")
            return@withLock null
        }

        // Retry up to 3 times to handle camera warmup and transient hardware failures
        for (attempt in 1..3) {
            val result = attemptSingleCapture(imageCapture, executor)
            if (result != null) {
                Log.d(TAG, "Silent photo captured successfully on attempt $attempt")
                return@withLock result
            }
            Log.w(TAG, "Capture attempt $attempt failed${if (attempt < 3) ", retrying after delay..." else ""}")
            if (attempt < 3) {
                kotlinx.coroutines.delay(600L * attempt)
            }
        }
        Log.e(TAG, "All 3 silent photo capture attempts failed")
        null
    }

    private suspend fun attemptSingleCapture(imageCapture: ImageCapture, executor: java.util.concurrent.Executor): Bitmap? {
        return kotlinx.coroutines.withTimeoutOrNull(6000L) {
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
                                        Log.w(TAG, "EXIF rotation failed, falling back to unrotated bitmap", exifError)
                                    }
                                    Log.d(TAG, "Silent photo decoded: ${finalBitmap.width}x${finalBitmap.height}")
                                    tempFile.delete()
                                    if (continuation.isActive) continuation.resume(finalBitmap)
                                } else {
                                    Log.e(TAG, "Failed to decode temp image (file size: ${tempFile.length()} bytes)")
                                    tempFile.delete()
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing saved image", e)
                                tempFile.delete()
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "takePicture error (code=${exception.imageCaptureError}): ${exception.message}", exception)
                            tempFile.delete()
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
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
