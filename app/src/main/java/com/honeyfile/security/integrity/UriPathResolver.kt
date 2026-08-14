package com.honeyfile.security.integrity

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File

/**
 * Converts a SAF content:// URI (returned by OpenDocumentTree) to a real filesystem path
 * that FileObserver (inotify) can watch.
 *
 * SAF URIs look like:
 *   content://com.android.externalstorage.documents/tree/primary:Documents
 *   content://com.android.externalstorage.documents/tree/1A3B-C4D5:Music  ← SD card
 *
 * The document ID encodes the storage volume and relative path separated by ':'.
 * We parse this to reconstruct the real path.
 *
 * IMPORTANT: This heuristic works on standard AOSP and most OEM ROMs, but some custom
 * ROMs use non-standard storage paths. The observer silently falls back to no inotify
 * watching if the path can't be resolved or doesn't exist.
 */
object UriPathResolver {

    private const val TAG = "UriPathResolver"
    private const val PRIMARY_VOLUME = "primary"
    private const val EMULATED_ROOT = "/storage/emulated/0"

    fun toRealPath(context: Context, treeUri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parts = docId.split(":")
            if (parts.size < 2) {
                Log.w(TAG, "Cannot parse document ID: $docId")
                return null
            }

            val volume = parts[0]
            val relativePath = parts[1]

            val root = when {
                volume.equals(PRIMARY_VOLUME, ignoreCase = true) -> EMULATED_ROOT
                volume.startsWith("content://") -> null  // Not a filesystem volume
                else -> {
                    // External/removable storage (SD card, USB OTG)
                    // Try /storage/<volumeId> — works on most devices
                    val candidate = "/storage/$volume"
                    if (File(candidate).exists()) candidate
                    else {
                        Log.w(TAG, "Non-primary volume path not found: $candidate")
                        null
                    }
                }
            }

            if (root == null) return null

            val fullPath = if (relativePath.isEmpty()) root else "$root/$relativePath"
            val file = File(fullPath)
            if (file.exists() && file.isDirectory) {
                Log.d(TAG, "Resolved SAF URI to real path: $treeUri → $fullPath")
                fullPath
            } else {
                Log.w(TAG, "Resolved path does not exist or is not a directory: $fullPath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve URI to real path: $treeUri", e)
            null
        }
    }
}
