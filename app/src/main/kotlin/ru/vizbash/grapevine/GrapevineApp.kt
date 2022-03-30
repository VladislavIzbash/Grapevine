package ru.vizbash.grapevine

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class GrapevineApp : Application() {

    companion object {
        lateinit var downloadsDir: String
            private set
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            System.setProperty(
                kotlinx.coroutines.DEBUG_PROPERTY_NAME,
                kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON,
            )
        }

        val filesDir = applicationContext.filesDir.absolutePath
        downloadsDir = "$filesDir/Downloads"

        File(downloadsDir).mkdir()
    }
}