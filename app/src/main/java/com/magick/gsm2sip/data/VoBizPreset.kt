package com.magick.gsm2sip.data

/**
 * VoBiz wholesale SIP-trunk defaults. Applied by the "VoBiz preset" one-tap
 * button on the settings screen; every field remains user-overridable.
 *
 * These mirror VoBiz's documented trunk parameters. Replace [DOMAIN] /
 * [STUN_SERVER] with the exact values from your VoBiz account portal if they
 * differ from the wholesale defaults.
 */
object VoBizPreset {

    const val DOMAIN = "sip.vobiz.com"
    const val STUN_SERVER = "stun.vobiz.com"          // falls back to Google STUN if unreachable
    const val STUN_FALLBACK = "stun.l.google.com:19302"

    const val PORT_UDP = 5060
    const val PORT_TCP = 5060
    const val PORT_TLS = 5061

    const val REG_EXPIRY_SECONDS = 300
    const val KEEPALIVE_SECONDS = 60

    /** Merge preset defaults into an existing config, preserving credentials. */
    fun applyTo(existing: SipConfig): SipConfig = existing.copy(
        domain = DOMAIN,
        port = if (existing.transport == SipTransport.TLS) PORT_TLS else PORT_UDP,
        realm = "*",
        stunEnabled = true,
        stunServer = STUN_SERVER,
        regExpirySeconds = REG_EXPIRY_SECONDS,
        keepAliveSeconds = KEEPALIVE_SECONDS,
        codecPriority = SipConfig.DEFAULT_CODEC_PRIORITY,
        dtmfRfc2833 = true,
    )
}
