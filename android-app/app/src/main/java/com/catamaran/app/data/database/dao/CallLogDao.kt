package com.catamaran.app.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.catamaran.app.data.database.entities.CallLogEntity
import com.catamaran.app.data.database.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogs(callLogs: List<CallLogEntity>)
    
    @Update
    suspend fun updateCallLog(callLog: CallLogEntity)
    
    @Delete
    suspend fun deleteCallLog(callLog: CallLogEntity)
    
    // Get all call logs ordered by timestamp (most recent first)
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>
    
    // Get call logs for a specific time range
    @Query("SELECT * FROM call_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getCallLogsInRange(startTime: Long, endTime: Long): List<CallLogEntity>
    
    // Get unsynced call logs for backend upload
    @Query("SELECT * FROM call_logs WHERE syncStatus = :status ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedCallLogs(status: SyncStatus = SyncStatus.PENDING, limit: Int = 50): List<CallLogEntity>
    
    // Get call logs with failed sync (for retry)
    @Query("SELECT * FROM call_logs WHERE syncStatus = :status AND syncRetryCount < :maxRetries ORDER BY lastSyncAttempt ASC LIMIT :limit")
    suspend fun getFailedSyncCallLogs(
        status: SyncStatus = SyncStatus.FAILED,
        maxRetries: Int = 3,
        limit: Int = 20
    ): List<CallLogEntity>
    
    // Mark call logs as synced
    @Query("UPDATE call_logs SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun markCallLogsAsSynced(ids: List<String>, status: SyncStatus = SyncStatus.SYNCED)
    
    // Update sync status for a specific call log
    @Query("UPDATE call_logs SET syncStatus = :status, lastSyncAttempt = :timestamp, syncRetryCount = syncRetryCount + 1 WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus, timestamp: Long = System.currentTimeMillis())
    
    // Get call log statistics
    @Query("SELECT COUNT(*) FROM call_logs WHERE timestamp > :sinceTimestamp")
    suspend fun getCallLogCount(sinceTimestamp: Long): Int
    
    @Query("SELECT COUNT(*) FROM call_logs WHERE timestamp > :sinceTimestamp AND isKnownContact = 0")
    suspend fun getUnknownCallCount(sinceTimestamp: Long): Int
    
    @Query("SELECT AVG(riskScore) FROM call_logs WHERE timestamp > :sinceTimestamp")
    suspend fun getAverageRiskScore(sinceTimestamp: Long): Float?
    
    // Clean up old data (privacy compliance)
    @Query("DELETE FROM call_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOldCallLogs(cutoffTimestamp: Long): Int
    
    // Get recent activity for dashboard
    @Query("SELECT * FROM call_logs WHERE timestamp > :sinceTimestamp ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCallLogs(sinceTimestamp: Long, limit: Int = 20): LiveData<List<CallLogEntity>>
    
    // Check if call log already exists (prevent duplicates)
    @Query("SELECT COUNT(*) FROM call_logs WHERE phoneNumberHash = :phoneHash AND timestamp = :timestamp AND duration = :duration")
    suspend fun checkCallLogExists(phoneHash: String, timestamp: Long, duration: Long): Int
    
    // Get all call logs for a specific phone number hash
    @Query("SELECT * FROM call_logs WHERE phoneNumberHash = :phoneHash ORDER BY timestamp DESC")
    suspend fun getCallLogsForNumber(phoneHash: String): List<CallLogEntity>
} 