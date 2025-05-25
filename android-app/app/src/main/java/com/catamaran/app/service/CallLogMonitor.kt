package com.catamaran.app.service

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import com.catamaran.app.data.database.entities.CallLogEntity
import com.catamaran.app.data.database.entities.CallType
import com.catamaran.app.data.database.entities.SyncStatus
import com.catamaran.app.utils.EncryptionManager
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Call log monitoring service
 * Reads call history with privacy protection - no call content, only metadata
 */
class CallLogMonitor(
    private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val permissionManager: PermissionManager
) {

    companion object {
        private const val MAX_CALL_LOGS_PER_SCAN = 100
        private const val SCAN_DAYS_BACK = 7 // Only scan last 7 days
    }

    /**
     * Scan call logs and return new entries since last scan
     */
    suspend fun scanCallLogs(lastScanTime: Long = 0): List<CallLogEntity> = withContext(Dispatchers.IO) {
        if (!permissionManager.hasPermission(android.Manifest.permission.READ_CALL_LOG)) {
            Logger.warning("READ_CALL_LOG permission not granted")
            return@withContext emptyList()
        }

        try {
            val callLogs = mutableListOf<CallLogEntity>()
            val cursor = getCallLogsCursor(lastScanTime)
            
            cursor?.use { c ->
                val idIndex = c.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = c.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = c.getColumnIndex(CallLog.Calls.DURATION)
                val cachedNameIndex = c.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (c.moveToNext() && callLogs.size < MAX_CALL_LOGS_PER_SCAN) {
                    try {
                        val phoneNumber = c.getString(numberIndex) ?: continue
                        val timestamp = c.getLong(dateIndex)
                        val duration = c.getLong(durationIndex)
                        val callTypeRaw = c.getInt(typeIndex)
                        val cachedName = c.getString(cachedNameIndex)

                        // Skip if this is too old or already processed
                        if (timestamp <= lastScanTime) continue

                        val callLog = createCallLogEntity(
                            phoneNumber = phoneNumber,
                            timestamp = timestamp,
                            duration = duration,
                            callTypeRaw = callTypeRaw,
                            cachedName = cachedName
                        )

                        callLogs.add(callLog)
                        
                    } catch (e: Exception) {
                        Logger.error("Error processing call log entry", e)
                    }
                }
            }
            
            Logger.info("Scanned ${callLogs.size} new call logs")
            callLogs
            
        } catch (e: Exception) {
            Logger.error("Error scanning call logs", e)
            emptyList()
        }
    }

    private fun getCallLogsCursor(lastScanTime: Long): Cursor? {
        val selection = if (lastScanTime > 0) {
            "${CallLog.Calls.DATE} > ?"
        } else {
            // For first scan, only get calls from last week to avoid overwhelming the system
            val weekAgo = System.currentTimeMillis() - (SCAN_DAYS_BACK * 24 * 60 * 60 * 1000L)
            "${CallLog.Calls.DATE} > $weekAgo"
        }

        val selectionArgs = if (lastScanTime > 0) {
            arrayOf(lastScanTime.toString())
        } else {
            null
        }

        return context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME
            ),
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} DESC"
        )
    }

    private suspend fun createCallLogEntity(
        phoneNumber: String,
        timestamp: Long,
        duration: Long,
        callTypeRaw: Int,
        cachedName: String?
    ): CallLogEntity = withContext(Dispatchers.Default) {
        
        // Hash phone number for privacy
        val phoneNumberHash = encryptionManager.hashPhoneNumber(phoneNumber)
        
        // Encrypt contact name if available
        val contactName = getContactName(phoneNumber) ?: cachedName
        val contactNameEncrypted = contactName?.let { encryptionManager.encryptText(it) }
        
        // Convert call type
        val callType = when (callTypeRaw) {
            CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
            CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
            CallLog.Calls.MISSED_TYPE -> CallType.MISSED
            else -> CallType.MISSED
        }
        
        // Determine if this is a known contact
        val isKnownContact = contactName != null
        
        // Calculate basic risk score
        val riskScore = calculateRiskScore(
            phoneNumber = phoneNumber,
            callType = callType,
            duration = duration,
            isKnownContact = isKnownContact
        )

        CallLogEntity(
            id = UUID.randomUUID().toString(),
            phoneNumberHash = phoneNumberHash,
            contactNameEncrypted = contactNameEncrypted,
            duration = duration,
            callType = callType,
            timestamp = timestamp,
            isKnownContact = isKnownContact,
            riskScore = riskScore,
            syncStatus = SyncStatus.PENDING
        )
    }

    /**
     * Get contact name from contacts provider
     */
    private fun getContactName(phoneNumber: String): String? {
        if (!permissionManager.hasPermission(android.Manifest.permission.READ_CONTACTS)) {
            return null
        }

        return try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(phoneNumber)
                .build()

            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Logger.error("Error looking up contact name", e)
            null
        }
    }

    /**
     * Calculate risk score based on call patterns
     */
    private fun calculateRiskScore(
        phoneNumber: String,
        callType: CallType,
        duration: Long,
        isKnownContact: Boolean
    ): Float {
        var riskScore = 0.0f

        // Base risk for unknown contacts
        if (!isKnownContact) {
            riskScore += 0.3f
        }

        // Risk factors for suspicious patterns
        when (callType) {
            CallType.MISSED -> riskScore += 0.1f
            CallType.INCOMING -> {
                // Very short incoming calls can be suspicious (robocalls)
                if (duration < 10) {
                    riskScore += 0.2f
                }
            }
            CallType.OUTGOING -> {
                // Outgoing calls generally less risky
                riskScore -= 0.1f
            }
        }

        // Check for suspicious number patterns
        if (isLikelySuspiciousNumber(phoneNumber)) {
            riskScore += 0.4f
        }

        // Ensure risk score is between 0 and 1
        return riskScore.coerceIn(0.0f, 1.0f)
    }

    /**
     * Check if phone number matches suspicious patterns
     */
    private fun isLikelySuspiciousNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        return when {
            // Very short numbers (like 5-digit scam numbers)
            cleanNumber.length < 7 -> true
            
            // Numbers with repeated digits (often spoofed)
            cleanNumber.matches(Regex(".*([0-9])\\1{4,}.*")) -> true
            
            // Common scam prefixes (this would be more sophisticated in production)
            cleanNumber.startsWith("1800") || 
            cleanNumber.startsWith("1888") || 
            cleanNumber.startsWith("1877") -> true
            
            else -> false
        }
    }

    /**
     * Get call statistics for monitoring dashboard
     */
    suspend fun getCallStatistics(sinceTimestamp: Long): CallStatistics = withContext(Dispatchers.IO) {
        if (!permissionManager.hasPermission(android.Manifest.permission.READ_CALL_LOG)) {
            return@withContext CallStatistics()
        }

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.CACHED_NAME
                ),
                "${CallLog.Calls.DATE} > ?",
                arrayOf(sinceTimestamp.toString()),
                null
            )

            var totalCalls = 0
            var unknownCalls = 0
            var missedCalls = 0
            var totalDuration = 0L

            cursor?.use { c ->
                val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                val durationIndex = c.getColumnIndex(CallLog.Calls.DURATION)
                val nameIndex = c.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (c.moveToNext()) {
                    totalCalls++
                    val type = c.getInt(typeIndex)
                    val duration = c.getLong(durationIndex)
                    val name = c.getString(nameIndex)

                    if (name.isNullOrBlank()) {
                        unknownCalls++
                    }

                    if (type == CallLog.Calls.MISSED_TYPE) {
                        missedCalls++
                    }

                    totalDuration += duration
                }
            }

            CallStatistics(
                totalCalls = totalCalls,
                unknownCalls = unknownCalls,
                missedCalls = missedCalls,
                totalDuration = totalDuration
            )

        } catch (e: Exception) {
            Logger.error("Error getting call statistics", e)
            CallStatistics()
        }
    }
}

/**
 * Data class for call statistics
 */
data class CallStatistics(
    val totalCalls: Int = 0,
    val unknownCalls: Int = 0,
    val missedCalls: Int = 0,
    val totalDuration: Long = 0L
) {
    val unknownCallPercentage: Float
        get() = if (totalCalls > 0) (unknownCalls.toFloat() / totalCalls) * 100 else 0f
} 