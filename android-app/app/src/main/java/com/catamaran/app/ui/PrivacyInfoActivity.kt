package com.catamaran.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.catamaran.app.R
import com.catamaran.app.utils.Logger

/**
 * Privacy information activity
 * Explains how the app protects user privacy in senior-friendly language
 */
class PrivacyInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_info)
        
        Logger.info("PrivacyInfoActivity created")
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.info("PrivacyInfoActivity destroyed")
    }
} 