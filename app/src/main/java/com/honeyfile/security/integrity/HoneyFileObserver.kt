package com.honeyfile.security.integrity

import android.os.FileObserver
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Watches a real filesystem directory using Linux inotify (via Android FileObserver).
 *
 * Detects TWO categories of events:
 *
 * 1. WRITE EVENTS (CREATE, MODIFY, DELETE, MOVE): file was tampered with.
 *    These are also caught by FolderScannerManager (SAF polling), but inotify fires
 *    instantly (microseconds) vs SAF polling which has a 500ms lag.
 *
 * 2. READ/ACCESS EVENTS (OPEN + CLOSE_NOWRITE): file was opened and read without being modified.
 *    This is the new capability — SAF polling CANNOT detect reads at all, only inotify can.
 *    We use CLOSE_NOWRITE (not OPEN or ACCESS) because:
 *    - OPEN fires for every process that touches the file (including system indexers)
 *    - ACCESS fires for every block read (extremely noisy)
 *    - CLOSE_NOWRITE fires exactly once when a process finishes reading the file
 *      and confirms it made no changes, which is the clearest signal of intentional access.
 *
 * HONEY FILE FILTERING:
 * To avoid false positives from system media scanners, thumbnail generators, and backup
 * agents that constantly read files, CLOSE_NOWRITE events are only reported for files
 * whose names contain honeyfile keywords. Admins should name their decoy files with
 * keywords like "password", "salary", "secret", etc.
 *
 * NOTE: Requires a real filesystem path. Call UriPathResolver.toRealPath() to convert
 * a SAF content:// URI to a usable path before constructing this observer.
 */
@Suppress("DEPRECATION")
class HoneyFileObserver(
    folderPath: String,
    private val onAlterationDetected: (FileAlterationEvent) -> Unit
) : FileObserver(
    folderPath,
    // Write events
    CREATE or MODIFY or DELETE or MOVED_FROM or MOVED_TO or
    // Read/access events — OPEN, ACCESS, CLOSE_NOWRITE
    OPEN or ACCESS or CLOSE_NOWRITE
) {
    private val recentAccessTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return

        if (isDeploymentInProgress || com.honeyfile.security.scanner.FolderScannerManager.isDeploymentInProgress) {
            Log.d(TAG, "Decoy deployment in progress — suppressing inotify event for: $path")
            return
        }

        // Suppress known decoy creations
        val masked = event and ALL_EVENTS
        if ((masked == CREATE || masked == MODIFY) && com.honeyfile.security.decoy.DecoyGeneratorEngine.isDecoyFileName(path)) {
            Log.d(TAG, "Known decoy file creation/write inotify event ignored: $path")
            return
        }

        // For read/open events, filter to honey-keywords to suppress system noise
        if (masked == OPEN || masked == ACCESS || masked == CLOSE_NOWRITE) {
            if (!isHoneyFile(path)) return

            val now = System.currentTimeMillis()
            val last = recentAccessTimestamps[path] ?: 0L
            if (now - last < 4000L) {
                return
            }
            recentAccessTimestamps[path] = now
        }

        val eventType = when (masked) {
            CREATE                              -> FileAlterationType.COPIED_PASTED
            MODIFY                              -> FileAlterationType.EDITED
            DELETE, DELETE_SELF                 -> FileAlterationType.DELETED
            MOVED_FROM, MOVED_TO                -> FileAlterationType.RENAMED
            OPEN, ACCESS, CLOSE_NOWRITE         -> FileAlterationType.ACCESSED
            else                                -> return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        Log.d(TAG, "inotify event: $path → $eventType (mask=$masked) at $timestamp")
        onAlterationDetected(FileAlterationEvent(fileName = path, eventType = eventType, timestamp = timestamp))
    }

    private fun isHoneyFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return HONEY_KEYWORDS.any { lower.contains(it) }
    }

    companion object {
        private const val TAG = "HoneyFileObserver"

        @Volatile
        var isDeploymentInProgress: Boolean = false
        private val HONEY_KEYWORDS = listOf(
            // Core trap terms
            "honey", "decoy", "trap", "bait",
            // Credential files
            "secret", "password", "credential", "private", "backup",
            "api_key", "token", "apikey", "passwd",
            // Financial
            "salary", "payroll", "statement", "bank",
            // Legal
            "nda", "confidential", "agreement",
            // Tax
            "itr", "tax", "assessment",
            // Crypto
            "seed", "ledger", "crypto",
            // Cloud / dev
            "gcp", "aws", "env", "service_account",
            // Database vault
            "vault", "internal", "credentials",
            // Admin
            "admin"
        )
    }
}
