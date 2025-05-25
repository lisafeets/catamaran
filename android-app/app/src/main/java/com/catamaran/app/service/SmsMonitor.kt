package com.catamaran.app.service

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.Telephony
import com.catamaran.app.data.database.entities.SmsLogEntity
import com.catamaran.app.data.database.entities.SmsType
import com.catamaran.app.data.database.entities.SyncStatus
import com.catamaran.app.utils.EncryptionManager
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * SMS monitoring service with strict privacy protection
 * CRITICAL: NO SMS content is ever read, stored, or transmitted
 * Only metadata like sender count, timestamp, and frequency patterns
 */
class SmsMonitor(
    private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val permissionManager: PermissionManager
) {

    companion object {
        private const val MAX_SMS_LOGS_PER_SCAN = 200
        private const val SCAN_DAYS_BACK = 7 // Only scan last 7 days
        private const val FREQUENCY_ANALYSIS_WINDOW_HOURS = 24
    }

    /**
     * Scan SMS logs and return new entries since last scan
     * PRIVACY: Only reads metadata - sender, timestamp, count
     */
    suspend fun scanSmsLogs(lastScanTime: Long = 0): List<SmsLogEntity> = withContext(Dispatchers.IO) {
        if (!permissionManager.hasPermission(android.Manifest.permission.READ_SMS)) {
            Logger.warning("READ_SMS permission not granted")
            return@withContext emptyList()
        }

        try {
            val smsLogs = mutableListOf<SmsLogEntity>()
            val cursor = getSmsLogsCursor(lastScanTime)
            
            cursor?.use { c ->
                val idIndex = c.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val typeIndex = c.getColumnIndex(Telephony.Sms.TYPE)
                val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)
                val threadIdIndex = c.getColumnIndex(Telephony.Sms.THREAD_ID)

                // Group messages by phone number and time window for conversation counting
                val conversationGroups = mutableMapOf<String, MutableList<SmsData>>()

                while (c.moveToNext() && smsLogs.size < MAX_SMS_LOGS_PER_SCAN) {
                    try {
                        val phoneNumber = c.getString(addressIndex) ?: continue
                        val timestamp = c.getLong(dateIndex)
                        val smsTypeRaw = c.getInt(typeIndex)
                        val threadId = c.getLong(threadIdIndex)

                        // Skip if this is too old or already processed
                        if (timestamp <= lastScanTime) continue

                        val smsData = SmsData(
                            phoneNumber = phoneNumber,
                            timestamp = timestamp,
                            smsTypeRaw = smsTypeRaw,
                            threadId = threadId
                        )

                        // Group by phone number for conversation analysis
                        val phoneHash = encryptionManager.hashPhoneNumber(phoneNumber)
                        conversationGroups.getOrPut(phoneHash) { mutableListOf() }.add(smsData)
                        
                    } catch (e: Exception) {
                        Logger.error("Error processing SMS log entry", e)
                    }
                }

                // Process conversation groups to create SMS log entities
                for ((phoneHash, messages) in conversationGroups) {
                    val smsLogEntities = processConversationGroup(phoneHash, messages)
                    smsLogs.addAll(smsLogEntities)
                }
            }
            
            Logger.info("Scanned ${smsLogs.size} new SMS conversation groups")
            smsLogs
            
        } catch (e: Exception) {
            Logger.error("Error scanning SMS logs", e)
            emptyList()
        }
    }

    private fun getSmsLogsCursor(lastScanTime: Long): Cursor? {
        val selection = if (lastScanTime > 0) {
            "${Telephony.Sms.DATE} > ?"
        } else {
            // For first scan, only get SMS from last week
            val weekAgo = System.currentTimeMillis() - (SCAN_DAYS_BACK * 24 * 60 * 60 * 1000L)
            "${Telephony.Sms.DATE} > $weekAgo"
        }

        val selectionArgs = if (lastScanTime > 0) {
            arrayOf(lastScanTime.toString())
        } else {
            null
        }

        return context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.TYPE,
                Telephony.Sms.DATE,
                Telephony.Sms.THREAD_ID
                // NOTE: Deliberately NOT reading Telephony.Sms.BODY for privacy
            ),
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        )
    }

    /**
     * Process a group of messages from the same sender
     * Groups them into conversation blocks for analysis
     */
    private suspend fun processConversationGroup(
        phoneHash: String,
        messages: List<SmsData>
    ): List<SmsLogEntity> = withContext(Dispatchers.Default) {
        
        val smsLogs = mutableListOf<SmsLogEntity>()
        
        // Sort messages by timestamp
        val sortedMessages = messages.sortedBy { it.timestamp }
        
        // Group messages into conversation blocks (within 1 hour of each other)
        val conversationBlocks = mutableListOf<MutableList<SmsData>>()
        var currentBlock = mutableListOf<SmsData>()
        var lastTimestamp = 0L
        
        for (message in sortedMessages) {
            val timeDiff = message.timestamp - lastTimestamp
            val oneHourMs = 60 * 60 * 1000L
            
            if (currentBlock.isEmpty() || timeDiff <= oneHourMs) {
                currentBlock.add(message)
            } else {
                if (currentBlock.isNotEmpty()) {
                    conversationBlocks.add(currentBlock)
                }
                currentBlock = mutableListOf(message)
            }
            lastTimestamp = message.timestamp
        }
        
        if (currentBlock.isNotEmpty()) {
            conversationBlocks.add(currentBlock)
        }
        
        // Create SMS log entities for each conversation block
        for (block in conversationBlocks) {
            val smsLog = createSmsLogEntity(phoneHash, block)
            smsLogs.add(smsLog)
        }
        
        smsLogs
    }

    private suspend fun createSmsLogEntity(
        phoneHash: String,
        messageBlock: List<SmsData>
    ): SmsLogEntity = withContext(Dispatchers.Default) {
        
        val firstMessage = messageBlock.first()
        val lastMessage = messageBlock.last()
        
        // Get original phone number for contact lookup (from first message)
        val phoneNumber = firstMessage.phoneNumber
        
        // Encrypt contact name if available
        val contactName = getContactName(phoneNumber)
        val contactNameEncrypted = contactName?.let { encryptionManager.encryptText(it) }
        
        // Determine SMS type (incoming/outgoing) - use the predominant type
        val incomingCount = messageBlock.count { it.smsTypeRaw == Telephony.Sms.MESSAGE_TYPE_INBOX }
        val outgoingCount = messageBlock.count { it.smsTypeRaw == Telephony.Sms.MESSAGE_TYPE_SENT }
        val smsType = if (incomingCount >= outgoingCount) SmsType.INCOMING else SmsType.OUTGOING
        
        // Calculate message count
        val messageCount = messageBlock.size
        
        // Determine if this is a known contact
        val isKnownContact = contactName != null
        
        // Calculate frequency pattern (encrypted for privacy)
        val frequencyPattern = createFrequencyPattern(messageBlock)
        val frequencyPatternEncrypted = frequencyPattern?.let { encryptionManager.encryptText(it) }
        
        // Calculate risk score
        val riskScore = calculateSmsRiskScore(
            phoneNumber = phoneNumber,
            messageCount = messageCount,
            smsType = smsType,
            isKnownContact = isKnownContact,
            timeSpan = lastMessage.timestamp - firstMessage.timestamp
        )

        SmsLogEntity(
            id = UUID.randomUUID().toString(),
            phoneNumberHash = phoneHash,
            contactNameEncrypted = contactNameEncrypted,
            messageCount = messageCount,
            smsType = smsType,
            timestamp = lastMessage.timestamp, // Use last message timestamp
            isKnownContact = isKnownContact,
            riskScore = riskScore,
            frequencyPattern = frequencyPatternEncrypted,
            syncStatus = SyncStatus.PENDING
        )
    }

    /**
     * Create frequency pattern for analysis (no content, just timing)
     */
    private fun createFrequencyPattern(messages: List<SmsData>): String? {
        if (messages.size < 2) return null
        
        // Create pattern based on time intervals between messages
        val intervals = mutableListOf<Long>()
        for (i in 1 until messages.size) {
            val interval = messages[i].timestamp - messages[i-1].timestamp
            intervals.add(interval)
        }
        
        // Create simple pattern classification
        val avgInterval = intervals.average()
        val pattern = when {
            avgInterval < 60_000 -> "RAPID" // Less than 1 minute between messages
            avgInterval < 300_000 -> "FREQUENT" // Less than 5 minutes
            avgInterval < 3600_000 -> "NORMAL" // Less than 1 hour
            else -> "SPORADIC"
        }
        
        return "$pattern:${messages.size}:${intervals.size}"
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
            Logger.error("Error looking up contact name for SMS", e)
            null
        }
    }

    /**
     * Calculate risk score based on SMS patterns (no content analysis)
     */
    private fun calculateSmsRiskScore(
        phoneNumber: String,
        messageCount: Int,
        smsType: SmsType,
        isKnownContact: Boolean,
        timeSpan: Long
    ): Float {
        var riskScore = 0.0f

        // Base risk for unknown contacts
        if (!isKnownContact) {
            riskScore += 0.4f
        }

        // Risk for high-frequency messaging from unknown numbers
        if (!isKnownContact && messageCount > 5) {
            riskScore += 0.3f
        }

        // Risk for very rapid messaging (potential spam)
        val avgTimePerMessage = if (messageCount > 1) timeSpan / messageCount else 0L
        if (avgTimePerMessage < 30_000) { // Less than 30 seconds between messages
            riskScore += 0.2f
        }

        // Risk for incoming messages only (potential spam/scam)
        if (smsType == SmsType.INCOMING && messageCount > 3) {
            riskScore += 0.1f
        }

        // Check for suspicious number patterns
        if (isLikelySuspiciousSmsNumber(phoneNumber)) {
            riskScore += 0.3f
        }

        return riskScore.coerceIn(0.0f, 1.0f)
    }

    /**
     * Check if SMS number matches suspicious patterns
     */
    private fun isLikelySuspiciousSmsNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        return when {
            // Very short numbers (often shortcodes)
            cleanNumber.length < 6 -> true
            
            // Common spam shortcodes
            cleanNumber in listOf("88888", "99999", "12345") -> true
            
            // Numbers that are all the same digit
            cleanNumber.matches(Regex("^(\\d)\\1+$")) -> true
            
            else -> false
        }
    }

    /**
     * Get SMS statistics for monitoring dashboard
     */
    suspend fun getSmsStatistics(sinceTimestamp: Long): SmsStatistics = withContext(Dispatchers.IO) {
        if (!permissionManager.hasPermission(android.Manifest.permission.READ_SMS)) {
            return@withContext SmsStatistics()
        }

        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.TYPE,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(sinceTimestamp.toString()),
                null
            )

            var totalSms = 0
            var unknownSms = 0
            var incomingSms = 0
            val uniqueSenders = mutableSetOf<String>()

            cursor?.use { c ->
                val typeIndex = c.getColumnIndex(Telephony.Sms.TYPE)
                val addressIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)

                while (c.moveToNext()) {
                    totalSms++
                    val type = c.getInt(typeIndex)
                    val address = c.getString(addressIndex)

                    if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                        incomingSms++
                    }

                    address?.let {
                        uniqueSenders.add(encryptionManager.hashPhoneNumber(it))
                        
                        // Check if this is from an unknown contact
                        if (getContactName(it) == null) {
                            unknownSms++
                        }
                    }
                }
            }

            SmsStatistics(
                totalSms = totalSms,
                unknownSms = unknownSms,
                incomingSms = incomingSms,
                uniqueSenders = uniqueSenders.size
            )

        } catch (e: Exception) {
            Logger.error("Error getting SMS statistics", e)
            SmsStatistics()
        }
    }

    /**
     * Data class for internal SMS processing
     */
    private data class SmsData(
        val phoneNumber: String,
        val timestamp: Long,
        val smsTypeRaw: Int,
        val threadId: Long
    )
}

/**
 * Data class for SMS statistics
 */
data class SmsStatistics(
    val totalSms: Int = 0,
    val unknownSms: Int = 0,
    val incomingSms: Int = 0,
    val uniqueSenders: Int = 0
) {
    val unknownSmsPercentage: Float
        get() = if (totalSms > 0) (unknownSms.toFloat() / totalSms) * 100 else 0f
} 