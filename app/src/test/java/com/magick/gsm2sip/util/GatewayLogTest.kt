package com.magick.gsm2sip.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the in-memory log ring buffer. android.util.Log is stubbed to
 * return defaults (testOptions.unitTests.isReturnDefaultValues = true).
 */
class GatewayLogTest {

    @Before
    fun setup() = GatewayLog.clear()

    @Test
    fun `appends entries with tag and level`() {
        GatewayLog.i(LogTag.SIP, "registered")
        val entries = GatewayLog.entries.value
        assertEquals(1, entries.size)
        assertEquals(LogTag.SIP, entries.first().tag)
        assertEquals(LogLevel.INFO, entries.first().level)
        assertEquals("registered", entries.first().message)
    }

    @Test
    fun `clear empties the buffer`() {
        GatewayLog.w(LogTag.GSM, "x")
        GatewayLog.clear()
        assertTrue(GatewayLog.entries.value.isEmpty())
    }

    @Test
    fun `ring buffer is bounded to the max size`() {
        repeat(1200) { GatewayLog.d(LogTag.SYSTEM, "line $it") }
        assertTrue(GatewayLog.entries.value.size <= 1000)
        // Oldest entries are dropped; the newest survives.
        assertTrue(GatewayLog.entries.value.last().message.contains("1199"))
    }

    @Test
    fun `error level is recorded`() {
        GatewayLog.e(LogTag.AUDIO, "bridge failed")
        assertEquals(LogLevel.ERROR, GatewayLog.entries.value.first().level)
    }
}
