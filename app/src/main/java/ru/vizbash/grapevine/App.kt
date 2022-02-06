package ru.vizbash.grapevine

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

val Any.TAG: String
    get() = javaClass.simpleName

@HiltAndroidApp
class GrapevineApp : Application()
