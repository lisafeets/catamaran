package com.catamaran.familysafety.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.catamaran.familysafety.R
import com.catamaran.familysafety.ui.MainActivity
import com.catamaran.familysafety.utils.DataCollector
import kotlinx.coroutines.*

class MonitoringService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataCollector: DataCollector
    private var callLogObserver: ContentObserver? = null
    private var smsObserver: ContentObserver? = null
    
    override fun onCreate() {
        super.onCreate()
        dataCollector = DataCollector(this)
        createNotificationChannel()
        setupContentObservers()
        Log.d("MonitoringService", "Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            else -> startMonitoring()
        }
        return START_STICKY // Restart if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        serviceScope.cancel()
        Log.d("MonitoringService", "Service destroyed")
    }
    
    private fun startMonitoring() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Register content observers
        registerContentObservers()
        
        // Schedule periodic data collection
        scheduleDataCollection()
        
        Log.d("MonitoringService", "Monitoring started")
    }
    
    private fun stopMonitoring() {
        unregisterContentObservers()
        stopForeground(true)
        stopSelf()
        Log.d("MonitoringService", "Monitoring stopped")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Family Safety Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous monitoring of phone activity for family safety"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Family Safety Active")
            .setContentText("Monitoring phone activity for family safety")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun setupContentObservers() {
        val handler = Handler(Looper.getMainLooper())
        
        callLogObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.d("MonitoringService", "Call log changed: $uri")
                serviceScope.launch {
                    dataCollector.collectNewActivity()
                }
            }
        }
        
        smsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                Log.d("MonitoringService", "SMS changed: $uri")
                serviceScope.launch {
                    dataCollector.collectNewActivity()
                }
            }
        }
    }
    
    private fun registerContentObservers() {
        try {
            callLogObserver?.let { observer ->
                contentResolver.registerContentObserver(
                    CallLog.Calls.CONTENT_URI,
                    true,
                    observer
                )
            }
            
            smsObserver?.let { observer ->
                contentResolver.registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    observer
                )
            }
            
            Log.d("MonitoringService", "Content observers registered")
        } catch (e: SecurityException) {
            Log.e("MonitoringService", "Permission denied for content observers", e)
        }
    }
    
    private fun unregisterContentObservers() {
        try {
            callLogObserver?.let { contentResolver.unregisterContentObserver(it) }
            smsObserver?.let { contentResolver.unregisterContentObserver(it) }
            Log.d("MonitoringService", "Content observers unregistered")
        } catch (e: Exception) {
            Log.e("MonitoringService", "Error unregistering content observers", e)
        }
    }
    
    private fun scheduleDataCollection() {
        serviceScope.launch {
            while (isActive) {
                try {
                    dataCollector.collectNewActivity()
                    delay(DATA_COLLECTION_INTERVAL)
                } catch (e: Exception) {
                    Log.e("MonitoringService", "Error during scheduled data collection", e)
                    delay(60000) // Wait 1 minute before retrying
                }
            }
        }
    }
    
    companion object {
        const val ACTION_START_MONITORING = "START_MONITORING"
        const val ACTION_STOP_MONITORING = "STOP_MONITORING"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "monitoring_channel"
        private const val DATA_COLLECTION_INTERVAL = 5 * 60 * 1000L // 5 minutes
        
        fun startService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }
} 