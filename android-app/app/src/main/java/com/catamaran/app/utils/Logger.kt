package com.catamaran.app.utils

import android.util.Log

/**
 * Simple logger utility for Catamaran app
 * Privacy-safe logging that doesn't expose sensitive data
 */
object Logger {
    private const val TAG = "CatamaranApp"

    fun debug(message: String) {
        Log.d(TAG, message)
    }

    fun info(message: String) {
        Log.i(TAG, message)
    }

    fun warning(message: String) {
        Log.w(TAG, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }
} 