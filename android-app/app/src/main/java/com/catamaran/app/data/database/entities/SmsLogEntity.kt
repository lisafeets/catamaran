package com.catamaran.app.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * SMS log entity for local storage
 * PRIVACY CRITICAL: NO SMS content is ever stored, only metadata
 */
@Entity(
    tableName = "sms_logs",
    indices = [
        Index(value = ["phoneNumberHash"]),
        Index(value = ["timestamp"]),
        Index(value = ["syncStatus"])
    ]
)
data class SmsLogEntity(
    @PrimaryKey
    val id: String, // UUID
    
    // Privacy: Phone number is hashed, never stored in plain text
    val phoneNumberHash: String,
    
    // Encrypted contact name (if available from contacts)
    val contactNameEncrypted: String?,
    
    // SMS metadata ONLY - NO CONTENT
    val messageCount: Int = 1, // Number of messages in this conversation block
    val smsType: SmsType,
    val timestamp: Long, // Unix timestamp
    
    // Privacy analysis (based on frequency patterns, NOT content)
    val isKnownContact: Boolean = false,
    val riskScore: Float = 0.0f,
    val frequencyPattern: String? = null, // Encrypted pattern analysis
    
    // Sync status
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncAttempt: Long? = null,
    val syncRetryCount: Int = 0
)

enum class SmsType {
    INCOMING,
    OUTGOING
} 