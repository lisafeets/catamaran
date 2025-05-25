package com.catamaran.familysafety.utils

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

class ContactMatcher(private val context: Context) {
    
    private val contactCache = mutableMapOf<String, String?>()
    
    fun getContactName(phoneNumber: String): String? {
        // Check cache first
        if (contactCache.containsKey(phoneNumber)) {
            return contactCache[phoneNumber]
        }
        
        val contactName = queryContactName(phoneNumber)
        contactCache[phoneNumber] = contactName
        return contactName
    }
    
    private fun queryContactName(phoneNumber: String): String? {
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
            val selectionArgs = arrayOf(phoneNumber)
            
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val name = cursor.getString(nameIndex)
                    Log.d("ContactMatcher", "Found contact name: $name for $phoneNumber")
                    return name
                }
            }
        } catch (e: SecurityException) {
            Log.e("ContactMatcher", "Permission denied to read contacts", e)
        } catch (e: Exception) {
            Log.e("ContactMatcher", "Error querying contact name for $phoneNumber", e)
        }
        
        return null
    }
    
    fun clearCache() {
        contactCache.clear()
    }
    
    fun getCacheSize(): Int {
        return contactCache.size
    }
} 