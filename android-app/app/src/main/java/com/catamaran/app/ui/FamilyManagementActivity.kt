package com.catamaran.app.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.catamaran.app.R
import com.catamaran.app.utils.Logger
import kotlinx.coroutines.launch

/**
 * Senior-friendly family management activity
 * Simple interface for adding and removing family members
 */
class FamilyManagementActivity : AppCompatActivity() {

    private lateinit var etNewFamilyEmail: EditText
    private lateinit var layoutFamilyMembers: LinearLayout

    // Sample data - in production, this would come from backend
    private val familyMembers = mutableListOf(
        FamilyMember("Sarah Johnson", "sarah.johnson@email.com", "Daughter", true),
        FamilyMember("Michael Johnson", "michael.j@email.com", "Son", true)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_management)
        
        Logger.info("FamilyManagementActivity created")
        
        initializeViews()
        setupClickListeners()
        updateFamilyMembersList()
    }

    private fun initializeViews() {
        etNewFamilyEmail = findViewById(R.id.et_new_family_email)
        layoutFamilyMembers = findViewById(R.id.layout_family_members)
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }
        
        // Add family member
        findViewById<View>(R.id.btn_add_family_member)?.setOnClickListener {
            addFamilyMember()
        }
    }

    private fun addFamilyMember() {
        val email = etNewFamilyEmail.text.toString().trim()
        
        if (email.isEmpty()) {
            showMessage("Please enter an email address")
            return
        }
        
        if (!isValidEmail(email)) {
            showMessage("Please enter a valid email address")
            return
        }
        
        // Check if already added
        if (familyMembers.any { it.email.equals(email, ignoreCase = true) }) {
            showMessage("This family member is already added")
            return
        }
        
        Logger.info("Adding family member: $email")
        
        // In production, this would call the backend API
        val newMember = FamilyMember(
            name = extractNameFromEmail(email),
            email = email,
            relationship = "Family Member",
            isActive = true
        )
        
        familyMembers.add(newMember)
        etNewFamilyEmail.text.clear()
        updateFamilyMembersList()
        
        showMessage("Family member added successfully!")
    }

    private fun removeFamilyMember(member: FamilyMember) {
        Logger.info("Removing family member: ${member.email}")
        
        // In production, this would call the backend API
        familyMembers.remove(member)
        updateFamilyMembersList()
        
        showMessage("${member.name} removed from family watch")
    }

    private fun toggleFamilyMember(member: FamilyMember) {
        Logger.info("Toggling family member: ${member.email}, active: ${!member.isActive}")
        
        // In production, this would call the backend API
        member.isActive = !member.isActive
        updateFamilyMembersList()
        
        val status = if (member.isActive) "will now" else "will no longer"
        showMessage("${member.name} $status receive updates")
    }

    private fun updateFamilyMembersList() {
        lifecycleScope.launch {
            try {
                // Clear existing views
                layoutFamilyMembers.removeAllViews()
                
                if (familyMembers.isEmpty()) {
                    showEmptyState()
                } else {
                    for (member in familyMembers) {
                        addFamilyMemberView(member)
                    }
                }
                
            } catch (e: Exception) {
                Logger.error("Error updating family members list", e)
            }
        }
    }

    private fun showEmptyState() {
        val emptyView = layoutInflater.inflate(R.layout.item_family_empty, layoutFamilyMembers, false)
        layoutFamilyMembers.addView(emptyView)
    }

    private fun addFamilyMemberView(member: FamilyMember) {
        val memberView = layoutInflater.inflate(R.layout.item_family_member, layoutFamilyMembers, false)
        
        // Set member information
        memberView.findViewById<TextView>(R.id.tv_member_name)?.text = member.name
        memberView.findViewById<TextView>(R.id.tv_member_email)?.text = member.email
        memberView.findViewById<TextView>(R.id.tv_member_relationship)?.text = member.relationship
        
        // Set status
        val statusText = if (member.isActive) "✅ Getting updates" else "❌ Not getting updates"
        val statusColor = if (member.isActive) getColor(R.color.status_active) else getColor(R.color.status_inactive)
        
        val tvStatus = memberView.findViewById<TextView>(R.id.tv_member_status)
        tvStatus?.text = statusText
        tvStatus?.setTextColor(statusColor)
        
        // Set click listeners
        memberView.findViewById<View>(R.id.btn_toggle_member)?.setOnClickListener {
            toggleFamilyMember(member)
        }
        
        memberView.findViewById<View>(R.id.btn_remove_member)?.setOnClickListener {
            showRemoveConfirmation(member)
        }
        
        layoutFamilyMembers.addView(memberView)
    }

    private fun showRemoveConfirmation(member: FamilyMember) {
        // In production, show a proper confirmation dialog
        // For now, just remove immediately
        Logger.info("Showing remove confirmation for: ${member.name}")
        removeFamilyMember(member)
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun extractNameFromEmail(email: String): String {
        // Simple name extraction from email
        val localPart = email.substringBefore("@")
        return localPart.replace(".", " ").split(" ")
            .joinToString(" ") { it.capitalize() }
    }

    private fun showMessage(message: String) {
        // In production, show a proper toast or snackbar
        Logger.info("Message: $message")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.info("FamilyManagementActivity destroyed")
    }
}

/**
 * Data class representing a family member
 */
data class FamilyMember(
    var name: String,
    val email: String,
    var relationship: String,
    var isActive: Boolean
) 