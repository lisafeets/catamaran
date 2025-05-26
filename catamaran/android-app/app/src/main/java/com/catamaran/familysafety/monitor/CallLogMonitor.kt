package com.catamaran.familysafety.monitor

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import com.catamaran.familysafety.data.model.CallLogEntry
import java.util.*

class CallLogMonitor(private val context: Context) {

    fun getRecentCalls(hoursBack: Int = 24): List<CallLogEntry> {
        val callLogEntries = mutableListOf<CallLogEntry>()
        
        try {
            val cutoffTime = System.currentTimeMillis() - (hoursBack * 60 * 60 * 1000)
            
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME
            )
            
            val selection = "${CallLog.Calls.DATE} > ?"
            val selectionArgs = arrayOf(cutoffTime.toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC"
            
            val cursor: Cursor? = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            
            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberColumn = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeColumn = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateColumn = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationColumn = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val nameColumn = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                
                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val number = c.getString(numberColumn) ?: "Unknown"
                    val type = c.getInt(typeColumn)
                    val date = c.getLong(dateColumn)
                    val duration = c.getLong(durationColumn)
                    val name = c.getString(nameColumn)
                    
                    val callType = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        else -> "UNKNOWN"
                    }
                    
                    callLogEntries.add(
                        CallLogEntry(
                            id = id,
                            phoneNumber = number,
                            contactName = name,
                            callType = callType,
                            timestamp = date,
                            duration = duration
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
            android.util.Log.e("CallLogMonitor", "Permission denied for call log access", e)
        } catch (e: Exception) {
            android.util.Log.e("CallLogMonitor", "Error reading call log", e)
        }
        
        return callLogEntries
    }
    
    fun getCallStatistics(hoursBack: Int = 24): CallStatistics {
        val calls = getRecentCalls(hoursBack)
        
        var incomingCount = 0
        var outgoingCount = 0
        var missedCount = 0
        var totalDuration = 0L
        
        calls.forEach { call ->
            when (call.callType) {
                "INCOMING" -> incomingCount++
                "OUTGOING" -> outgoingCount++
                "MISSED" -> missedCount++
            }
            totalDuration += call.duration
        }
        
        return CallStatistics(
            totalCalls = calls.size,
            incomingCalls = incomingCount,
            outgoingCalls = outgoingCount,
            missedCalls = missedCount,
            totalDurationSeconds = totalDuration,
            timeRangeHours = hoursBack
        )
    }
}

data class CallStatistics(
    val totalCalls: Int,
    val incomingCalls: Int,
    val outgoingCalls: Int,
    val missedCalls: Int,
    val totalDurationSeconds: Long,
    val timeRangeHours: Int
) 