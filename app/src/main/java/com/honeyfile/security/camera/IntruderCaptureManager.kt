package com.honeyfile.security.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
            if (bitmap != null) {
                FileOutputStream(photoFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                Log.d(TAG, "Intruder photo saved successfully: ${photoFile.absolutePath}")
                photoFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save intruder photo", e)
            null
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
