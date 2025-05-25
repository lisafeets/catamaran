package com.catamaran.app.data.database

import androidx.room.TypeConverter
import com.catamaran.app.data.database.entities.CallType
import com.catamaran.app.data.database.entities.SmsType
import com.catamaran.app.data.database.entities.SyncStatus

/**
 * Room type converters for enum types and data transformations
 */
class DatabaseConverters {

    @TypeConverter
    fun fromCallType(callType: CallType): String {
        return callType.name
    }

    @TypeConverter
    fun toCallType(callType: String): CallType {
        return CallType.valueOf(callType)
    }

    @TypeConverter
    fun fromSmsType(smsType: SmsType): String {
        return smsType.name
    }

    @TypeConverter
    fun toSmsType(smsType: String): SmsType {
        return SmsType.valueOf(smsType)
    }

    @TypeConverter
    fun fromSyncStatus(syncStatus: SyncStatus): String {
        return syncStatus.name
    }

    @TypeConverter
    fun toSyncStatus(syncStatus: String): SyncStatus {
        return SyncStatus.valueOf(syncStatus)
    }
} 