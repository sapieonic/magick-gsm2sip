package com.magick.gsm2sip.sip

import com.magick.gsm2sip.data.SipConfig
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import org.pjsip.pjsua2.Account
import org.pjsip.pjsua2.AccountConfig
import org.pjsip.pjsua2.AuthCredInfo
import org.pjsip.pjsua2.OnIncomingCallParam
import org.pjsip.pjsua2.OnRegStateParam
import java.util.concurrent.ConcurrentHashMap

/**
 * A registered VoBiz SIP account. Bridges pjsua2 [Account] callbacks to the
 * gateway's [SipListener] and keeps a registry of live [SipCall] objects keyed
 * by PJSIP call id.
 */
class SipAccount(
    private val listener: SipListener,
    private val callFactoryForIncoming: (Int) -> SipCall?,
) : Account() {

    private val calls = ConcurrentHashMap<Int, SipCall>()

    fun register(call: SipCall) {
        calls[call.id] = call
    }

    fun callFor(id: Int): SipCall? = calls[id]

    fun forget(id: Int) {
        calls.remove(id)
    }

    override fun onRegState(prm: OnRegStateParam) {
        val code = prm.code.swigValue()
        val active = info.regIsActive
        val state = when {
            code / 100 == 2 && active -> SipRegState.Registered
            code / 100 == 2 -> SipRegState.Unregistered
            code == 0 -> SipRegState.InProgress
            else -> SipRegState.Failed(code, prm.reason)
        }
        GatewayLog.i(LogTag.SIP, "reg state: code=${'$'}code active=${'$'}active reason=${'$'}{prm.reason}")
        listener.onRegState(state)
    }

    override fun onIncomingCall(prm: OnIncomingCallParam) {
        val callId = prm.callId
        val call = SipCall(this, listener, callId)
        calls[callId] = call

        val info = runCatching { call.info }.getOrNull()
        val remoteUri = info?.remoteUri ?: "unknown"

        // The X-GSM-Forward header carries the number to dial on the cellular
        // modem. It lives in the raw INVITE; pull it from the whole SIP message.
        val wholeMsg = runCatching { prm.rdata.wholeMsg }.getOrDefault("")
        val forward = parseForwardHeader(wholeMsg)

        GatewayLog.i(LogTag.SIP, "incoming INVITE from ${'$'}remoteUri; X-GSM-Forward=${'$'}forward")
        listener.onIncomingCall(callId, remoteUri, forward)
    }

    companion object {
        private const val FORWARD_HEADER = "X-GSM-Forward"

        /** Case-insensitive single-line header extraction from a raw SIP message. */
        fun parseForwardHeader(rawSipMessage: String): String? {
            if (rawSipMessage.isBlank()) return null
            return rawSipMessage.lineSequence()
                .firstOrNull { it.trimStart().startsWith(FORWARD_HEADER, ignoreCase = true) }
                ?.substringAfter(':', "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        fun buildConfig(config: SipConfig): AccountConfig = AccountConfig().apply {
            idUri = if (config.displayName.isNotBlank()) {
                "\"${'$'}{config.displayName}\" <${'$'}{config.accountUri}>"
            } else {
                config.accountUri
            }

            regConfig.apply {
                registrarUri = "sip:${'$'}{config.domain}:${'$'}{config.port}"
                timeoutSec = config.regExpirySeconds.toLong()
                retryIntervalSec = 30L
                registerOnAdd = true
            }

            sipConfig.authCreds.add(
                AuthCredInfo("digest", config.realm, config.username, 0, config.password)
            )
            if (config.outboundProxy.isNotBlank()) {
                sipConfig.proxies.add(config.outboundProxy)
            }

            // NAT / STUN + keep-alive OPTIONS ping. STUN servers themselves are
            // configured endpoint-wide (see SipStack); here we enable ICE and
            // contact rewriting so responses return through the NAT binding.
            natConfig.apply {
                iceEnabled = config.stunEnabled
                contactRewriteUse = 1
                // SIP keep-alive interval (seconds) on the transport.
                udpKaIntervalSec = config.keepAliveSeconds.toLong()
            }

            // RFC 2833 telephone-event DTMF is PJSIP's default; nothing to toggle.
        }
    }
}
