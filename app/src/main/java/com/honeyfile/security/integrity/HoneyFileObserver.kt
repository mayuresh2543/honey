package com.honeyfile.security.integrity

import android.os.FileObserver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
    private val folderPath: String,
    private val onAlterationDetected: (FileAlterationEvent) -> Unit
) : FileObserver(
    folderPath,
    // Write & mutation events
    CREATE or MODIFY or CLOSE_WRITE or ATTRIB or DELETE or DELETE_SELF or MOVED_FROM or MOVED_TO or
    // Read/access event — CLOSE_NOWRITE is the sole reliable indicator of a finished file read
    CLOSE_NOWRITE
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val recentAccessTimestamps = ConcurrentHashMap<String, Long>()
    private val pendingAccessJobs = ConcurrentHashMap<String, Job>()
    private val recentlyDeletedFiles = ConcurrentHashMap<String, Long>()
    @Volatile
    private var lastFolderMutationTimeMs = 0L
    private val pendingMovedFromMap = ConcurrentHashMap<String, Long>()

    override fun onEvent(event: Int, path: String?) {
        if (path.isNullOrBlank()) return

        // 1. Ignore inotify events on directories themselves (0x40000000 = IN_ISDIR)
        if ((event and 0x40000000) != 0) {
            return
        }

        // Ignore SQLite database files, lock files, journals, and hidden dot-files (.db, .sqlite, .db-wal, etc.)
        if (path.endsWith(".db", ignoreCase = true) ||
            path.endsWith(".sqlite", ignoreCase = true) ||
            path.endsWith("-journal") ||
            path.endsWith("-wal") ||
            path.endsWith("-shm") ||
            path.startsWith(".")) {
            return
        }

        if (isDeploymentInProgress || com.honeyfile.security.scanner.FolderScannerManager.isDeploymentInProgress) {
            Log.d(TAG, "Decoy deployment in progress — suppressing inotify event for: $path")
            return
        }

        val targetFile = File(folderPath, path)
        val fileExists = targetFile.exists()

        // Bitwise inotify event flags
        val isMovedFrom = (event and MOVED_FROM) != 0
        val isMovedTo = (event and MOVED_TO) != 0
        val isDelete = (event and (DELETE or DELETE_SELF)) != 0
        val isCreate = (event and CREATE) != 0
        val isModify = (event and (MODIFY or CLOSE_WRITE or ATTRIB)) != 0
        val isAccess = (event and CLOSE_NOWRITE) != 0

        // Track folder mutation timestamps (deletion, creation, modify, rename)
        if (isDelete || isCreate || isModify || isMovedFrom || isMovedTo || !fileExists) {
            lastFolderMutationTimeMs = System.currentTimeMillis()
        }

        // 2. Suppress known decoy creations and initial writes
        if ((isCreate || isModify) && fileExists && com.honeyfile.security.decoy.DecoyGeneratorEngine.isDecoyFileName(path)) {
            Log.d(TAG, "Known decoy file creation/write inotify event ignored: $path")
            return
        }

        // If file was deleted or unlinked, immediately cancel any pending access job and track deletion
        if (isDelete || !fileExists) {
            pendingAccessJobs.remove(path)?.cancel()
            recentlyDeletedFiles[path] = System.currentTimeMillis()
        }

        // 3. For read/access events (CLOSE_NOWRITE):
        if (isAccess) {
            val now = System.currentTimeMillis()

            // If file was deleted recently (within 5 seconds) or does not exist on disk,
            // this CLOSE_NOWRITE is a trailing inotify kernel artifact from unlinking/closing the deleted file.
            val lastDeletedTime = recentlyDeletedFiles[path]
            if (!fileExists || (lastDeletedTime != null && now - lastDeletedTime < 5000L)) {
                Log.d(TAG, "Suppressed trailing CLOSE_NOWRITE for deleted file: $path")
                return
            }

            // Must match honey keywords
            if (!isHoneyFile(path)) return

            // Mutation cooldown: If a file deletion, creation, or rename occurred in this directory
            // within the last 3.5 seconds, ignore CLOSE_NOWRITE (OS media/sqlite cleanup probe on existing files)
            if (now - lastFolderMutationTimeMs < 3500L) {
                Log.d(TAG, "Suppressed read event for '$path' during post-deletion/mutation settling window (${now - lastFolderMutationTimeMs}ms)")
                return
            }

            // Ensure target is an actual existing regular file, not a directory
            if (targetFile.isDirectory) {
                return
            }

            val lastModified = targetFile.lastModified()

            // Grace period: ignore reads within 15s of file creation/modification (OS thumbnailer/media indexer probe)
            if (now - lastModified < 15_000L) {
                Log.d(TAG, "Ignored initial OS indexer read for: $path (age: ${now - lastModified}ms)")
                return
            }

            // Debounce rapid successive read/close events for the same file
            val lastAccess = recentAccessTimestamps[path] ?: 0L
            if (now - lastAccess < 8000L) {
                return
            }
            recentAccessTimestamps[path] = now

            // Buffer access event by 450ms settling delay to differentiate genuine read from a pre-delete attribute check.
            // Android file managers routinely open & close files immediately before unlinking/deleting them.
            pendingAccessJobs.remove(path)?.cancel()
            val accessJob = coroutineScope.launch {
                delay(450L)
                pendingAccessJobs.remove(path)

                val checkFile = File(folderPath, path)
                val checkExists = checkFile.exists()
                val wasDeleted = recentlyDeletedFiles[path]?.let { System.currentTimeMillis() - it < 5000L } == true

                if (!checkExists || wasDeleted) {
                    Log.d(TAG, "File '$path' unlinked during access settling window — converting to DELETED")
                    recentlyDeletedFiles[path] = System.currentTimeMillis()
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    onAlterationDetected(FileAlterationEvent(fileName = path, eventType = FileAlterationType.DELETED, timestamp = timestamp))
                } else {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    Log.d(TAG, "inotify verified access: $path → ACCESSED at $timestamp")
                    onAlterationDetected(FileAlterationEvent(fileName = path, eventType = FileAlterationType.ACCESSED, timestamp = timestamp))
                }
            }
            pendingAccessJobs[path] = accessJob
            return
        }

        // Buffer MOVED_FROM to pair with subsequent MOVED_TO for clean rename tracking
        if (isMovedFrom) {
            val moveTime = System.currentTimeMillis()
            pendingMovedFromMap[path] = moveTime
            coroutineScope.launch {
                delay(1800L)
                if (pendingMovedFromMap.remove(path, moveTime)) {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    Log.d(TAG, "inotify event: $path → DELETED (moved out of directory) at $timestamp")
                    onAlterationDetected(FileAlterationEvent(fileName = path, eventType = FileAlterationType.DELETED, timestamp = timestamp))
                }
            }
            return
        }

        var reportedFileName = path

        // 4. Resolve exact event type
        val eventType: FileAlterationType = when {
            isMovedTo -> {
                val now = System.currentTimeMillis()
                val matchedEntry = pendingMovedFromMap.entries
                    .filter { now - it.value < 2000L }
                    .maxByOrNull { it.value }

                if (matchedEntry != null && pendingMovedFromMap.remove(matchedEntry.key, matchedEntry.value)) {
                    reportedFileName = "${matchedEntry.key} ➔ $path"
                }
                FileAlterationType.RENAMED
            }
            isDelete || !fileExists -> {
                pendingAccessJobs.remove(path)?.cancel()
                recentlyDeletedFiles[path] = System.currentTimeMillis()
                FileAlterationType.DELETED
            }
            isCreate -> {
                FileAlterationType.COPIED_PASTED
            }
            isModify -> {
                FileAlterationType.EDITED
            }
            else -> return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        Log.d(TAG, "inotify event: $reportedFileName → $eventType (event=$event, exists=$fileExists) at $timestamp")
        onAlterationDetected(FileAlterationEvent(fileName = reportedFileName, eventType = eventType, timestamp = timestamp))
    }

    override fun stopWatching() {
        super.stopWatching()
        pendingAccessJobs.values.forEach { it.cancel() }
        pendingAccessJobs.clear()
        pendingMovedFromMap.clear()
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
