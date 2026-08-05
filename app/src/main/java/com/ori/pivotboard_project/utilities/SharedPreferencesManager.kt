package com.ori.pivotboard_project.utilities

import android.content.Context
import android.content.SharedPreferences

/**
 * Typed access to the app's single SharedPreferences file. Keys live in `Constants.SP_KEYS`.
 * No `WeakReference` here on purpose - the [SharedPreferences] handle is all this manager
 * needs, so holding the Context afterwards would just be a leak waiting to happen.
 */
class SharedPreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.SP.FILE_NAME, Context.MODE_PRIVATE)

    fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    fun getString(key: String, defaultValue: String = ""): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        prefs.getBoolean(key, defaultValue)

    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()

    fun getInt(key: String, defaultValue: Int = 0): Int = prefs.getInt(key, defaultValue)

    fun clear() = prefs.edit().clear().apply()

    companion object {
        @Volatile
        private var instance: SharedPreferencesManager? = null

        fun init(context: Context): SharedPreferencesManager =
            instance ?: synchronized(this) {
                instance ?: SharedPreferencesManager(context).also { instance = it }
            }

        fun getInstance(): SharedPreferencesManager = instance
            ?: throw IllegalStateException("SharedPreferencesManager must be initialized by calling init(context) before use.")
    }
}
