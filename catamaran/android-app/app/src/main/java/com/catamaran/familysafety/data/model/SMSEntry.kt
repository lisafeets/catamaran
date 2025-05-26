package com.catamaran.familysafety.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_messages")
data class SMSEntry(
    @PrimaryKey val id: Long,
    val phoneNumber: String,
    val messageBody: String,
    val timestamp: Long,
    val messageType: String, // RECEIVED, SENT, DRAFT, OUTBOX, FAILED, QUEUED
    val isRead: Boolean,
    val synced: Boolean = false
) 