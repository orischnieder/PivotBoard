package com.ori.pivotboard_project

import android.app.Application
import com.ori.pivotboard_project.utilities.AuthManager
import com.ori.pivotboard_project.utilities.DatabaseManager
import com.ori.pivotboard_project.utilities.ImageLoader
import com.ori.pivotboard_project.utilities.SharedPreferencesManager
import com.ori.pivotboard_project.utilities.SignalManager
import com.ori.pivotboard_project.utilities.StorageManager

/**
 * Initializes every manager singleton once, before any Activity exists, so screens can
 * safely call `XxxManager.getInstance()`.
 *
 * TODO(ori): add google-services.json to app/ - the Firebase managers below need it at runtime.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        SignalManager.init(this)
        ImageLoader.init(this)
        SharedPreferencesManager.init(this)

        AuthManager.init(this)
        DatabaseManager.init(this)
        StorageManager.init(this)
    }
}
