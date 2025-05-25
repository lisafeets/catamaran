package com.catamaran.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.catamaran.app.R
import com.catamaran.app.utils.Logger

/**
 * Senior-friendly help activity
 * Simple explanations and support options
 */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)
        
        Logger.info("HelpActivity created")
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }
        
        // Call support
        findViewById<View>(R.id.btn_call_support)?.setOnClickListener {
            callSupport()
        }
        
        // Email support
        findViewById<View>(R.id.btn_email_support)?.setOnClickListener {
            emailSupport()
        }
        
        // View privacy info
        findViewById<View>(R.id.btn_view_privacy)?.setOnClickListener {
            viewPrivacyInfo()
        }
        
        // Family help
        findViewById<View>(R.id.btn_family_help)?.setOnClickListener {
            familyHelp()
        }
    }

    private fun callSupport() {
        Logger.info("User wants to call support")
        
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:1-800-CATAMARAN") // In production, use real support number
            }
            startActivity(intent)
        } catch (e: Exception) {
            Logger.error("Error opening phone dialer", e)
            showMessage("Unable to open phone dialer")
        }
    }

    private fun emailSupport() {
        Logger.info("User wants to email support")
        
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@catamaran.app")
                putExtra(Intent.EXTRA_SUBJECT, "Catamaran App Help Request")
                putExtra(Intent.EXTRA_TEXT, "Hello, I need help with the Catamaran app.\n\n")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Logger.error("Error opening email app", e)
            showMessage("Unable to open email app")
        }
    }

    private fun viewPrivacyInfo() {
        Logger.info("User wants to view privacy information")
        val intent = Intent(this, PrivacyInfoActivity::class.java)
        startActivity(intent)
    }

    private fun familyHelp() {
        Logger.info("User wants family help")
        
        // In production, this could:
        // 1. Call the primary family contact
        // 2. Send an alert to family members
        // 3. Show emergency contacts
        
        showMessage("Contacting your family for help...")
    }

    private fun showMessage(message: String) {
        // In production, show a proper toast or snackbar
        Logger.info("Message: $message")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.info("HelpActivity destroyed")
    }
} 