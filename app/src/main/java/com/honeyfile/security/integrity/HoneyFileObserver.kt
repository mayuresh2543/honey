package com.honeyfile.security.integrity

import android.os.Build
import android.os.FileObserver
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("DEPRECATION")
class HoneyFileObserver(
    folderPath: String,
    private val onAlterationDetected: (FileAlterationEvent) -> Unit
) : FileObserver(
    folderPath,
    CREATE or MODIFY or DELETE or MOVED_FROM or MOVED_TO
) {

    override fun onEvent(event: Int, path: String?) {
        if (path == null) return

        val eventType = when (event and ALL_EVENTS) {
            CREATE -> FileAlterationType.COPIED_PASTED
            MODIFY -> FileAlterationType.EDITED
            DELETE, DELETE_SELF -> FileAlterationType.DELETED
            MOVED_FROM, MOVED_TO -> FileAlterationType.RENAMED
            else -> return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val alterationEvent = FileAlterationEvent(
            fileName = path,
            eventType = eventType,
            timestamp = timestamp
        )

        Log.d(TAG, "File Alteration Event: $path -> $eventType at $timestamp")
        onAlterationDetected(alterationEvent)
    }

    companion object {
        private const val TAG = "HoneyFileObserver"
    }
}
