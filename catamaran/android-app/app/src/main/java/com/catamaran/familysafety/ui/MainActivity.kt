package com.catamaran.familysafety.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.catamaran.familysafety.CatamaranApplication
import com.catamaran.familysafety.R
import com.catamaran.familysafety.databinding.ActivityMainBinding
import com.catamaran.familysafety.service.MonitoringService
import com.catamaran.familysafety.viewmodel.MainViewModel
import com.catamaran.familysafety.viewmodel.MainViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    // Required permissions for monitoring
    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS
    )

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
            updateUI()
        } else {
            Toast.makeText(this, "Permissions are required for family safety monitoring", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ViewModel
        val application = application as CatamaranApplication
        
        // TODO: Remove this temporary auth token - add proper login flow
        if (!application.preferenceManager.isLoggedIn()) {
            application.preferenceManager.setAuthToken("temp_token_for_testing")
            application.preferenceManager.setUserId("temp_user_id")
        }
        
        val factory = MainViewModelFactory(application.repository, application.preferenceManager)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupUI()
        observeViewModel()
        checkPermissions()
    }

    private fun setupUI() {
        // Set up click listeners for large buttons
        binding.btnToggleMonitoring.setOnClickListener {
            if (hasAllPermissions()) {
                viewModel.toggleMonitoring()
            } else {
                requestPermissions()
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnEmergencyContact.setOnClickListener {
            viewModel.callEmergencyContact()
        }

        binding.btnViewActivity.setOnClickListener {
            // TODO: Navigate to activity view
            Toast.makeText(this, "Activity view coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewModel() {
        viewModel.isMonitoringActive.observe(this) { isActive ->
            updateMonitoringStatus(isActive)
        }

        viewModel.lastSyncTime.observe(this) { syncTime ->
            binding.tvLastSync.text = if (syncTime.isNotEmpty()) {
                "Last sync: $syncTime"
            } else {
                "Never synced"
            }
        }

        viewModel.connectionStatus.observe(this) { isConnected ->
            binding.tvConnectionStatus.text = if (isConnected) {
                "✓ Connected to Catamaran"
            } else {
                "⚠ Not connected"
            }
            binding.tvConnectionStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isConnected) R.color.success_green else R.color.warning_orange
                )
            )
        }

        viewModel.errorMessage.observe(this) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateMonitoringStatus(isActive: Boolean) {
        if (isActive) {
            binding.btnToggleMonitoring.text = "STOP MONITORING"
            binding.btnToggleMonitoring.setBackgroundColor(
                ContextCompat.getColor(this, R.color.stop_red)
            )
            binding.tvMonitoringStatus.text = "🛡️ Family Watch ACTIVE"
            binding.tvMonitoringStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.white)
            )
            binding.tvMonitoringStatus.setBackgroundResource(R.drawable.gradient_green_card)
            
            // Start monitoring service
            val intent = Intent(this, MonitoringService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } else {
            binding.btnToggleMonitoring.text = "START MONITORING"
            binding.btnToggleMonitoring.setBackgroundColor(
                ContextCompat.getColor(this, R.color.start_green)
            )
            binding.tvMonitoringStatus.text = "⏸️ Family Watch INACTIVE"
            binding.tvMonitoringStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.white)
            )
            binding.tvMonitoringStatus.setBackgroundResource(R.drawable.gradient_blue_card)
            
            // Stop monitoring service
            val intent = Intent(this, MonitoringService::class.java)
            stopService(intent)
        }
    }

    private fun updateUI() {
        val isActive = viewModel.isMonitoringActive.value ?: false
        updateMonitoringStatus(isActive)
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissions() {
        if (!hasAllPermissions()) {
            requestPermissions()
        } else {
            viewModel.onPermissionsGranted()
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(requiredPermissions)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }
} 