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
     * Asynchronously captures a photo in the background without UI camera preview.
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
                        val rawBitmap = try {
                            imageProxy.toBitmap()
                        } catch (e: Exception) {
                            Log.w(TAG, "imageProxy.toBitmap failed, attempting YUV decode fallback", e)
                            imageProxyToBitmap(imageProxy)
                        }

                        imageProxy.close()

                        if (rawBitmap != null) {
                            val rotatedBitmap = rotateBitmap(rawBitmap, rotation)
                            Log.d(TAG, "Silent photo captured successfully: ${rotatedBitmap.width}x${rotatedBitmap.height}, rotation=$rotation")
                            if (continuation.isActive) {
                                continuation.resume(rotatedBitmap)
                            }
                        } else {
                            Log.e(TAG, "Failed to decode bitmap from imageProxy")
                            if (continuation.isActive) continuation.resume(null)
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

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val planeProxy = image.planes[0] ?: return null
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
