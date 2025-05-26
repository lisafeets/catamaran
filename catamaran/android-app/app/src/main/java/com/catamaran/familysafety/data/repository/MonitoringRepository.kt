package com.catamaran.familysafety.data.repository

import com.catamaran.familysafety.data.database.MonitoringDao
import com.catamaran.familysafety.data.model.CallLogEntry
import com.catamaran.familysafety.data.model.SMSEntry
import com.catamaran.familysafety.network.ApiService
import com.catamaran.familysafety.network.CallLogEntryAPI
import com.catamaran.familysafety.network.SMSEntryAPI
import com.catamaran.familysafety.network.ActivitySyncRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MonitoringRepository(
    private val dao: MonitoringDao,
    private val apiService: ApiService
) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    // Call Log operations - Updated to use unified API
    suspend fun syncCallLogs(callLogs: List<CallLogEntry>) = withContext(Dispatchers.IO) {
        try {
            // Store locally first
            dao.insertCallLogs(callLogs)
            
            // Get unsynced call logs
            val unsyncedLogs = dao.getUnsyncedCallLogs()
            
            if (unsyncedLogs.isNotEmpty()) {
                // Convert to API format
                val apiLogs = unsyncedLogs.map { log ->
                    CallLogEntryAPI(
                        phoneNumber = log.phoneNumber,
                        duration = log.duration.toInt(),
                        callType = when (log.callType.uppercase()) {
                            "INCOMING" -> "incoming"
                            "OUTGOING" -> "outgoing"
                            "MISSED" -> "missed"
                            else -> "missed"
                        },
                        timestamp = dateFormat.format(Date(log.timestamp)),
                        isKnownContact = log.isKnownContact,
                        contactName = log.contactName
                    )
                }
                
                // Send to unified API endpoint
                val request = ActivitySyncRequest(callLogs = apiLogs, smsLogs = emptyList())
                val response = apiService.syncActivity(request)
                
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
    
    // SMS operations - Updated to use unified API
    suspend fun syncSMSData(smsMessages: List<SMSEntry>) = withContext(Dispatchers.IO) {
        try {
            // Store locally first
            dao.insertSMSMessages(smsMessages)
            
            // Get unsynced SMS messages
            val unsyncedMessages = dao.getUnsyncedSMSMessages()
            
            if (unsyncedMessages.isNotEmpty()) {
                // Convert to API format
                val apiMessages = unsyncedMessages.map { sms ->
                    SMSEntryAPI(
                        senderNumber = sms.phoneNumber, // Railway expects 'senderNumber'
                        messageCount = 1, // Each SMS entry represents one message
                        messageType = when (sms.messageType.uppercase()) {
                            "RECEIVED" -> "received"
                            "SENT" -> "sent"
                            else -> "received"
                        },
                        timestamp = dateFormat.format(Date(sms.timestamp)),
                        isKnownContact = true, // Default to true, could be enhanced
                        contactName = null, // Could be enhanced to lookup contact name
                        hasLink = false
                    )
                }
                
                // Send to unified API endpoint
                val request = ActivitySyncRequest(callLogs = emptyList(), smsLogs = apiMessages)
                val response = apiService.syncActivity(request)
                
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
    
    // General sync operation - Updated to use unified API endpoint
    suspend fun syncData() = withContext(Dispatchers.IO) {
        try {
            // Get unsynced data
            val unsyncedCallLogs = dao.getUnsyncedCallLogs()
            val unsyncedSMS = dao.getUnsyncedSMSMessages()
            
            // Only sync if there's data to sync
            if (unsyncedCallLogs.isNotEmpty() || unsyncedSMS.isNotEmpty()) {
                
                // Convert call logs to API format
                val apiCallLogs = unsyncedCallLogs.map { log ->
                    CallLogEntryAPI(
                        phoneNumber = log.phoneNumber,
                        duration = log.duration.toInt(),
                        callType = when (log.callType.uppercase()) {
                            "INCOMING" -> "incoming"
                            "OUTGOING" -> "outgoing"
                            "MISSED" -> "missed"
                            else -> "missed"
                        },
                        timestamp = dateFormat.format(Date(log.timestamp)),
                        isKnownContact = log.isKnownContact,
                        contactName = log.contactName
                    )
                }
                
                // Convert SMS messages to API format
                val apiSmsLogs = unsyncedSMS.map { sms ->
                    SMSEntryAPI(
                        senderNumber = sms.phoneNumber, // Railway expects 'senderNumber'
                        messageCount = 1,
                        messageType = when (sms.messageType.uppercase()) {
                            "RECEIVED" -> "received"
                            "SENT" -> "sent"
                            else -> "received"
                        },
                        timestamp = dateFormat.format(Date(sms.timestamp)),
                        isKnownContact = true,
                        contactName = null,
                        hasLink = false
                    )
                }
                
                // Send unified request to Railway backend
                val activityRequest = ActivitySyncRequest(
                    callLogs = apiCallLogs,
                    smsLogs = apiSmsLogs
                )
                
                val response = apiService.syncActivity(activityRequest)
                
                if (response.isSuccessful) {
                    // Mark call logs as synced
                    if (unsyncedCallLogs.isNotEmpty()) {
                        dao.markCallLogsSynced(unsyncedCallLogs.map { it.id })
                    }
                    
                    // Mark SMS messages as synced
                    if (unsyncedSMS.isNotEmpty()) {
                        dao.markSMSMessagesSynced(unsyncedSMS.map { it.id })
                    }
                    
                    android.util.Log.i("MonitoringRepository", "Successfully synced ${apiCallLogs.size} call logs and ${apiSmsLogs.size} SMS messages")
                } else {
                    android.util.Log.e("MonitoringRepository", "Sync failed with response: ${response.code()} - ${response.message()}")
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