package com.catamaran.familysafety.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed")
            
            // Check if monitoring was enabled before reboot
            val preferences = context.getSharedPreferences("catamaran_prefs", Context.MODE_PRIVATE)
            val wasMonitoringEnabled = preferences.getBoolean("monitoring_enabled", false)
            
            if (wasMonitoringEnabled) {
                Log.d("BootReceiver", "Restarting monitoring service after boot")
                MonitoringService.startService(context)
                SyncService.scheduleSync(context)
            }
        }
    }
} 