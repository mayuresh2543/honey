package com.honeyfile.security.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
     * Saves a real front-camera photo to the Vault if a frame was captured.
     * Returns null if no camera frame was captured (no synthetic graphics created).
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

    /**
     * Asynchronously captures a real silent photo using CameraX.
     */
    suspend fun takeSilentPhoto(imageCapture: ImageCapture?, executor: java.util.concurrent.Executor): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (imageCapture == null) {
            Log.e(TAG, "ImageCapture is not initialized")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rotation = imageProxy.imageInfo.rotationDegrees
                        val rawBitmap = imageProxy.toBitmap()
                        val rotatedBitmap = rotateBitmap(rawBitmap, rotation)
                        imageProxy.close()
                        Log.d(TAG, "Silent photo captured successfully: ${rotatedBitmap.width}x${rotatedBitmap.height}, rotation=$rotation")
                        if (continuation.isActive) {
                            continuation.resume(rotatedBitmap)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed converting imageProxy to bitmap", e)
                        imageProxy.close()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Silent photo capture error: ${exception.message}", exception)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        )
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
