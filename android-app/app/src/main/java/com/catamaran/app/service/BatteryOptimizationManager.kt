package com.catamaran.app.service

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.catamaran.app.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comprehensive battery optimization manager for ensuring reliable background monitoring
 * Features:
 * - Battery optimization detection and management
 * - Doze mode whitelist management
 * - Auto-start permission handling
 * - Battery usage statistics
 * - Device-specific optimization guidance
 * - User-friendly permission requests
 */
class BatteryOptimizationManager(private val context: Context) {

    companion object {
        private const val BATTERY_USAGE_THRESHOLD = 5.0 // 5% battery usage threshold
        private const val BACKGROUND_ACTIVITY_THRESHOLD = 80 // 80% background activity threshold
        
        // Device manufacturer specific intents for power management settings
        private val POWER_MANAGER_INTENTS = mapOf(
            "xiaomi" to arrayOf(
                "com.miui.securitycenter.permission.AppPermissionsEditorActivity",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            "oppo" to arrayOf(
                "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            "vivo" to arrayOf(
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            "huawei" to arrayOf(
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            ),
            "samsung" to arrayOf(
                "com.samsung.android.lool.looldutySetting.ui.activity.SleepingAppsActivity",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ),
            "oneplus" to arrayOf(
                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
            )
        )
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    /**
     * Check if battery optimization is disabled for the app
     */
    fun isOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true // No battery optimization on pre-M devices
        }
    }

    /**
     * Check if app has permission to ignore battery optimization
     */
    fun hasIgnoreBatteryOptimizationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            packageManager.checkPermission(
                android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                packageName
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No battery optimization on pre-M devices
        }
    }

    /**
     * Request to ignore battery optimization (requires permission)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun requestIgnoreBatteryOptimization(): Boolean {
        return try {
            if (hasIgnoreBatteryOptimizationPermission()) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } else {
                Logger.warning("App does not have REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission")
                false
            }
        } catch (e: Exception) {
            Logger.error("Failed to request battery optimization ignore", e)
            false
        }
    }

    /**
     * Open battery optimization settings for the app
     */
    fun openBatteryOptimizationSettings(activity: Activity? = null): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            }
            
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            Logger.error("Failed to open battery optimization settings", e)
            false
        }
    }

    /**
     * Open device-specific power management settings
     */
    fun openDeviceSpecificPowerSettings(activity: Activity? = null): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val deviceIntents = POWER_MANAGER_INTENTS[manufacturer]
        
        if (deviceIntents != null) {
            for (intentAction in deviceIntents) {
                try {
                    val intent = Intent().apply {
                        component = android.content.ComponentName("com.android.settings", intentAction)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    
                    if (activity != null) {
                        activity.startActivity(intent)
                    } else {
                        context.startActivity(intent)
                    }
                    
                    Logger.info("Opened device-specific power settings for $manufacturer")
                    return true
                    
                } catch (e: Exception) {
                    Logger.debug("Failed to open $intentAction for $manufacturer", e)
                    // Try next intent
                }
            }
        }
        
        // Fallback to general battery settings
        return openGeneralBatterySettings(activity)
    }

    /**
     * Open general battery settings as fallback
     */
    private fun openGeneralBatterySettings(activity: Activity? = null): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            Logger.error("Failed to open general battery settings", e)
            false
        }
    }

    /**
     * Check if app is whitelisted in Doze mode
     */
    fun isDozeWhitelisted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true // No Doze mode on pre-M devices
        }
    }

    /**
     * Check if auto-start is enabled (device-specific)
     */
    fun isAutoStartEnabled(): Boolean {
        // This is device-specific and hard to detect programmatically
        // We'll use heuristics based on app behavior
        return try {
            val prefs = context.getSharedPreferences("catamaran_battery", Context.MODE_PRIVATE)
            prefs.getBoolean("auto_start_enabled", false)
        } catch (e: Exception) {
            Logger.error("Error checking auto-start status", e)
            false
        }
    }

    /**
     * Get comprehensive battery usage statistics
     */
    suspend fun getBatteryUsageStats(): BatteryStats = withContext(Dispatchers.IO) {
        try {
            val stats = getBatteryUsageInternal()
            Logger.debug("Battery usage stats - Usage: ${stats.usagePercentage}%, Background: ${stats.backgroundActivityHigh}")
            stats
        } catch (e: Exception) {
            Logger.error("Error getting battery usage stats", e)
            BatteryStats() // Return default stats on error
        }
    }

    /**
     * Internal method to get battery usage statistics
     */
    @SuppressLint("BatteryLife")
    private suspend fun getBatteryUsageInternal(): BatteryStats = withContext(Dispatchers.IO) {
        var usagePercentage = 0.0
        var backgroundActivityHigh = false
        var isExcessiveUsage = false
        
        try {
            // On newer Android versions, we can get more detailed battery stats
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // This would require BATTERY_STATS permission which is system-level
                // For now, we'll use heuristics and stored data
                
                val prefs = context.getSharedPreferences("catamaran_battery", Context.MODE_PRIVATE)
                usagePercentage = prefs.getFloat("last_usage_percentage", 0.0f).toDouble()
                backgroundActivityHigh = prefs.getBoolean("background_activity_high", false)
                
                // Consider usage excessive if over threshold
                isExcessiveUsage = usagePercentage > BATTERY_USAGE_THRESHOLD
            }
            
        } catch (e: Exception) {
            Logger.error("Error getting detailed battery stats", e)
        }
        
        BatteryStats(
            usagePercentage = usagePercentage,
            backgroundActivityHigh = backgroundActivityHigh,
            isExcessiveUsage = isExcessiveUsage,
            isDozeWhitelisted = isDozeWhitelisted(),
            isAutoStartEnabled = isAutoStartEnabled()
        )
    }

    /**
     * Update battery usage statistics (called periodically by monitoring service)
     */
    suspend fun updateBatteryUsageStats(
        serviceRuntime: Long,
        syncCount: Int,
        backgroundActivity: Double
    ) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("catamaran_battery", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Estimate usage based on service activity
            val estimatedUsage = calculateEstimatedUsage(serviceRuntime, syncCount, backgroundActivity)
            
            editor.putFloat("last_usage_percentage", estimatedUsage.toFloat())
            editor.putBoolean("background_activity_high", backgroundActivity > BACKGROUND_ACTIVITY_THRESHOLD)
            editor.putLong("last_stats_update", System.currentTimeMillis())
            editor.putLong("service_runtime", serviceRuntime)
            editor.putInt("sync_count", syncCount)
            
            editor.apply()
            
            Logger.debug("Updated battery usage stats - Estimated: ${estimatedUsage}%, Background: $backgroundActivity%")
            
        } catch (e: Exception) {
            Logger.error("Error updating battery usage stats", e)
        }
    }

    /**
     * Calculate estimated battery usage based on service activity
     */
    private fun calculateEstimatedUsage(
        serviceRuntime: Long,
        syncCount: Int,
        backgroundActivity: Double
    ): Double {
        // Simple heuristic calculation
        // This is an estimate since we can't get actual battery usage without system permissions
        
        val runtimeHours = serviceRuntime / (1000.0 * 60.0 * 60.0)
        val baseUsage = runtimeHours * 0.5 // 0.5% per hour base usage
        val syncUsage = syncCount * 0.01 // 0.01% per sync operation
        val backgroundUsage = (backgroundActivity / 100.0) * 1.0 // Up to 1% for high background activity
        
        return (baseUsage + syncUsage + backgroundUsage).coerceIn(0.0, 10.0) // Cap at 10%
    }

    /**
     * Get device-specific battery optimization guidance
     */
    fun getDeviceSpecificGuidance(): BatteryOptimizationGuidance {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                BatteryOptimizationGuidance(
                    manufacturer = "Xiaomi/MIUI",
                    steps = listOf(
                        "Open Security app",
                        "Go to 'App lock'",
                        "Add Catamaran to protected apps",
                        "Go to 'Battery & Performance'",
                        "Choose 'No restrictions' for Catamaran",
                        "Enable 'Auto start' for Catamaran"
                    ),
                    criticalSettings = listOf("Protected apps", "Auto start", "Battery restrictions")
                )
            }
            
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                BatteryOptimizationGuidance(
                    manufacturer = "Huawei/EMUI",
                    steps = listOf(
                        "Open Phone Manager",
                        "Go to 'Protected apps'",
                        "Enable protection for Catamaran",
                        "Go to 'Battery optimization'",
                        "Set Catamaran to 'Don't optimize'",
                        "Check 'Auto launch' settings"
                    ),
                    criticalSettings = listOf("Protected apps", "Auto launch", "Battery optimization")
                )
            }
            
            manufacturer.contains("oppo") -> {
                BatteryOptimizationGuidance(
                    manufacturer = "OPPO/ColorOS",
                    steps = listOf(
                        "Open Security app",
                        "Go to 'Privacy permissions'",
                        "Select 'Startup manager'",
                        "Allow Catamaran to auto-start",
                        "Go to 'Battery optimization'",
                        "Set Catamaran to 'Don't optimize'"
                    ),
                    criticalSettings = listOf("Startup manager", "Battery optimization", "Background app management")
                )
            }
            
            manufacturer.contains("vivo") -> {
                BatteryOptimizationGuidance(
                    manufacturer = "Vivo/FuntouchOS",
                    steps = listOf(
                        "Open i Manager",
                        "Go to 'App manager'",
                        "Select 'Autostart manager'",
                        "Enable auto-start for Catamaran",
                        "Go to 'Battery optimization'",
                        "Add Catamaran to whitelist"
                    ),
                    criticalSettings = listOf("Autostart manager", "Background app management", "Battery whitelist")
                )
            }
            
            manufacturer.contains("samsung") -> {
                BatteryOptimizationGuidance(
                    manufacturer = "Samsung/One UI",
                    steps = listOf(
                        "Open Device Care",
                        "Go to 'Battery'",
                        "Select 'App power management'",
                        "Add Catamaran to 'Apps that won't be put to sleep'",
                        "Disable 'Adaptive battery' or exclude Catamaran",
                        "Check 'Auto restart' settings"
                    ),
                    criticalSettings = listOf("Sleeping apps", "Adaptive battery", "Auto restart")
                )
            }
            
            manufacturer.contains("oneplus") -> {
                BatteryOptimizationGuidance(
                    manufacturer = "OnePlus/OxygenOS",
                    steps = listOf(
                        "Open Settings",
                        "Go to 'Battery'",
                        "Select 'Battery optimization'",
                        "Set Catamaran to 'Don't optimize'",
                        "Go to 'Advanced optimization'",
                        "Disable for Catamaran"
                    ),
                    criticalSettings = listOf("Battery optimization", "Advanced optimization", "App auto-launch")
                )
            }
            
            else -> {
                BatteryOptimizationGuidance(
                    manufacturer = "Android (Stock)",
                    steps = listOf(
                        "Open Settings",
                        "Go to 'Apps & notifications'",
                        "Select 'Advanced' > 'Special app access'",
                        "Choose 'Battery optimization'",
                        "Find Catamaran and select 'Don't optimize'",
                        "Confirm the change"
                    ),
                    criticalSettings = listOf("Battery optimization", "Background activity")
                )
            }
        }
    }

    /**
     * Data class for battery usage statistics
     */
    data class BatteryStats(
        val usagePercentage: Double = 0.0,
        val backgroundActivityHigh: Boolean = false,
        val isExcessiveUsage: Boolean = false,
        val isDozeWhitelisted: Boolean = false,
        val isAutoStartEnabled: Boolean = false
    )

    /**
     * Data class for device-specific battery optimization guidance
     */
    data class BatteryOptimizationGuidance(
        val manufacturer: String,
        val steps: List<String>,
        val criticalSettings: List<String>
    )
} 