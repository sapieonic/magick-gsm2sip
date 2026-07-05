package com.magick.gsm2sip

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented smoke test (runs on a device/emulator). CI runs the JVM unit
 * tests by default; this exists so the androidTest source set builds and can be
 * executed with `./gradlew connectedAndroidTest` when an emulator is available.
 */
@RunWith(AndroidJUnit4::class)
class AppContextTest {
    @Test
    fun usesGatewayPackageName() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.magick.gsm2sip", ctx.packageName)
    }
}
