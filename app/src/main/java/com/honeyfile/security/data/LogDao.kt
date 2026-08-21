package com.honeyfile.security.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {

    @Insert
    suspend fun insertLog(log: AccessLog)

    @Query("SELECT * FROM access_logs ORDER BY id DESC")
    fun getAllLogs(): LiveData<List<AccessLog>>

    @Query("SELECT * FROM access_logs ORDER BY id DESC")
    suspend fun getAllLogsList(): List<AccessLog>

    @Query("SELECT COUNT(*) FROM access_logs WHERE LOWER(user) LIKE 'admin%' AND action != 'DEPLOYED'")
    fun getAdminCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM access_logs WHERE LOWER(user) LIKE 'intruder%' OR action = 'BREACH'")
    fun getIntruderCount(): LiveData<Int>

    @Query("DELETE FROM access_logs")
    suspend fun clearAll()
}
