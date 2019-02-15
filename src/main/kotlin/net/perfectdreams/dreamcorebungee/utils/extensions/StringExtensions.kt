package net.perfectdreams.dreamcorebungee.utils.extensions

import net.perfectdreams.dreamcorebungee.utils.TextUtils
import java.nio.ByteBuffer
import java.util.*

fun String.centralize() = TextUtils.getCenteredMessage(this)
fun String.centralizeHeader() = TextUtils.getCenteredHeader(this)

fun UUID.toBytes(): ByteArray {
    val bb = ByteBuffer.wrap(ByteArray(16))
    bb.putLong(this.getMostSignificantBits())
    bb.putLong(this.getLeastSignificantBits())

    return bb.array()
}