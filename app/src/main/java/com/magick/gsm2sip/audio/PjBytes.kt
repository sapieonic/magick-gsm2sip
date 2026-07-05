package com.magick.gsm2sip.audio

import org.pjsip.pjsua2.ByteVector

/** Copy a pjsua2 [ByteVector] into a Kotlin [ByteArray]. */
fun ByteVector.toByteArray(): ByteArray {
    val n = size.toInt()
    val out = ByteArray(n)
    for (i in 0 until n) out[i] = get(i).toByte()
    return out
}

/**
 * Build a [ByteVector] of exactly [targetBytes] from this array, padding with
 * silence or truncating as needed so PJSIP always gets a full frame.
 */
fun ByteArray.toByteVectorSafe(targetBytes: Int): ByteVector {
    val vec = ByteVector()
    val n = minOf(size, targetBytes)
    for (i in 0 until n) vec.add(this[i].toShort())
    for (i in n until targetBytes) vec.add(0.toShort())
    return vec
}
