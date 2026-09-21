package com.honeyfile.security.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {

    @Insert
    fun insertLog(log: AccessLog): Long

    @Query("SELECT * FROM access_logs ORDER BY id DESC")
    fun getAllLogs(): LiveData<List<AccessLog>>

    @Query("SELECT * FROM access_logs ORDER BY id DESC")
    fun getAllLogsList(): List<AccessLog>

    @Query("SELECT COUNT(*) FROM access_logs WHERE LOWER(user) NOT LIKE 'intruder%' AND action != 'BREACH' AND action != 'DEPLOYED'")
    fun getAdminCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM access_logs WHERE (LOWER(user) LIKE 'intruder%' OR action = 'BREACH') AND action != 'DEPLOYED'")
    fun getIntruderCount(): LiveData<Int>

    @Query("DELETE FROM access_logs WHERE file = :fileName AND (action = 'ACCESSED' OR action = 'ACCESS')")
    fun deleteAccessLogsForFile(fileName: String): Int

    @Query("DELETE FROM access_logs")
    fun clearAll(): Int
}
