package com.magick.gsm2sip.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Round-trip + framing behaviour of the ByteVector <-> ByteArray helpers. */
class PjBytesTest {

    @Test
    fun `round trips bytes through a ByteVector`() {
        val original = byteArrayOf(1, 2, 3, -4, 127, -128)
        val vec = original.toByteVectorSafe(original.size)
        assertArrayEquals(original, vec.toByteArray())
    }

    @Test
    fun `pads short input with silence to the target frame size`() {
        val vec = byteArrayOf(9, 8).toByteVectorSafe(5)
        val out = vec.toByteArray()
        assertEquals(5, out.size)
        assertArrayEquals(byteArrayOf(9, 8, 0, 0, 0), out)
    }

    @Test
    fun `truncates oversized input to the target frame size`() {
        val vec = byteArrayOf(1, 2, 3, 4, 5, 6).toByteVectorSafe(4)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), vec.toByteArray())
    }
}
