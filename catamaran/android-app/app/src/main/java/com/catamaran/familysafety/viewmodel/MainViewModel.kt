package com.catamaran.familysafety.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catamaran.familysafety.data.repository.MonitoringRepository
import com.catamaran.familysafety.utils.PreferenceManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(
    private val repository: MonitoringRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _isMonitoringActive = MutableLiveData<Boolean>()
    val isMonitoringActive: LiveData<Boolean> = _isMonitoringActive

    private val _lastSyncTime = MutableLiveData<String>()
    val lastSyncTime: LiveData<String> = _lastSyncTime

    private val _connectionStatus = MutableLiveData<Boolean>()
    val connectionStatus: LiveData<Boolean> = _connectionStatus

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    private val dateFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        _isMonitoringActive.value = preferenceManager.isMonitoringEnabled()
        
        val lastSync = preferenceManager.getLastSyncTime()
        _lastSyncTime.value = if (lastSync > 0) {
            dateFormatter.format(Date(lastSync))
        } else {
            ""
        }
        
        // Check connection status
        checkConnectionStatus()
    }

    fun toggleMonitoring() {
        val currentState = _isMonitoringActive.value ?: false
        val newState = !currentState
        
        preferenceManager.setMonitoringEnabled(newState)
        _isMonitoringActive.value = newState
        
        if (newState) {
            startMonitoring()
        } else {
            stopMonitoring()
        }
    }

    fun onPermissionsGranted() {
        // Permissions are now available, can start monitoring if enabled
        if (preferenceManager.isMonitoringEnabled()) {
            _isMonitoringActive.value = true
        }
    }

    fun callEmergencyContact() {
        val emergencyContact = preferenceManager.getEmergencyContact()
        if (emergencyContact.isNotEmpty()) {
            // This would typically be handled by the Activity
            // For now, we'll just set an error if no contact is set
        } else {
            _errorMessage.value = "No emergency contact set. Please configure in Settings."
        }
    }

    private fun startMonitoring() {
        viewModelScope.launch {
            try {
                // Start background monitoring
                repository.startMonitoring()
                _errorMessage.value = "Family safety monitoring started"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to start monitoring: ${e.message}"
                _isMonitoringActive.value = false
                preferenceManager.setMonitoringEnabled(false)
            }
        }
    }

    private fun stopMonitoring() {
        viewModelScope.launch {
            try {
                // Stop background monitoring
                repository.stopMonitoring()
                _errorMessage.value = "Family safety monitoring stopped"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to stop monitoring: ${e.message}"
            }
        }
    }

    private fun checkConnectionStatus() {
        viewModelScope.launch {
            try {
                val isConnected = repository.checkConnection()
                _connectionStatus.value = isConnected
            } catch (e: Exception) {
                _connectionStatus.value = false
            }
        }
    }

    fun syncData() {
        viewModelScope.launch {
            try {
                repository.syncData()
                preferenceManager.setLastSyncTime(System.currentTimeMillis())
                refreshStatus()
                _errorMessage.value = "Data synced successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Sync failed: ${e.message}"
            }
        }
    }
} 