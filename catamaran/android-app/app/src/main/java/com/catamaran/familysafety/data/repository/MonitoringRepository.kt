package com.catamaran.familysafety.data.repository

import com.catamaran.familysafety.data.database.MonitoringDao
import com.catamaran.familysafety.data.model.CallLogEntry
import com.catamaran.familysafety.data.model.SMSEntry
import com.catamaran.familysafety.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MonitoringRepository(
    private val dao: MonitoringDao,
    private val apiService: ApiService
) {
    
    // Call Log operations
    suspend fun syncCallLogs(callLogs: List<CallLogEntry>) = withContext(Dispatchers.IO) {
        try {
            // Store locally first
            dao.insertCallLogs(callLogs)
            
            // Get unsynced call logs
            val unsyncedLogs = dao.getUnsyncedCallLogs()
            
            if (unsyncedLogs.isNotEmpty()) {
                // Send to API
                val response = apiService.uploadCallLogs(unsyncedLogs)
                
                if (response.isSuccessful) {
                    // Mark as synced
                    val syncedIds = unsyncedLogs.map { it.id }
                    dao.markCallLogsSynced(syncedIds)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MonitoringRepository", "Failed to sync call logs", e)
            throw e
        }
    }
    
    // SMS operations
    suspend fun syncSMSData(smsMessages: List<SMSEntry>) = withContext(Dispatchers.IO) {
        try {
            // Store locally first
            dao.insertSMSMessages(smsMessages)
            
            // Get unsynced SMS messages
            val unsyncedMessages = dao.getUnsyncedSMSMessages()
            
            if (unsyncedMessages.isNotEmpty()) {
                // Send to API
                val response = apiService.uploadSMSMessages(unsyncedMessages)
                
                if (response.isSuccessful) {
                    // Mark as synced
                    val syncedIds = unsyncedMessages.map { it.id }
                    dao.markSMSMessagesSynced(syncedIds)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MonitoringRepository", "Failed to sync SMS data", e)
            throw e
        }
    }
    
    // General sync operation
    suspend fun syncData() = withContext(Dispatchers.IO) {
        try {
            // Sync call logs
            val unsyncedCallLogs = dao.getUnsyncedCallLogs()
            if (unsyncedCallLogs.isNotEmpty()) {
                val callLogResponse = apiService.uploadCallLogs(unsyncedCallLogs)
                if (callLogResponse.isSuccessful) {
                    dao.markCallLogsSynced(unsyncedCallLogs.map { it.id })
                }
            }
            
            // Sync SMS messages
            val unsyncedSMS = dao.getUnsyncedSMSMessages()
            if (unsyncedSMS.isNotEmpty()) {
                val smsResponse = apiService.uploadSMSMessages(unsyncedSMS)
                if (smsResponse.isSuccessful) {
                    dao.markSMSMessagesSynced(unsyncedSMS.map { it.id })
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MonitoringRepository", "Failed to sync data", e)
            throw e
        }
    }
    
    // Check connection to backend
    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.healthCheck()
            response.isSuccessful
        } catch (e: Exception) {
            android.util.Log.e("MonitoringRepository", "Connection check failed", e)
            false
        }
    }
    
    // Start monitoring (placeholder for future implementation)
    suspend fun startMonitoring() = withContext(Dispatchers.IO) {
        // This would typically start background services or workers
        android.util.Log.i("MonitoringRepository", "Monitoring started")
    }
    
    // Stop monitoring (placeholder for future implementation)
    suspend fun stopMonitoring() = withContext(Dispatchers.IO) {
        // This would typically stop background services or workers
        android.util.Log.i("MonitoringRepository", "Monitoring stopped")
    }
    
    // Clean up old data
    suspend fun cleanupOldData(daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        dao.deleteOldCallLogs(cutoffTime)
        dao.deleteOldSMSMessages(cutoffTime)
    }
    
    // Get statistics
    suspend fun getActivityStatistics(hoursBack: Int = 24): ActivityStatistics = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - (hoursBack * 60 * 60 * 1000L)
        val callCount = dao.getCallCountSince(since)
        val smsCount = dao.getSMSCountSince(since)
        
        ActivityStatistics(
            callCount = callCount,
            smsCount = smsCount,
            timeRangeHours = hoursBack
        )
    }
}

data class ActivityStatistics(
    val callCount: Int,
    val smsCount: Int,
    val timeRangeHours: Int
) 