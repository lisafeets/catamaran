package com.catamaran.app.service.workers

import android.content.Context
import androidx.work.*
import com.catamaran.app.data.database.CatamaranDatabase
import com.catamaran.app.data.database.entities.SyncStatus
import com.catamaran.app.service.DataSyncService
import com.catamaran.app.utils.EncryptionManager
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Production-ready WorkManager worker for reliable data synchronization
 * Features:
 * - Network-aware data synchronization
 * - Exponential backoff retry strategy
 * - Batch processing for efficiency
 * - Secure data transmission with encryption
 * - Comprehensive error handling and recovery
 * - Progress tracking and statistics
 */
class DataSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val MAX_SYNC_BATCH_SIZE = 100
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_DELAY_SECONDS = 30L
        
        // Input data keys
        const val KEY_SYNC_TYPE = "sync_type"
        const val KEY_FORCE_SYNC = "force_sync"
        const val KEY_PRIORITY = "priority"
        
        // Output data keys
        const val KEY_SYNCED_CALLS = "synced_calls"
        const val KEY_SYNCED_SMS = "synced_sms"
        const val KEY_FAILED_ITEMS = "failed_items"
        const val KEY_ERROR_MESSAGE = "error_message"
        
        // Sync types
        const val SYNC_TYPE_FULL = "full"
        const val SYNC_TYPE_INCREMENTAL = "incremental"
        const val SYNC_TYPE_RETRY_FAILED = "retry_failed"
        const val SYNC_TYPE_HIGH_PRIORITY = "high_priority"
        
        /**
         * Create work request for immediate sync
         */
        fun createImmediateSyncRequest(
            syncType: String = SYNC_TYPE_INCREMENTAL,
            priority: Boolean = false
        ): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // Allow sync even when battery is low for important data
                .build()
            
            val inputData = Data.Builder()
                .putString(KEY_SYNC_TYPE, syncType)
                .putBoolean(KEY_FORCE_SYNC, true)
                .putBoolean(KEY_PRIORITY, priority)
                .build()
            
            return OneTimeWorkRequestBuilder<DataSyncWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_DELAY_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag("catamaran_sync")
                .addTag("immediate_sync")
                .apply {
                    if (priority) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()
        }
        
        /**
         * Create periodic sync work request
         */
        fun createPeriodicSyncRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()
            
            return PeriodicWorkRequestBuilder<DataSyncWorker>(
                15, TimeUnit.MINUTES, // Repeat interval
                5, TimeUnit.MINUTES   // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    INITIAL_BACKOFF_DELAY_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag("catamaran_sync")
                .addTag("periodic_sync")
                .build()
        }
    }

    private lateinit var database: CatamaranDatabase
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var dataSyncService: DataSyncService
    private lateinit var networkUtils: NetworkUtils

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Initialize components
            initializeComponents()
            
            // Check network connectivity
            if (!networkUtils.isConnectedToInternet()) {
                Logger.warning("No internet connection available for sync")
                return@withContext Result.retry()
            }
            
            // Get sync parameters
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_INCREMENTAL
            val forceSync = inputData.getBoolean(KEY_FORCE_SYNC, false)
            val priority = inputData.getBoolean(KEY_PRIORITY, false)
            
            Logger.info("Starting data sync - Type: $syncType, Force: $forceSync, Priority: $priority")
            
            // Set progress to indicate sync started
            setProgress(createProgressData(0, 0, "Starting sync..."))
            
            // Perform sync based on type
            val syncResult = when (syncType) {
                SYNC_TYPE_FULL -> performFullSync()
                SYNC_TYPE_INCREMENTAL -> performIncrementalSync()
                SYNC_TYPE_RETRY_FAILED -> performRetryFailedSync()
                SYNC_TYPE_HIGH_PRIORITY -> performHighPrioritySync()
                else -> performIncrementalSync()
            }
            
            // Create output data
            val outputData = Data.Builder()
                .putInt(KEY_SYNCED_CALLS, syncResult.syncedCalls)
                .putInt(KEY_SYNCED_SMS, syncResult.syncedSms)
                .putInt(KEY_FAILED_ITEMS, syncResult.failedItems)
                .putString(KEY_ERROR_MESSAGE, syncResult.errorMessage)
                .build()
            
            // Determine result based on sync outcome
            when {
                syncResult.totalSuccess -> {
                    Logger.info("Data sync completed successfully - Calls: ${syncResult.syncedCalls}, SMS: ${syncResult.syncedSms}")
                    Result.success(outputData)
                }
                syncResult.partialSuccess -> {
                    Logger.warning("Data sync partially successful - Some items failed")
                    Result.success(outputData) // Still consider success if some data was synced
                }
                syncResult.shouldRetry -> {
                    Logger.warning("Data sync failed but should retry - ${syncResult.errorMessage}")
                    Result.retry()
                }
                else -> {
                    Logger.error("Data sync failed permanently - ${syncResult.errorMessage}")
                    Result.failure(outputData)
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Data sync worker encountered unexpected error", e)
            
            val errorData = Data.Builder()
                .putString(KEY_ERROR_MESSAGE, e.message ?: "Unknown error")
                .build()
            
            // Retry on recoverable errors, fail on permanent errors
            if (isRecoverableError(e)) {
                Result.retry()
            } else {
                Result.failure(errorData)
            }
        }
    }

    private fun initializeComponents() {
        encryptionManager = EncryptionManager(applicationContext)
        database = CatamaranDatabase.getDatabase(applicationContext, encryptionManager)
        dataSyncService = DataSyncService(applicationContext, database)
        networkUtils = NetworkUtils(applicationContext)
    }

    /**
     * Perform full synchronization of all pending data
     */
    private suspend fun performFullSync(): SyncResult = withContext(Dispatchers.IO) {
        var syncedCalls = 0
        var syncedSms = 0
        var failedItems = 0
        var errorMessage: String? = null
        
        try {
            setProgress(createProgressData(syncedCalls, syncedSms, "Syncing call logs..."))
            
            // Sync all pending call logs
            val callSyncResult = syncCallLogsBatch()
            syncedCalls = callSyncResult.syncedCount
            failedItems += callSyncResult.failedCount
            
            setProgress(createProgressData(syncedCalls, syncedSms, "Syncing SMS logs..."))
            
            // Sync all pending SMS logs
            val smsSyncResult = syncSmsLogsBatch()
            syncedSms = smsSyncResult.syncedCount
            failedItems += smsSyncResult.failedCount
            
            // Retry failed items once
            if (failedItems > 0) {
                setProgress(createProgressData(syncedCalls, syncedSms, "Retrying failed items..."))
                val retryResult = retryFailedItems()
                syncedCalls += retryResult.retriedCalls
                syncedSms += retryResult.retriedSms
                failedItems = retryResult.stillFailed
            }
            
        } catch (e: Exception) {
            Logger.error("Error during full sync", e)
            errorMessage = e.message
        }
        
        SyncResult(
            syncedCalls = syncedCalls,
            syncedSms = syncedSms,
            failedItems = failedItems,
            errorMessage = errorMessage,
            totalSuccess = failedItems == 0 && errorMessage == null,
            partialSuccess = (syncedCalls > 0 || syncedSms > 0) && errorMessage == null,
            shouldRetry = errorMessage != null && isRetryableError(errorMessage)
        )
    }

    /**
     * Perform incremental sync of new data only
     */
    private suspend fun performIncrementalSync(): SyncResult = withContext(Dispatchers.IO) {
        var syncedCalls = 0
        var syncedSms = 0
        var failedItems = 0
        var errorMessage: String? = null
        
        try {
            // Get recent unsync data (last 24 hours)
            val since = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            
            setProgress(createProgressData(syncedCalls, syncedSms, "Syncing recent call logs..."))
            
            // Sync recent call logs
            val callSyncResult = syncRecentCallLogs(since)
            syncedCalls = callSyncResult.syncedCount
            failedItems += callSyncResult.failedCount
            
            setProgress(createProgressData(syncedCalls, syncedSms, "Syncing recent SMS logs..."))
            
            // Sync recent SMS logs
            val smsSyncResult = syncRecentSmsLogs(since)
            syncedSms = smsSyncResult.syncedCount
            failedItems += smsSyncResult.failedCount
            
        } catch (e: Exception) {
            Logger.error("Error during incremental sync", e)
            errorMessage = e.message
        }
        
        SyncResult(
            syncedCalls = syncedCalls,
            syncedSms = syncedSms,
            failedItems = failedItems,
            errorMessage = errorMessage,
            totalSuccess = failedItems == 0 && errorMessage == null,
            partialSuccess = (syncedCalls > 0 || syncedSms > 0) && errorMessage == null,
            shouldRetry = errorMessage != null && isRetryableError(errorMessage)
        )
    }

    /**
     * Retry previously failed sync attempts
     */
    private suspend fun performRetryFailedSync(): SyncResult = withContext(Dispatchers.IO) {
        val retryResult = retryFailedItems()
        
        SyncResult(
            syncedCalls = retryResult.retriedCalls,
            syncedSms = retryResult.retriedSms,
            failedItems = retryResult.stillFailed,
            errorMessage = null,
            totalSuccess = retryResult.stillFailed == 0,
            partialSuccess = retryResult.retriedCalls > 0 || retryResult.retriedSms > 0,
            shouldRetry = retryResult.stillFailed > 0
        )
    }

    /**
     * Sync high-priority items (high risk score)
     */
    private suspend fun performHighPrioritySync(): SyncResult = withContext(Dispatchers.IO) {
        var syncedCalls = 0
        var syncedSms = 0
        var failedItems = 0
        var errorMessage: String? = null
        
        try {
            setProgress(createProgressData(syncedCalls, syncedSms, "Syncing high-priority data..."))
            
            // Sync high-risk call logs first
            val highRiskCalls = database.callLogDao().getHighRiskUnsyncedCallLogs(7, 50) // Risk score > 7
            if (highRiskCalls.isNotEmpty()) {
                val callResult = dataSyncService.syncCallLogsBatch(highRiskCalls)
                syncedCalls = if (callResult) highRiskCalls.size else 0
                if (!callResult) failedItems += highRiskCalls.size
            }
            
            // Sync high-risk SMS logs
            val highRiskSms = database.smsLogDao().getHighRiskUnsyncedSmsLogs(7, 50) // Risk score > 7
            if (highRiskSms.isNotEmpty()) {
                val smsResult = dataSyncService.syncSmsLogsBatch(highRiskSms)
                syncedSms = if (smsResult) highRiskSms.size else 0
                if (!smsResult) failedItems += highRiskSms.size
            }
            
        } catch (e: Exception) {
            Logger.error("Error during high-priority sync", e)
            errorMessage = e.message
        }
        
        SyncResult(
            syncedCalls = syncedCalls,
            syncedSms = syncedSms,
            failedItems = failedItems,
            errorMessage = errorMessage,
            totalSuccess = failedItems == 0 && errorMessage == null,
            partialSuccess = (syncedCalls > 0 || syncedSms > 0) && errorMessage == null,
            shouldRetry = errorMessage != null && isRetryableError(errorMessage)
        )
    }

    /**
     * Sync call logs in batches
     */
    private suspend fun syncCallLogsBatch(): BatchSyncResult = withContext(Dispatchers.IO) {
        var syncedCount = 0
        var failedCount = 0
        
        try {
            var offset = 0
            var hasMore = true
            
            while (hasMore) {
                val batch = database.callLogDao().getUnsyncedCallLogs(SyncStatus.PENDING, MAX_SYNC_BATCH_SIZE, offset)
                
                if (batch.isEmpty()) {
                    hasMore = false
                } else {
                    val success = dataSyncService.syncCallLogsBatch(batch)
                    if (success) {
                        syncedCount += batch.size
                    } else {
                        failedCount += batch.size
                    }
                    
                    offset += MAX_SYNC_BATCH_SIZE
                    
                    // Update progress
                    setProgress(createProgressData(syncedCount, 0, "Synced $syncedCount call logs..."))
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Error syncing call logs batch", e)
            throw e
        }
        
        BatchSyncResult(syncedCount, failedCount)
    }

    /**
     * Sync SMS logs in batches
     */
    private suspend fun syncSmsLogsBatch(): BatchSyncResult = withContext(Dispatchers.IO) {
        var syncedCount = 0
        var failedCount = 0
        
        try {
            var offset = 0
            var hasMore = true
            
            while (hasMore) {
                val batch = database.smsLogDao().getUnsyncedSmsLogs(SyncStatus.PENDING, MAX_SYNC_BATCH_SIZE, offset)
                
                if (batch.isEmpty()) {
                    hasMore = false
                } else {
                    val success = dataSyncService.syncSmsLogsBatch(batch)
                    if (success) {
                        syncedCount += batch.size
                    } else {
                        failedCount += batch.size
                    }
                    
                    offset += MAX_SYNC_BATCH_SIZE
                    
                    // Update progress
                    setProgress(createProgressData(0, syncedCount, "Synced $syncedCount SMS logs..."))
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Error syncing SMS logs batch", e)
            throw e
        }
        
        BatchSyncResult(syncedCount, failedCount)
    }

    /**
     * Sync recent call logs since timestamp
     */
    private suspend fun syncRecentCallLogs(since: Long): BatchSyncResult = withContext(Dispatchers.IO) {
        val recentCalls = database.callLogDao().getUnsyncedCallLogsSince(since, SyncStatus.PENDING, MAX_SYNC_BATCH_SIZE)
        
        return if (recentCalls.isNotEmpty()) {
            val success = dataSyncService.syncCallLogsBatch(recentCalls)
            if (success) {
                BatchSyncResult(recentCalls.size, 0)
            } else {
                BatchSyncResult(0, recentCalls.size)
            }
        } else {
            BatchSyncResult(0, 0)
        }
    }

    /**
     * Sync recent SMS logs since timestamp
     */
    private suspend fun syncRecentSmsLogs(since: Long): BatchSyncResult = withContext(Dispatchers.IO) {
        val recentSms = database.smsLogDao().getUnsyncedSmsLogsSince(since, SyncStatus.PENDING, MAX_SYNC_BATCH_SIZE)
        
        return if (recentSms.isNotEmpty()) {
            val success = dataSyncService.syncSmsLogsBatch(recentSms)
            if (success) {
                BatchSyncResult(recentSms.size, 0)
            } else {
                BatchSyncResult(0, recentSms.size)
            }
        } else {
            BatchSyncResult(0, 0)
        }
    }

    /**
     * Retry previously failed sync items
     */
    private suspend fun retryFailedItems(): RetryResult = withContext(Dispatchers.IO) {
        var retriedCalls = 0
        var retriedSms = 0
        var stillFailed = 0
        
        try {
            // Retry failed call logs
            val failedCalls = database.callLogDao().getFailedSyncCallLogs(SyncStatus.FAILED, MAX_RETRY_ATTEMPTS, 20)
            for (callLog in failedCalls) {
                val success = dataSyncService.syncSingleCallLog(callLog)
                if (success) {
                    retriedCalls++
                } else {
                    stillFailed++
                }
            }
            
            // Retry failed SMS logs
            val failedSms = database.smsLogDao().getFailedSyncSmsLogs(SyncStatus.FAILED, MAX_RETRY_ATTEMPTS, 20)
            for (smsLog in failedSms) {
                val success = dataSyncService.syncSingleSmsLog(smsLog)
                if (success) {
                    retriedSms++
                } else {
                    stillFailed++
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Error retrying failed items", e)
        }
        
        RetryResult(retriedCalls, retriedSms, stillFailed)
    }

    /**
     * Create progress data for UI updates
     */
    private fun createProgressData(syncedCalls: Int, syncedSms: Int, message: String): Data {
        return Data.Builder()
            .putInt(KEY_SYNCED_CALLS, syncedCalls)
            .putInt(KEY_SYNCED_SMS, syncedSms)
            .putString("progress_message", message)
            .build()
    }

    /**
     * Check if error is recoverable and should trigger retry
     */
    private fun isRecoverableError(error: Throwable): Boolean {
        return when (error) {
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.net.UnknownHostException,
            is javax.net.ssl.SSLException -> true
            else -> false
        }
    }

    /**
     * Check if error message indicates a retryable condition
     */
    private fun isRetryableError(errorMessage: String): Boolean {
        val retryableErrors = listOf(
            "timeout", "connection", "network", "unavailable", "busy", "rate limit"
        )
        return retryableErrors.any { errorMessage.lowercase().contains(it) }
    }

    // Data classes for results
    data class SyncResult(
        val syncedCalls: Int,
        val syncedSms: Int,
        val failedItems: Int,
        val errorMessage: String?,
        val totalSuccess: Boolean,
        val partialSuccess: Boolean,
        val shouldRetry: Boolean
    )

    data class BatchSyncResult(
        val syncedCount: Int,
        val failedCount: Int
    )

    data class RetryResult(
        val retriedCalls: Int,
        val retriedSms: Int,
        val stillFailed: Int
    )
} 