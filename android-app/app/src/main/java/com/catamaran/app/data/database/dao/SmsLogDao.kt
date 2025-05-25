package com.catamaran.app.data.database.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.catamaran.app.data.database.entities.SmsLogEntity
import com.catamaran.app.data.database.entities.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsLogDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsLog(smsLog: SmsLogEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsLogs(smsLogs: List<SmsLogEntity>)
    
    @Update
    suspend fun updateSmsLog(smsLog: SmsLogEntity)
    
    @Delete
    suspend fun deleteSmsLog(smsLog: SmsLogEntity)
    
    // Get all SMS logs ordered by timestamp (most recent first)
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllSmsLogs(): Flow<List<SmsLogEntity>>
    
    // Get SMS logs for a specific time range
    @Query("SELECT * FROM sms_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getSmsLogsInRange(startTime: Long, endTime: Long): List<SmsLogEntity>
    
    // Get unsynced SMS logs for backend upload
    @Query("SELECT * FROM sms_logs WHERE syncStatus = :status ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedSmsLogs(status: SyncStatus = SyncStatus.PENDING, limit: Int = 50): List<SmsLogEntity>
    
    // Get SMS logs with failed sync (for retry)
    @Query("SELECT * FROM sms_logs WHERE syncStatus = :status AND syncRetryCount < :maxRetries ORDER BY lastSyncAttempt ASC LIMIT :limit")
    suspend fun getFailedSyncSmsLogs(
        status: SyncStatus = SyncStatus.FAILED,
        maxRetries: Int = 3,
        limit: Int = 20
    ): List<SmsLogEntity>
    
    // Mark SMS logs as synced
    @Query("UPDATE sms_logs SET syncStatus = :status WHERE id IN (:ids)")
    suspend fun markSmsLogsAsSynced(ids: List<String>, status: SyncStatus = SyncStatus.SYNCED)
    
    // Update sync status for a specific SMS log
    @Query("UPDATE sms_logs SET syncStatus = :status, lastSyncAttempt = :timestamp, syncRetryCount = syncRetryCount + 1 WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus, timestamp: Long = System.currentTimeMillis())
    
    // Get SMS log statistics
    @Query("SELECT COUNT(*) FROM sms_logs WHERE timestamp > :sinceTimestamp")
    suspend fun getSmsLogCount(sinceTimestamp: Long): Int
    
    @Query("SELECT COUNT(*) FROM sms_logs WHERE timestamp > :sinceTimestamp AND isKnownContact = 0")
    suspend fun getUnknownSmsCount(sinceTimestamp: Long): Int
    
    @Query("SELECT SUM(messageCount) FROM sms_logs WHERE timestamp > :sinceTimestamp")
    suspend fun getTotalMessageCount(sinceTimestamp: Long): Int?
    
    @Query("SELECT AVG(riskScore) FROM sms_logs WHERE timestamp > :sinceTimestamp")
    suspend fun getAverageRiskScore(sinceTimestamp: Long): Float?
    
    // Clean up old data (privacy compliance)
    @Query("DELETE FROM sms_logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOldSmsLogs(cutoffTimestamp: Long): Int
    
    // Get recent activity for dashboard
    @Query("SELECT * FROM sms_logs WHERE timestamp > :sinceTimestamp ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSmsLogs(sinceTimestamp: Long, limit: Int = 20): LiveData<List<SmsLogEntity>>
    
    // Check if SMS log already exists (prevent duplicates)
    @Query("SELECT COUNT(*) FROM sms_logs WHERE phoneNumberHash = :phoneHash AND timestamp = :timestamp")
    suspend fun checkSmsLogExists(phoneHash: String, timestamp: Long): Int
    
    // Get all SMS logs for a specific phone number hash
    @Query("SELECT * FROM sms_logs WHERE phoneNumberHash = :phoneHash ORDER BY timestamp DESC")
    suspend fun getSmsLogsForNumber(phoneHash: String): List<SmsLogEntity>
    
    // Get SMS frequency for pattern analysis (privacy-safe)
    @Query("SELECT COUNT(*) FROM sms_logs WHERE phoneNumberHash = :phoneHash AND timestamp > :sinceTimestamp")
    suspend fun getSmsFrequencyForNumber(phoneHash: String, sinceTimestamp: Long): Int
} 