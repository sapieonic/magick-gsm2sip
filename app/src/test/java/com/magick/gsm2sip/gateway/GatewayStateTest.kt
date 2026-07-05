package com.magick.gsm2sip.gateway

import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayStateTest {

    @Test
    fun `labels are human readable`() {
        assertEquals("Idle", GatewayState.Stopped.label)
        assertEquals("Starting…", GatewayState.Starting.label)
        assertEquals("Registered", GatewayState.Registered.label)
        assertEquals("Calling +15551234567", GatewayState.Calling("+15551234567").label)
        assertEquals("Error", GatewayState.Error("boom").label)
    }

    @Test
    fun `in-call label reflects whether audio is bridged`() {
        assertEquals("In call (bridged)", GatewayState.InCall("sip:x@y", audioBridged = true).label)
        assertEquals("In call (no audio)", GatewayState.InCall("sip:x@y", audioBridged = false).label)
    }

    @Test
    fun `error carries its message`() {
        val err = GatewayState.Error("registration failed")
        assertEquals("registration failed", err.message)
    }
}
