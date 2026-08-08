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
import com.google.mlkit.vision.face.FaceLandmark
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

    var admin1Name: String
        get() = prefs.getString(KEY_ADMIN1_NAME, null)?.trim()?.takeIf { it.isNotBlank() } ?: "Admin 1"
        set(value) = prefs.edit().putString(KEY_ADMIN1_NAME, value.trim()).apply()

    var admin2Name: String
        get() = prefs.getString(KEY_ADMIN2_NAME, null)?.trim()?.takeIf { it.isNotBlank() } ?: "Admin 2"
        set(value) = prefs.edit().putString(KEY_ADMIN2_NAME, value.trim()).apply()

    var admin1Email: String?
        get() = prefs.getString(KEY_ADMIN1_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ADMIN1_EMAIL, value).apply()

    var admin2Email: String?
        get() = prefs.getString(KEY_ADMIN2_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_ADMIN2_EMAIL, value).apply()

    fun isEmailTaken(email: String, targetAdmin: Int): Boolean {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isEmpty()) return false
        return if (targetAdmin == 1) {
            isAdmin2Enrolled && admin2Email?.trim()?.lowercase() == cleanEmail
        } else {
            isAdmin1Enrolled && admin1Email?.trim()?.lowercase() == cleanEmail
        }
    }

    fun isNameTaken(name: String, targetAdmin: Int): Boolean {
        val cleanName = name.trim().lowercase()
        if (cleanName.isEmpty()) return false
        return if (targetAdmin == 1) {
            isAdmin2Enrolled && admin2Name.lowercase() == cleanName
        } else {
            isAdmin1Enrolled && admin1Name.lowercase() == cleanName
        }
    }

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
                    val detectedFace = faces.first()
                    if (isAdmin2Enrolled && compareWithEnrolledAdmins(detectedFace) == admin2Name) {
                        Log.d(TAG, "Enrollment rejected: Scanned face matches Admin 2 ($admin2Name)")
                        continuation.resume(EnrollmentResult(isSuccess = false, message = "❌ Facial scan matches $admin2Name! Admin 1 must be a distinct administrator."))
                        return@addOnSuccessListener
                    }

                    val saved = saveAdminBitmap(bitmap, "admin1_face.jpg")
                    if (saved) {
                        isAdmin1Enrolled = true
                        extractFaceRatios(detectedFace)?.let { saveAdmin1Ratios(it[0], it[1]) }
                        Log.d(TAG, "Admin 1 face profile enrolled and saved to disk.")
                        continuation.resume(EnrollmentResult(isSuccess = true, message = "Face scan captured! Complete profile details below. ✅"))
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
                    val detectedFace = faces.first()
                    if (isAdmin1Enrolled && compareWithEnrolledAdmins(detectedFace) == admin1Name) {
                        Log.d(TAG, "Enrollment rejected: Scanned face matches Admin 1 ($admin1Name)")
                        continuation.resume(EnrollmentResult(isSuccess = false, message = "❌ Facial scan matches $admin1Name! Admin 2 must be a distinct administrator."))
                        return@addOnSuccessListener
                    }

                    val saved = saveAdminBitmap(bitmap, "admin2_face.jpg")
                    if (saved) {
                        isAdmin2Enrolled = true
                        extractFaceRatios(detectedFace)?.let { saveAdmin2Ratios(it[0], it[1]) }
                        Log.d(TAG, "Admin 2 face profile enrolled and saved to disk.")
                        continuation.resume(EnrollmentResult(isSuccess = true, message = "Face scan captured! Complete profile details below. ✅"))
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

    private fun saveAdmin1Ratios(r1: Float, r2: Float) {
        prefs.edit().putFloat(KEY_ADMIN1_R1, r1).putFloat(KEY_ADMIN1_R2, r2).apply()
    }

    private fun saveAdmin2Ratios(r1: Float, r2: Float) {
        prefs.edit().putFloat(KEY_ADMIN2_R1, r1).putFloat(KEY_ADMIN2_R2, r2).apply()
    }

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
        prefs.edit().remove(KEY_ADMIN1_NAME).remove(KEY_ADMIN1_R1).remove(KEY_ADMIN1_R2).apply()
        val file = getAdminFile("admin1_face.jpg")
        if (file.exists()) file.delete()
        Log.d(TAG, "Admin 1 face profile cleared")
    }

    fun clearAdmin2() {
        isAdmin2Enrolled = false
        admin2Email = null
        prefs.edit().remove(KEY_ADMIN2_NAME).remove(KEY_ADMIN2_R1).remove(KEY_ADMIN2_R2).apply()
        val file = getAdminFile("admin2_face.jpg")
        if (file.exists()) file.delete()
        Log.d(TAG, "Admin 2 face profile cleared")
    }

    suspend fun authenticateFace(capturedBitmap: Bitmap): AuthResult = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(capturedBitmap, 0)
        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    Log.d(TAG, "No face detected in capture frame. Classifying as Intruder.")
                    continuation.resume(AuthResult(isAuthenticated = false))
                } else {
                    val detectedFace = faces.first()
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

    private fun extractFaceRatios(face: Face): FloatArray? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position ?: return null
        val bounds = face.boundingBox

        val faceWidth = bounds.width().toFloat().coerceAtLeast(1.0f)
        val faceHeight = bounds.height().toFloat().coerceAtLeast(1.0f)

        val eyeDist = Math.hypot((leftEye.x - rightEye.x).toDouble(), (leftEye.y - rightEye.y).toDouble()).toFloat()
        val eyeNoseDist = Math.hypot(((leftEye.x + rightEye.x) / 2.0 - nose.x), ((leftEye.y + rightEye.y) / 2.0 - nose.y)).toFloat()

        val r1 = eyeDist / faceWidth
        val r2 = eyeNoseDist / faceHeight
        return floatArrayOf(r1, r2)
    }

    private fun compareWithEnrolledAdmins(face: Face): String? {
        val admin1File = getAdminFile("admin1_face.jpg")
        val admin2File = getAdminFile("admin2_face.jpg")

        val hasAdmin1 = isAdmin1Enrolled && admin1File.exists()
        val hasAdmin2 = isAdmin2Enrolled && admin2File.exists()

        if (!hasAdmin1 && !hasAdmin2) {
            Log.d(TAG, "No Admin faces enrolled. Classifying detected face as Intruder.")
            return null
        }

        val capturedRatios = extractFaceRatios(face) ?: return null

        if (hasAdmin1) {
            val r1_1 = prefs.getFloat(KEY_ADMIN1_R1, -1f)
            val r1_2 = prefs.getFloat(KEY_ADMIN1_R2, -1f)
            if (r1_1 > 0 && r1_2 > 0) {
                val diff1 = Math.abs(capturedRatios[0] - r1_1) + Math.abs(capturedRatios[1] - r1_2)
                if (diff1 < 0.12f) {
                    Log.d(TAG, "Captured face matched Admin 1 ($admin1Name) with diff=$diff1")
                    return admin1Name
                }
            }
        }

        if (hasAdmin2) {
            val r2_1 = prefs.getFloat(KEY_ADMIN2_R1, -1f)
            val r2_2 = prefs.getFloat(KEY_ADMIN2_R2, -1f)
            if (r2_1 > 0 && r2_2 > 0) {
                val diff2 = Math.abs(capturedRatios[0] - r2_1) + Math.abs(capturedRatios[1] - r2_2)
                if (diff2 < 0.12f) {
                    Log.d(TAG, "Captured face matched Admin 2 ($admin2Name) with diff=$diff2")
                    return admin2Name
                }
            }
        }

        Log.d(TAG, "Captured face did NOT match enrolled Admin 1 or Admin 2 ratios. Classifying as INTRUDER 🚨")
        return null
    }

    companion object {
        private const val TAG = "FaceAuthManager"
        private const val PREF_NAME = "honeyfile_admin_prefs"
        private const val KEY_ADMIN1_ENROLLED = "key_admin1_enrolled"
        private const val KEY_ADMIN2_ENROLLED = "key_admin2_enrolled"
        private const val KEY_ADMIN1_NAME = "key_admin1_name"
        private const val KEY_ADMIN2_NAME = "key_admin2_name"
        private const val KEY_ADMIN1_EMAIL = "key_admin1_email"
        private const val KEY_ADMIN2_EMAIL = "key_admin2_email"
        private const val KEY_ADMIN1_R1 = "key_admin1_r1"
        private const val KEY_ADMIN1_R2 = "key_admin1_r2"
        private const val KEY_ADMIN2_R1 = "key_admin2_r1"
        private const val KEY_ADMIN2_R2 = "key_admin2_r2"
    }
}
