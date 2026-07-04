package com.magick.gsm2sip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SipConfigTest {

    @Test
    fun `accountUri builds sip uri from username and domain`() {
        val cfg = SipConfig(domain = "sip.vobiz.com", username = "1000")
        assertEquals("sip:1000@sip.vobiz.com", cfg.accountUri)
    }

    @Test
    fun `isComplete requires domain username and password`() {
        assertFalse(SipConfig().isComplete)
        assertFalse(SipConfig(domain = "d", username = "u").isComplete)
        assertTrue(SipConfig(domain = "d", username = "u", password = "p").isComplete)
    }

    @Test
    fun `default codec priority is G722 first then G711 variants`() {
        val codecs = SipConfig.DEFAULT_CODEC_PRIORITY
        assertEquals("G722/16000", codecs[0])
        assertEquals("PCMA/8000", codecs[1])
        assertEquals("PCMU/8000", codecs[2])
    }

    @Test
    fun `sensible defaults for a fresh config`() {
        val cfg = SipConfig()
        assertEquals(5060, cfg.port)
        assertEquals(SipTransport.UDP, cfg.transport)
        assertEquals(300, cfg.regExpirySeconds)
        assertEquals(60, cfg.keepAliveSeconds)
        assertTrue(cfg.autoAnswerGsm)
        assertTrue(cfg.dtmfRfc2833)
        assertFalse(cfg.autoStartOnBoot)
    }
}
