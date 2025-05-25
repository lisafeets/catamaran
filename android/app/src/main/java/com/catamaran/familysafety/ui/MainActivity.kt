package com.catamaran.familysafety.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.catamaran.familysafety.databinding.ActivityMainBinding
import com.catamaran.familysafety.network.ApiClient
import com.catamaran.familysafety.service.MonitoringService
import com.catamaran.familysafety.service.SyncService
import com.catamaran.familysafety.utils.DataCollector
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var apiClient: ApiClient
    private lateinit var dataCollector: DataCollector
    private var isMonitoring = false
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            showPermissionExplanation()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        apiClient = ApiClient(this)
        dataCollector = DataCollector(this)
        
        setupUI()
        checkLoginStatus()
        updateMonitoringStatus()
    }
    
    private fun setupUI() {
        binding.apply {
            // Monitor toggle
            btnToggleMonitoring.setOnClickListener {
                if (isMonitoring) {
                    stopMonitoring()
                } else {
                    startMonitoring()
                }
            }
            
            // Login button
            btnLogin.setOnClickListener {
                if (apiClient.isLoggedIn()) {
                    logout()
                } else {
                    login()
                }
            }
            
            // Export data button
            btnExportData.setOnClickListener {
                exportData()
            }
            
            // Permission setup button
            btnPermissions.setOnClickListener {
                requestPermissions()
            }
        }
    }
    
    private fun checkLoginStatus() {
        if (apiClient.isLoggedIn()) {
            binding.btnLogin.text = "Logout"
            binding.tvLoginStatus.text = "✓ Logged in"
        } else {
            binding.btnLogin.text = "Login"
            binding.tvLoginStatus.text = "Not logged in"
        }
    }
    
    private fun updateMonitoringStatus() {
        val preferences = getSharedPreferences("catamaran_prefs", MODE_PRIVATE)
        isMonitoring = preferences.getBoolean("monitoring_enabled", false)
        
        if (isMonitoring) {
            binding.btnToggleMonitoring.text = "Stop Monitoring"
            binding.btnToggleMonitoring.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.tvMonitoringStatus.text = "✓ Monitoring Active"
        } else {
            binding.btnToggleMonitoring.text = "Start Monitoring"
            binding.btnToggleMonitoring.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.tvMonitoringStatus.text = "Monitoring Inactive"
        }
    }
    
    private fun startMonitoring() {
        if (!hasRequiredPermissions()) {
            requestPermissions()
            return
        }
        
        if (!apiClient.isLoggedIn()) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            return
        }
        
        MonitoringService.startService(this)
        SyncService.scheduleSync(this)
        
        getSharedPreferences("catamaran_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("monitoring_enabled", true)
            .apply()
        
        isMonitoring = true
        updateMonitoringStatus()
        
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", "Monitoring started by user")
    }
    
    private fun stopMonitoring() {
        MonitoringService.stopService(this)
        
        getSharedPreferences("catamaran_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("monitoring_enabled", false)
            .apply()
        
        isMonitoring = false
        updateMonitoringStatus()
        
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", "Monitoring stopped by user")
    }
    
    private fun login() {
        // For demo purposes, use the seeded credentials
        lifecycleScope.launch {
            try {
                val result = apiClient.login("grandma@example.com", "password123")
                if (result.isSuccess) {
                    checkLoginStatus()
                    Toast.makeText(this@MainActivity, "Login successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Login failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Login error", e)
            }
        }
    }
    
    private fun logout() {
        apiClient.logout()
        checkLoginStatus()
        if (isMonitoring) {
            stopMonitoring()
        }
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
    }
    
    private fun exportData() {
        lifecycleScope.launch {
            try {
                val callLogs = dataCollector.getAllCallLogs()
                val smsLogs = dataCollector.getAllSmsLogs()
                
                val data = "Call Logs: ${callLogs.size}\nSMS Logs: ${smsLogs.size}"
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, data)
                    putExtra(Intent.EXTRA_SUBJECT, "Catamaran Family Safety Data")
                }
                startActivity(Intent.createChooser(intent, "Share data"))
                
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MainActivity", "Export error", e)
            }
        }
    }
    
    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        
        permissionLauncher.launch(permissions)
    }
    
    private fun hasRequiredPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun onPermissionsGranted() {
        Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        binding.tvPermissionStatus.text = "✓ Permissions Granted"
    }
    
    private fun showPermissionExplanation() {
        Toast.makeText(this, "Permissions are required for family safety monitoring", Toast.LENGTH_LONG).show()
        binding.tvPermissionStatus.text = "Permissions Required"
    }
} 