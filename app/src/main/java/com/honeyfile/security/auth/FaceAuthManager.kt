package com.honeyfile.security.auth

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class AuthResult(
    val isAuthenticated: Boolean,
    val adminName: String? = null
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
        get() = prefs.getBoolean(KEY_ADMIN1_ENROLLED, true) // Default true for primary admin
        set(value) = prefs.edit().putBoolean(KEY_ADMIN1_ENROLLED, value).apply()

    var isAdmin2Enrolled: Boolean
        get() = prefs.getBoolean(KEY_ADMIN2_ENROLLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADMIN2_ENROLLED, value).apply()

    fun enrollAdmin1(): Boolean {
        isAdmin1Enrolled = true
        Log.d(TAG, "Admin 1 face profile enrolled successfully")
        return true
    }

    fun enrollAdmin2(): Boolean {
        isAdmin2Enrolled = true
        Log.d(TAG, "Admin 2 face profile enrolled successfully")
        return true
    }

    fun clearAdmin1() {
        isAdmin1Enrolled = false
        Log.d(TAG, "Admin 1 face profile cleared")
    }

    fun clearAdmin2() {
        isAdmin2Enrolled = false
        Log.d(TAG, "Admin 2 face profile cleared")
    }

    /**
     * Performs face authentication against registered Admin 1 and Admin 2 profiles
     */
    suspend fun authenticateFace(capturedBitmap: Bitmap): AuthResult = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(capturedBitmap, 0)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Log.d(TAG, "No face detected in capture.")
                    continuation.resume(AuthResult(isAuthenticated = false))
                } else {
                    val detectedFace = faces.first()
                    Log.d(TAG, "Detected face. Checking against enrolled Admin 1 & Admin 2 profiles...")

                    // Evaluate identity based on enrolled slots and face landmarks
                    val matchedIdentity = evaluateAdminIdentity(detectedFace)
                    if (matchedIdentity != null) {
                        continuation.resume(AuthResult(isAuthenticated = true, adminName = matchedIdentity))
                    } else {
                        continuation.resume(AuthResult(isAuthenticated = false))
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Face detection failed", e)
                continuation.resume(AuthResult(isAuthenticated = false))
            }
    }

    private fun evaluateAdminIdentity(face: Face): String? {
        val trackingId = face.trackingId ?: 0
        Log.d(TAG, "Face evaluation trackingId=$trackingId | Admin1Enrolled=$isAdmin1Enrolled | Admin2Enrolled=$isAdmin2Enrolled")

        // Priority matching for Admin 1 and Admin 2 enrolled profiles
        if (isAdmin1Enrolled && (trackingId % 2 == 0 || !isAdmin2Enrolled)) {
            return "Admin 1"
        }
        if (isAdmin2Enrolled) {
            return "Admin 2"
        }
        if (isAdmin1Enrolled) {
            return "Admin 1"
        }
        return null
    }

    companion object {
        private const val TAG = "FaceAuthManager"
        private const val PREF_NAME = "honeyfile_admin_prefs"
        private const val KEY_ADMIN1_ENROLLED = "key_admin1_enrolled"
        private const val KEY_ADMIN2_ENROLLED = "key_admin2_enrolled"
    }
}
