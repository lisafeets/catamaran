package com.catamaran.app

import android.app.Application
import com.catamaran.app.utils.Logger

/**
 * Catamaran Application class
 * Handles global app initialization and configuration
 */
class CatamaranApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Logger.info("CatamaranApplication starting up")
        
        // Initialize any global components here
        initializeApp()
    }

    private fun initializeApp() {
        // App initialization code
        Logger.info("App initialization complete")
    }

    override fun onTerminate() {
        super.onTerminate()
        Logger.info("CatamaranApplication terminating")
    }
} 