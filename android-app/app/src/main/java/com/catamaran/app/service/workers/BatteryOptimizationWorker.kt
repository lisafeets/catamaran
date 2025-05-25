package com.catamaran.app.service.workers

import android.content.Context
import androidx.work.*
import com.catamaran.app.service.BatteryOptimizationManager
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for battery optimization management
 * Features:
 * - Periodic battery optimization checks
 * - User notification and guidance
 * - Auto-ignore battery optimization when possible
 * - Battery usage statistics monitoring
 * - User education about power management
 */
class BatteryOptimizationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val CHECK_INTERVAL_HOURS = 12L
        private const val REMINDER_INTERVAL_DAYS = 3L
        
        // Input data keys
        const val KEY_CHECK_TYPE = "check_type"
        const val KEY_SHOW_NOTIFICATION = "show_notification"
        const val KEY_FORCE_CHECK = "force_check"
        
        // Output data keys
        const val KEY_OPTIMIZATION_STATUS = "optimization_status"
        const val KEY_ACTION_TAKEN = "action_taken"
        const val KEY_USER_GUIDANCE_NEEDED = "user_guidance_needed"
        
        // Check types
        const val CHECK_TYPE_ROUTINE = "routine"
        const val CHECK_TYPE_AFTER_INSTALL = "after_install"
        const val CHECK_TYPE_AFTER_UPDATE = "after_update"
        const val CHECK_TYPE_PERMISSION_LOST = "permission_lost"
        
        /**
         * Create periodic battery optimization check request
         */
        fun createPeriodicCheckRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<BatteryOptimizationWorker>(
                CHECK_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false) // Can run even when battery is low
                        .build()
                )
                .addTag("catamaran_battery")
                .addTag("periodic_battery_check")
                .build()
        }
        
        /**
         * Create immediate battery optimization check request
         */
        fun createImmediateCheckRequest(
            checkType: String = CHECK_TYPE_ROUTINE,
            showNotification: Boolean = true
        ): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_CHECK_TYPE, checkType)
                .putBoolean(KEY_SHOW_NOTIFICATION, showNotification)
                .putBoolean(KEY_FORCE_CHECK, true)
                .build()
            
            return OneTimeWorkRequestBuilder<BatteryOptimizationWorker>()
                .setInputData(inputData)
                .addTag("catamaran_battery")
                .addTag("immediate_battery_check")
                .build()
        }
    }

    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var notificationHelper: NotificationHelper

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Initialize components
            initializeComponents()
            
            // Get check parameters
            val checkType = inputData.getString(KEY_CHECK_TYPE) ?: CHECK_TYPE_ROUTINE
            val showNotification = inputData.getBoolean(KEY_SHOW_NOTIFICATION, true)
            val forceCheck = inputData.getBoolean(KEY_FORCE_CHECK, false)
            
            Logger.info("Starting battery optimization check - Type: $checkType")
            
            // Perform battery optimization check
            val checkResult = performBatteryOptimizationCheck(checkType, showNotification, forceCheck)
            
            // Create output data
            val outputData = Data.Builder()
                .putString(KEY_OPTIMIZATION_STATUS, checkResult.status)
                .putString(KEY_ACTION_TAKEN, checkResult.actionTaken)
                .putBoolean(KEY_USER_GUIDANCE_NEEDED, checkResult.userGuidanceNeeded)
                .build()
            
            // Log result
            Logger.info("Battery optimization check completed - Status: ${checkResult.status}, Action: ${checkResult.actionTaken}")
            
            Result.success(outputData)
            
        } catch (e: Exception) {
            Logger.error("Battery optimization worker encountered error", e)
            
            val errorData = Data.Builder()
                .putString("error_message", e.message ?: "Unknown error")
                .build()
            
            Result.failure(errorData)
        }
    }

    private fun initializeComponents() {
        batteryOptimizationManager = BatteryOptimizationManager(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
    }

    /**
     * Perform comprehensive battery optimization check
     */
    private suspend fun performBatteryOptimizationCheck(
        checkType: String,
        showNotification: Boolean,
        forceCheck: Boolean
    ): CheckResult = withContext(Dispatchers.IO) {
        
        // Check current battery optimization status
        val isOptimizationDisabled = batteryOptimizationManager.isOptimizationDisabled()
        val hasPermission = batteryOptimizationManager.hasIgnoreBatteryOptimizationPermission()
        
        Logger.debug("Battery optimization status - Disabled: $isOptimizationDisabled, Permission: $hasPermission")
        
        var actionTaken = "none"
        var userGuidanceNeeded = false
        
        when {
            // Battery optimization is already disabled - all good
            isOptimizationDisabled -> {
                Logger.info("Battery optimization is already disabled")
                
                if (showNotification && checkType == CHECK_TYPE_AFTER_INSTALL) {
                    notificationHelper.showBatteryOptimizationSuccessNotification()
                    actionTaken = "success_notification_shown"
                }
                
                // Check for any battery usage issues
                checkBatteryUsagePatterns()
            }
            
            // Has permission but optimization not disabled - try to disable automatically
            hasPermission -> {
                Logger.info("Attempting to disable battery optimization automatically")
                
                val disabled = batteryOptimizationManager.requestIgnoreBatteryOptimization()
                if (disabled) {
                    Logger.info("Successfully disabled battery optimization automatically")
                    actionTaken = "auto_disabled"
                    
                    if (showNotification) {
                        notificationHelper.showBatteryOptimizationSuccessNotification()
                    }
                } else {
                    Logger.warning("Failed to disable battery optimization automatically")
                    userGuidanceNeeded = true
                    actionTaken = "auto_disable_failed"
                    
                    if (showNotification) {
                        showBatteryOptimizationGuidance(checkType)
                    }
                }
            }
            
            // No permission - need user guidance
            else -> {
                Logger.warning("Battery optimization permission not granted")
                userGuidanceNeeded = true
                actionTaken = "guidance_needed"
                
                if (showNotification) {
                    showBatteryOptimizationGuidance(checkType)
                }
            }
        }
        
        // Update check statistics
        updateBatteryCheckStatistics(checkType, isOptimizationDisabled)
        
        CheckResult(
            status = if (isOptimizationDisabled) "disabled" else "enabled",
            actionTaken = actionTaken,
            userGuidanceNeeded = userGuidanceNeeded
        )
    }

    /**
     * Show appropriate battery optimization guidance to user
     */
    private suspend fun showBatteryOptimizationGuidance(checkType: String) = withContext(Dispatchers.IO) {
        when (checkType) {
            CHECK_TYPE_AFTER_INSTALL -> {
                notificationHelper.showBatteryOptimizationSetupNotification()
            }
            CHECK_TYPE_AFTER_UPDATE -> {
                notificationHelper.showBatteryOptimizationUpdateNotification()
            }
            CHECK_TYPE_PERMISSION_LOST -> {
                notificationHelper.showBatteryOptimizationPermissionLostNotification()
            }
            CHECK_TYPE_ROUTINE -> {
                // Only show routine reminders every few days
                if (shouldShowRoutineReminder()) {
                    notificationHelper.showBatteryOptimizationReminderNotification()
                }
            }
        }
    }

    /**
     * Check battery usage patterns for optimization recommendations
     */
    private suspend fun checkBatteryUsagePatterns() = withContext(Dispatchers.IO) {
        try {
            val batteryStats = batteryOptimizationManager.getBatteryUsageStats()
            
            // Check if app is using excessive battery
            if (batteryStats.isExcessiveUsage) {
                Logger.warning("App is using excessive battery: ${batteryStats.usagePercentage}%")
                notificationHelper.showBatteryUsageWarningNotification(batteryStats.usagePercentage)
            }
            
            // Check for battery optimization recommendations
            val recommendations = generateBatteryOptimizationRecommendations(batteryStats)
            if (recommendations.isNotEmpty()) {
                Logger.info("Generated ${recommendations.size} battery optimization recommendations")
                // Store recommendations for later display in app
                saveBatteryRecommendations(recommendations)
            }
            
        } catch (e: Exception) {
            Logger.error("Error checking battery usage patterns", e)
        }
    }

    /**
     * Generate battery optimization recommendations
     */
    private fun generateBatteryOptimizationRecommendations(batteryStats: BatteryOptimizationManager.BatteryStats): List<String> {
        val recommendations = mutableListOf<String>()
        
        // High usage recommendations
        if (batteryStats.usagePercentage > 5.0) {
            recommendations.add("Consider reducing monitoring frequency during low activity periods")
        }
        
        // Background activity recommendations
        if (batteryStats.backgroundActivityHigh) {
            recommendations.add("Optimize sync intervals to reduce background activity")
        }
        
        // Doze mode recommendations
        if (!batteryStats.isDozeWhitelisted) {
            recommendations.add("Add Catamaran to Doze mode whitelist for better reliability")
        }
        
        // Auto-start recommendations
        if (!batteryStats.isAutoStartEnabled) {
            recommendations.add("Enable auto-start permission for consistent monitoring")
        }
        
        return recommendations
    }

    /**
     * Check if routine reminder should be shown
     */
    private suspend fun shouldShowRoutineReminder(): Boolean = withContext(Dispatchers.IO) {
        val lastReminderTime = getLastReminderTime()
        val currentTime = System.currentTimeMillis()
        val daysSinceLastReminder = (currentTime - lastReminderTime) / (24 * 60 * 60 * 1000)
        
        return daysSinceLastReminder >= REMINDER_INTERVAL_DAYS
    }

    /**
     * Get last reminder time from preferences
     */
    private fun getLastReminderTime(): Long {
        val prefs = applicationContext.getSharedPreferences("catamaran_battery", Context.MODE_PRIVATE)
        return prefs.getLong("last_reminder_time", 0)
    }

    /**
     * Update battery check statistics
     */
    private suspend fun updateBatteryCheckStatistics(
        checkType: String,
        isOptimizationDisabled: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("catamaran_battery", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Update check count
            val checkCount = prefs.getInt("battery_check_count", 0) + 1
            editor.putInt("battery_check_count", checkCount)
            
            // Update last check time
            editor.putLong("last_battery_check", System.currentTimeMillis())
            
            // Update optimization status history
            val statusKey = "optimization_disabled_$checkType"
            editor.putBoolean(statusKey, isOptimizationDisabled)
            
            // Update reminder time if notification was shown
            if (checkType == CHECK_TYPE_ROUTINE) {
                editor.putLong("last_reminder_time", System.currentTimeMillis())
            }
            
            editor.apply()
            
            Logger.debug("Updated battery check statistics - Count: $checkCount, Status: $isOptimizationDisabled")
            
        } catch (e: Exception) {
            Logger.error("Error updating battery check statistics", e)
        }
    }

    /**
     * Save battery optimization recommendations
     */
    private suspend fun saveBatteryRecommendations(recommendations: List<String>) = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("catamaran_battery", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Save recommendations as JSON or comma-separated string
            val recommendationsString = recommendations.joinToString("|")
            editor.putString("battery_recommendations", recommendationsString)
            editor.putLong("recommendations_generated", System.currentTimeMillis())
            
            editor.apply()
            
            Logger.debug("Saved ${recommendations.size} battery recommendations")
            
        } catch (e: Exception) {
            Logger.error("Error saving battery recommendations", e)
        }
    }

    /**
     * Result of battery optimization check
     */
    data class CheckResult(
        val status: String,
        val actionTaken: String,
        val userGuidanceNeeded: Boolean
    )
} 