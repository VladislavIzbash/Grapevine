package ru.vizbash.grapevine.util

import java.nio.ByteBuffer
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

val Any.TAG: String
    get() = javaClass.simpleName

@ExperimentalUnsignedTypes
fun ByteArray.toHexString() = asUByteArray().joinToString("") {
    it.toString(16).padStart(2, '0')
}

fun Number.toHumanSize(units: Array<String>): String {
    val size = toLong()
    require(size >= 0)

    val digitGroup = (log10(size.toDouble()) / log10(1024F)).toInt()
    val num = DecimalFormat("#,##0.#").format(size / 1024.0.pow(digitGroup))
    return "$num ${units[digitGroup]}"
}