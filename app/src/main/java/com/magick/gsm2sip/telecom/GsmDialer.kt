package com.magick.gsm2sip.telecom

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag

/**
 * Places outbound GSM calls on the cellular modem and manages the self-managed
 * [PhoneAccountHandle] used by [GsmConnectionService].
 *
 * Placing the call through [TelecomManager.placeCall] routes it via the SIM's
 * built-in ConnectionService (the real radio), and — because this app is the
 * default dialer — the resulting [android.telecom.Call] appears in
 * [GsmInCallService], where the orchestrator drives it.
 */
class GsmDialer(private val context: Context) {

    private val telecom = context.getSystemService(TelecomManager::class.java)

    val selfManagedHandle: PhoneAccountHandle
        get() = PhoneAccountHandle(
            ComponentName(context, GsmConnectionService::class.java),
            SELF_MANAGED_ID,
        )

    /** Register the self-managed phone account once (idempotent). */
    fun ensurePhoneAccountRegistered() {
        val account = PhoneAccount.builder(selfManagedHandle, "Magick GSM2SIP Gateway")
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .build()
        runCatching { telecom.registerPhoneAccount(account) }
            .onFailure { GatewayLog.w(LogTag.GSM, "registerPhoneAccount: ${'$'}{it.message}") }
    }

    /**
     * Dial [number] on the cellular network. Requires CALL_PHONE and, on API 34,
     * the app to be the default dialer to reliably control the call afterwards.
     *
     * @return true if the placeCall request was issued.
     */
    fun placeGsmCall(number: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            GatewayLog.e(LogTag.GSM, "CALL_PHONE not granted; cannot dial ${'$'}number")
            return false
        }
        val uri = Uri.fromParts("tel", number, null)
        val extras = Bundle().apply {
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
            // Let Telecom pick the default SIM's calling account (the real radio).
        }
        return runCatching {
            GatewayLog.i(LogTag.GSM, "placing GSM call to ${'$'}number")
            telecom.placeCall(uri, extras)
            true
        }.onFailure { GatewayLog.e(LogTag.GSM, "placeCall failed", it) }.getOrDefault(false)
    }

    private companion object {
        const val SELF_MANAGED_ID = "magick_gsm2sip_self_managed"
    }
}
