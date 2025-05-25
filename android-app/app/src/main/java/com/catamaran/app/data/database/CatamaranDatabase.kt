package com.catamaran.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import com.catamaran.app.data.database.entities.CallLogEntity
import com.catamaran.app.data.database.entities.SmsLogEntity
import com.catamaran.app.data.database.dao.CallLogDao
import com.catamaran.app.data.database.dao.SmsLogDao
import com.catamaran.app.utils.EncryptionManager

/**
 * Catamaran encrypted database using SQLCipher
 * All sensitive data is encrypted at rest for maximum privacy
 */
@Database(
    entities = [
        CallLogEntity::class,
        SmsLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(DatabaseConverters::class)
abstract class CatamaranDatabase : RoomDatabase() {

    abstract fun callLogDao(): CallLogDao
    abstract fun smsLogDao(): SmsLogDao

    companion object {
        @Volatile
        private var INSTANCE: CatamaranDatabase? = null
        private const val DATABASE_NAME = "catamaran_encrypted.db"

        fun getDatabase(
            context: Context,
            encryptionManager: EncryptionManager
        ): CatamaranDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context, encryptionManager)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(
            context: Context,
            encryptionManager: EncryptionManager
        ): CatamaranDatabase {
            // Get or create database encryption key
            val databaseKey = encryptionManager.getDatabaseKey()
            
            // Create SQLCipher support factory
            val supportFactory = SupportFactory(SQLiteDatabase.getBytes(databaseKey.toCharArray()))

            return Room.databaseBuilder(
                context.applicationContext,
                CatamaranDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(supportFactory)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Enable write-ahead logging for better performance
                        db.execSQL("PRAGMA journal_mode=WAL")
                        // Set secure delete to overwrite deleted data
                        db.execSQL("PRAGMA secure_delete=ON")
                        // Set cache size for better performance
                        db.execSQL("PRAGMA cache_size=10000")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Verify database integrity on open
                        db.execSQL("PRAGMA integrity_check")
                    }
                })
                .fallbackToDestructiveMigration() // For development - remove in production
                .build()
        }

        /**
         * Clear database instance (for testing or logout)
         */
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
} 