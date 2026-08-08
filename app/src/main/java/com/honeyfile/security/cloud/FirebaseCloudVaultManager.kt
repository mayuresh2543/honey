package com.honeyfile.security.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.honeyfile.security.alert.DeviceTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class CloudVaultSyncResult(
    val isSuccess: Boolean,
    val imageUrl: String? = null,
    val documentId: String? = null,
    val message: String
)

class FirebaseCloudVaultManager(private val context: Context) {

    private fun isFirebaseAvailable(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseApp is not initialized yet: ${e.message}")
            false
        }
    }

    suspend fun syncBreachIncidentToCloud(
        fileName: String,
        actionType: String,
        timestamp: String,
        details: String,
        imageFile: File?,
        telemetry: DeviceTelemetry?
    ): CloudVaultSyncResult = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable()) {
            val msg = "Firebase is not configured or google-services.json is missing. Skipping cloud vault sync."
            Log.d(TAG, msg)
            return@withContext CloudVaultSyncResult(isSuccess = false, message = msg)
        }

        try {
            // 0. Ensure Firebase Anonymous Authentication session
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                try {
                    auth.signInAnonymously().await()
                    Log.d(TAG, "Authenticated anonymous Firebase session ✅: ${auth.currentUser?.uid}")
                } catch (e: Exception) {
                    Log.w(TAG, "Anonymous auth warning: ${e.message}. Proceeding with unauthenticated request.")
                }
            }

            // 1. Encode Intruder Evidence Photo directly to Base64 String (No Paid Storage Needed!)
            val photoBase64 = encodeImageToBase64(imageFile)

            // 2. Insert Off-Device Incident Record into 100% Free Firebase Firestore Database
            val firestore = FirebaseFirestore.getInstance()
            val incidentData = hashMapOf(
                "file_name" to fileName,
                "action_type" to actionType,
                "timestamp" to timestamp,
                "details" to details,
                "photo_base64" to (photoBase64 ?: ""),
                "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "android_version" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "synced_at_ms" to System.currentTimeMillis(),
                "telemetry" to hashMapOf(
                    "latitude" to (telemetry?.latitude ?: 0.0),
                    "longitude" to (telemetry?.longitude ?: 0.0),
                    "google_maps_url" to (telemetry?.googleMapsUrl ?: ""),
                    "ip_address" to (telemetry?.ipAddress ?: "127.0.0.1"),
                    "wifi_ssid" to (telemetry?.wifiSsid ?: "Unknown"),
                    "battery_percentage" to (telemetry?.batteryPercentage ?: 100),
                    "is_charging" to (telemetry?.isCharging ?: false)
                )
            )

            val docRef = firestore.collection("breach_incidents").add(incidentData).await()
            val docId = docRef.id
            val successMsg = "Synced breach incident & photo directly to free Firestore Vault ✅ Document ID: $docId"
            Log.d(TAG, successMsg)

            CloudVaultSyncResult(
                isSuccess = true,
                imageUrl = if (photoBase64 != null) "data:image/jpeg;base64,$photoBase64" else null,
                documentId = docId,
                message = successMsg
            )
        } catch (e: Exception) {
            val errorMsg = "Firebase Cloud Vault Sync Error: ${e.localizedMessage ?: e.message}"
            Log.e(TAG, errorMsg, e)
            CloudVaultSyncResult(isSuccess = false, message = errorMsg)
        }
    }

    private fun encodeImageToBase64(imageFile: File?): String? {
        if (imageFile == null || !imageFile.exists()) return null
        return try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return null
            val outputStream = ByteArrayOutputStream()
            // Compress bitmap to 60% quality JPEG thumbnail for efficient Firestore document storage
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding intruder photo to Base64", e)
            null
        }
    }

    companion object {
        private const val TAG = "FirebaseCloudVault"
    }
}
