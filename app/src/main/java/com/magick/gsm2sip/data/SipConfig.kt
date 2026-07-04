package com.magick.gsm2sip.data

/** SIP transport protocol. */
enum class SipTransport { UDP, TCP, TLS }

/**
 * All user-configurable SIP + gateway settings. Immutable value object;
 * persisted via [SettingsRepository] and consumed by the SIP stack.
 */
data class SipConfig(
    // --- Registrar / account ---
    val domain: String = "",
    val username: String = "",
    val password: String = "",
    val realm: String = "*",        // "*" = accept any realm challenge
    val displayName: String = "",
    val port: Int = 5060,
    val transport: SipTransport = SipTransport.UDP,

    // --- Outbound / caller-id ---
    val outboundProxy: String = "",     // optional; empty = none
    val callerId: String = "",          // assigned DID; empty = use username

    // --- NAT / STUN ---
    val stunEnabled: Boolean = true,
    val stunServer: String = "stun.l.google.com:19302",

    // --- Registration / keep-alive ---
    val regExpirySeconds: Int = 300,
    val keepAliveSeconds: Int = 60,

    // --- Codec priority (highest first). See CodecPriority. ---
    val codecPriority: List<String> = DEFAULT_CODEC_PRIORITY,

    // --- Behaviour ---
    val autoStartOnBoot: Boolean = false,
    val autoAnswerGsm: Boolean = true,
    val dtmfRfc2833: Boolean = true,
) {
    /** SIP URI for this account, e.g. sip:1000@sip.vobiz.com */
    val accountUri: String get() = "sip:${'$'}username@${'$'}domain"

    val isComplete: Boolean
        get() = domain.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    companion object {
        /** G.722 (wideband) first, then G.711 A-law, then U-law, then Opus. */
        val DEFAULT_CODEC_PRIORITY = listOf("G722/16000", "PCMA/8000", "PCMU/8000", "opus/48000")
    }
}
