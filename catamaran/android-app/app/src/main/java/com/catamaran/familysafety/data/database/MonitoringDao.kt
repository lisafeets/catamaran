package com.catamaran.familysafety.data.database

import androidx.room.*
import com.catamaran.familysafety.data.model.CallLogEntry
import com.catamaran.familysafety.data.model.SMSEntry

@Dao
interface MonitoringDao {
    
    // Call Log operations
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    suspend fun getAllCallLogs(): List<CallLogEntry>
    
    @Query("SELECT * FROM call_logs WHERE synced = 0 ORDER BY timestamp DESC")
    suspend fun getUnsyncedCallLogs(): List<CallLogEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLogs(callLogs: List<CallLogEntry>)
    
    @Update
    suspend fun updateCallLog(callLog: CallLogEntry)
    
    @Query("UPDATE call_logs SET synced = 1 WHERE id IN (:ids)")
    suspend fun markCallLogsSynced(ids: List<Long>)
    
    @Query("DELETE FROM call_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOldCallLogs(cutoffTime: Long)
    
    // SMS operations
    @Query("SELECT * FROM sms_messages ORDER BY timestamp DESC")
    suspend fun getAllSMSMessages(): List<SMSEntry>
    
    @Query("SELECT * FROM sms_messages WHERE synced = 0 ORDER BY timestamp DESC")
    suspend fun getUnsyncedSMSMessages(): List<SMSEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSMSMessages(smsMessages: List<SMSEntry>)
    
    @Update
    suspend fun updateSMSMessage(smsMessage: SMSEntry)
    
    @Query("UPDATE sms_messages SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSMSMessagesSynced(ids: List<Long>)
    
    @Query("DELETE FROM sms_messages WHERE timestamp < :cutoffTime")
    suspend fun deleteOldSMSMessages(cutoffTime: Long)
    
    // Statistics
    @Query("SELECT COUNT(*) FROM call_logs WHERE timestamp > :since")
    suspend fun getCallCountSince(since: Long): Int
    
    @Query("SELECT COUNT(*) FROM sms_messages WHERE timestamp > :since")
    suspend fun getSMSCountSince(since: Long): Int
} 