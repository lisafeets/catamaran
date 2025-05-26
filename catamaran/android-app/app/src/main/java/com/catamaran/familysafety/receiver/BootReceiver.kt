package com.catamaran.familysafety.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.catamaran.familysafety.CatamaranApplication
import com.catamaran.familysafety.service.MonitoringService

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Check if monitoring was enabled before restart
                val application = context.applicationContext as CatamaranApplication
                val preferenceManager = application.preferenceManager
                
                if (preferenceManager.isMonitoringEnabled()) {
                    // Restart monitoring service
                    val serviceIntent = Intent(context, MonitoringService::class.java)
                    ContextCompat.startForegroundService(context, serviceIntent)
                    
                    android.util.Log.i("BootReceiver", "Monitoring service restarted after boot")
                }
            }
        }
    }
} 