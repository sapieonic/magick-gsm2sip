package com.magick.gsm2sip

import android.app.Application
import com.magick.gsm2sip.gateway.GatewayController
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        loadNativeLibraries()
        GatewayController.init(this)
    }

    /**
     * PJSIP's pjsua2 JNI wrapper. `libpjsua2.so` is produced by
     * pjsip-android-builder (or shipped inside the prebuilt AAR). If it is
     * missing, SIP features are disabled but the rest of the app still runs so
     * the user can review settings.
     */
    private fun loadNativeLibraries() {
        runCatching {
            System.loadLibrary("pjsua2")
            GatewayLog.i(LogTag.SYSTEM, "loaded libpjsua2.so")
        }.onFailure {
            GatewayLog.e(
                LogTag.SYSTEM,
                "libpjsua2.so not found — add the PJSIP AAR/native libs (see docs/PJSIP_BUILD.md)",
                it,
            )
        }
    }
}
