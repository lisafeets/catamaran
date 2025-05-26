package com.catamaran.familysafety.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.catamaran.familysafety.data.repository.MonitoringRepository
import com.catamaran.familysafety.utils.PreferenceManager

class MainViewModelFactory(
    private val repository: MonitoringRepository,
    private val preferenceManager: PreferenceManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repository, preferenceManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 