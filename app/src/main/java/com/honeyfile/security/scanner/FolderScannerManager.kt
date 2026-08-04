package com.honeyfile.security.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileSnapshot(
    val name: String,
    val lastModified: Long,
    val size: Long,
    val uriStr: String
)

data class FileChangeEvent(
    val fileName: String,
    val eventType: String, // "CREATED" or "MODIFIED"
    val timestamp: String,
    val changeDetails: String
)

data class ScanResult(
    val folderUri: String = "",
    val folderName: String = "None selected",
    val totalFilesScanned: Int = 0,
    val honeyFilesFound: Int = 0,
    val honeyFileNames: List<String> = emptyList(),
    val isScanningActive: Boolean = false,
    val lastScanTime: String = "Never",
    val latestChangeSummary: String = "No changes detected"
)

class FolderScannerManager(private val context: Context) {

    private val _scanResult = MutableStateFlow(ScanResult())
    val scanResult: StateFlow<ScanResult> = _scanResult

    private val _fileChangeEvents = MutableSharedFlow<FileChangeEvent>(replay = 0)
    val fileChangeEvents: SharedFlow<FileChangeEvent> = _fileChangeEvents

    private var scanJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Snapshot cache of files in monitored folder
    private val previousSnapshots = mutableMapOf<String, FileSnapshot>()
    private var isFirstScan = true

    // Honeyfile matching keywords (case-insensitive)
    private val honeyfileKeywords = listOf(
        "honey", "secret", "password", "confidential", "salary",
        "admin", "credential", "private", "decoy", "backup"
    )

    fun startContinuousScanning(folderUri: Uri, intervalMs: Long = 2000L) {
        stopScanning()

        val docFile = DocumentFile.fromTreeUri(context, folderUri)
        val folderName = docFile?.name ?: folderUri.lastPathSegment ?: "Selected Folder"

        previousSnapshots.clear()
        isFirstScan = true

        _scanResult.value = _scanResult.value.copy(
            folderUri = folderUri.toString(),
            folderName = folderName,
            isScanningActive = true
        )

        scanJob = coroutineScope.launch {
            Log.d(TAG, "Starting continuous file modification scanner for: $folderName")
            while (isActive) {
                performScan(folderUri, folderName)
                delay(intervalMs)
            }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        previousSnapshots.clear()
        isFirstScan = true
        _scanResult.value = _scanResult.value.copy(isScanningActive = false)
        Log.d(TAG, "Stopped folder scanner")
    }

    private suspend fun performScan(folderUri: Uri, folderName: String) {
        try {
            var totalCount = 0
            val detectedHoneyfiles = mutableListOf<String>()
            val currentSnapshots = mutableMapOf<String, FileSnapshot>()
            val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timestamp = timeFormatter.format(Date())

            if (folderUri.scheme == "content") {
                val tree = DocumentFile.fromTreeUri(context, folderUri)
                if (tree != null && tree.exists() && tree.isDirectory) {
                    val files = tree.listFiles()
                    totalCount = files.size
                    for (file in files) {
                        val name = file.name ?: continue
                        val lastMod = file.lastModified()
                        val size = file.length()
                        val uriStr = file.uri.toString()

                        currentSnapshots[name] = FileSnapshot(name, lastMod, size, uriStr)

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
                        val name = file.name
                        val lastMod = file.lastModified()
                        val size = file.length()

                        currentSnapshots[name] = FileSnapshot(name, lastMod, size, file.absolutePath)

                        if (isHoneyfile(name)) {
                            detectedHoneyfiles.add(name)
                        }
                    }
                }
            }

            var latestChangeText = _scanResult.value.latestChangeSummary

            // Check for file modifications/additions if not first scan
            if (!isFirstScan) {
                for ((fileName, snapshot) in currentSnapshots) {
                    val prev = previousSnapshots[fileName]
                    if (prev == null) {
                        // New file created
                        val detail = "New file '$fileName' created (${formatFileSize(snapshot.size)})"
                        latestChangeText = "$fileName created at $timestamp"
                        Log.i(TAG, "File Creation Detected: $detail")

                        _fileChangeEvents.emit(
                            FileChangeEvent(
                                fileName = fileName,
                                eventType = "CREATED",
                                timestamp = timestamp,
                                changeDetails = detail
                            )
                        )
                    } else if (snapshot.lastModified > prev.lastModified || snapshot.size != prev.size) {
                        // File modified
                        val sizeDiff = snapshot.size - prev.size
                        val diffStr = if (sizeDiff >= 0) "+${formatFileSize(sizeDiff)}" else "-${formatFileSize(-sizeDiff)}"
                        val detail = "File '$fileName' modified at $timestamp (Size change: $diffStr, Total: ${formatFileSize(snapshot.size)})"
                        latestChangeText = "$fileName modified at $timestamp ($diffStr)"
                        Log.i(TAG, "File Modification Detected: $detail")

                        _fileChangeEvents.emit(
                            FileChangeEvent(
                                fileName = fileName,
                                eventType = "MODIFIED",
                                timestamp = timestamp,
                                changeDetails = detail
                            )
                        )
                    }
                }
            } else {
                isFirstScan = false
            }

            // Update snapshot cache
            previousSnapshots.clear()
            previousSnapshots.putAll(currentSnapshots)

            _scanResult.value = ScanResult(
                folderUri = folderUri.toString(),
                folderName = folderName,
                totalFilesScanned = totalCount,
                honeyFilesFound = detectedHoneyfiles.size,
                honeyFileNames = detectedHoneyfiles,
                isScanningActive = true,
                lastScanTime = timestamp,
                latestChangeSummary = latestChangeText
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing file modification scan on $folderUri", e)
        }
    }

    private fun isHoneyfile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return honeyfileKeywords.any { keyword -> lower.contains(keyword) }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    companion object {
        private const val TAG = "FolderScannerManager"
    }
}
