package com.catamaran.familysafety.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntry>>
    
    @Query("SELECT * FROM call_logs WHERE isSynced = 0")
    suspend fun getUnsyncedCallLogs(): List<CallLogEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntry)
    
    @Query("UPDATE call_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
    
    @Query("DELETE FROM call_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEntries(cutoffTime: Long)
}

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_logs ORDER BY timestamp DESC")
    fun getAllSmsLogs(): Flow<List<SmsLogEntry>>
    
    @Query("SELECT * FROM sms_logs WHERE isSynced = 0")
    suspend fun getUnsyncedSmsLogs(): List<SmsLogEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsLog(smsLog: SmsLogEntry)
    
    @Query("UPDATE sms_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)
    
    @Query("DELETE FROM sms_logs WHERE timestamp < :cutoffTime")
    suspend fun deleteOldEntries(cutoffTime: Long)
}

@Database(
    entities = [CallLogEntry::class, SmsLogEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao
    abstract fun smsLogDao(): SmsLogDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "catamaran_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
} 