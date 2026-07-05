package com.magick.gsm2sip.sip

/** Registration outcome reported by PJSIP for the account. */
sealed interface SipRegState {
    data object InProgress : SipRegState
    data object Registered : SipRegState
    data object Unregistered : SipRegState
    data class Failed(val code: Int, val reason: String) : SipRegState
}

/** State of a single PJSIP call leg. */
enum class SipCallState { CALLING, EARLY, CONNECTING, CONFIRMED, DISCONNECTED }

/**
 * Events the SIP stack raises to the gateway orchestrator. Implemented by
 * [com.magick.gsm2sip.gateway.CallOrchestrator]. All callbacks arrive on a
 * PJSIP worker thread — implementors must hop to their own executor before
 * touching Android UI/telecom state.
 */
interface SipListener {
    fun onRegState(state: SipRegState)

    /**
     * An inbound INVITE arrived.
     *
     * @param callId       PJSIP call id (opaque handle).
     * @param remoteUri    caller's SIP URI.
     * @param forwardNumber phone number parsed from the X-GSM-Forward header,
     *                      or null if the header was absent (a plain SIP call).
     */
    fun onIncomingCall(callId: Int, remoteUri: String, forwardNumber: String?)

    fun onCallState(callId: Int, state: SipCallState, code: Int)

    /** The call's confirmed audio media is available for bridging. */
    fun onCallMediaReady(callId: Int)
}
