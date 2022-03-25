package ru.vizbash.grapevine

import android.content.Context

abstract class GvException : Exception() {
    abstract fun localizedMessage(context: Context): String
}

class GvNodeNotAvailableException : GvException() {
    override fun localizedMessage(context: Context) = context.getString(R.string.node_offline)
}

class GvRejectedException : GvException() {
    override fun localizedMessage(context: Context) = context.getString(R.string.message_rejected)
}

class GvInvalidResponseException : GvException() {
    override fun localizedMessage(context: Context) = context.getString(R.string.invalid_response_received)
}

class GvTimeoutException : GvException() {
    override fun localizedMessage(context: Context) = context.getString(R.string.timeout)
}
