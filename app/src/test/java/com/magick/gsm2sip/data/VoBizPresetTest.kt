package com.magick.gsm2sip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoBizPresetTest {

    private val credentials = SipConfig(
        username = "trunk-user",
        password = "s3cret",
        displayName = "My Gateway",
    )

    @Test
    fun `preserves credentials while applying trunk defaults`() {
        val result = VoBizPreset.applyTo(credentials)
        assertEquals("trunk-user", result.username)
        assertEquals("s3cret", result.password)
        assertEquals("My Gateway", result.displayName)
    }

    @Test
    fun `fills in vobiz registrar and stun`() {
        val result = VoBizPreset.applyTo(credentials)
        assertEquals(VoBizPreset.DOMAIN, result.domain)
        assertEquals(VoBizPreset.STUN_SERVER, result.stunServer)
        assertTrue(result.stunEnabled)
    }

    @Test
    fun `sets registration and keepalive timers`() {
        val result = VoBizPreset.applyTo(credentials)
        assertEquals(300, result.regExpirySeconds)
        assertEquals(60, result.keepAliveSeconds)
    }

    @Test
    fun `uses 5060 for UDP and 5061 for TLS`() {
        val udp = VoBizPreset.applyTo(credentials.copy(transport = SipTransport.UDP))
        assertEquals(VoBizPreset.PORT_UDP, udp.port)

        val tls = VoBizPreset.applyTo(credentials.copy(transport = SipTransport.TLS))
        assertEquals(VoBizPreset.PORT_TLS, tls.port)
    }

    @Test
    fun `codec priority favours G722 then G711`() {
        val result = VoBizPreset.applyTo(credentials)
        assertEquals(SipConfig.DEFAULT_CODEC_PRIORITY, result.codecPriority)
        assertEquals("G722/16000", result.codecPriority.first())
    }
}
