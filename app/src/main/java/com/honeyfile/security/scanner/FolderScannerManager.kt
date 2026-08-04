package com.honeyfile.security.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class ScanResult(
    val folderUri: String = "",
    val folderName: String = "None selected",
    val totalFilesScanned: Int = 0,
    val honeyFilesFound: Int = 0,
    val honeyFileNames: List<String> = emptyList(),
    val isScanningActive: Boolean = false,
    val lastScanTime: String = "Never"
)

class FolderScannerManager(private val context: Context) {

    private val _scanResult = MutableStateFlow(ScanResult())
    val scanResult: StateFlow<ScanResult> = _scanResult

    private var scanJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Honeyfile matching keywords (case-insensitive)
    private val honeyfileKeywords = listOf(
        "honey", "secret", "password", "confidential", "salary",
        "admin", "credential", "private", "decoy", "backup"
    )

    fun startContinuousScanning(folderUri: Uri, intervalMs: Long = 3000L) {
        stopScanning()

        val docFile = DocumentFile.fromTreeUri(context, folderUri)
        val folderName = docFile?.name ?: folderUri.lastPathSegment ?: "Selected Folder"

        _scanResult.value = _scanResult.value.copy(
            folderUri = folderUri.toString(),
            folderName = folderName,
            isScanningActive = true
        )

        scanJob = coroutineScope.launch {
            Log.d(TAG, "Starting continuous folder scanner for: $folderName")
            while (isActive) {
                performScan(folderUri, folderName)
                delay(intervalMs)
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        _scanResult.value = _scanResult.value.copy(isScanningActive = false)
        Log.d(TAG, "Stopped folder scanner")
    }

    private fun performScan(folderUri: Uri, folderName: String) {
        try {
            var totalCount = 0
            val detectedHoneyfiles = mutableListOf<String>()

            if (folderUri.scheme == "content") {
                val tree = DocumentFile.fromTreeUri(context, folderUri)
                if (tree != null && tree.exists() && tree.isDirectory) {
                    val files = tree.listFiles()
                    totalCount = files.size
                    for (file in files) {
                        val name = file.name ?: continue
                        if (isHoneyfile(name)) {
                            detectedHoneyfiles.add(name)
                        }
                    }
                }
            } else if (folderUri.scheme == "file" || folderUri.path != null) {
                val localFile = File(folderUri.path ?: "")
                if (localFile.exists() && localFile.isDirectory) {
                    val files = localFile.listFiles() ?: emptyArray()
                    totalCount = files.size
                    for (file in files) {
                        if (isHoneyfile(file.name)) {
                            detectedHoneyfiles.add(file.name)
                        }
                    }
                }
            }

            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

            _scanResult.value = ScanResult(
                folderUri = folderUri.toString(),
                folderName = folderName,
                totalFilesScanned = totalCount,
                honeyFilesFound = detectedHoneyfiles.size,
                honeyFileNames = detectedHoneyfiles,
                isScanningActive = true,
                lastScanTime = timestamp
            )

            Log.d(TAG, "Scan completed: $totalCount files scanned, ${detectedHoneyfiles.size} honeyfiles detected at $timestamp")
        } catch (e: Exception) {
            Log.e(TAG, "Error performing scan on $folderUri", e)
        }
    }

    private fun isHoneyfile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return honeyfileKeywords.any { keyword -> lower.contains(keyword) }
    }

    companion object {
        private const val TAG = "FolderScannerManager"
    }
}
