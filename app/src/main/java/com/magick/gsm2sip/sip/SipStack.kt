package com.magick.gsm2sip.sip

import com.magick.gsm2sip.data.SipConfig
import com.magick.gsm2sip.data.SipTransport
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.CallOpParam
import org.pjsip.pjsua2.CodecInfo
import org.pjsip.pjsua2.Endpoint
import org.pjsip.pjsua2.EpConfig
import org.pjsip.pjsua2.LogConfig
import org.pjsip.pjsua2.TransportConfig
import org.pjsip.pjsua2.pjsip_status_code
import org.pjsip.pjsua2.pjsip_transport_type_e

/**
 * Thin, lifecycle-safe facade over the PJSIP `pjsua2` [Endpoint].
 *
 * Owns the single process-wide PJSIP library instance, creates the transport,
 * applies codec priorities, and registers the [SipAccount]. All SIP work funnels
 * through here so the rest of the app never touches pjsua2 directly.
 *
 * Requires the native `libpjsua2.so` (loaded in [App]) and the pjsua2 Java
 * bindings. See docs/PJSIP_BUILD.md.
 */
class SipStack(private val listener: SipListener) {

    @Volatile private var endpoint: Endpoint? = null
    @Volatile private var account: SipAccount? = null
    private var config: SipConfig = SipConfig()

    val isStarted: Boolean get() = endpoint != null

    /** Create + start the PJSIP library and register the account. Idempotent-ish. */
    @Synchronized
    fun start(config: SipConfig) {
        if (endpoint != null) {
            GatewayLog.w(LogTag.SIP, "SIP stack already started; restarting")
            stop()
        }
        this.config = config

        val ep = Endpoint()
        endpoint = ep
        ep.libCreate()

        val epConfig = EpConfig().apply {
            // Route PJSIP's internal logging into our log view at a modest level.
            logConfig = LogConfig().apply {
                level = 4L
                consoleLevel = 4L
                writer = PjLogBridge
            }
            uaConfig.apply {
                userAgent = "MagickGsm2Sip/1.0 (pjsua2)"
                if (config.stunEnabled && config.stunServer.isNotBlank()) {
                    stunServer.add(config.stunServer)
                }
            }
            medConfig.apply {
                clockRate = 16000L           // wideband to favour G.722
                sndClockRate = 16000L
                ecTailLen = 200L             // built-in echo canceller tail (ms)
                noVad = false
            }
        }
        ep.libInit(epConfig)

        createTransport(ep, config)
        applyCodecPriority(ep, config)

        ep.libStart()
        GatewayLog.i(LogTag.SIP, "PJSIP library started (v${'$'}{ep.libVersion().full})")

        val acc = SipAccount(listener) { id -> callObjectFor(id) }
        acc.create(buildAccountConfig(config))
        account = acc
    }

    @Synchronized
    fun stop() {
        val ep = endpoint ?: return
        runCatching { account?.let { it.setRegistration(false); it.delete() } }
            .onFailure { GatewayLog.w(LogTag.SIP, "account teardown: ${'$'}{it.message}") }
        account = null
        runCatching {
            ep.libDestroy()
            ep.delete()
        }.onFailure { GatewayLog.e(LogTag.SIP, "libDestroy failed", it) }
        endpoint = null
        GatewayLog.i(LogTag.SIP, "PJSIP library destroyed")
    }

    /** Re-register (used by keep-alive / manual refresh). */
    @Synchronized
    fun reRegister() = runCatching { account?.setRegistration(true) }
        .onFailure { GatewayLog.e(LogTag.SIP, "re-register failed", it) }

    /** Answer an inbound call with 200 OK. */
    fun answer(callId: Int) = withCall(callId) { call ->
        val prm = CallOpParam(true)
        prm.statusCode = pjsip_status_code.PJSIP_SC_OK
        call.answer(prm)
        GatewayLog.i(LogTag.SIP, "answered SIP call ${'$'}callId")
    }

    /** Ring (send 180) while the GSM leg is being set up. */
    fun ring(callId: Int) = withCall(callId) { call ->
        val prm = CallOpParam(true)
        prm.statusCode = pjsip_status_code.PJSIP_SC_RINGING
        call.answer(prm)
    }

    /** Reject/terminate a call with the given SIP status code. */
    fun hangup(callId: Int, code: Int = pjsip_status_code.PJSIP_SC_DECLINE.swigValue()) =
        withCall(callId) { call ->
            val prm = CallOpParam(true)
            prm.statusCode = pjsip_status_code.swigToEnum(code)
            call.hangup(prm)
            GatewayLog.i(LogTag.SIP, "hung up SIP call ${'$'}callId (code=${'$'}code)")
        }

    /** Place an outbound SIP call to [number]@domain (used for GSM->SIP bridging). */
    fun makeCall(number: String): Int? {
        val acc = account ?: return null
        val uri = "sip:${'$'}number@${'$'}{config.domain}"
        return runCatching {
            val call = SipCall(acc, listener)
            val prm = CallOpParam(true)
            call.makeCall(uri, prm)
            acc.register(call)
            GatewayLog.i(LogTag.SIP, "placed outbound SIP call to ${'$'}uri")
            call.id
        }.onFailure { GatewayLog.e(LogTag.SIP, "makeCall failed", it) }.getOrNull()
    }

    /**
     * The confirmed [AudioMedia] for a call, or null if media isn't up yet.
     * Handed to the [com.magick.gsm2sip.audio.AudioBridge] for GSM<->SIP routing.
     */
    fun audioMedia(callId: Int): AudioMedia? = callObjectFor(callId)?.confirmedAudioMedia()

    private fun callObjectFor(callId: Int): SipCall? = account?.callFor(callId)

    private inline fun withCall(callId: Int, block: (SipCall) -> Unit) {
        val call = callObjectFor(callId)
        if (call == null) {
            GatewayLog.w(LogTag.SIP, "no call object for id ${'$'}callId")
            return
        }
        runCatching { block(call) }.onFailure { GatewayLog.e(LogTag.SIP, "call op failed", it) }
    }

    // ---------------------------------------------------------------------

    private fun createTransport(ep: Endpoint, config: SipConfig) {
        val tcfg = TransportConfig().apply { port = config.port.toLong() }
        val type = when (config.transport) {
            SipTransport.UDP -> pjsip_transport_type_e.PJSIP_TRANSPORT_UDP
            SipTransport.TCP -> pjsip_transport_type_e.PJSIP_TRANSPORT_TCP
            SipTransport.TLS -> pjsip_transport_type_e.PJSIP_TRANSPORT_TLS
        }
        ep.transportCreate(type, tcfg)
        GatewayLog.i(LogTag.SIP, "transport ${'$'}{config.transport} on port ${'$'}{config.port}")
    }

    /**
     * Enable only the requested codecs and order them by priority. Everything
     * not in [SipConfig.codecPriority] is disabled so the SDP offer stays clean:
     * G.722 (wideband) first, then G.711 A/U-law, then Opus.
     */
    private fun applyCodecPriority(ep: Endpoint, config: SipConfig) {
        val wanted = config.codecPriority
        val available: List<CodecInfo> = ep.codecEnum2().toList()
        for (codec in available) {
            val id = codec.codecId
            val idx = wanted.indexOfFirst { id.equals(it, ignoreCase = true) }
            val priority: Short = if (idx >= 0) (255 - idx).toShort() else 0.toShort()
            ep.codecSetPriority(id, priority)
            GatewayLog.d(LogTag.SIP, "codec ${'$'}id -> priority ${'$'}priority")
        }
    }

    private fun buildAccountConfig(config: SipConfig) =
        SipAccount.buildConfig(config)
}
