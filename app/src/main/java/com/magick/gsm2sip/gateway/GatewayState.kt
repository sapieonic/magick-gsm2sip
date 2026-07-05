package com.magick.gsm2sip.gateway

/**
 * High-level state of the gateway, surfaced to the UI status indicator.
 *
 * The lifecycle roughly follows:
 *   Stopped -> Starting -> Registered -> (Calling <-> InCall) -> Registered -> Stopped
 * with [Error] reachable from any state.
 */
sealed interface GatewayState {
    /** Service not running; PJSIP endpoint destroyed. */
    data object Stopped : GatewayState

    /** Endpoint created, registration in flight. */
    data object Starting : GatewayState

    /** Registered with the SIP trunk and idle. */
    data object Registered : GatewayState

    /** A call leg is being set up (SIP INVITE received / GSM dialing). */
    data class Calling(val remote: String) : GatewayState

    /** Both legs are up and audio is (attempting to be) bridged. */
    data class InCall(val remote: String, val audioBridged: Boolean) : GatewayState

    /** Unrecoverable-ish error; message is human-readable. */
    data class Error(val message: String) : GatewayState

    val label: String
        get() = when (this) {
            Stopped -> "Idle"
            Starting -> "Starting…"
            Registered -> "Registered"
            is Calling -> "Calling $remote"
            is InCall -> if (audioBridged) "In call (bridged)" else "In call (no audio)"
            is Error -> "Error"
        }
}
