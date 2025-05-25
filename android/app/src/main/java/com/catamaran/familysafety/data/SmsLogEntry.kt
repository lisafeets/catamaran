package com.catamaran.familysafety.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_logs")
data class SmsLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderNumber: String,
    val contactName: String?,
    val messageType: String, // "received", "sent"
    val timestamp: Long,
    val messageCount: Int = 1,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) 