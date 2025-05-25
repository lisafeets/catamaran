package com.catamaran.app.service

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import android.provider.ContactsContract
import com.catamaran.app.data.database.CatamaranDatabase
import com.catamaran.app.data.database.entities.CallLogEntity
import com.catamaran.app.data.database.entities.SyncStatus
import com.catamaran.app.utils.EncryptionManager
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.RiskAssessment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Efficient call log watcher with real-time monitoring and risk assessment
 * Features:
 * - Incremental scanning to minimize battery usage
 * - Contact resolution with caching
 * - Risk scoring for suspicious calls
 * - Duplicate detection and deduplication
 * - Encrypted data storage
 */
class CallLogWatcher(
    private val context: Context,
    private val database: CatamaranDatabase,
    private val encryptionManager: EncryptionManager
) {

    companion object {
        private const val MAX_SCAN_BATCH_SIZE = 100
        private const val UNKNOWN_CONTACT_RISK_THRESHOLD = 5
        private const val FREQUENT_UNKNOWN_RISK_THRESHOLD = 10
    }

    // Cache for contact resolution to reduce database queries
    private val contactCache = ConcurrentHashMap<String, ContactInfo>()
    private var lastScanTimestamp = 0L
    private val riskAssessment = RiskAssessment()

    /**
     * Process new call logs since last scan
     */
    suspend fun processNewCallLogs(): List<CallLogEntity> = withContext(Dispatchers.IO) {
        try {
            Logger.debug("Scanning call logs since timestamp: $lastScanTimestamp")
            
            val newCallLogs = scanCallLogsSince(lastScanTimestamp)
            
            if (newCallLogs.isNotEmpty()) {
                // Resolve contacts and assess risk
                val processedCalls = newCallLogs.map { callLog ->
                    processCallLog(callLog)
                }
                
                // Save to database
                database.callLogDao().insertCallLogs(processedCalls)
                
                // Update scan timestamp
                updateLastScanTimestamp(processedCalls)
                
                Logger.info("Processed ${processedCalls.size} new call logs")
                processedCalls
            } else {
                emptyList()
            }
            
        } catch (e: Exception) {
            Logger.error("Error processing call logs", e)
            emptyList()
        }
    }

    /**
     * Scan call logs since specific timestamp
     */
    private suspend fun scanCallLogsSince(sinceTimestamp: Long): List<RawCallLog> = withContext(Dispatchers.IO) {
        val callLogs = mutableListOf<RawCallLog>()
        
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_NUMBER_TYPE,
            CallLog.Calls.CACHED_NUMBER_LABEL
        )
        
        val selection = "${CallLog.Calls.DATE} > ?"
        val selectionArgs = arrayOf(sinceTimestamp.toString())
        val sortOrder = "${CallLog.Calls.DATE} ASC LIMIT $MAX_SCAN_BATCH_SIZE"
        
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            
            val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
            val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberTypeIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NUMBER_TYPE)
            val numberLabelIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NUMBER_LABEL)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val number = cursor.getString(numberIndex) ?: ""
                val date = cursor.getLong(dateIndex)
                val duration = cursor.getLong(durationIndex)
                val type = cursor.getInt(typeIndex)
                val cachedName = cursor.getString(nameIndex)
                val numberType = cursor.getInt(numberTypeIndex)
                val numberLabel = cursor.getString(numberLabelIndex)
                
                callLogs.add(
                    RawCallLog(
                        id = id,
                        phoneNumber = number,
                        timestamp = date,
                        duration = duration,
                        callType = mapCallType(type),
                        cachedName = cachedName,
                        numberType = numberType,
                        numberLabel = numberLabel
                    )
                )
            }
        }
        
        Logger.debug("Found ${callLogs.size} call logs since $sinceTimestamp")
        callLogs
    }

    /**
     * Process individual call log with contact resolution and risk assessment
     */
    private suspend fun processCallLog(rawCallLog: RawCallLog): CallLogEntity = withContext(Dispatchers.IO) {
        // Resolve contact information
        val contactInfo = resolveContact(rawCallLog.phoneNumber, rawCallLog.cachedName)
        
        // Assess risk level
        val riskScore = assessCallRisk(rawCallLog, contactInfo)
        
        // Create encrypted call log entity
        CallLogEntity(
            id = generateUniqueId(rawCallLog),
            phoneNumber = encryptionManager.encrypt(rawCallLog.phoneNumber),
            contactName = contactInfo.name?.let { encryptionManager.encrypt(it) },
            duration = rawCallLog.duration,
            callType = rawCallLog.callType,
            timestamp = rawCallLog.timestamp,
            isKnownContact = contactInfo.isKnown,
            riskScore = riskScore,
            suspiciousPatterns = identifySuspiciousPatterns(rawCallLog, contactInfo),
            syncStatus = SyncStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Resolve contact information with caching
     */
    private suspend fun resolveContact(phoneNumber: String, cachedName: String?): ContactInfo = withContext(Dispatchers.IO) {
        // Check cache first
        contactCache[phoneNumber]?.let { return@withContext it }
        
        // Use cached name if available
        cachedName?.let { name ->
            val contactInfo = ContactInfo(
                name = name,
                isKnown = true,
                contactType = ContactType.CACHED
            )
            contactCache[phoneNumber] = contactInfo
            return@withContext contactInfo
        }
        
        // Query contacts database
        val contactInfo = queryContactsDatabase(phoneNumber)
        
        // Cache the result
        contactCache[phoneNumber] = contactInfo
        
        contactInfo
    }

    /**
     * Query contacts database for phone number
     */
    private suspend fun queryContactsDatabase(phoneNumber: String): ContactInfo = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.TYPE,
                ContactsContract.PhoneLookup.LABEL
            )
            
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(phoneNumber)
                .build()
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val typeIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.TYPE)
                    val labelIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.LABEL)
                    
                    val name = cursor.getString(nameIndex)
                    val type = cursor.getInt(typeIndex)
                    val label = cursor.getString(labelIndex)
                    
                    return@withContext ContactInfo(
                        name = name,
                        isKnown = true,
                        contactType = ContactType.CONTACT,
                        phoneType = type,
                        phoneLabel = label
                    )
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Error querying contacts database", e)
        }
        
        // Return unknown contact
        ContactInfo(
            name = null,
            isKnown = false,
            contactType = ContactType.UNKNOWN
        )
    }

    /**
     * Assess risk level for call
     */
    private suspend fun assessCallRisk(rawCallLog: RawCallLog, contactInfo: ContactInfo): Int = withContext(Dispatchers.IO) {
        var riskScore = 0
        
        // Unknown contact base risk
        if (!contactInfo.isKnown) {
            riskScore += UNKNOWN_CONTACT_RISK_THRESHOLD
        }
        
        // Call type risk
        riskScore += when (rawCallLog.callType) {
            "MISSED" -> 2 // Missed calls from unknown numbers are suspicious
            "INCOMING" -> 1
            "OUTGOING" -> 0 // Outgoing calls are user-initiated
            else -> 1
        }
        
        // Duration-based risk (very short or very long calls from unknown numbers)
        if (!contactInfo.isKnown) {
            when {
                rawCallLog.duration < 10 && rawCallLog.callType != "MISSED" -> riskScore += 3 // Hang-ups
                rawCallLog.duration > 600 -> riskScore += 2 // Very long calls from unknowns
            }
        }
        
        // Frequency-based risk (frequent calls from same unknown number)
        if (!contactInfo.isKnown) {
            val recentCallsFromNumber = database.callLogDao()
                .getCallsFromNumberSince(
                    encryptionManager.encrypt(rawCallLog.phoneNumber),
                    System.currentTimeMillis() - 24 * 60 * 60 * 1000 // Last 24 hours
                )
            
            if (recentCallsFromNumber.size >= 3) {
                riskScore += FREQUENT_UNKNOWN_RISK_THRESHOLD
            }
        }
        
        // Time-based risk (calls at unusual hours)
        val hour = java.util.Calendar.getInstance().apply {
            timeInMillis = rawCallLog.timestamp
        }.get(java.util.Calendar.HOUR_OF_DAY)
        
        if (hour < 7 || hour > 22) { // Before 7 AM or after 10 PM
            riskScore += 2
        }
        
        // Pattern-based risk using AI/ML (placeholder for future enhancement)
        riskScore += riskAssessment.assessCallPattern(rawCallLog, contactInfo)
        
        // Cap risk score at 10
        minOf(riskScore, 10)
    }

    /**
     * Identify suspicious patterns in calls
     */
    private suspend fun identifySuspiciousPatterns(rawCallLog: RawCallLog, contactInfo: ContactInfo): List<String> = withContext(Dispatchers.IO) {
        val patterns = mutableListOf<String>()
        
        // Unknown caller pattern
        if (!contactInfo.isKnown) {
            patterns.add("UNKNOWN_CALLER")
        }
        
        // Rapid succession calls
        val recentCalls = database.callLogDao()
            .getCallsFromNumberSince(
                encryptionManager.encrypt(rawCallLog.phoneNumber),
                rawCallLog.timestamp - 30 * 60 * 1000 // Last 30 minutes
            )
        
        if (recentCalls.size > 2) {
            patterns.add("RAPID_SUCCESSION")
        }
        
        // Short duration pattern (potential robocalls)
        if (rawCallLog.duration in 1..5 && rawCallLog.callType != "MISSED") {
            patterns.add("SHORT_DURATION")
        }
        
        // Off-hours pattern
        val hour = java.util.Calendar.getInstance().apply {
            timeInMillis = rawCallLog.timestamp
        }.get(java.util.Calendar.HOUR_OF_DAY)
        
        if (hour < 7 || hour > 22) {
            patterns.add("OFF_HOURS")
        }
        
        // International number pattern
        if (rawCallLog.phoneNumber.startsWith("+") && !rawCallLog.phoneNumber.startsWith("+1")) {
            patterns.add("INTERNATIONAL")
        }
        
        // Spoofed number pattern (basic detection)
        if (rawCallLog.phoneNumber.matches(Regex("\\+?1?\\d{10}")) && 
            rawCallLog.phoneNumber.contains(Regex("(\\d)\\1{6,}"))) { // 7+ consecutive same digits
            patterns.add("POTENTIAL_SPOOFING")
        }
        
        patterns
    }

    /**
     * Generate unique ID for call log entry
     */
    private fun generateUniqueId(rawCallLog: RawCallLog): String {
        return "${rawCallLog.id}_${rawCallLog.timestamp}_${rawCallLog.phoneNumber.hashCode()}"
    }

    /**
     * Update last scan timestamp based on processed calls
     */
    private fun updateLastScanTimestamp(callLogs: List<CallLogEntity>) {
        if (callLogs.isNotEmpty()) {
            lastScanTimestamp = callLogs.maxOf { it.timestamp }
            Logger.debug("Updated last scan timestamp to: $lastScanTimestamp")
        }
    }

    /**
     * Map call log type integer to string
     */
    private fun mapCallType(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "INCOMING"
            CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
            CallLog.Calls.MISSED_TYPE -> "MISSED"
            CallLog.Calls.VOICEMAIL_TYPE -> "VOICEMAIL"
            CallLog.Calls.REJECTED_TYPE -> "REJECTED"
            CallLog.Calls.BLOCKED_TYPE -> "BLOCKED"
            CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> "ANSWERED_EXTERNALLY"
            else -> "UNKNOWN"
        }
    }

    /**
     * Clear contact cache (call periodically to prevent memory issues)
     */
    fun clearContactCache() {
        contactCache.clear()
        Logger.debug("Contact cache cleared")
    }

    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            size = contactCache.size,
            hitRate = 0.0 // TODO: Implement hit rate tracking
        )
    }

    // Data classes
    data class RawCallLog(
        val id: Long,
        val phoneNumber: String,
        val timestamp: Long,
        val duration: Long,
        val callType: String,
        val cachedName: String?,
        val numberType: Int,
        val numberLabel: String?
    )

    data class ContactInfo(
        val name: String?,
        val isKnown: Boolean,
        val contactType: ContactType,
        val phoneType: Int = 0,
        val phoneLabel: String? = null
    )

    enum class ContactType {
        UNKNOWN, CACHED, CONTACT, EMERGENCY
    }

    data class CacheStats(
        val size: Int,
        val hitRate: Double
    )
} 