package com.magick.gsm2sip.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Persistence round-trip for [SettingsRepository] on DataStore, run on the JVM
 * via Robolectric (no device/emulator needed).
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private val repo = SettingsRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun `saved config is read back intact`() = runBlocking {
        val config = SipConfig(
            domain = "sip.vobiz.com",
            username = "trunk-42",
            password = "hunter2",
            realm = "vobiz",
            displayName = "Lab GW",
            port = 5061,
            transport = SipTransport.TLS,
            stunEnabled = true,
            stunServer = "stun.vobiz.com",
            regExpirySeconds = 300,
            keepAliveSeconds = 60,
            autoAnswerGsm = false,
            autoStartOnBoot = true,
        )
        repo.save(config)

        val restored = repo.config.first()
        assertEquals(config.domain, restored.domain)
        assertEquals(config.username, restored.username)
        assertEquals(config.password, restored.password)
        assertEquals(config.transport, restored.transport)
        assertEquals(config.port, restored.port)
        assertEquals(config.stunServer, restored.stunServer)
        assertEquals(config.autoAnswerGsm, restored.autoAnswerGsm)
        assertEquals(config.autoStartOnBoot, restored.autoStartOnBoot)
    }

    @Test
    fun `codec priority survives serialization`() = runBlocking {
        val config = SipConfig(
            domain = "d", username = "u", password = "p",
            codecPriority = listOf("G722/16000", "PCMA/8000"),
        )
        repo.save(config)
        assertEquals(listOf("G722/16000", "PCMA/8000"), repo.config.first().codecPriority)
    }
}
