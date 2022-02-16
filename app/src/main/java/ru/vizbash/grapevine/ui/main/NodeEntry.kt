package ru.vizbash.grapevine.ui.main

import android.graphics.Bitmap
import ru.vizbash.grapevine.network.Node

data class NodeEntry(
    val node: Node,
    val photoFetcher: suspend () -> Bitmap?,
) {
    var photo: Bitmap? = null
}

