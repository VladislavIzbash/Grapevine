package ru.vizbash.grapevine

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GrapevineApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            System.setProperty(
                kotlinx.coroutines.DEBUG_PROPERTY_NAME,
                kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON,
            )
        }
    }
}