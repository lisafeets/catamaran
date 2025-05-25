package com.catamaran.familysafety.data

import android.content.Context
import android.provider.CallLog
import android.util.Log
import com.catamaran.familysafety.utils.ContactMatcher
import java.text.SimpleDateFormat
import java.util.*

class CallLogReader(private val context: Context) {
    
    private val contactMatcher = ContactMatcher(context)
    
    fun getCallLogsSince(sinceTimestamp: Long): List<CallLogEntry> {
        val callLogs = mutableListOf<CallLogEntry>()
        
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )
        
        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(sinceTimestamp.toString())
        val sortOrder = "${CallLog.Calls.DATE} DESC"
        
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                
                while (cursor.moveToNext()) {
                    val phoneNumber = cursor.getString(numberIndex) ?: "Unknown"
                    val timestamp = cursor.getLong(dateIndex)
                    val duration = cursor.getLong(durationIndex)
                    val callType = when (cursor.getInt(typeIndex)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "unknown"
                    }
                    
                    // Get contact name if available
                    val contactName = contactMatcher.getContactName(phoneNumber)
                    
                    val callLogEntry = CallLogEntry(
                        phoneNumber = phoneNumber,
                        contactName = contactName,
                        duration = duration,
                        timestamp = timestamp,
                        callType = callType
                    )
                    
                    callLogs.add(callLogEntry)
                    
                    Log.d("CallLogReader", "Found call: $phoneNumber ($contactName) at ${formatTimestamp(timestamp)}")
                }
            }
        } catch (e: SecurityException) {
            Log.e("CallLogReader", "Permission denied to read call logs", e)
        } catch (e: Exception) {
            Log.e("CallLogReader", "Error reading call logs", e)
        }
        
        return callLogs
    }
    
    fun getAllCallLogs(): List<CallLogEntry> {
        return getCallLogsSince(0)
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
} 