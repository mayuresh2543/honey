package com.honeyfile.security.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "access_logs")
data class AccessLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val file: String,
    val user: String,          // e.g., "Admin 1", "Admin 2", "Intruder", "System"
    val action: String = "ACCESS", // e.g., "CREATED", "MODIFIED", "DELETED", "RENAMED", "ACCESS", "BREACH"
    val details: String = "",   // Human-readable change details
    val timestamp: String
)
