package com.catamaran.familysafety.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.catamaran.familysafety.R

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // For now, just show a simple message
        // In a full implementation, this would have preference fragments
        Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        
        // Set up action bar
        supportActionBar?.apply {
            title = "Settings"
            setDisplayHomeAsUpEnabled(true)
        }
        
        finish() // Close for now since it's not implemented
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
} 