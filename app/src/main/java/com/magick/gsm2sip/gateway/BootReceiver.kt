package com.magick.gsm2sip.gateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.magick.gsm2sip.data.SettingsRepository
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Restarts the gateway after reboot when the user enabled auto-start. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val autoStart = runCatching {
            runBlocking { SettingsRepository(context).config.first().autoStartOnBoot }
        }.getOrDefault(false)
        if (autoStart) {
            GatewayLog.i(LogTag.SYSTEM, "boot completed; auto-starting gateway")
            GatewayService.start(context)
        }
    }
}
