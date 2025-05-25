package com.catamaran.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.catamaran.app.R
import com.catamaran.app.service.MonitoringService
import com.catamaran.app.utils.Logger
import com.catamaran.app.utils.PermissionManager
import kotlinx.coroutines.launch

/**
 * Senior-friendly onboarding flow for Catamaran app
 * Simple step-by-step setup with clear explanations
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var permissionManager: PermissionManager
    private lateinit var viewPager: ViewPager2
    private var currentStep = 0
    
    companion object {
        private const val STEP_WELCOME = 0
        private const val STEP_PERMISSIONS = 1
        private const val STEP_FAMILY_SETUP = 2
        private const val STEP_COMPLETE = 3
        private const val TOTAL_STEPS = 4
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        
        permissionManager = PermissionManager(this)
        
        Logger.info("OnboardingActivity started")
        
        setupOnboarding()
        showCurrentStep()
    }

    private fun setupOnboarding() {
        // Initialize UI components
        setupClickListeners()
    }

    private fun setupClickListeners() {
        findViewById<View>(R.id.btn_next)?.setOnClickListener {
            handleNextStep()
        }
        
        findViewById<View>(R.id.btn_skip)?.setOnClickListener {
            handleSkipStep()
        }
        
        findViewById<View>(R.id.btn_grant_permissions)?.setOnClickListener {
            requestPermissions()
        }
        
        findViewById<View>(R.id.btn_finish)?.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun handleNextStep() {
        when (currentStep) {
            STEP_WELCOME -> {
                currentStep = STEP_PERMISSIONS
                showCurrentStep()
            }
            STEP_PERMISSIONS -> {
                if (permissionManager.hasRequiredPermissions()) {
                    currentStep = STEP_FAMILY_SETUP
                    showCurrentStep()
                } else {
                    requestPermissions()
                }
            }
            STEP_FAMILY_SETUP -> {
                currentStep = STEP_COMPLETE
                showCurrentStep()
            }
            STEP_COMPLETE -> {
                finishOnboarding()
            }
        }
    }

    private fun handleSkipStep() {
        when (currentStep) {
            STEP_FAMILY_SETUP -> {
                currentStep = STEP_COMPLETE
                showCurrentStep()
            }
            else -> {
                // Most steps can't be skipped
                handleNextStep()
            }
        }
    }

    private fun showCurrentStep() {
        // Hide all step layouts
        findViewById<View>(R.id.layout_welcome)?.visibility = View.GONE
        findViewById<View>(R.id.layout_permissions)?.visibility = View.GONE
        findViewById<View>(R.id.layout_family_setup)?.visibility = View.GONE
        findViewById<View>(R.id.layout_complete)?.visibility = View.GONE

        // Show current step
        when (currentStep) {
            STEP_WELCOME -> {
                findViewById<View>(R.id.layout_welcome)?.visibility = View.VISIBLE
                updateNavigationButtons(showNext = true, showSkip = false)
            }
            STEP_PERMISSIONS -> {
                findViewById<View>(R.id.layout_permissions)?.visibility = View.VISIBLE
                updatePermissionStep()
            }
            STEP_FAMILY_SETUP -> {
                findViewById<View>(R.id.layout_family_setup)?.visibility = View.VISIBLE
                updateNavigationButtons(showNext = true, showSkip = true)
            }
            STEP_COMPLETE -> {
                findViewById<View>(R.id.layout_complete)?.visibility = View.VISIBLE
                updateNavigationButtons(showNext = false, showSkip = false, showFinish = true)
            }
        }
        
        updateProgressIndicator()
    }

    private fun updatePermissionStep() {
        val hasRequired = permissionManager.hasRequiredPermissions()
        
        if (hasRequired) {
            // Permissions granted - show success and next button
            findViewById<View>(R.id.layout_permissions_needed)?.visibility = View.GONE
            findViewById<View>(R.id.layout_permissions_granted)?.visibility = View.VISIBLE
            updateNavigationButtons(showNext = true, showSkip = false)
        } else {
            // Permissions needed - show explanation and grant button
            findViewById<View>(R.id.layout_permissions_needed)?.visibility = View.VISIBLE
            findViewById<View>(R.id.layout_permissions_granted)?.visibility = View.GONE
            updateNavigationButtons(showNext = false, showSkip = false, showGrant = true)
        }
    }

    private fun updateNavigationButtons(
        showNext: Boolean = false,
        showSkip: Boolean = false,
        showGrant: Boolean = false,
        showFinish: Boolean = false
    ) {
        findViewById<View>(R.id.btn_next)?.visibility = if (showNext) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_skip)?.visibility = if (showSkip) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_grant_permissions)?.visibility = if (showGrant) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_finish)?.visibility = if (showFinish) View.VISIBLE else View.GONE
    }

    private fun updateProgressIndicator() {
        val progressText = "${currentStep + 1} of $TOTAL_STEPS"
        // In production, update actual progress indicator
        Logger.debug("Onboarding progress: $progressText")
    }

    private fun requestPermissions() {
        Logger.info("Requesting permissions during onboarding")
        
        permissionManager.requestRequiredPermissions(
            activity = this,
            onPermissionsGranted = {
                Logger.info("Required permissions granted during onboarding")
                updatePermissionStep()
                
                // Also request optional permissions
                requestOptionalPermissions()
            },
            onPermissionsDenied = { deniedPermissions ->
                Logger.warning("Some permissions denied during onboarding: $deniedPermissions")
                showPermissionExplanation(deniedPermissions)
            }
        )
    }

    private fun requestOptionalPermissions() {
        permissionManager.requestOptionalPermissions(
            activity = this,
            onCompleted = { grantedPermissions ->
                Logger.info("Optional permissions completed: $grantedPermissions")
            }
        )
    }

    private fun showPermissionExplanation(deniedPermissions: List<String>) {
        // Show detailed explanation for denied permissions
        val explanations = deniedPermissions.joinToString("\n\n") { permission ->
            permissionManager.getPermissionExplanation(permission)
        }
        
        Logger.info("Showing permission explanations: $explanations")
        
        // In production, show a dialog with explanations
        // For now, just update the permission step
        updatePermissionStep()
    }

    private fun finishOnboarding() {
        Logger.info("Onboarding completed")
        
        // Mark onboarding as complete
        val prefs = getSharedPreferences("catamaran_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        
        // Start monitoring service if permissions are granted
        if (permissionManager.hasRequiredPermissions()) {
            MonitoringService.startService(this)
        }
        
        // Navigate to main activity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        
        // Check if permissions changed while in settings
        if (currentStep == STEP_PERMISSIONS) {
            updatePermissionStep()
        }
    }
} 