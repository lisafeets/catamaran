package com.catamaran.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.catamaran.app.R
import com.catamaran.app.data.database.CatamaranDatabase
import com.catamaran.app.service.workers.DataSyncWorker
import com.catamaran.app.service.workers.BatteryOptimizationWorker
import com.catamaran.app.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Production-ready background monitoring service for 24/7 phone activity monitoring
 * Features:
 * - Foreground service with persistent notification
 * - Content observers for real-time call/SMS detection
 * - WorkManager integration for reliable background tasks
 * - Battery optimization handling
 * - Network connectivity management
 * - Exponential backoff for failed operations
 * - Secure data handling with encryption
 */
class BackgroundMonitorService : LifecycleService() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "catamaran_monitoring"
        private const val CHANNEL_NAME = "Family Watch Monitoring"
        
        // Service actions
        const val ACTION_START_MONITORING = "start_monitoring"
        const val ACTION_STOP_MONITORING = "stop_monitoring"
        const val ACTION_FORCE_SYNC = "force_sync"
        const val ACTION_CHECK_BATTERY_OPTIMIZATION = "check_battery_optimization"
        
        // Monitoring intervals
        private const val HEALTH_CHECK_INTERVAL_MINUTES = 1L
        private const val SYNC_INTERVAL_MINUTES = 1L
        private const val BATTERY_CHECK_INTERVAL_HOURS = 6L
        
        // Retry configuration
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_SECONDS = 30L
        
        fun startService(context: Context) {
            val intent = Intent(context, BackgroundMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
        
        fun forceSyncData(context: Context) {
            val intent = Intent(context, BackgroundMonitorService::class.java).apply {
                action = ACTION_FORCE_SYNC
            }
            context.startService(intent)
        }
    }

    // Core components
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var database: CatamaranDatabase
    private lateinit var callLogWatcher: CallLogWatcher
    private lateinit var smsWatcher: SmsWatcher
    private lateinit var contactResolver: ContactResolver
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var powerManager: PowerManager
    private lateinit var workManager: WorkManager
    
    // Service state
    private val _serviceState = MutableStateFlow(ServiceState.STOPPED)
    val serviceState: StateFlow<ServiceState> = _serviceState
    
    private val _networkState = MutableStateFlow(NetworkState.UNKNOWN)
    val networkState: StateFlow<NetworkState> = _networkState
    
    // Jobs and observers
    private var healthCheckJob: Job? = null
    private var batteryCheckJob: Job? = null
    private var callLogObserver: ContentObserver? = null
    private var smsObserver: ContentObserver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // Statistics
    private var callsMonitored = 0
    private var smsMonitored = 0
    private var lastSyncTime = 0L
    private var retryCount = 0
    private val startTime = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        Logger.info("BackgroundMonitorService created")
        
        initializeComponents()
        createNotificationChannel()
        setupNetworkMonitoring()
        
        _serviceState.value = ServiceState.INITIALIZING
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_FORCE_SYNC -> forceSyncData()
            ACTION_CHECK_BATTERY_OPTIMIZATION -> checkBatteryOptimization()
            else -> startMonitoring() // Default action
        }
        
        return START_STICKY // Critical: Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.info("BackgroundMonitorService destroyed")
        
        stopMonitoring()
        unregisterNetworkCallback()
        
        _serviceState.value = ServiceState.STOPPED
    }

    private fun initializeComponents() {
        encryptionManager = EncryptionManager(this)
        permissionManager = PermissionManager(this)
        database = CatamaranDatabase.getDatabase(this, encryptionManager)
        callLogWatcher = CallLogWatcher(this, database, encryptionManager)
        smsWatcher = SmsWatcher(this, database, encryptionManager)
        contactResolver = ContactResolver(this)
        syncScheduler = SyncScheduler(this, database)
        batteryOptimizationManager = BatteryOptimizationManager(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        workManager = WorkManager.getInstance(this)
    }

    private fun startMonitoring() {
        Logger.info("Starting comprehensive monitoring")
        
        // Check critical permissions
        if (!permissionManager.canMonitorActivity()) {
            Logger.error("Cannot start monitoring - insufficient permissions")
            schedulePermissionReminder()
            stopSelf()
            return
        }
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        _serviceState.value = ServiceState.RUNNING
        
        // Setup content observers for real-time monitoring
        setupContentObservers()
        
        // Start periodic jobs
        startHealthCheckJob()
        startBatteryCheckJob()
        
        // Schedule WorkManager tasks
        scheduleWorkManagerTasks()
        
        // Reset retry count on successful start
        retryCount = 0
        
        Logger.info("Monitoring started successfully")
    }

    private fun stopMonitoring() {
        Logger.info("Stopping monitoring")
        
        // Cancel all jobs
        healthCheckJob?.cancel()
        batteryCheckJob?.cancel()
        
        // Unregister content observers
        unregisterContentObservers()
        
        // Cancel WorkManager tasks
        workManager.cancelAllWorkByTag("catamaran_monitoring")
        
        // Stop foreground service
        stopForeground(true)
        stopSelf()
        
        _serviceState.value = ServiceState.STOPPED
    }

    private fun setupContentObservers() {
        try {
            val handler = Handler(mainLooper)
            
            // Call log observer
            callLogObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    lifecycleScope.launch {
                        handleCallLogChange()
                    }
                }
            }
            
            // SMS observer
            smsObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    lifecycleScope.launch {
                        handleSmsChange()
                    }
                }
            }
            
            // Register observers
            contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callLogObserver!!
            )
            
            contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                smsObserver!!
            )
            
            Logger.info("Content observers registered successfully")
            
        } catch (e: Exception) {
            Logger.error("Failed to setup content observers", e)
            scheduleRetryWithBackoff()
        }
    }

    private fun unregisterContentObservers() {
        try {
            callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
            smsObserver?.let { contentResolver.unregisterContentObserver(it) }
            
            callLogObserver = null
            smsObserver = null
            
            Logger.info("Content observers unregistered")
            
        } catch (e: Exception) {
            Logger.error("Error unregistering content observers", e)
        }
    }

    private suspend fun handleCallLogChange() {
        try {
            Logger.debug("Call log change detected")
            
            val newCalls = callLogWatcher.processNewCallLogs()
            if (newCalls.isNotEmpty()) {
                callsMonitored += newCalls.size
                Logger.info("Processed ${newCalls.size} new calls")
                
                // TESTING MODE: Trigger immediate sync for ALL new calls (not just high-risk)
                Logger.info("Triggering immediate sync for ${newCalls.size} new calls (testing mode)")
                triggerImmediateSync()
                
                // Also check for high-risk calls
                val highRiskCalls = newCalls.filter { it.riskScore > 7 }
                if (highRiskCalls.isNotEmpty()) {
                    Logger.warning("Found ${highRiskCalls.size} high-risk calls - already triggered immediate sync")
                }
                
                updateNotification()
            }
            
        } catch (e: Exception) {
            Logger.error("Error handling call log change", e)
            scheduleRetryWithBackoff()
        }
    }

    private suspend fun handleSmsChange() {
        try {
            Logger.debug("SMS change detected")
            
            val newSms = smsWatcher.processNewSmsLogs()
            if (newSms.isNotEmpty()) {
                smsMonitored += newSms.size
                Logger.info("Processed ${newSms.size} new SMS")
                
                // TESTING MODE: Trigger immediate sync for ALL new SMS (not just high-risk)
                Logger.info("Triggering immediate sync for ${newSms.size} new SMS messages (testing mode)")
                triggerImmediateSync()
                
                // Also check for suspicious SMS patterns
                val suspiciousSms = newSms.filter { it.riskScore > 7 }
                if (suspiciousSms.isNotEmpty()) {
                    Logger.warning("Found ${suspiciousSms.size} suspicious SMS - already triggered immediate sync")
                }
                
                updateNotification()
            }
            
        } catch (e: Exception) {
            Logger.error("Error handling SMS change", e)
            scheduleRetryWithBackoff()
        }
    }

    private fun startHealthCheckJob() {
        healthCheckJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    performHealthCheck()
                    delay(HEALTH_CHECK_INTERVAL_MINUTES.minutes)
                } catch (e: Exception) {
                    Logger.error("Health check failed", e)
                    delay(1.minutes) // Shorter delay on error
                }
            }
        }
    }

    private fun startBatteryCheckJob() {
        batteryCheckJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    checkBatteryOptimization()
                    delay(BATTERY_CHECK_INTERVAL_HOURS * 60.minutes)
                } catch (e: Exception) {
                    Logger.error("Battery check failed", e)
                    delay(30.minutes) // Retry in 30 minutes on error
                }
            }
        }
    }

    private suspend fun performHealthCheck() {
        Logger.debug("Performing health check")
        
        // Check permissions
        if (!permissionManager.canMonitorActivity()) {
            Logger.warning("Permissions lost during monitoring")
            schedulePermissionReminder()
            return
        }
        
        // Check database health
        try {
            database.callLogDao().getRecentCallLogsCount(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        } catch (e: Exception) {
            Logger.error("Database health check failed", e)
            scheduleRetryWithBackoff()
            return
        }
        
        // Check network connectivity for sync
        if (_networkState.value == NetworkState.CONNECTED) {
            syncScheduler.scheduleSyncIfNeeded()
        }
        
        // Update notification with fresh stats
        updateNotification()
        
        Logger.debug("Health check completed successfully")
    }

    private fun checkBatteryOptimization() {
        if (!batteryOptimizationManager.isOptimizationDisabled()) {
            Logger.warning("Battery optimization not disabled - scheduling reminder")
            scheduleBatteryOptimizationReminder()
        } else {
            Logger.debug("Battery optimization properly configured")
        }
    }

    private fun setupNetworkMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _networkState.value = NetworkState.CONNECTED
                    Logger.info("Network connected - scheduling sync")
                    lifecycleScope.launch {
                        syncScheduler.scheduleSyncIfNeeded()
                    }
                }
                
                override fun onLost(network: Network) {
                    _networkState.value = NetworkState.DISCONNECTED
                    Logger.info("Network disconnected")
                }
                
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    
                    _networkState.value = when {
                        isWifi -> NetworkState.WIFI
                        isCellular -> NetworkState.CELLULAR
                        else -> NetworkState.CONNECTED
                    }
                }
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        }
    }

    private fun unregisterNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        }
    }

    private fun scheduleWorkManagerTasks() {
        // Data sync worker - runs every 1 minute for testing (changed from 15 minutes)
        val syncConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false) // Allow when battery is low for critical sync
            .build()
        
        val syncWorkRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(
            1, TimeUnit.MINUTES,  // Changed from 15 to 1 minute for testing
            1, TimeUnit.MINUTES   // Changed flex interval from 5 to 1 minute for testing
        )
            .setConstraints(syncConstraints)
            .addTag("catamaran_monitoring")
            .addTag("data_sync")
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .build()
        
        // Battery optimization reminder - runs daily
        val batteryWorkRequest = PeriodicWorkRequestBuilder<BatteryOptimizationWorker>(
            1, TimeUnit.DAYS
        )
            .addTag("catamaran_monitoring")
            .addTag("battery_check")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "catamaran_data_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncWorkRequest
        )
        
        workManager.enqueueUniquePeriodicWork(
            "catamaran_battery_check",
            ExistingPeriodicWorkPolicy.KEEP,
            batteryWorkRequest
        )
        
        Logger.info("WorkManager tasks scheduled")
    }

    private fun triggerImmediateSync() {
        val immediateSync = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("catamaran_monitoring")
            .addTag("immediate_sync")
            .build()
        
        workManager.enqueue(immediateSync)
        Logger.info("Immediate sync triggered")
    }

    private fun forceSyncData() {
        Logger.info("Force sync requested")
        triggerImmediateSync()
    }

    private fun scheduleRetryWithBackoff() {
        retryCount++
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Logger.error("Max retry attempts reached - stopping service")
            stopMonitoring()
            return
        }
        
        val backoffSeconds = INITIAL_BACKOFF_SECONDS * (1L shl (retryCount - 1))
        Logger.warning("Scheduling retry #$retryCount in ${backoffSeconds}s")
        
        lifecycleScope.launch {
            delay(backoffSeconds.seconds)
            if (_serviceState.value == ServiceState.RUNNING) {
                setupContentObservers()
            }
        }
    }

    private fun schedulePermissionReminder() {
        // TODO: Schedule notification to remind user about permissions
        Logger.warning("Permission reminder needed")
    }

    private fun scheduleBatteryOptimizationReminder() {
        // TODO: Schedule notification to guide user through battery optimization
        Logger.warning("Battery optimization reminder needed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors your phone activity to keep family connected"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, BackgroundMonitorService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val syncIntent = Intent(this, BackgroundMonitorService::class.java).apply {
            action = ACTION_FORCE_SYNC
        }
        val syncPendingIntent = PendingIntent.getService(
            this, 1, syncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Catamaran Family Watch")
            .setContentText(getNotificationText())
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setShowWhen(true)
            .setWhen(startTime)
            .addAction(R.drawable.ic_sync, "Sync", syncPendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(getDetailedNotificationText()))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun getNotificationText(): String {
        val runtime = (System.currentTimeMillis() - startTime) / 1000 / 60 // minutes
        return "Active ${runtime}m • $callsMonitored calls • $smsMonitored messages"
    }

    private fun getDetailedNotificationText(): String {
        val networkStatus = when (_networkState.value) {
            NetworkState.WIFI -> "WiFi"
            NetworkState.CELLULAR -> "Mobile"
            NetworkState.CONNECTED -> "Connected"
            NetworkState.DISCONNECTED -> "Offline"
            NetworkState.UNKNOWN -> "Unknown"
        }
        
        val lastSyncText = if (lastSyncTime > 0) {
            val minutesAgo = (System.currentTimeMillis() - lastSyncTime) / 1000 / 60
            "Last sync: ${minutesAgo}m ago"
        } else {
            "No sync yet"
        }
        
        return "Monitoring: $callsMonitored calls, $smsMonitored messages\n" +
                "Network: $networkStatus • $lastSyncText\n" +
                "Keeping your family connected and safe"
    }

    enum class ServiceState {
        STOPPED, INITIALIZING, RUNNING, ERROR
    }

    enum class NetworkState {
        UNKNOWN, CONNECTED, DISCONNECTED, WIFI, CELLULAR
    }
} 