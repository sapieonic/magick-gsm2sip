package com.magick.gsm2sip.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.pjsip.pjsua2.MediaFrame
import org.pjsip.pjsua2.MediaFrameType

/**
 * Verifies the jitter-buffer plumbing of [GsmAudioPort] without any native
 * PJSIP: the pjsua2 base classes come from the compile stub, and the queue
 * logic is pure JVM.
 */
class GsmAudioPortTest {

    private fun port() = GsmAudioPort(clockRate = 16000, samplesPerFrame = 320)

    @Test
    fun `format describes 16kHz mono 16-bit 20ms frames`() {
        val fmt = port().format()
        assertEquals(16000L, fmt.clockRate)
        assertEquals(1L, fmt.channelCount)
        assertEquals(16L, fmt.bitsPerSample)
        assertEquals(20_000L, fmt.frameTimeUsec) // 320 samples @ 16kHz = 20ms
    }

    @Test
    fun `onFrameReceived queues SIP audio toward the GSM side`() {
        val p = port()
        val frame = MediaFrame().apply {
            type = MediaFrameType.PJMEDIA_FRAME_TYPE_AUDIO
            buf = byteArrayOf(10, 20, 30, 40).toByteVectorSafe(4)
        }
        p.onFrameReceived(frame)
        assertArrayEquals(byteArrayOf(10, 20, 30, 40), p.pollToGsm())
    }

    @Test
    fun `onFrameRequested drains GSM audio into the SIP frame`() {
        val p = port()
        p.pushFromGsm(byteArrayOf(5, 6, 7, 8))
        val frame = MediaFrame()
        p.onFrameRequested(frame)
        // With 320-sample frames the buffer is padded to 640 bytes; first 4 match.
        val out = frame.buf.toByteArray()
        assertEquals(640, out.size)
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), out.copyOfRange(0, 4))
    }

    @Test
    fun `buffers stay bounded and drop oldest under overflow`() {
        val p = port()
        repeat(25) { p.pushFromGsm(byteArrayOf(it.toByte())) }
        // Capacity is 10 each way; overflow drops oldest rather than blocking.
        assertEquals(10, p.fromGsm.size)
    }

    @Test
    fun `reset clears both buffers`() {
        val p = port()
        p.pushFromGsm(byteArrayOf(1))
        p.onFrameReceived(MediaFrame().apply {
            type = MediaFrameType.PJMEDIA_FRAME_TYPE_AUDIO
            buf = byteArrayOf(2).toByteVectorSafe(1)
        })
        p.reset()
        assertEquals(0, p.fromGsm.size)
        assertEquals(0, p.toGsm.size)
    }
}
