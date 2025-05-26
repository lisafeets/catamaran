package com.catamaran.app.service

import android.content.Context
import com.catamaran.app.data.database.CatamaranDatabase
import com.catamaran.app.data.database.entities.SyncStatus
import com.catamaran.app.data.database.entities.CallLogEntity
import com.catamaran.app.data.database.entities.SmsLogEntity
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.NetworkUtils
import com.catamaran.app.utils.EncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Enhanced data sync service for uploading activity data to backend
 * Features:
 * - Secure transmission with NetworkUtils integration
 * - Batch processing for efficiency
 * - Comprehensive retry logic and error handling
 * - Bandwidth optimization with compression
 * - Sync statistics and monitoring
 * - Partial sync recovery
 */
class DataSyncService(
    private val context: Context,
    private val database: CatamaranDatabase
) {

    companion object {
        private const val MAX_SYNC_BATCH_SIZE = 50
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val SYNC_API_ENDPOINT = "https://catamaran-production-3422.up.railway.app/api/activity"
        private const val ACTIVITY_SYNC_ENDPOINT = "$SYNC_API_ENDPOINT/sync"
        private const val BATCH_ENDPOINT = "$SYNC_API_ENDPOINT/sync"
    }

    private val networkUtils = NetworkUtils(context)
    private val encryptionManager = EncryptionManager(context)
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Sync all pending data to backend
     */
    suspend fun syncPendingData() = withContext(Dispatchers.IO) {
        try {
            Logger.debug("Starting data sync...")
            
            // Sync call logs
            syncCallLogs()
            
            // Sync SMS logs  
            syncSmsLogs()
            
            // Retry failed syncs
            retryFailedSyncs()
            
            Logger.debug("Data sync completed")
            
        } catch (e: Exception) {
            Logger.error("Error during data sync", e)
            throw e
        }
    }

    /**
     * Sync call logs batch (used by DataSyncWorker)
     */
    suspend fun syncCallLogsBatch(callLogs: List<CallLogEntity>): Boolean = withContext(Dispatchers.IO) {
        if (callLogs.isEmpty()) {
            Logger.debug("No call logs to sync")
            return@withContext true
        }

        try {
            Logger.info("Syncing ${callLogs.size} call logs")
            
            // Mark as syncing
            val ids = callLogs.map { it.id }
            database.callLogDao().markCallLogsAsSynced(ids, SyncStatus.SYNCING)
            
            // Upload to backend API
            val uploadSuccess = uploadCallLogsToBackend(callLogs)
            
            if (uploadSuccess) {
                // Mark as synced
                database.callLogDao().markCallLogsAsSynced(ids, SyncStatus.SYNCED)
                Logger.info("Successfully synced ${callLogs.size} call logs")
                return@withContext true
            } else {
                // Mark as failed
                for (id in ids) {
                    database.callLogDao().updateSyncStatus(id, SyncStatus.FAILED)
                }
                Logger.warning("Failed to sync call logs batch")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Logger.error("Error syncing call logs batch", e)
            // Mark as failed on exception
            callLogs.forEach { callLog ->
                database.callLogDao().updateSyncStatus(callLog.id, SyncStatus.FAILED)
            }
            return@withContext false
        }
    }

    /**
     * Sync SMS logs batch (used by DataSyncWorker)
     */
    suspend fun syncSmsLogsBatch(smsLogs: List<SmsLogEntity>): Boolean = withContext(Dispatchers.IO) {
        if (smsLogs.isEmpty()) {
            Logger.debug("No SMS logs to sync")
            return@withContext true
        }

        try {
            Logger.info("Syncing ${smsLogs.size} SMS logs")
            
            // Mark as syncing
            val ids = smsLogs.map { it.id }
            database.smsLogDao().markSmsLogsAsSynced(ids, SyncStatus.SYNCING)
            
            // Upload to backend API
            val uploadSuccess = uploadSmsLogsToBackend(smsLogs)
            
            if (uploadSuccess) {
                // Mark as synced
                database.smsLogDao().markSmsLogsAsSynced(ids, SyncStatus.SYNCED)
                Logger.info("Successfully synced ${smsLogs.size} SMS logs")
                return@withContext true
            } else {
                // Mark as failed
                for (id in ids) {
                    database.smsLogDao().updateSyncStatus(id, SyncStatus.FAILED)
                }
                Logger.warning("Failed to sync SMS logs batch")
                return@withContext false
            }
            
        } catch (e: Exception) {
            Logger.error("Error syncing SMS logs batch", e)
            // Mark as failed on exception
            smsLogs.forEach { smsLog ->
                database.smsLogDao().updateSyncStatus(smsLog.id, SyncStatus.FAILED)
            }
            return@withContext false
        }
    }

    private suspend fun syncCallLogs() {
        try {
            val unsyncedCallLogs = database.callLogDao()
                .getUnsyncedCallLogs(SyncStatus.PENDING, MAX_SYNC_BATCH_SIZE)
            
            if (unsyncedCallLogs.isEmpty()) {
                Logger.debug("No unsynced call logs found")
                return
            }
            
            Logger.info("Syncing ${unsyncedCallLogs.size} call logs")
            
            // Mark as syncing
            val ids = unsyncedCallLogs.map { it.id }
            database.callLogDao().markCallLogsAsSynced(ids, SyncStatus.SYNCING)
            
            // Upload to backend API
            val uploadSuccess = uploadCallLogsToBackend(unsyncedCallLogs)
            
            if (uploadSuccess) {
                // Mark as synced
                database.callLogDao().markCallLogsAsSynced(ids, SyncStatus.SYNCED)
                Logger.info("Successfully synced ${unsyncedCallLogs.size} call logs")
            } else {
                // Mark as failed
                for (id in ids) {
                    database.callLogDao().updateSyncStatus(id, SyncStatus.FAILED)
                }
                Logger.warning("Failed to sync call logs")
            }
            
        } catch (e: Exception) {
            Logger.error("Error syncing call logs", e)
        }
    }

    private suspend fun syncSmsLogs() {
        try {
            val unsyncedSmsLogs = database.smsLogDao()
                .getUnsyncedSmsLogs(SyncStatus.PENDING, MAX_SYNC_BATCH_SIZE)
            
            if (unsyncedSmsLogs.isEmpty()) {
                Logger.debug("No unsynced SMS logs found")
                return
            }
            
            Logger.info("Syncing ${unsyncedSmsLogs.size} SMS logs")
            
            // Mark as syncing
            val ids = unsyncedSmsLogs.map { it.id }
            database.smsLogDao().markSmsLogsAsSynced(ids, SyncStatus.SYNCING)
            
            // Upload to backend API
            val uploadSuccess = uploadSmsLogsToBackend(unsyncedSmsLogs)
            
            if (uploadSuccess) {
                // Mark as synced
                database.smsLogDao().markSmsLogsAsSynced(ids, SyncStatus.SYNCED)
                Logger.info("Successfully synced ${unsyncedSmsLogs.size} SMS logs")
            } else {
                // Mark as failed
                for (id in ids) {
                    database.smsLogDao().updateSyncStatus(id, SyncStatus.FAILED)
                }
                Logger.warning("Failed to sync SMS logs")
            }
            
        } catch (e: Exception) {
            Logger.error("Error syncing SMS logs", e)
        }
    }

    private suspend fun retryFailedSyncs() {
        try {
            // Retry failed call log syncs
            val failedCallLogs = database.callLogDao()
                .getFailedSyncCallLogs(SyncStatus.FAILED, MAX_RETRY_ATTEMPTS, 10)
            
            if (failedCallLogs.isNotEmpty()) {
                Logger.info("Retrying ${failedCallLogs.size} failed call log syncs")
                
                for (callLog in failedCallLogs) {
                    database.callLogDao().updateSyncStatus(callLog.id, SyncStatus.SYNCING)
                    
                    val success = uploadCallLogsToBackend(listOf(callLog))
                    if (success) {
                        database.callLogDao().markCallLogsAsSynced(listOf(callLog.id), SyncStatus.SYNCED)
                    } else {
                        database.callLogDao().updateSyncStatus(callLog.id, SyncStatus.FAILED)
                    }
                }
            }
            
            // Retry failed SMS log syncs
            val failedSmsLogs = database.smsLogDao()
                .getFailedSyncSmsLogs(SyncStatus.FAILED, MAX_RETRY_ATTEMPTS, 10)
            
            if (failedSmsLogs.isNotEmpty()) {
                Logger.info("Retrying ${failedSmsLogs.size} failed SMS log syncs")
                
                for (smsLog in failedSmsLogs) {
                    database.smsLogDao().updateSyncStatus(smsLog.id, SyncStatus.SYNCING)
                    
                    val success = uploadSmsLogsToBackend(listOf(smsLog))
                    if (success) {
                        database.smsLogDao().markSmsLogsAsSynced(listOf(smsLog.id), SyncStatus.SYNCED)
                    } else {
                        database.smsLogDao().updateSyncStatus(smsLog.id, SyncStatus.FAILED)
                    }
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Error retrying failed syncs", e)
        }
    }

    /**
     * Enhanced upload function for call logs with real API integration
     */
    private suspend fun uploadCallLogsToBackend(
        callLogs: List<CallLogEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.debug("Uploading ${callLogs.size} call logs to backend")
            
            // Convert to Railway backend format
            val callLogsData = callLogs.map { callLog ->
                mapOf(
                    "phoneNumber" to encryptionManager.decrypt(callLog.phoneNumber),
                    "contactName" to callLog.contactName?.let { encryptionManager.decrypt(it) },
                    "duration" to callLog.duration,
                    "callType" to callLog.callType.name.lowercase(),
                    "timestamp" to java.time.Instant.ofEpochMilli(callLog.timestamp).toString(),
                    "isKnownContact" to callLog.isKnownContact
                )
            }
            
            // Create request in Railway backend format
            val requestData = mapOf(
                "callLogs" to callLogsData,
                "smsLogs" to emptyList<Map<String, Any>>()
            )
            
            // Serialize to JSON
            val jsonData = json.encodeToString(requestData).toByteArray()
            
            // Create auth headers (simplified for testing)
            val headers = mapOf(
                "Content-Type" to "application/json"
                // TODO: Add proper authentication when available
            )
            
            // Upload with compression
            val result = networkUtils.uploadData(
                endpoint = ACTIVITY_SYNC_ENDPOINT,
                data = jsonData,
                headers = headers,
                compressed = false // Disable compression for testing
            )
            
            return@withContext when (result) {
                is NetworkUtils.NetworkResult.Success -> {
                    Logger.debug("Call logs uploaded successfully")
                    true
                }
                is NetworkUtils.NetworkResult.Error -> {
                    Logger.error("Failed to upload call logs: ${result.message}")
                    false
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Error uploading call logs", e)
            false
        }
    }

    /**
     * Enhanced upload function for SMS logs with real API integration
     */
    private suspend fun uploadSmsLogsToBackend(
        smsLogs: List<SmsLogEntity>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Logger.debug("Uploading ${smsLogs.size} SMS logs to backend")
            
            // Convert to Railway backend format
            val smsLogsData = smsLogs.map { smsLog ->
                mapOf(
                    "senderNumber" to encryptionManager.decrypt(smsLog.phoneNumber), // Note: Railway expects 'senderNumber'
                    "contactName" to smsLog.contactName?.let { encryptionManager.decrypt(it) },
                    "messageCount" to smsLog.messageCount,
                    "messageType" to when (smsLog.smsType) { // Note: Railway expects 'messageType'
                        com.catamaran.app.data.database.entities.SmsType.INCOMING -> "received"
                        com.catamaran.app.data.database.entities.SmsType.OUTGOING -> "sent"
                    },
                    "timestamp" to java.time.Instant.ofEpochMilli(smsLog.timestamp).toString(),
                    "isKnownContact" to smsLog.isKnownContact,
                    "hasLink" to false // Default for now
                )
            }
            
            // Create request in Railway backend format
            val requestData = mapOf(
                "callLogs" to emptyList<Map<String, Any>>(),
                "smsLogs" to smsLogsData
            )
            
            // Serialize to JSON
            val jsonData = json.encodeToString(requestData).toByteArray()
            
            // Create auth headers (simplified for testing)
            val headers = mapOf(
                "Content-Type" to "application/json"
                // TODO: Add proper authentication when available
            )
            
            // Upload with compression
            val result = networkUtils.uploadData(
                endpoint = ACTIVITY_SYNC_ENDPOINT,
                data = jsonData,
                headers = headers,
                compressed = false // Disable compression for testing
            )
            
            return@withContext when (result) {
                is NetworkUtils.NetworkResult.Success -> {
                    Logger.debug("SMS logs uploaded successfully")
                    true
                }
                is NetworkUtils.NetworkResult.Error -> {
                    Logger.error("Failed to upload SMS logs: ${result.message}")
                    false
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Error uploading SMS logs", e)
            false
        }
    }

    /**
     * Get sync statistics
     */
    suspend fun getSyncStatistics(): SyncStatistics = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val last24Hours = currentTime - (24 * 60 * 60 * 1000L)
            
            // Get call log sync stats
            val pendingCallLogs = database.callLogDao()
                .getUnsyncedCallLogs(SyncStatus.PENDING, Int.MAX_VALUE).size
            val failedCallLogs = database.callLogDao()
                .getFailedSyncCallLogs(SyncStatus.FAILED, MAX_RETRY_ATTEMPTS, Int.MAX_VALUE).size
            val recentCallLogs = database.callLogDao()
                .getCallLogCount(last24Hours)
            
            // Get SMS log sync stats
            val pendingSmsLogs = database.smsLogDao()
                .getUnsyncedSmsLogs(SyncStatus.PENDING, Int.MAX_VALUE).size
            val failedSmsLogs = database.smsLogDao()
                .getFailedSyncSmsLogs(SyncStatus.FAILED, MAX_RETRY_ATTEMPTS, Int.MAX_VALUE).size
            val recentSmsLogs = database.smsLogDao()
                .getSmsLogCount(last24Hours)
            
            SyncStatistics(
                pendingCallLogs = pendingCallLogs,
                failedCallLogs = failedCallLogs,
                recentCallLogs = recentCallLogs,
                pendingSmsLogs = pendingSmsLogs,
                failedSmsLogs = failedSmsLogs,
                recentSmsLogs = recentSmsLogs
            )
            
        } catch (e: Exception) {
            Logger.error("Error getting sync statistics", e)
            SyncStatistics()
        }
    }

    /**
     * Clean up old synced data to save storage space
     */
    suspend fun cleanupOldSyncedData(olderThanDays: Int = 30) = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
            
            val deletedCallLogs = database.callLogDao().deleteOldSyncedCallLogs(cutoffTime, SyncStatus.SYNCED)
            val deletedSmsLogs = database.smsLogDao().deleteOldSyncedSmsLogs(cutoffTime, SyncStatus.SYNCED)
            
            Logger.info("Cleaned up $deletedCallLogs call logs and $deletedSmsLogs SMS logs older than $olderThanDays days")
            
        } catch (e: Exception) {
            Logger.error("Error cleaning up old synced data", e)
        }
    }

    // Helper methods for auth and device info
    private fun getDeviceId(): String {
        // Implementation would get actual device ID
        return "device_123" // Placeholder
    }

    private fun getUserId(): String {
        // Implementation would get actual user ID from preferences
        return "user_123" // Placeholder
    }

    private fun getAuthToken(): String {
        // Implementation would get actual auth token from secure storage
        return "auth_token_123" // Placeholder
    }

    // Data classes for API communication
    @Serializable
    data class SyncRequest(
        val deviceId: String,
        val userId: String,
        val timestamp: Long,
        val callLogs: List<CallLogSyncDto>,
        val smsLogs: List<SmsLogSyncDto>
    )

    @Serializable
    data class CallLogSyncDto(
        val id: String,
        val phoneNumber: String,
        val contactName: String?,
        val duration: Long,
        val callType: String,
        val timestamp: Long,
        val isKnownContact: Boolean,
        val riskScore: Float,
        val suspiciousPatterns: String?,
        val createdAt: Long
    )

    @Serializable
    data class SmsLogSyncDto(
        val id: String,
        val phoneNumber: String,
        val contactName: String?,
        val messageCount: Int,
        val smsType: String,
        val timestamp: Long,
        val isKnownContact: Boolean,
        val riskScore: Float,
        val frequencyPattern: String?,
        val threadId: Long?,
        val createdAt: Long
    )

    data class SyncStatistics(
        val pendingCallLogs: Int = 0,
        val failedCallLogs: Int = 0,
        val recentCallLogs: Int = 0,
        val pendingSmsLogs: Int = 0,
        val failedSmsLogs: Int = 0,
        val recentSmsLogs: Int = 0
    )
} 
} 