package com.magick.gsm2sip.sip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SipAccount.parseForwardHeader] — the X-GSM-Forward extraction
 * that decides which number gets dialed on the cellular modem.
 */
class ForwardHeaderTest {

    private fun invite(vararg extraHeaders: String): String = buildString {
        append("INVITE sip:gw@sip.vobiz.com SIP/2.0\r\n")
        append("Via: SIP/2.0/UDP 10.0.0.1:5060\r\n")
        append("From: <sip:caller@sip.vobiz.com>;tag=abc\r\n")
        append("To: <sip:gw@sip.vobiz.com>\r\n")
        extraHeaders.forEach { append(it).append("\r\n") }
        append("\r\n")
    }

    @Test
    fun `extracts number from X-GSM-Forward header`() {
        val msg = invite("X-GSM-Forward: +15551234567")
        assertEquals("+15551234567", SipAccount.parseForwardHeader(msg))
    }

    @Test
    fun `header match is case-insensitive`() {
        val msg = invite("x-gsm-forward: 8005551212")
        assertEquals("8005551212", SipAccount.parseForwardHeader(msg))
    }

    @Test
    fun `trims surrounding whitespace`() {
        val msg = invite("X-GSM-Forward:    +441234567890   ")
        assertEquals("+441234567890", SipAccount.parseForwardHeader(msg))
    }

    @Test
    fun `returns null when header absent`() {
        assertNull(SipAccount.parseForwardHeader(invite()))
    }

    @Test
    fun `returns null for blank message`() {
        assertNull(SipAccount.parseForwardHeader(""))
    }

    @Test
    fun `returns null when header value empty`() {
        val msg = invite("X-GSM-Forward:")
        assertNull(SipAccount.parseForwardHeader(msg))
    }
}
