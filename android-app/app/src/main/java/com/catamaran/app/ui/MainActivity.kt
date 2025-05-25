package com.catamaran.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.catamaran.app.R
import com.catamaran.app.service.MonitoringService
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.PermissionManager
import kotlinx.coroutines.launch

/**
 * Senior-friendly main activity for Catamaran app
 * Simple home screen with clear status and easy navigation
 */
class MainActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager
    private var isMonitoringActive = false

    // UI Components
    private lateinit var tvStatus: TextView
    private lateinit var tvStatusDetails: TextView
    private lateinit var tvTodayStats: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if this is first launch
        if (isFirstLaunch()) {
            startOnboarding()
            return
        }
        
        setContentView(R.layout.activity_main)
        
        permissionManager = PermissionManager(this)
        
        Logger.info("MainActivity created")
        
        // Initialize UI
        initializeViews()
        setupClickListeners()
        
        // Check permissions and start monitoring if ready
        checkPermissionsAndStartMonitoring()
    }

    private fun isFirstLaunch(): Boolean {
        val prefs = getSharedPreferences("catamaran_prefs", MODE_PRIVATE)
        return !prefs.getBoolean("onboarding_complete", false)
    }

    private fun startOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun initializeViews() {
        tvStatus = findViewById(R.id.tv_status)
        tvStatusDetails = findViewById(R.id.tv_status_details)
        tvTodayStats = findViewById(R.id.tv_today_stats)
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_toggle_protection)?.setOnClickListener {
            toggleProtection()
        }
        
        findViewById<View>(R.id.btn_call_family)?.setOnClickListener {
            callPrimaryContact()
        }
        
        findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            openSettings()
        }
        
        findViewById<View>(R.id.btn_need_help)?.setOnClickListener {
            showHelp()
        }
        
        findViewById<View>(R.id.btn_view_activity)?.setOnClickListener {
            viewActivity()
        }
    }

    private fun checkPermissionsAndStartMonitoring() {
        if (permissionManager.hasRequiredPermissions()) {
            Logger.info("All required permissions granted - checking monitoring status")
            // In production, check if service is actually running
            isMonitoringActive = true
            if (!isMonitoringActive) {
                startMonitoringService()
            }
        } else {
            Logger.info("Missing required permissions")
            isMonitoringActive = false
        }
        updateUI()
    }

    private fun toggleProtection() {
        Logger.info("User toggled protection")
        
        if (!permissionManager.hasRequiredPermissions()) {
            // Need permissions first
            requestPermissions()
            return
        }
        
        if (isMonitoringActive) {
            stopMonitoringService()
        } else {
            startMonitoringService()
        }
    }

    private fun requestPermissions() {
        Logger.info("Requesting permissions from main screen")
        
        permissionManager.requestRequiredPermissions(
            activity = this,
            onPermissionsGranted = {
                Logger.info("Required permissions granted")
                updateUI()
                startMonitoringService()
            },
            onPermissionsDenied = { deniedPermissions ->
                Logger.warning("Some permissions denied: $deniedPermissions")
                updateUI()
                showPermissionExplanation(deniedPermissions)
            }
        )
    }

    private fun showPermissionExplanation(deniedPermissions: List<String>) {
        // In production, show a dialog with clear explanation
        Logger.info("Showing permission explanation for: $deniedPermissions")
    }

    private fun startMonitoringService() {
        if (!isMonitoringActive) {
            Logger.info("Starting monitoring service from main activity")
            MonitoringService.startService(this)
            isMonitoringActive = true
            updateUI()
        }
    }

    private fun stopMonitoringService() {
        if (isMonitoringActive) {
            Logger.info("Stopping monitoring service from main activity")
            MonitoringService.stopService(this)
            isMonitoringActive = false
            updateUI()
        }
    }

    private fun callPrimaryContact() {
        Logger.info("User wants to call primary family contact")
        
        // In production, call the primary family contact
        // For now, just show a message
        showMessage("Calling your primary family contact...")
    }

    private fun openSettings() {
        Logger.info("Opening settings")
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    private fun showHelp() {
        Logger.info("User requested help")
        val intent = Intent(this, HelpActivity::class.java)
        startActivity(intent)
    }

    private fun viewActivity() {
        Logger.info("User wants to view recent activity")
        // In production, show recent activity summary
        showMessage("Showing recent activity...")
    }

    private fun updateUI() {
        lifecycleScope.launch {
            try {
                updateStatusDisplay()
                updateTodayStats()
                updateButtons()
                
            } catch (e: Exception) {
                Logger.error("Error updating UI", e)
            }
        }
    }

    private fun updateStatusDisplay() {
        val capabilities = permissionManager.getMonitoringCapabilities()
        
        when {
            isMonitoringActive && capabilities.hasAnyMonitoring -> {
                tvStatus.text = "✅ Protecting Your Family"
                tvStatus.setTextColor(getColor(R.color.status_active))
                tvStatusDetails.text = "Catamaran is watching for suspicious calls and messages"
                tvStatusDetails.setTextColor(getColor(R.color.text_secondary))
            }
            capabilities.hasAnyMonitoring -> {
                tvStatus.text = "⚠️ Protection Available"
                tvStatus.setTextColor(getColor(R.color.status_warning))
                tvStatusDetails.text = "Tap the button below to start protection"
                tvStatusDetails.setTextColor(getColor(R.color.text_secondary))
            }
            else -> {
                tvStatus.text = "❌ Protection Needs Setup"
                tvStatus.setTextColor(getColor(R.color.status_error))
                tvStatusDetails.text = "We need app permissions to protect you"
                tvStatusDetails.setTextColor(getColor(R.color.text_secondary))
            }
        }
    }

    private suspend fun updateTodayStats() {
        // In production, get real stats from monitoring service
        // For now, show sample data
        val todayText = buildString {
            append("Today:\n")
            append("📞 4 calls shared with family\n")
            append("💬 7 messages shared with family\n")
            append("🛡️ 0 suspicious activity detected")
        }
        
        tvTodayStats.text = todayText
    }

    private fun updateButtons() {
        val hasPermissions = permissionManager.hasRequiredPermissions()
        val capabilities = permissionManager.getMonitoringCapabilities()
        
        // Update toggle protection button
        val toggleButton = findViewById<View>(R.id.btn_toggle_protection)
        val toggleText = findViewById<TextView>(R.id.btn_toggle_protection)
        
        when {
            !hasPermissions -> {
                toggleText?.text = "Grant Permissions"
                toggleButton?.setBackgroundColor(getColor(R.color.button_primary))
            }
            isMonitoringActive -> {
                toggleText?.text = "Turn Off Protection"
                toggleButton?.setBackgroundColor(getColor(R.color.button_negative))
            }
            else -> {
                toggleText?.text = "Start Protection"
                toggleButton?.setBackgroundColor(getColor(R.color.button_positive))
            }
        }
        
        // Enable/disable other buttons based on setup status
        val buttonsEnabled = hasPermissions
        findViewById<View>(R.id.btn_call_family)?.isEnabled = buttonsEnabled
        findViewById<View>(R.id.btn_view_activity)?.isEnabled = buttonsEnabled
    }

    private fun showMessage(message: String) {
        // In production, show a proper toast or snackbar
        Logger.info("Message: $message")
    }

    override fun onResume() {
        super.onResume()
        
        // Check if permissions changed while app was in background
        updateUI()
        
        // Check if monitoring service is still running
        // In production, query the actual service status
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.info("MainActivity destroyed")
    }
} 