package com.catamaran.familysafety.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val contactName: String?,
    val duration: Long, // in seconds
    val timestamp: Long,
    val callType: String, // "incoming", "outgoing", "missed"
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) 