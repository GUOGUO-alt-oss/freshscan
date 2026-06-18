package com.example.freshscan.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Top-level DataStore delegate for taste profile preferences.
 *
 * Must be declared as a top-level property — the [preferencesDataStore] delegate
 * relies on Kotlin property identity (file + name) to create a single DataStore
 * instance; nesting inside a class or object breaks this guarantee and can cause
 * duplicate instances or multi-process issues.
 *
 * See: https://developer.android.com/topic/libraries/architecture/datastore#kotlin
 */
object TasteProfileDataStore {
    fun get(context: Context): DataStore<Preferences> = context.store

    /** Top-level extension delegate — one DataStore per process. */
    private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "taste_profile")
}
