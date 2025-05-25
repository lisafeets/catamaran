package com.catamaran.app.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

/**
 * Permission manager for Catamaran app
 * Handles runtime permissions with clear explanations for seniors
 */
class PermissionManager(private val context: Context) {

    companion object {
        // Required permissions for core functionality
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )

        // Optional permissions for enhanced features
        val OPTIONAL_PERMISSIONS = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.WAKE_LOCK
        )

        // Permission request codes
        const val REQUEST_REQUIRED_PERMISSIONS = 1001
        const val REQUEST_OPTIONAL_PERMISSIONS = 1002
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if a specific permission is granted
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Request required permissions with senior-friendly explanations
     */
    fun requestRequiredPermissions(
        activity: Activity,
        onPermissionsGranted: () -> Unit,
        onPermissionsDenied: (deniedPermissions: List<String>) -> Unit
    ) {
        Dexter.withContext(activity)
            .withPermissions(*REQUIRED_PERMISSIONS)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let {
                        if (it.areAllPermissionsGranted()) {
                            Logger.info("All required permissions granted")
                            onPermissionsGranted()
                        } else {
                            val deniedPermissions = it.deniedPermissionResponses.map { response ->
                                response.permissionName
                            }
                            Logger.warning("Required permissions denied: $deniedPermissions")
                            onPermissionsDenied(deniedPermissions)
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    // Show explanation dialog and then continue
                    showPermissionExplanationDialog(activity, permissions, token)
                }
            })
            .check()
    }

    /**
     * Request optional permissions
     */
    fun requestOptionalPermissions(
        activity: Activity,
        onCompleted: (grantedPermissions: List<String>) -> Unit
    ) {
        Dexter.withContext(activity)
            .withPermissions(*OPTIONAL_PERMISSIONS)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    report?.let {
                        val grantedPermissions = it.grantedPermissionResponses.map { response ->
                            response.permissionName
                        }
                        Logger.info("Optional permissions granted: $grantedPermissions")
                        onCompleted(grantedPermissions)
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    // For optional permissions, just continue without showing dialog
                    token?.continuePermissionRequest()
                }
            })
            .check()
    }

    private fun showPermissionExplanationDialog(
        activity: Activity,
        permissions: MutableList<PermissionRequest>?,
        token: PermissionToken?
    ) {
        val explanations = permissions?.map { getPermissionExplanation(it.name) }?.joinToString("\n\n")
        
        // In a real app, show a proper dialog here
        // For now, just continue with the permission request
        Logger.info("Permission explanation: $explanations")
        token?.continuePermissionRequest()
    }

    /**
     * Get user-friendly explanation for each permission
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            Manifest.permission.READ_CALL_LOG -> 
                "📞 Call History: We need to see your call history to detect unusual activity and protect you from scam calls. We only look at who called and when - never listen to your conversations."

            Manifest.permission.READ_SMS -> 
                "💬 Text Messages: We check how many text messages you receive to spot scam patterns. We NEVER read the content of your messages - only count them and see who sent them."

            Manifest.permission.READ_CONTACTS -> 
                "👥 Contacts: We use your contacts to tell the difference between calls from people you know and unknown numbers. This helps us identify potential scam calls."

            Manifest.permission.READ_PHONE_STATE -> 
                "📱 Phone Status: We need to know when your phone is receiving calls so we can monitor for suspicious activity in the background."

            Manifest.permission.POST_NOTIFICATIONS -> 
                "🔔 Notifications: We'll send notifications to alert your family if we detect suspicious activity. You can turn this off anytime."

            Manifest.permission.RECEIVE_BOOT_COMPLETED -> 
                "🔄 Auto-Start: This allows the app to start protecting you automatically when your phone restarts."

            Manifest.permission.WAKE_LOCK -> 
                "⚡ Background Operation: This helps the app continue monitoring even when your phone is sleeping, but uses very little battery."

            else -> "This permission helps the app protect you from scams and keep your family informed."
        }
    }

    /**
     * Get critical missing permissions that prevent core functionality
     */
    fun getCriticalMissingPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            !hasPermission(permission)
        }
    }

    /**
     * Check if user can be monitored with current permissions
     */
    fun canMonitorActivity(): Boolean {
        return hasPermission(Manifest.permission.READ_CALL_LOG) ||
                hasPermission(Manifest.permission.READ_SMS)
    }

    /**
     * Get monitoring capabilities based on granted permissions
     */
    fun getMonitoringCapabilities(): MonitoringCapabilities {
        return MonitoringCapabilities(
            canMonitorCalls = hasPermission(Manifest.permission.READ_CALL_LOG),
            canMonitorSms = hasPermission(Manifest.permission.READ_SMS),
            canResolveContacts = hasPermission(Manifest.permission.READ_CONTACTS),
            canSendNotifications = hasPermission(Manifest.permission.POST_NOTIFICATIONS),
            canAutoStart = hasPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        )
    }
}

/**
 * Data class representing what the app can monitor based on permissions
 */
data class MonitoringCapabilities(
    val canMonitorCalls: Boolean,
    val canMonitorSms: Boolean,
    val canResolveContacts: Boolean,
    val canSendNotifications: Boolean,
    val canAutoStart: Boolean
) {
    val hasAnyMonitoring: Boolean
        get() = canMonitorCalls || canMonitorSms
} 