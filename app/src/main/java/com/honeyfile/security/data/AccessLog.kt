package com.honeyfile.security.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "access_logs")
data class AccessLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val file: String,
    val user: String,
    val timestamp: String
)
