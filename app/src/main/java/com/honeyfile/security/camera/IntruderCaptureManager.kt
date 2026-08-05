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
        suspendCancellableCoroutine { continuation ->
            if (imageCapture == null) {
                Log.e(TAG, "ImageCapture is not initialized")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

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
                                val exif = androidx.exifinterface.media.ExifInterface(tempFile.absolutePath)
                                val rotation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
                                val rotationInDegrees = exifToDegrees(rotation)
                                val rotatedBitmap = rotateBitmap(bitmap, rotationInDegrees)
                                Log.d(TAG, "Silent photo saved and loaded successfully: ${rotatedBitmap.width}x${rotatedBitmap.height}")
                                tempFile.delete() // Cleanup
                                if (continuation.isActive) continuation.resume(rotatedBitmap)
                            } else {
                                Log.e(TAG, "Failed to decode saved temp image")
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
                        Log.e(TAG, "Silent photo capture error: ${exception.message}", exception)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }
            )
        }
    }

    private fun exifToDegrees(exifOrientation: Int): Int {
        return when (exifOrientation) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
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
