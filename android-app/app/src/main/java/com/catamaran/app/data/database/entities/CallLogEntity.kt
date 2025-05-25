package com.catamaran.app.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Call log entity for local storage
 * Privacy-focused: No call content, only metadata
 */
@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["phoneNumberHash"]),
        Index(value = ["timestamp"]),
        Index(value = ["syncStatus"])
    ]
)
data class CallLogEntity(
    @PrimaryKey
    val id: String, // UUID
    
    // Privacy: Phone number is hashed, never stored in plain text
    val phoneNumberHash: String,
    
    // Encrypted contact name (if available from contacts)
    val contactNameEncrypted: String?,
    
    // Call metadata
    val duration: Long, // Duration in seconds
    val callType: CallType,
    val timestamp: Long, // Unix timestamp
    
    // Privacy analysis
    val isKnownContact: Boolean = false,
    val riskScore: Float = 0.0f,
    
    // Sync status
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncAttempt: Long? = null,
    val syncRetryCount: Int = 0
)

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED
}

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
} 