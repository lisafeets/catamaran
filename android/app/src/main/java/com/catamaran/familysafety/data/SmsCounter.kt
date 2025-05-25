package com.catamaran.familysafety.data

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.catamaran.familysafety.utils.ContactMatcher
import java.text.SimpleDateFormat
import java.util.*

class SmsCounter(private val context: Context) {
    
    private val contactMatcher = ContactMatcher(context)
    
    fun getSmsLogsSince(sinceTimestamp: Long): List<SmsLogEntry> {
        val smsLogs = mutableListOf<SmsLogEntry>()
        
        // Read received SMS
        smsLogs.addAll(getReceivedSms(sinceTimestamp))
        
        // Read sent SMS
        smsLogs.addAll(getSentSms(sinceTimestamp))
        
        return smsLogs.sortedByDescending { it.timestamp }
    }
    
    private fun getReceivedSms(sinceTimestamp: Long): List<SmsLogEntry> {
        val smsLogs = mutableListOf<SmsLogEntry>()
        
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        
        val selection = "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(
            sinceTimestamp.toString(),
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString()
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                
                while (cursor.moveToNext()) {
                    val senderNumber = cursor.getString(addressIndex) ?: "Unknown"
                    val timestamp = cursor.getLong(dateIndex)
                    
                    // Get contact name if available
                    val contactName = contactMatcher.getContactName(senderNumber)
                    
                    val smsLogEntry = SmsLogEntry(
                        senderNumber = senderNumber,
                        contactName = contactName,
                        messageType = "received",
                        timestamp = timestamp
                    )
                    
                    smsLogs.add(smsLogEntry)
                    
                    Log.d("SmsCounter", "Found received SMS from: $senderNumber ($contactName) at ${formatTimestamp(timestamp)}")
                }
            }
        } catch (e: SecurityException) {
            Log.e("SmsCounter", "Permission denied to read SMS", e)
        } catch (e: Exception) {
            Log.e("SmsCounter", "Error reading received SMS", e)
        }
        
        return smsLogs
    }
    
    private fun getSentSms(sinceTimestamp: Long): List<SmsLogEntry> {
        val smsLogs = mutableListOf<SmsLogEntry>()
        
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        
        val selection = "${Telephony.Sms.DATE} > ? AND ${Telephony.Sms.TYPE} = ?"
        val selectionArgs = arrayOf(
            sinceTimestamp.toString(),
            Telephony.Sms.MESSAGE_TYPE_SENT.toString()
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC"
        
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                
                while (cursor.moveToNext()) {
                    val recipientNumber = cursor.getString(addressIndex) ?: "Unknown"
                    val timestamp = cursor.getLong(dateIndex)
                    
                    // Get contact name if available
                    val contactName = contactMatcher.getContactName(recipientNumber)
                    
                    val smsLogEntry = SmsLogEntry(
                        senderNumber = recipientNumber, // For sent SMS, this is actually the recipient
                        contactName = contactName,
                        messageType = "sent",
                        timestamp = timestamp
                    )
                    
                    smsLogs.add(smsLogEntry)
                    
                    Log.d("SmsCounter", "Found sent SMS to: $recipientNumber ($contactName) at ${formatTimestamp(timestamp)}")
                }
            }
        } catch (e: SecurityException) {
            Log.e("SmsCounter", "Permission denied to read SMS", e)
        } catch (e: Exception) {
            Log.e("SmsCounter", "Error reading sent SMS", e)
        }
        
        return smsLogs
    }
    
    fun getAllSmsLogs(): List<SmsLogEntry> {
        return getSmsLogsSince(0)
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
} 