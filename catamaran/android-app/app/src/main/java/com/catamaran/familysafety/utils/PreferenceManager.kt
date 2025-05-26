package com.catamaran.familysafety.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Monitoring settings
    fun isMonitoringEnabled(): Boolean = 
        sharedPreferences.getBoolean(KEY_MONITORING_ENABLED, false)

    fun setMonitoringEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_MONITORING_ENABLED, enabled)
            .apply()
    }

    // Sync settings
    fun getLastSyncTime(): Long = 
        sharedPreferences.getLong(KEY_LAST_SYNC_TIME, 0L)

    fun setLastSyncTime(timestamp: Long) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_SYNC_TIME, timestamp)
            .apply()
    }

    fun getSyncFrequency(): Int = 
        sharedPreferences.getInt(KEY_SYNC_FREQUENCY, DEFAULT_SYNC_FREQUENCY)

    fun setSyncFrequency(minutes: Int) {
        sharedPreferences.edit()
            .putInt(KEY_SYNC_FREQUENCY, minutes)
            .apply()
    }

    // Emergency contact
    fun getEmergencyContact(): String = 
        sharedPreferences.getString(KEY_EMERGENCY_CONTACT, "") ?: ""

    fun setEmergencyContact(phoneNumber: String) {
        sharedPreferences.edit()
            .putString(KEY_EMERGENCY_CONTACT, phoneNumber)
            .apply()
    }

    // User authentication
    fun getAuthToken(): String = 
        sharedPreferences.getString(KEY_AUTH_TOKEN, "") ?: ""

    fun setAuthToken(token: String) {
        sharedPreferences.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .apply()
    }

    fun getUserId(): String = 
        sharedPreferences.getString(KEY_USER_ID, "") ?: ""

    fun setUserId(userId: String) {
        sharedPreferences.edit()
            .putString(KEY_USER_ID, userId)
            .apply()
    }

    fun isLoggedIn(): Boolean = 
        getAuthToken().isNotEmpty() && getUserId().isNotEmpty()

    // API settings
    fun getApiBaseUrl(): String = 
        sharedPreferences.getString(KEY_API_BASE_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL

    fun setApiBaseUrl(url: String) {
        sharedPreferences.edit()
            .putString(KEY_API_BASE_URL, url)
            .apply()
    }

    // First run
    fun isFirstRun(): Boolean = 
        sharedPreferences.getBoolean(KEY_FIRST_RUN, true)

    fun setFirstRunCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_FIRST_RUN, false)
            .apply()
    }

    // Clear all data (logout)
    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "catamaran_prefs"
        
        // Keys
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_SYNC_FREQUENCY = "sync_frequency"
        private const val KEY_EMERGENCY_CONTACT = "emergency_contact"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_API_BASE_URL = "api_base_url"
        private const val KEY_FIRST_RUN = "first_run"
        
        // Defaults
        private const val DEFAULT_SYNC_FREQUENCY = 30 // minutes
        private const val DEFAULT_API_URL = "https://catamaran-production-3422.up.railway.app"
    }
} 