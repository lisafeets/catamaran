package com.catamaran.familysafety.monitor

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.catamaran.familysafety.data.model.SMSEntry

class SMSMonitor(private val context: Context) {

    fun getRecentMessages(hoursBack: Int = 24): List<SMSEntry> {
        val smsEntries = mutableListOf<SMSEntry>()
        
        try {
            val cutoffTime = System.currentTimeMillis() - (hoursBack * 60 * 60 * 1000)
            android.util.Log.d("SMSMonitor", "Looking for SMS messages since: ${java.util.Date(cutoffTime)}")
            
            val projection = arrayOf(
                "_id",
                "address",
                "body",
                "date",
                "type",
                "read"
            )
            
            val selection = "date > ?"
            val selectionArgs = arrayOf(cutoffTime.toString())
            val sortOrder = "date DESC"
            
            val cursor: Cursor? = context.contentResolver.query(
                Uri.parse("content://sms"),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow("_id")
                val addressColumn = c.getColumnIndexOrThrow("address")
                val bodyColumn = c.getColumnIndexOrThrow("body")
                val dateColumn = c.getColumnIndexOrThrow("date")
                val typeColumn = c.getColumnIndexOrThrow("type")
                val readColumn = c.getColumnIndexOrThrow("read")
                
                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val address = c.getString(addressColumn) ?: "Unknown"
                    val body = c.getString(bodyColumn) ?: ""
                    val date = c.getLong(dateColumn)
                    val type = c.getInt(typeColumn)
                    val isRead = c.getInt(readColumn) == 1
                    
                    val messageType = when (type) {
                        1 -> "RECEIVED"
                        2 -> "SENT"
                        3 -> "DRAFT"
                        4 -> "OUTBOX"
                        5 -> "FAILED"
                        6 -> "QUEUED"
                        else -> "UNKNOWN"
                    }
                    
                    android.util.Log.d("SMSMonitor", "Found SMS: id=$id, address=$address, type=$messageType, date=${java.util.Date(date)}")
                    
                    smsEntries.add(
                        SMSEntry(
                            id = id,
                            phoneNumber = address,
                            messageBody = body,
                            timestamp = date,
                            messageType = messageType,
                            isRead = isRead
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
            android.util.Log.e("SMSMonitor", "Permission denied for SMS access", e)
        } catch (e: Exception) {
            android.util.Log.e("SMSMonitor", "Error reading SMS", e)
        }
        
        android.util.Log.d("SMSMonitor", "Returning ${smsEntries.size} SMS messages")
        return smsEntries
    }
    
    fun getSMSStatistics(hoursBack: Int = 24): SMSStatistics {
        val messages = getRecentMessages(hoursBack)
        
        var receivedCount = 0
        var sentCount = 0
        var unreadCount = 0
        
        messages.forEach { message ->
            when (message.messageType) {
                "RECEIVED" -> {
                    receivedCount++
                    if (!message.isRead) unreadCount++
                }
                "SENT" -> sentCount++
            }
        }
        
        return SMSStatistics(
            totalMessages = messages.size,
            receivedMessages = receivedCount,
            sentMessages = sentCount,
            unreadMessages = unreadCount,
            timeRangeHours = hoursBack
        )
    }
}

data class SMSStatistics(
    val totalMessages: Int,
    val receivedMessages: Int,
    val sentMessages: Int,
    val unreadMessages: Int,
    val timeRangeHours: Int
) 