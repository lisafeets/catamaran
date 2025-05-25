package com.catamaran.app.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import kotlin.text.Charsets.UTF_8

/**
 * Encryption manager for Catamaran app
 * Handles all encryption/decryption operations with security best practices
 */
class EncryptionManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_ALIAS = "catamaran_master_key"
        private const val DATABASE_KEY_ALIAS = "catamaran_db_key"
        private const val PHONE_HASH_SALT_KEY = "phone_hash_salt"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val SALT_LENGTH = 32
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "catamaran_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Get or create database encryption key
     */
    fun getDatabaseKey(): String {
        return encryptedPrefs.getString(DATABASE_KEY_ALIAS, null)
            ?: generateAndStoreDatabaseKey()
    }

    private fun generateAndStoreDatabaseKey(): String {
        val key = generateSecureRandomString(32)
        encryptedPrefs.edit()
            .putString(DATABASE_KEY_ALIAS, key)
            .apply()
        return key
    }

    /**
     * Hash phone number for privacy
     * Uses SHA-256 with app-specific salt
     */
    fun hashPhoneNumber(phoneNumber: String): String {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val salt = getOrCreatePhoneHashSalt()
        val input = "$salt$cleanNumber".toByteArray(UTF_8)
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input)
        
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    private fun getOrCreatePhoneHashSalt(): String {
        return encryptedPrefs.getString(PHONE_HASH_SALT_KEY, null)
            ?: generateAndStorePhoneHashSalt()
    }

    private fun generateAndStorePhoneHashSalt(): String {
        val salt = generateSecureRandomString(SALT_LENGTH)
        encryptedPrefs.edit()
            .putString(PHONE_HASH_SALT_KEY, salt)
            .apply()
        return salt
    }

    /**
     * Encrypt contact name or other sensitive text
     */
    fun encryptText(plainText: String): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(UTF_8))
            
            // Combine IV and encrypted data
            val combined = iv + encryptedBytes
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Logger.error("Failed to encrypt text", e)
            null
        }
    }

    /**
     * Decrypt contact name or other sensitive text
     */
    fun decryptText(encryptedText: String): String? {
        return try {
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            val iv = combined.sliceArray(0..GCM_IV_LENGTH - 1)
            val encryptedBytes = combined.sliceArray(GCM_IV_LENGTH until combined.size)
            
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, UTF_8)
        } catch (e: Exception) {
            Logger.error("Failed to decrypt text", e)
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        return if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        } else {
            generateSecretKey()
        }
    }

    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // For background operation
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun generateSecureRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * Clear all encryption keys (for logout/uninstall)
     */
    fun clearKeys() {
        try {
            encryptedPrefs.edit().clear().apply()
            
            val keyStore = java.security.KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }
        } catch (e: Exception) {
            Logger.error("Failed to clear encryption keys", e)
        }
    }
} 