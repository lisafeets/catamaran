package com.catamaran.app.service

import android.content.Context
import android.provider.Telephony
import com.catamaran.app.data.database.CatamaranDatabase
import com.catamaran.app.data.database.entities.SmsLogEntity
import com.catamaran.app.data.database.entities.SyncStatus
import com.catamaran.app.utils.EncryptionManager
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.RiskAssessment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Efficient SMS watcher with real-time monitoring and suspicious pattern detection
 * Features:
 * - Conversation-based SMS grouping
 * - Suspicious content pattern detection
 * - Frequency analysis for spam detection
 * - Contact resolution with caching
 * - Encrypted data storage
 * - Minimal battery impact with intelligent scanning
 */
class SmsWatcher(
    private val context: Context,
    private val database: CatamaranDatabase,
    private val encryptionManager: EncryptionManager
) {

    companion object {
        private const val MAX_SCAN_BATCH_SIZE = 200
        private const val SUSPICIOUS_KEYWORD_THRESHOLD = 3
        private const val FREQUENT_UNKNOWN_SMS_THRESHOLD = 5
        private const val MAX_MESSAGE_PREVIEW_LENGTH = 100
        
        // Suspicious keywords for basic pattern detection
        private val SUSPICIOUS_KEYWORDS = setOf(
            "urgent", "winner", "congratulations", "free", "prize", "click",
            "verify", "suspend", "account", "bank", "credit", "debt",
            "loan", "offer", "deal", "limited", "expires", "act now",
            "cash", "money", "refund", "tax", "irs", "government",
            "pharmacy", "medication", "viagra", "pharmacy",
            "investment", "bitcoin", "crypto", "trading"
        )
        
        // High-risk keywords that should trigger immediate alerts
        private val HIGH_RISK_KEYWORDS = setOf(
            "social security", "ssn", "medicare", "medicaid", "bank account",
            "routing number", "pin", "password", "security code", "otp",
            "verification code", "authorize", "confirm payment", "wire transfer"
        )
    }

    // Cache for contact resolution and conversation grouping
    private val contactCache = ConcurrentHashMap<String, ContactInfo>()
    private val conversationCache = ConcurrentHashMap<String, ConversationInfo>()
    private var lastScanTimestamp = 0L
    private val riskAssessment = RiskAssessment()

    /**
     * Process new SMS messages since last scan
     */
    suspend fun processNewSmsLogs(): List<SmsLogEntity> = withContext(Dispatchers.IO) {
        try {
            Logger.debug("Scanning SMS messages since timestamp: $lastScanTimestamp")
            
            val newSmsMessages = scanSmsMessagesSince(lastScanTimestamp)
            
            if (newSmsMessages.isNotEmpty()) {
                // Group messages by conversation
                val conversations = groupMessagesByConversation(newSmsMessages)
                
                // Process each conversation
                val processedSmsLogs = conversations.map { conversation ->
                    processSmsConversation(conversation)
                }
                
                // Save to database
                database.smsLogDao().insertSmsLogs(processedSmsLogs)
                
                // Update scan timestamp
                updateLastScanTimestamp(newSmsMessages)
                
                Logger.info("Processed ${processedSmsLogs.size} SMS conversations from ${newSmsMessages.size} messages")
                processedSmsLogs
            } else {
                emptyList()
            }
            
        } catch (e: Exception) {
            Logger.error("Error processing SMS logs", e)
            emptyList()
        }
    }

    /**
     * Scan SMS messages since specific timestamp
     */
    private suspend fun scanSmsMessagesSince(sinceTimestamp: Long): List<RawSmsMessage> = withContext(Dispatchers.IO) {
        val smsMessages = mutableListOf<RawSmsMessage>()
        
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.THREAD_ID
        )
        
        val selection = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(sinceTimestamp.toString())
        val sortOrder = "${Telephony.Sms.DATE} ASC LIMIT $MAX_SCAN_BATCH_SIZE"
        
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                
                val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = cursor.getColumnIndex(Telephony.Sms.READ)
                val threadIndex = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val address = cursor.getString(addressIndex) ?: ""
                    val body = cursor.getString(bodyIndex) ?: ""
                    val date = cursor.getLong(dateIndex)
                    val type = cursor.getInt(typeIndex)
                    val read = cursor.getInt(readIndex) == 1
                    val threadId = cursor.getLong(threadIndex)
                    
                    smsMessages.add(
                        RawSmsMessage(
                            id = id,
                            phoneNumber = address,
                            body = body,
                            timestamp = date,
                            smsType = mapSmsType(type),
                            isRead = read,
                            threadId = threadId
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Logger.error("Permission denied while reading SMS", e)
        } catch (e: Exception) {
            Logger.error("Error reading SMS messages", e)
        }
        
        Logger.debug("Found ${smsMessages.size} SMS messages since $sinceTimestamp")
        smsMessages
    }

    /**
     * Group SMS messages by conversation (phone number and thread)
     */
    private fun groupMessagesByConversation(messages: List<RawSmsMessage>): List<SmsConversation> {
        val conversationMap = mutableMapOf<String, MutableList<RawSmsMessage>>()
        
        messages.forEach { message ->
            val key = "${message.phoneNumber}_${message.threadId}"
            conversationMap.getOrPut(key) { mutableListOf() }.add(message)
        }
        
        return conversationMap.map { (key, messages) ->
            val phoneNumber = messages.first().phoneNumber
            val threadId = messages.first().threadId
            
            SmsConversation(
                phoneNumber = phoneNumber,
                threadId = threadId,
                messages = messages.sortedBy { it.timestamp }
            )
        }
    }

    /**
     * Process SMS conversation with risk assessment
     */
    private suspend fun processSmsConversation(conversation: SmsConversation): SmsLogEntity = withContext(Dispatchers.IO) {
        // Resolve contact information
        val contactInfo = resolveContact(conversation.phoneNumber)
        
        // Analyze conversation content
        val contentAnalysis = analyzeConversationContent(conversation)
        
        // Assess risk level
        val riskScore = assessSmsRisk(conversation, contactInfo, contentAnalysis)
        
        // Determine SMS type (predominantly incoming or outgoing)
        val smsType = determinePredominantSmsType(conversation)
        
        // Create SMS log entity
        SmsLogEntity(
            id = generateUniqueId(conversation),
            phoneNumber = encryptionManager.encrypt(conversation.phoneNumber),
            contactName = contactInfo.name?.let { encryptionManager.encrypt(it) },
            messageCount = conversation.messages.size,
            smsType = smsType,
            timestamp = conversation.messages.maxOf { it.timestamp }, // Latest message timestamp
            isKnownContact = contactInfo.isKnown,
            riskScore = riskScore,
            frequencyPattern = identifyFrequencyPatterns(conversation),
            contentFlags = contentAnalysis.flags,
            threadId = conversation.threadId,
            syncStatus = SyncStatus.PENDING,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Resolve contact information with caching
     */
    private suspend fun resolveContact(phoneNumber: String): ContactInfo = withContext(Dispatchers.IO) {
        // Check cache first
        contactCache[phoneNumber]?.let { return@withContext it }
        
        // Use the same contact resolution logic as CallLogWatcher
        val contactInfo = queryContactsDatabase(phoneNumber)
        
        // Cache the result
        contactCache[phoneNumber] = contactInfo
        
        contactInfo
    }

    /**
     * Query contacts database for phone number (shared logic with CallLogWatcher)
     */
    private suspend fun queryContactsDatabase(phoneNumber: String): ContactInfo = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME,
                android.provider.ContactsContract.PhoneLookup.TYPE,
                android.provider.ContactsContract.PhoneLookup.LABEL
            )
            
            val uri = android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath(phoneNumber)
                .build()
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val typeIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.TYPE)
                    val labelIndex = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.LABEL)
                    
                    val name = cursor.getString(nameIndex)
                    val type = cursor.getInt(typeIndex)
                    val label = cursor.getString(labelIndex)
                    
                    return@withContext ContactInfo(
                        name = name,
                        isKnown = true,
                        phoneType = type,
                        phoneLabel = label
                    )
                }
            }
            
        } catch (e: Exception) {
            Logger.error("Error querying contacts database for SMS", e)
        }
        
        // Return unknown contact
        ContactInfo(
            name = null,
            isKnown = false
        )
    }

    /**
     * Analyze conversation content for suspicious patterns
     */
    private fun analyzeConversationContent(conversation: SmsConversation): ContentAnalysis {
        val flags = mutableSetOf<String>()
        var suspiciousScore = 0
        val allText = conversation.messages.joinToString(" ") { it.body.lowercase() }
        
        // Check for suspicious keywords
        SUSPICIOUS_KEYWORDS.forEach { keyword ->
            if (allText.contains(keyword)) {
                flags.add("SUSPICIOUS_KEYWORD_$keyword".uppercase())
                suspiciousScore += 1
            }
        }
        
        // Check for high-risk keywords
        HIGH_RISK_KEYWORDS.forEach { keyword ->
            if (allText.contains(keyword)) {
                flags.add("HIGH_RISK_KEYWORD_${keyword.replace(" ", "_")}".uppercase())
                suspiciousScore += 3
            }
        }
        
        // Check for URL patterns (potential phishing)
        val urlPattern = Regex("https?://[\\w.-]+")
        if (urlPattern.containsMatchIn(allText)) {
            flags.add("CONTAINS_URL")
            suspiciousScore += 2
        }
        
        // Check for phone number patterns (potential spoofing)
        val phonePattern = Regex("\\b\\d{10,11}\\b")
        if (phonePattern.containsMatchIn(allText)) {
            flags.add("CONTAINS_PHONE_NUMBER")
            suspiciousScore += 1
        }
        
        // Check for urgent language patterns
        val urgentPatterns = listOf("urgent", "immediately", "act now", "expires", "limited time")
        if (urgentPatterns.any { allText.contains(it) }) {
            flags.add("URGENT_LANGUAGE")
            suspiciousScore += 2
        }
        
        // Check for financial patterns
        val financialPatterns = listOf("$", "payment", "transfer", "account", "bank", "refund")
        if (financialPatterns.any { allText.contains(it) }) {
            flags.add("FINANCIAL_CONTENT")
            suspiciousScore += 2
        }
        
        // Check for verification code patterns
        val codePattern = Regex("\\b\\d{4,8}\\b")
        if (codePattern.containsMatchIn(allText) && allText.contains("code")) {
            flags.add("VERIFICATION_CODE")
            suspiciousScore += 1
        }
        
        return ContentAnalysis(
            flags = flags.toList(),
            suspiciousScore = suspiciousScore
        )
    }

    /**
     * Assess risk level for SMS conversation
     */
    private suspend fun assessSmsRisk(
        conversation: SmsConversation,
        contactInfo: ContactInfo,
        contentAnalysis: ContentAnalysis
    ): Int = withContext(Dispatchers.IO) {
        var riskScore = 0
        
        // Unknown contact base risk
        if (!contactInfo.isKnown) {
            riskScore += 3
        }
        
        // Content-based risk
        riskScore += contentAnalysis.suspiciousScore
        
        // Message frequency risk
        if (conversation.messages.size > 10) {
            riskScore += 2 // High volume conversations
        }
        
        // Time-based risk (messages at unusual hours)
        val nightTimeMessages = conversation.messages.count { message ->
            val hour = java.util.Calendar.getInstance().apply {
                timeInMillis = message.timestamp
            }.get(java.util.Calendar.HOUR_OF_DAY)
            hour < 7 || hour > 22
        }
        
        if (nightTimeMessages > 0) {
            riskScore += minOf(nightTimeMessages, 3) // Cap at 3 points
        }
        
        // One-way conversation risk (only incoming messages)
        val incomingCount = conversation.messages.count { it.smsType == "INCOMING" }
        val outgoingCount = conversation.messages.count { it.smsType == "OUTGOING" }
        
        if (incomingCount > 0 && outgoingCount == 0 && incomingCount > 2) {
            riskScore += 3 // Unsolicited bulk messages
        }
        
        // Frequency-based risk (many messages in short time)
        if (!contactInfo.isKnown) {
            val recentSmsFromNumber = database.smsLogDao()
                .getSmsFromNumberSince(
                    encryptionManager.encrypt(conversation.phoneNumber),
                    System.currentTimeMillis() - 24 * 60 * 60 * 1000 // Last 24 hours
                )
            
            if (recentSmsFromNumber.size >= FREQUENT_UNKNOWN_SMS_THRESHOLD) {
                riskScore += 5
            }
        }
        
        // International number risk
        if (conversation.phoneNumber.startsWith("+") && 
            !conversation.phoneNumber.startsWith("+1")) {
            riskScore += 2
        }
        
        // Short code risk (5-6 digit numbers can be legitimate but also suspicious)
        if (conversation.phoneNumber.matches(Regex("\\d{5,6}"))) {
            if (!contactInfo.isKnown) {
                riskScore += 1
            }
        }
        
        // Cap risk score at 10
        minOf(riskScore, 10)
    }

    /**
     * Determine predominant SMS type in conversation
     */
    private fun determinePredominantSmsType(conversation: SmsConversation): String {
        val incomingCount = conversation.messages.count { it.smsType == "INCOMING" }
        val outgoingCount = conversation.messages.count { it.smsType == "OUTGOING" }
        
        return when {
            incomingCount > outgoingCount -> "INCOMING"
            outgoingCount > incomingCount -> "OUTGOING"
            else -> "MIXED"
        }
    }

    /**
     * Identify frequency patterns in SMS conversation
     */
    private fun identifyFrequencyPatterns(conversation: SmsConversation): List<String> {
        val patterns = mutableListOf<String>()
        
        // High frequency pattern
        if (conversation.messages.size > 10) {
            patterns.add("HIGH_FREQUENCY")
        }
        
        // Rapid succession pattern (multiple messages within minutes)
        val rapidMessages = conversation.messages.zipWithNext { current, next ->
            (next.timestamp - current.timestamp) < 60000 // Less than 1 minute apart
        }.count { it }
        
        if (rapidMessages > 2) {
            patterns.add("RAPID_SUCCESSION")
        }
        
        // Burst pattern (many messages in short time window)
        val now = System.currentTimeMillis()
        val recentMessages = conversation.messages.count { 
            (now - it.timestamp) < 3600000 // Last hour
        }
        
        if (recentMessages > 5) {
            patterns.add("BURST_PATTERN")
        }
        
        // One-way pattern
        val incomingCount = conversation.messages.count { it.smsType == "INCOMING" }
        val outgoingCount = conversation.messages.count { it.smsType == "OUTGOING" }
        
        if (incomingCount > 0 && outgoingCount == 0) {
            patterns.add("ONE_WAY_INCOMING")
        } else if (outgoingCount > 0 && incomingCount == 0) {
            patterns.add("ONE_WAY_OUTGOING")
        }
        
        return patterns
    }

    /**
     * Generate unique ID for SMS conversation
     */
    private fun generateUniqueId(conversation: SmsConversation): String {
        val latestTimestamp = conversation.messages.maxOf { it.timestamp }
        return "${conversation.phoneNumber.hashCode()}_${conversation.threadId}_$latestTimestamp"
    }

    /**
     * Update last scan timestamp based on processed messages
     */
    private fun updateLastScanTimestamp(messages: List<RawSmsMessage>) {
        if (messages.isNotEmpty()) {
            lastScanTimestamp = messages.maxOf { it.timestamp }
            Logger.debug("Updated SMS scan timestamp to: $lastScanTimestamp")
        }
    }

    /**
     * Map SMS type integer to string
     */
    private fun mapSmsType(type: Int): String {
        return when (type) {
            Telephony.Sms.MESSAGE_TYPE_INBOX -> "INCOMING"
            Telephony.Sms.MESSAGE_TYPE_SENT -> "OUTGOING"
            Telephony.Sms.MESSAGE_TYPE_DRAFT -> "DRAFT"
            Telephony.Sms.MESSAGE_TYPE_OUTBOX -> "OUTBOX"
            Telephony.Sms.MESSAGE_TYPE_FAILED -> "FAILED"
            Telephony.Sms.MESSAGE_TYPE_QUEUED -> "QUEUED"
            else -> "UNKNOWN"
        }
    }

    /**
     * Clear caches to prevent memory issues
     */
    fun clearCaches() {
        contactCache.clear()
        conversationCache.clear()
        Logger.debug("SMS watcher caches cleared")
    }

    /**
     * Get cache statistics for monitoring
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            contactCacheSize = contactCache.size,
            conversationCacheSize = conversationCache.size
        )
    }

    // Data classes
    data class RawSmsMessage(
        val id: Long,
        val phoneNumber: String,
        val body: String,
        val timestamp: Long,
        val smsType: String,
        val isRead: Boolean,
        val threadId: Long
    )

    data class SmsConversation(
        val phoneNumber: String,
        val threadId: Long,
        val messages: List<RawSmsMessage>
    )

    data class ContactInfo(
        val name: String?,
        val isKnown: Boolean,
        val phoneType: Int = 0,
        val phoneLabel: String? = null
    )

    data class ContentAnalysis(
        val flags: List<String>,
        val suspiciousScore: Int
    )

    data class CacheStats(
        val contactCacheSize: Int,
        val conversationCacheSize: Int
    )
} 