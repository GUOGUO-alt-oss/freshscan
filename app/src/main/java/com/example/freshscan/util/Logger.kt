package com.example.freshscan.util

import android.util.Log
import com.example.freshscan.BuildConfig

/**
 * Centralized logging utility.
 *
 * All log messages are prefixed with "FreshScan_" for easy filtering.
 * Verbose (debug) logs are suppressed in release builds.
 */
object Logger {

    private fun tag(subsystem: String): String = "${Constants.LOG_TAG_PREFIX}_$subsystem"

    fun d(subsystem: String, message: String) {
        if (BuildConfig.ENABLE_VERBOSE_LOGGING) {
            Log.d(tag(subsystem), message)
        }
    }

    fun i(subsystem: String, message: String) {
        Log.i(tag(subsystem), message)
    }

    fun w(subsystem: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag(subsystem), message, throwable)
        } else {
            Log.w(tag(subsystem), message)
        }
    }

    fun e(subsystem: String, message: String, throwable: Throwable? = null) {
        Log.e(tag(subsystem), message, throwable)
    }
}
