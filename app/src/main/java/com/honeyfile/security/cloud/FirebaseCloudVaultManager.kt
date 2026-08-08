package com.honeyfile.security.cloud

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.honeyfile.security.alert.DeviceTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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
            var publicPhotoUrl: String? = null

            // 1. Upload Intruder Evidence Photo to Firebase Cloud Storage
            if (imageFile != null && imageFile.exists()) {
                try {
                    val storageRef = FirebaseStorage.getInstance().reference
                    val photoRef = storageRef.child("intruder_evidence/${System.currentTimeMillis()}_${imageFile.name}")
                    val fileUri = Uri.fromFile(imageFile)

                    photoRef.putFile(fileUri).await()
                    publicPhotoUrl = photoRef.downloadUrl.await().toString()
                    Log.d(TAG, "Intruder photo uploaded to Firebase Storage ✅: $publicPhotoUrl")
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading intruder photo to Firebase Storage", e)
                }
            }

            // 2. Insert Off-Device Incident Record into Firebase Firestore
            val firestore = FirebaseFirestore.getInstance()
            val incidentData = hashMapOf(
                "file_name" to fileName,
                "action_type" to actionType,
                "timestamp" to timestamp,
                "details" to details,
                "photo_url" to (publicPhotoUrl ?: ""),
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
            val successMsg = "Synced breach incident to Firebase Cloud Vault ✅ Document ID: $docId"
            Log.d(TAG, successMsg)

            CloudVaultSyncResult(
                isSuccess = true,
                imageUrl = publicPhotoUrl,
                documentId = docId,
                message = successMsg
            )
        } catch (e: Exception) {
            val errorMsg = "Firebase Cloud Vault Sync Error: ${e.localizedMessage ?: e.message}"
            Log.e(TAG, errorMsg, e)
            CloudVaultSyncResult(isSuccess = false, message = errorMsg)
        }
    }

    companion object {
        private const val TAG = "FirebaseCloudVault"
    }
}
