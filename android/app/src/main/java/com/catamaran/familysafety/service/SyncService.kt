package com.catamaran.familysafety.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.work.*
import com.catamaran.familysafety.network.ApiClient
import com.catamaran.familysafety.utils.DataCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncService : Service() {
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Use WorkManager for reliable background sync
        scheduleSyncWork()
        stopSelf()
        return START_NOT_STICKY
    }
    
    private fun scheduleSyncWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        val syncWork = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "catamaran_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        )
        
        Log.d("SyncService", "Periodic sync work scheduled")
    }
    
    companion object {
        fun scheduleSync(context: Context) {
            val intent = Intent(context, SyncService::class.java)
            context.startService(intent)
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    
    private val dataCollector = DataCollector(applicationContext)
    private val apiClient = ApiClient(applicationContext)
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SyncWorker", "Starting sync work")
                
                if (!apiClient.isLoggedIn()) {
                    Log.w("SyncWorker", "User not logged in, skipping sync")
                    return@withContext Result.success()
                }
                
                // Get unsynced data
                val (unsyncedCalls, unsyncedSms) = dataCollector.getUnsyncedData()
                
                if (unsyncedCalls.isEmpty() && unsyncedSms.isEmpty()) {
                    Log.d("SyncWorker", "No unsynced data to upload")
                    return@withContext Result.success()
                }
                
                Log.d("SyncWorker", "Syncing ${unsyncedCalls.size} calls and ${unsyncedSms.size} SMS")
                
                // Upload to backend
                val syncResult = apiClient.syncActivity(unsyncedCalls, unsyncedSms)
                
                if (syncResult.isSuccess) {
                    // Mark as synced
                    val callIds = unsyncedCalls.map { it.id }
                    val smsIds = unsyncedSms.map { it.id }
                    dataCollector.markAsSynced(callIds, smsIds)
                    
                    Log.d("SyncWorker", "Sync completed successfully")
                    Result.success()
                } else {
                    Log.e("SyncWorker", "Sync failed: ${syncResult.exceptionOrNull()?.message}")
                    Result.retry()
                }
                
            } catch (e: Exception) {
                Log.e("SyncWorker", "Sync work error", e)
                Result.retry()
            }
        }
    }
} 