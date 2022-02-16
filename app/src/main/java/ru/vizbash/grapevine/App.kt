package ru.vizbash.grapevine

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

val Any.TAG: String
    get() = javaClass.simpleName

@ExperimentalUnsignedTypes
fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }

@HiltAndroidApp
class GrapevineApp : Application()
