package com.catamaran.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.catamaran.app.R
import com.catamaran.app.data.database.CatamaranDatabase
import com.catamaran.app.utils.EncryptionManager
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.PermissionManager
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.minutes

/**
 * Main background monitoring service for Catamaran
 * Coordinates call and SMS monitoring with battery optimization
 */
class MonitoringService : LifecycleService() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "catamaran_monitoring"
        private const val MONITORING_INTERVAL_MINUTES = 1L
        private const val SYNC_INTERVAL_MINUTES = 1L
        
        // Service actions
        const val ACTION_START_MONITORING = "start_monitoring"
        const val ACTION_STOP_MONITORING = "stop_monitoring"
        const val ACTION_SYNC_DATA = "sync_data"
        
        fun startService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            context.startForegroundService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }

    private lateinit var encryptionManager: EncryptionManager
    private lateinit var permissionManager: PermissionManager
    private lateinit var database: CatamaranDatabase
    private lateinit var callLogMonitor: CallLogMonitor
    private lateinit var smsMonitor: SmsMonitor
    private lateinit var dataSyncService: DataSyncService

    private var monitoringJob: Job? = null
    private var syncJob: Job? = null
    private var lastCallScanTime = 0L
    private var lastSmsScanTime = 0L

    override fun onCreate() {
        super.onCreate()
        Logger.info("MonitoringService created")
        
        // Initialize components
        encryptionManager = EncryptionManager(this)
        permissionManager = PermissionManager(this)
        database = CatamaranDatabase.getDatabase(this, encryptionManager)
        callLogMonitor = CallLogMonitor(this, encryptionManager, permissionManager)
        smsMonitor = SmsMonitor(this, encryptionManager, permissionManager)
        dataSyncService = DataSyncService(this, database)
        
        // Create notification channel
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            ACTION_SYNC_DATA -> syncData()
            else -> startMonitoring() // Default action
        }
        
        return START_STICKY // Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? {
        super.onBind(intent)
        return null // Not a bound service
    }

    private fun startMonitoring() {
        Logger.info("Starting monitoring service")
        
        // Check permissions before starting
        if (!permissionManager.canMonitorActivity()) {
            Logger.warning("Cannot start monitoring - insufficient permissions")
            stopSelf()
            return
        }
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Start monitoring job
        monitoringJob = lifecycleScope.launch {
            startPeriodicMonitoring()
        }
        
        // Start sync job
        syncJob = lifecycleScope.launch {
            startPeriodicSync()
        }
    }

    private fun stopMonitoring() {
        Logger.info("Stopping monitoring service")
        
        // Cancel monitoring jobs
        monitoringJob?.cancel()
        syncJob?.cancel()
        
        // Stop foreground service
        stopForeground(true)
        stopSelf()
    }

    private suspend fun startPeriodicMonitoring() {
        while (isActive) {
            try {
                Logger.debug("Running monitoring scan...")
                
                // Monitor calls if permission granted
                if (permissionManager.hasPermission(android.Manifest.permission.READ_CALL_LOG)) {
                    monitorCallLogs()
                }
                
                // Monitor SMS if permission granted
                if (permissionManager.hasPermission(android.Manifest.permission.READ_SMS)) {
                    monitorSmsLogs()
                }
                
                // Update notification with latest stats
                updateNotification()
                
                // Wait for next monitoring cycle
                delay(MONITORING_INTERVAL_MINUTES.minutes)
                
            } catch (e: Exception) {
                Logger.error("Error during monitoring cycle", e)
                delay(1.minutes) // Shorter delay on error
            }
        }
    }

    private suspend fun monitorCallLogs() {
        try {
            val newCallLogs = callLogMonitor.scanCallLogs(lastCallScanTime)
            
            if (newCallLogs.isNotEmpty()) {
                Logger.info("Found ${newCallLogs.size} new call logs")
                
                // Save to local database
                database.callLogDao().insertCallLogs(newCallLogs)
                
                // Update last scan time
                lastCallScanTime = System.currentTimeMillis()
                
                // Check for high-risk calls and trigger alerts
                checkForHighRiskCalls(newCallLogs)
            }
            
        } catch (e: Exception) {
            Logger.error("Error monitoring call logs", e)
        }
    }

    private suspend fun monitorSmsLogs() {
        try {
            val newSmsLogs = smsMonitor.scanSmsLogs(lastSmsScanTime)
            
            if (newSmsLogs.isNotEmpty()) {
                Logger.info("Found ${newSmsLogs.size} new SMS conversation groups")
                
                // Save to local database
                database.smsLogDao().insertSmsLogs(newSmsLogs)
                
                // Update last scan time
                lastSmsScanTime = System.currentTimeMillis()
                
                // Check for high-risk SMS patterns and trigger alerts
                checkForHighRiskSms(newSmsLogs)
            }
            
        } catch (e: Exception) {
            Logger.error("Error monitoring SMS logs", e)
        }
    }

    private suspend fun checkForHighRiskCalls(callLogs: List<com.catamaran.app.data.database.entities.CallLogEntity>) {
        val highRiskCalls = callLogs.filter { it.riskScore > 0.7f }
        
        if (highRiskCalls.isNotEmpty()) {
            Logger.warning("Detected ${highRiskCalls.size} high-risk calls")
            
            // TODO: Trigger family alerts through backend API
            // This would be implemented when the network service is ready
        }
    }

    private suspend fun checkForHighRiskSms(smsLogs: List<com.catamaran.app.data.database.entities.SmsLogEntity>) {
        val highRiskSms = smsLogs.filter { it.riskScore > 0.7f }
        
        if (highRiskSms.isNotEmpty()) {
            Logger.warning("Detected ${highRiskSms.size} high-risk SMS patterns")
            
            // TODO: Trigger family alerts through backend API
            // This would be implemented when the network service is ready
        }
    }

    private suspend fun startPeriodicSync() {
        while (isActive) {
            try {
                Logger.debug("Running data sync...")
                syncData()
                
                // Wait for next sync cycle
                delay(SYNC_INTERVAL_MINUTES.minutes)
                
            } catch (e: Exception) {
                Logger.error("Error during sync cycle", e)
                delay(5.minutes) // Longer delay on sync error
            }
        }
    }

    private fun syncData() {
        lifecycleScope.launch {
            try {
                dataSyncService.syncPendingData()
            } catch (e: Exception) {
                Logger.error("Error syncing data", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Catamaran Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background monitoring for family safety"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val capabilities = permissionManager.getMonitoringCapabilities()
        
        val statusText = when {
            capabilities.canMonitorCalls && capabilities.canMonitorSms -> "Monitoring calls and messages"
            capabilities.canMonitorCalls -> "Monitoring calls only"
            capabilities.canMonitorSms -> "Monitoring messages only"
            else -> "Limited monitoring - permissions needed"
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Catamaran Protection Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification) // You'll need to add this icon
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private suspend fun updateNotification() {
        try {
            // Get recent statistics
            val last24Hours = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            val callStats = callLogMonitor.getCallStatistics(last24Hours)
            val smsStats = smsMonitor.getSmsStatistics(last24Hours)
            
            val statusText = "Calls: ${callStats.totalCalls} | SMS: ${smsStats.totalSms} | Unknown: ${callStats.unknownCalls + smsStats.unknownSms}"
            
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Catamaran Protection Active")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, notification)
            
        } catch (e: Exception) {
            Logger.error("Error updating notification", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.info("MonitoringService destroyed")
        
        // Cancel all jobs
        monitoringJob?.cancel()
        syncJob?.cancel()
        
        // Close database
        database.close()
    }
} 