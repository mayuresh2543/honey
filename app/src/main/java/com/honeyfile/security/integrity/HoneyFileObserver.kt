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
    // Read/access events — CLOSE_NOWRITE is the cleanest read signal
    CLOSE_NOWRITE
) {
    override fun onEvent(event: Int, path: String?) {
        if (path == null) return

        val masked = event and ALL_EVENTS

        // For read events, only fire for honey-keyword filenames to suppress system noise
        if (masked == CLOSE_NOWRITE) {
            if (!isHoneyFile(path)) return
        }

        val eventType = when (masked) {
            CREATE                  -> FileAlterationType.COPIED_PASTED
            MODIFY                  -> FileAlterationType.EDITED
            DELETE, DELETE_SELF     -> FileAlterationType.DELETED
            MOVED_FROM, MOVED_TO    -> FileAlterationType.RENAMED
            CLOSE_NOWRITE           -> FileAlterationType.ACCESSED
            else                    -> return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        Log.d(TAG, "inotify event: $path → $eventType at $timestamp")
        onAlterationDetected(FileAlterationEvent(fileName = path, eventType = eventType, timestamp = timestamp))
    }

    private fun isHoneyFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return HONEY_KEYWORDS.any { lower.contains(it) }
    }

    companion object {
        private const val TAG = "HoneyFileObserver"
        private val HONEY_KEYWORDS = listOf(
            "honey", "secret", "password", "confidential", "salary",
            "admin", "credential", "private", "decoy", "backup",
            "api_key", "token", "apikey", "passwd"
        )
    }
}
