package com.magick.gsm2sip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallRecordTest {

    @Test
    fun `duration is null while call in progress`() {
        val record = CallRecord(
            direction = CallDirection.SIP_TO_GSM,
            sipRemote = "sip:a@b",
            gsmNumber = "123",
            startedAtMillis = 1_000L,
        )
        assertNull(record.durationSeconds)
    }

    @Test
    fun `duration computed from start and end`() {
        val record = CallRecord(
            direction = CallDirection.GSM_TO_SIP,
            sipRemote = "sip:a@b",
            gsmNumber = "123",
            startedAtMillis = 10_000L,
            endedAtMillis = 73_000L,
        )
        assertEquals(63L, record.durationSeconds)
    }

    @Test
    fun `both directions are representable`() {
        assertEquals(2, CallDirection.entries.size)
    }
}
