package com.catamaran.familysafety

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.catamaran.familysafety.data.database.CatamaranDatabase
import com.catamaran.familysafety.data.repository.MonitoringRepository
import com.catamaran.familysafety.network.ApiService
import com.catamaran.familysafety.utils.PreferenceManager

class CatamaranApplication : Application() {

    // Lazy initialization of database
    val database by lazy { CatamaranDatabase.getDatabase(this) }
    
    // Lazy initialization of repository
    val repository by lazy { MonitoringRepository(database.monitoringDao(), ApiService.create()) }
    
    // Preference manager
    val preferenceManager by lazy { PreferenceManager(this) }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize WorkManager
        initializeWorkManager()
        
        // Create notification channels
        createNotificationChannels()
    }

private fun initializeWorkManager() {
    // Check if WorkManager is already initialized
    try {
        WorkManager.getInstance(this)
        // Already initialized, no need to do anything
    } catch (e: IllegalStateException) {
        // Not initialized yet, so initialize it
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
        WorkManager.initialize(this, config)
    }
}

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Monitoring service channel
            val monitoringChannel = NotificationChannel(
                MONITORING_CHANNEL_ID,
                "Family Safety Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when family safety monitoring is active"
                setShowBadge(false)
            }
            
            // Alerts channel
            val alertsChannel = NotificationChannel(
                ALERTS_CHANNEL_ID,
                "Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important safety alerts and notifications"
            }
            
            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    companion object {
        const val MONITORING_CHANNEL_ID = "monitoring_channel"
        const val ALERTS_CHANNEL_ID = "alerts_channel"
    }
} 