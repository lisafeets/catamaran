package com.catamaran.familysafety.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntry(
    @PrimaryKey val id: Long,
    val phoneNumber: String,
    val contactName: String?,
    val callType: String, // INCOMING, OUTGOING, MISSED, REJECTED
    val timestamp: Long,
    val duration: Long, // in seconds
    val isKnownContact: Boolean = false,
    val synced: Boolean = false
) 