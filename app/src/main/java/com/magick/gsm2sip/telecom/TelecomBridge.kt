package com.magick.gsm2sip.telecom

import android.telecom.Call
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide rendezvous between [GsmInCallService] (which the Android Telecom
 * framework instantiates on its own) and the [com.magick.gsm2sip.gateway.CallOrchestrator].
 *
 * The InCallService can't be constructed with our dependencies, so it publishes
 * GSM call events here and the orchestrator subscribes. Kept as a singleton
 * object because there is exactly one active InCallService per process.
 */
object TelecomBridge {

    /** GSM-side call lifecycle events observed by the InCallService. */
    sealed interface GsmEvent {
        data class Added(val number: String, val incoming: Boolean) : GsmEvent
        data object Ringing : GsmEvent
        data object Active : GsmEvent
        data class Removed(val cause: Int) : GsmEvent
    }

    private val _events = MutableSharedFlow<GsmEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GsmEvent> = _events

    /** The InCallService registers itself here so the orchestrator can drive calls. */
    @Volatile
    var inCallService: GsmInCallService? = null

    private val _hasActiveGsmCall = MutableStateFlow(false)
    val hasActiveGsmCall: StateFlow<Boolean> = _hasActiveGsmCall

    internal fun emit(event: GsmEvent) {
        _hasActiveGsmCall.value = when (event) {
            is GsmEvent.Active, is GsmEvent.Added, GsmEvent.Ringing -> true
            is GsmEvent.Removed -> false
        }
        _events.tryEmit(event)
    }

    /** True while the app is the default dialer AND a GSM Call handle exists. */
    val currentGsmCall: Call? get() = inCallService?.currentCall
}
