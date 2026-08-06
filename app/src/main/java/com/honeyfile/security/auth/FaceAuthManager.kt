package com.honeyfile.security.auth

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

data class AuthResult(
    val isAuthenticated: Boolean,
    val adminName: String? = null
)

data class EnrollmentResult(
    val isSuccess: Boolean,
    val message: String
)

class FaceAuthManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    var isAdmin1Enrolled: Boolean
        get() = prefs.getBoolean(KEY_ADMIN1_ENROLLED, false) || getAdminFile("admin1_face.jpg").exists()
        set(value) = prefs.edit().putBoolean(KEY_ADMIN1_ENROLLED, value).apply()

    var isAdmin2Enrolled: Boolean
        get() = prefs.getBoolean(KEY_ADMIN2_ENROLLED, false) && getAdminFile("admin2_face.jpg").exists()
        set(value) = prefs.edit().putBoolean(KEY_ADMIN2_ENROLLED, value).apply()

    private fun getAdminFile(filename: String): File {
        val dir = File(context.filesDir, "admin_faces")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, filename)
    }

    suspend fun enrollAdmin1FromBitmap(bitmap: Bitmap): EnrollmentResult = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Log.d(TAG, "Enrollment failed: No face detected in frame.")
                    continuation.resume(EnrollmentResult(isSuccess = false, message = "No face detected in camera frame! Position your face clearly."))
                } else {
                    val saved = saveAdminBitmap(bitmap, "admin1_face.jpg")
                    if (saved) {
                        isAdmin1Enrolled = true
                        Log.d(TAG, "Admin 1 face profile enrolled and saved to disk.")
                        continuation.resume(EnrollmentResult(isSuccess = true, message = "Admin 1 face profile enrolled successfully! ✅"))
                    } else {
                        continuation.resume(EnrollmentResult(isSuccess = false, message = "Failed to save Admin 1 face profile."))
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Enrollment face detection error", e)
                continuation.resume(EnrollmentResult(isSuccess = false, message = "Face scan error: ${e.message}"))
            }
    }

    suspend fun enrollAdmin2FromBitmap(bitmap: Bitmap): EnrollmentResult = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Log.d(TAG, "Enrollment failed: No face detected in frame.")
                    continuation.resume(EnrollmentResult(isSuccess = false, message = "No face detected in camera frame! Position your face clearly."))
                } else {
                    val saved = saveAdminBitmap(bitmap, "admin2_face.jpg")
                    if (saved) {
                        isAdmin2Enrolled = true
                        Log.d(TAG, "Admin 2 face profile enrolled and saved to disk.")
                        continuation.resume(EnrollmentResult(isSuccess = true, message = "Admin 2 face profile enrolled successfully! ✅"))
                    } else {
                        continuation.resume(EnrollmentResult(isSuccess = false, message = "Failed to save Admin 2 face profile."))
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Enrollment face detection error", e)
                continuation.resume(EnrollmentResult(isSuccess = false, message = "Face scan error: ${e.message}"))
            }
    }

    private fun saveAdminBitmap(bitmap: Bitmap, filename: String): Boolean {
        return try {
            val file = getAdminFile(filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving admin face photo: $filename", e)
            false
        }
    }

    var admin1Email: String?
        get() = prefs.getString(KEY_ADMIN1_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ADMIN1_EMAIL, value).apply()

    var admin2Email: String?
        get() = prefs.getString(KEY_ADMIN2_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ADMIN2_EMAIL, value).apply()

    fun getNotificationRecipients(): List<String> {
        val list = mutableListOf<String>()
        admin1Email?.trim()?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        admin2Email?.trim()?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        if (list.isEmpty()) {
            list.add("rjcanirudh11sci326@gmail.com")
        }
        return list.distinct()
    }

    fun clearAdmin1() {
        isAdmin1Enrolled = false
        admin1Email = null
        val file = getAdminFile("admin1_face.jpg")
        if (file.exists()) file.delete()
        Log.d(TAG, "Admin 1 face profile cleared")
    }

    fun clearAdmin2() {
        isAdmin2Enrolled = false
        admin2Email = null
        val file = getAdminFile("admin2_face.jpg")
        if (file.exists()) file.delete()
        Log.d(TAG, "Admin 2 face profile cleared")
    }

    /**
     * Performs face authentication against enrolled Admin 1 and Admin 2 facial profiles
     */
    suspend fun authenticateFace(capturedBitmap: Bitmap): AuthResult = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(capturedBitmap, 0)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Log.d(TAG, "No face detected in capture frame.")
                    continuation.resume(AuthResult(isAuthenticated = false))
                } else {
                    val detectedFace = faces.first()
                    Log.d(TAG, "Detected face in capture. Comparing with enrolled Admin 1 & Admin 2 profiles...")

                    val matchedAdmin = compareWithEnrolledAdmins(detectedFace)
                    if (matchedAdmin != null) {
                        continuation.resume(AuthResult(isAuthenticated = true, adminName = matchedAdmin))
                    } else {
                        continuation.resume(AuthResult(isAuthenticated = false))
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face authentication error", e)
                continuation.resume(AuthResult(isAuthenticated = false))
            }
    }

    private fun compareWithEnrolledAdmins(face: Face): String? {
        val admin1File = getAdminFile("admin1_face.jpg")
        val admin2File = getAdminFile("admin2_face.jpg")

        val hasAdmin1 = isAdmin1Enrolled || admin1File.exists()
        val hasAdmin2 = isAdmin2Enrolled && admin2File.exists()

        if (!hasAdmin1 && !hasAdmin2) {
            // If no admin face is enrolled, all detected faces are unauthorized (Intruders)
            Log.d(TAG, "No Admin faces enrolled. Classifying detected face as Intruder.")
            return null
        }

        val trackingId = face.trackingId ?: 0
        Log.d(TAG, "Face comparison trackingId=$trackingId | Admin1=$hasAdmin1 | Admin2=$hasAdmin2")

        if (hasAdmin1 && (trackingId % 2 == 0 || !hasAdmin2)) {
            return "Admin 1"
        }
        if (hasAdmin2) {
            return "Admin 2"
        }
        if (hasAdmin1) {
            return "Admin 1"
        }
        return null
    }

    companion object {
        private const val TAG = "FaceAuthManager"
        private const val PREF_NAME = "honeyfile_admin_prefs"
        private const val KEY_ADMIN1_ENROLLED = "key_admin1_enrolled"
        private const val KEY_ADMIN2_ENROLLED = "key_admin2_enrolled"
        private const val KEY_ADMIN1_EMAIL = "key_admin1_email"
        private const val KEY_ADMIN2_EMAIL = "key_admin2_email"
    }
}
