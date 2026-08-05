package com.honeyfile.security.integrity

enum class FileAlterationType {
    EDITED,
    COPIED_PASTED,
    DELETED,
    RENAMED
}

data class FileAlterationEvent(
    val fileName: String,
    val eventType: FileAlterationType,
    val timestamp: String
)
