package com.catamaran.familysafety.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.catamaran.familysafety.CatamaranApplication
import com.catamaran.familysafety.R
import com.catamaran.familysafety.ui.MainActivity
import com.catamaran.familysafety.worker.DataSyncWorker
import java.util.concurrent.TimeUnit

class MonitoringService : Service() {

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        scheduleDataSync()
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CatamaranApplication.MONITORING_CHANNEL_ID)
            .setContentTitle(getString(R.string.monitoring_notification_title))
            .setContentText(getString(R.string.monitoring_notification_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun scheduleDataSync() {
        val application = application as CatamaranApplication
        val syncFrequency = application.preferenceManager.getSyncFrequency().toLong()
        
        val syncWorkRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(
            syncFrequency, TimeUnit.MINUTES
        )
            .addTag(DATA_SYNC_WORK_TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DATA_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncWorkRequest
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel scheduled work when service is destroyed
        WorkManager.getInstance(this).cancelUniqueWork(DATA_SYNC_WORK_NAME)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val DATA_SYNC_WORK_NAME = "data_sync_work"
        private const val DATA_SYNC_WORK_TAG = "data_sync"
    }
} 