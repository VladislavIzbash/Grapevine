package ru.vizbash.grapevine.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

private val TIMESTAMP_FORMAT = SimpleDateFormat("k:mm", Locale.US)

fun formatMessageTimestamp(date: Date): String = TIMESTAMP_FORMAT.format(date)

fun Number.toHumanSize(units: Array<String>): String {
    val size = toLong()
    require(size >= 0)

    val digitGroup = (log10(size.toDouble()) / log10(1024F)).toInt()
    val num = DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroup))
    return "$num ${units[digitGroup]}"
}