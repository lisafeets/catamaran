package com.catamaran.familysafety.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.catamaran.familysafety.CatamaranApplication
import com.catamaran.familysafety.monitor.CallLogMonitor
import com.catamaran.familysafety.monitor.SMSMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val application = applicationContext as CatamaranApplication
            val repository = application.repository
            val preferenceManager = application.preferenceManager

            // Check if monitoring is enabled
            if (!preferenceManager.isMonitoringEnabled()) {
                return@withContext Result.success()
            }

            // Collect call log data
            val callLogMonitor = CallLogMonitor(applicationContext)
            val callLogData = callLogMonitor.getRecentCalls()

            // Collect SMS data
            val smsMonitor = SMSMonitor(applicationContext)
            val smsData = smsMonitor.getRecentMessages()
            android.util.Log.d("DataSyncWorker", "Collected ${smsData.size} SMS messages for sync")

            // Sync data with backend
            repository.syncCallLogs(callLogData)
            repository.syncSMSData(smsData)
            android.util.Log.d("DataSyncWorker", "Sync completed successfully")

            // Update last sync time
            preferenceManager.setLastSyncTime(System.currentTimeMillis())

            Result.success()
        } catch (e: Exception) {
            // Log error and retry
            android.util.Log.e("DataSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }
} 