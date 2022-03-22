package ru.vizbash.grapevine.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import ru.vizbash.grapevine.network.dispatch.GrapevineNetwork
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrapevineService @Inject constructor(
    private val network: GrapevineNetwork,
) {
    companion object {
        private const val TAG = "GrapevineService"
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var started = false

    fun start() {
        check(!started)

        Log.i(TAG, "Starting")



        started = true
    }
}