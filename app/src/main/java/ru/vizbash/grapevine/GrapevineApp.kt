package ru.vizbash.grapevine

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.android.HiltAndroidApp
import ru.vizbash.grapevine.network.Node
import ru.vizbash.grapevine.network.Router
import java.security.KeyPairGenerator

val Any.TAG: String
    get() = javaClass.simpleName

@HiltAndroidApp
class GrapevineApp : Application()
