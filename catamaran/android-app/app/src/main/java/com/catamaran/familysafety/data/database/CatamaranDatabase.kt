package com.catamaran.familysafety.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.catamaran.familysafety.data.model.CallLogEntry
import com.catamaran.familysafety.data.model.SMSEntry

@Database(
    entities = [CallLogEntry::class, SMSEntry::class],
    version = 1,
    exportSchema = false
)
abstract class CatamaranDatabase : RoomDatabase() {
    
    abstract fun monitoringDao(): MonitoringDao
    
    companion object {
        @Volatile
        private var INSTANCE: CatamaranDatabase? = null
        
        fun getDatabase(context: Context): CatamaranDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CatamaranDatabase::class.java,
                    "catamaran_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 