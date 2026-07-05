package com.magick.gsm2sip.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.magick.gsm2sip.data.SettingsRepository
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

/**
 * The default-dialer entry point. When this app holds the default-phone role,
 * Android routes every GSM call through here, letting the gateway observe state,
 * auto-answer inbound calls, and route audio to earpiece/speaker off.
 *
 * Real cellular calls placed via [android.telecom.TelecomManager.placeCall] also
 * surface here as outgoing [Call]s — that is how the SIP->GSM leg is controlled.
 */
class GsmInCallService : InCallService() {

    var currentCall: Call? = null
        private set

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            handleState(call, state)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callCallback)

        val number = call.details.handle?.schemeSpecificPart ?: "unknown"
        val incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        GatewayLog.i(LogTag.GSM, "GSM call added: $number (incoming=$incoming)")
        TelecomBridge.emit(TelecomBridge.GsmEvent.Added(number, incoming))
        TelecomBridge.inCallService = this

        // Force audio out of the earpiece/speaker; the gateway taps the streams.
        runCatching { setAudioRoute(CallAudioState.ROUTE_EARPIECE) }

        handleState(call, call.state)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) currentCall = null
        GatewayLog.i(LogTag.GSM, "GSM call removed")
        TelecomBridge.emit(TelecomBridge.GsmEvent.Removed(call.details.disconnectCause?.code ?: 0))
    }

    private fun handleState(call: Call, state: Int) {
        when (state) {
            Call.STATE_RINGING -> {
                GatewayLog.i(LogTag.GSM, "GSM ringing")
                TelecomBridge.emit(TelecomBridge.GsmEvent.Ringing)
                if (autoAnswerEnabled()) {
                    GatewayLog.i(LogTag.GSM, "auto-answering GSM call")
                    call.answer(0 /* VideoProfile.STATE_AUDIO_ONLY */)
                }
            }
            Call.STATE_ACTIVE -> {
                GatewayLog.i(LogTag.GSM, "GSM active")
                TelecomBridge.emit(TelecomBridge.GsmEvent.Active)
            }
            Call.STATE_DISCONNECTED -> {
                GatewayLog.i(LogTag.GSM, "GSM disconnected")
            }
        }
    }

    /** Hangup / disconnect the current GSM leg. Called by the orchestrator. */
    fun hangupCurrent() {
        currentCall?.let {
            GatewayLog.i(LogTag.GSM, "orchestrator requested GSM hangup")
            it.disconnect()
        }
    }

    private fun autoAnswerEnabled(): Boolean = runCatching {
        runBlocking { SettingsRepository(applicationContext).config.first().autoAnswerGsm }
    }.getOrDefault(true)

    override fun onDestroy() {
        if (TelecomBridge.inCallService == this) TelecomBridge.inCallService = null
        super.onDestroy()
    }
}
