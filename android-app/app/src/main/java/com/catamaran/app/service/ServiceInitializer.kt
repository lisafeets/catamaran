package com.catamaran.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.PermissionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service initializer for managing background monitoring services
 * Features:
 * - Automatic service startup and shutdown
 * - Permission validation before starting services
 * - Lifecycle-aware service management
 * - Graceful error handling and recovery
 * - Boot receiver integration
 */
class ServiceInitializer(private val context: Context) : DefaultLifecycleObserver {

    companion object {
        private const val PREFS_NAME = "catamaran_service_prefs"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        
        @Volatile
        private var INSTANCE: ServiceInitializer? = null
        
        fun getInstance(context: Context): ServiceInitializer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServiceInitializer(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val permissionManager = PermissionManager(context)
    private val workManager = WorkManager.getInstance(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private var isServiceRunning = false
    private var isInitialized = false

    init {
        // Register for app lifecycle events
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * Initialize the service system
     */
    fun initialize() {
        if (isInitialized) {
            Logger.debug("ServiceInitializer already initialized")
            return
        }
        
        Logger.info("Initializing Catamaran background services")
        
        applicationScope.launch {
            try {
                // Check if this is first launch
                if (isFirstLaunch()) {
                    handleFirstLaunch()
                }
                
                // Check if service should be running
                if (isServiceEnabled() && hasRequiredPermissions()) {
                    startBackgroundServices()
                } else {
                    Logger.info("Service not started - permissions or settings not ready")
                }
                
                isInitialized = true
                Logger.info("ServiceInitializer initialization complete")
                
            } catch (e: Exception) {
                Logger.error("Error during service initialization", e)
            }
        }
    }

    /**
     * Start background monitoring services
     */
    fun startBackgroundServices() {
        if (isServiceRunning) {
            Logger.debug("Background services already running")
            return
        }
        
        if (!hasRequiredPermissions()) {
            Logger.warning("Cannot start services - missing required permissions")
            return
        }
        
        try {
            Logger.info("Starting background monitoring services")
            
            // Start the main background monitor service
            val serviceIntent = Intent(context, BackgroundMonitorService::class.java).apply {
                action = BackgroundMonitorService.ACTION_START_MONITORING
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Mark service as enabled
            setServiceEnabled(true)
            isServiceRunning = true
            
            Logger.info("Background services started successfully")
            
        } catch (e: Exception) {
            Logger.error("Failed to start background services", e)
            isServiceRunning = false
        }
    }

    /**
     * Stop background monitoring services
     */
    fun stopBackgroundServices() {
        if (!isServiceRunning) {
            Logger.debug("Background services not running")
            return
        }
        
        try {
            Logger.info("Stopping background monitoring services")
            
            // Stop the main background monitor service
            val serviceIntent = Intent(context, BackgroundMonitorService::class.java).apply {
                action = BackgroundMonitorService.ACTION_STOP_MONITORING
            }
            context.startService(serviceIntent)
            
            // Cancel all WorkManager tasks
            workManager.cancelAllWorkByTag("catamaran_monitoring")
            
            // Mark service as disabled
            setServiceEnabled(false)
            isServiceRunning = false
            
            Logger.info("Background services stopped successfully")
            
        } catch (e: Exception) {
            Logger.error("Failed to stop background services", e)
        }
    }

    /**
     * Restart background services (useful after permission changes)
     */
    fun restartBackgroundServices() {
        Logger.info("Restarting background services")
        stopBackgroundServices()
        
        // Small delay to ensure clean shutdown
        applicationScope.launch {
            kotlinx.coroutines.delay(1000)
            startBackgroundServices()
        }
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        return permissionManager.canMonitorActivity()
    }

    /**
     * Check if background services are enabled
     */
    fun isServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
    }

    /**
     * Enable or disable background services
     */
    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        
        if (enabled && hasRequiredPermissions()) {
            startBackgroundServices()
        } else if (!enabled) {
            stopBackgroundServices()
        }
    }

    /**
     * Check if this is the first app launch
     */
    private fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    /**
     * Handle first launch setup
     */
    private fun handleFirstLaunch() {
        Logger.info("First launch detected - setting up defaults")
        
        // Mark first launch as complete
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
        
        // Set default service state (disabled until user grants permissions)
        setServiceEnabled(false)
    }

    /**
     * Get current service status
     */
    fun getServiceStatus(): ServiceStatus {
        return ServiceStatus(
            isRunning = isServiceRunning,
            isEnabled = isServiceEnabled(),
            hasPermissions = hasRequiredPermissions(),
            isInitialized = isInitialized
        )
    }

    // Lifecycle callbacks
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Logger.debug("App moved to foreground")
        
        // Check if services should be running when app comes to foreground
        if (isServiceEnabled() && hasRequiredPermissions() && !isServiceRunning) {
            startBackgroundServices()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Logger.debug("App moved to background")
        
        // Services should continue running in background
        // This is just for logging/monitoring purposes
    }

    /**
     * Handle device boot completion
     */
    fun onBootCompleted() {
        Logger.info("Device boot completed - checking service state")
        
        if (isServiceEnabled() && hasRequiredPermissions()) {
            // Small delay to ensure system is ready
            applicationScope.launch {
                kotlinx.coroutines.delay(5000) // 5 second delay
                startBackgroundServices()
            }
        }
    }

    /**
     * Handle permission changes
     */
    fun onPermissionsChanged() {
        Logger.info("Permissions changed - updating service state")
        
        if (isServiceEnabled()) {
            if (hasRequiredPermissions()) {
                if (!isServiceRunning) {
                    startBackgroundServices()
                }
            } else {
                if (isServiceRunning) {
                    stopBackgroundServices()
                }
            }
        }
    }

    /**
     * Force immediate data sync
     */
    fun forceSyncData() {
        if (isServiceRunning) {
            BackgroundMonitorService.forceSyncData(context)
        } else {
            Logger.warning("Cannot force sync - service not running")
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        Logger.info("Cleaning up ServiceInitializer")
        
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        stopBackgroundServices()
        
        isInitialized = false
        INSTANCE = null
    }

    // Data classes
    data class ServiceStatus(
        val isRunning: Boolean,
        val isEnabled: Boolean,
        val hasPermissions: Boolean,
        val isInitialized: Boolean
    ) {
        val canStart: Boolean
            get() = !isRunning && isEnabled && hasPermissions && isInitialized
        
        val shouldStart: Boolean
            get() = isEnabled && hasPermissions && !isRunning
    }
} 