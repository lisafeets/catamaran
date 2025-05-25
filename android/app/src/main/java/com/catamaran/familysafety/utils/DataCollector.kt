package com.catamaran.familysafety.utils

import android.content.Context
import android.util.Log
import com.catamaran.familysafety.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataCollector(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val callLogReader = CallLogReader(context)
    private val smsCounter = SmsCounter(context)
    private val preferences = context.getSharedPreferences("catamaran_prefs", Context.MODE_PRIVATE)
    
    suspend fun collectNewActivity() {
        withContext(Dispatchers.IO) {
            try {
                val lastSyncTime = getLastSyncTime()
                Log.d("DataCollector", "Collecting activity since: $lastSyncTime")
                
                // Collect new call logs
                val newCallLogs = callLogReader.getCallLogsSince(lastSyncTime)
                Log.d("DataCollector", "Found ${newCallLogs.size} new call logs")
                
                newCallLogs.forEach { callLog ->
                    database.callLogDao().insertCallLog(callLog)
                }
                
                // Collect new SMS logs
                val newSmsLogs = smsCounter.getSmsLogsSince(lastSyncTime)
                Log.d("DataCollector", "Found ${newSmsLogs.size} new SMS logs")
                
                newSmsLogs.forEach { smsLog ->
                    database.smsLogDao().insertSmsLog(smsLog)
                }
                
                // Update last sync time
                updateLastSyncTime()
                
                // Clean up old data (keep last 30 days)
                cleanupOldData()
                
                Log.d("DataCollector", "Data collection completed successfully")
                
            } catch (e: Exception) {
                Log.e("DataCollector", "Error during data collection", e)
            }
        }
    }
    
    suspend fun getAllCallLogs(): List<CallLogEntry> {
        return withContext(Dispatchers.IO) {
            try {
                callLogReader.getAllCallLogs()
            } catch (e: Exception) {
                Log.e("DataCollector", "Error getting all call logs", e)
                emptyList()
            }
        }
    }
    
    suspend fun getAllSmsLogs(): List<SmsLogEntry> {
        return withContext(Dispatchers.IO) {
            try {
                smsCounter.getAllSmsLogs()
            } catch (e: Exception) {
                Log.e("DataCollector", "Error getting all SMS logs", e)
                emptyList()
            }
        }
    }
    
    suspend fun getUnsyncedData(): Pair<List<CallLogEntry>, List<SmsLogEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                val unsyncedCalls = database.callLogDao().getUnsyncedCallLogs()
                val unsyncedSms = database.smsLogDao().getUnsyncedSmsLogs()
                Pair(unsyncedCalls, unsyncedSms)
            } catch (e: Exception) {
                Log.e("DataCollector", "Error getting unsynced data", e)
                Pair(emptyList(), emptyList())
            }
        }
    }
    
    suspend fun markAsSynced(callLogIds: List<Long>, smsLogIds: List<Long>) {
        withContext(Dispatchers.IO) {
            try {
                if (callLogIds.isNotEmpty()) {
                    database.callLogDao().markAsSynced(callLogIds)
                }
                if (smsLogIds.isNotEmpty()) {
                    database.smsLogDao().markAsSynced(smsLogIds)
                }
                Log.d("DataCollector", "Marked ${callLogIds.size} calls and ${smsLogIds.size} SMS as synced")
            } catch (e: Exception) {
                Log.e("DataCollector", "Error marking data as synced", e)
            }
        }
    }
    
    private fun getLastSyncTime(): Long {
        return preferences.getLong(LAST_SYNC_TIME_KEY, 0)
    }
    
    private fun updateLastSyncTime() {
        preferences.edit()
            .putLong(LAST_SYNC_TIME_KEY, System.currentTimeMillis())
            .apply()
    }
    
    private suspend fun cleanupOldData() {
        try {
            val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000)
            database.callLogDao().deleteOldEntries(thirtyDaysAgo)
            database.smsLogDao().deleteOldEntries(thirtyDaysAgo)
            Log.d("DataCollector", "Cleaned up data older than 30 days")
        } catch (e: Exception) {
            Log.e("DataCollector", "Error cleaning up old data", e)
        }
    }
    
    companion object {
        private const val LAST_SYNC_TIME_KEY = "last_sync_time"
    }
} 