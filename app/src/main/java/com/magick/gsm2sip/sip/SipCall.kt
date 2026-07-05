package com.magick.gsm2sip.sip

import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import org.pjsip.pjsua2.AudioMedia
import org.pjsip.pjsua2.Call
import org.pjsip.pjsua2.OnCallMediaStateParam
import org.pjsip.pjsua2.OnCallStateParam
import org.pjsip.pjsua2.pjmedia_type
import org.pjsip.pjsua2.pjsip_inv_state

/**
 * One PJSIP call leg. Subclasses pjsua2 [Call] to receive state + media
 * callbacks and forwards them to the gateway [SipListener].
 *
 * Use the 2-arg constructor for inbound calls (PJSIP supplies the id) and the
 * 1-arg constructor for outbound calls placed via [Call.makeCall].
 */
class SipCall : Call {

    private val listener: SipListener
    private val account: SipAccount

    constructor(account: SipAccount, listener: SipListener) : super(account) {
        this.account = account
        this.listener = listener
    }

    constructor(account: SipAccount, listener: SipListener, callId: Int) : super(account, callId) {
        this.account = account
        this.listener = listener
    }

    // Note: the call id is the inherited pjsua2 getId(), exposed to Kotlin as
    // the `id` property — we deliberately do NOT redeclare it here (that would
    // clash with getId()'s JVM signature).

    override fun onCallState(prm: OnCallStateParam) {
        val ci = runCatching { info }.getOrNull() ?: return
        val cid = getId()
        val mapped = when (ci.state) {
            pjsip_inv_state.PJSIP_INV_STATE_CALLING -> SipCallState.CALLING
            pjsip_inv_state.PJSIP_INV_STATE_EARLY -> SipCallState.EARLY
            pjsip_inv_state.PJSIP_INV_STATE_CONNECTING -> SipCallState.CONNECTING
            pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> SipCallState.CONFIRMED
            pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> SipCallState.DISCONNECTED
            else -> return
        }
        GatewayLog.d(LogTag.SIP, "call $cid state=${ci.stateText} (${ci.lastStatusCode})")
        listener.onCallState(cid, mapped, ci.lastStatusCode.swigValue())

        if (mapped == SipCallState.DISCONNECTED) {
            account.forget(cid)
            // pjsua2 requires the Call object be deleted once disconnected.
            runCatching { delete() }
        }
    }

    override fun onCallMediaState(prm: OnCallMediaStateParam) {
        val cid = getId()
        GatewayLog.d(LogTag.SIP, "call $cid media state changed")
        if (confirmedAudioMedia() != null) {
            listener.onCallMediaReady(cid)
        }
    }

    /** The active audio media once media negotiation is confirmed, else null. */
    fun confirmedAudioMedia(): AudioMedia? {
        val ci = runCatching { info }.getOrNull() ?: return null
        for (i in 0 until ci.media.size.toInt()) {
            val m = ci.media[i]
            if (m.type == pjmedia_type.PJMEDIA_TYPE_AUDIO) {
                return runCatching { getAudioMedia(i.toLong()) }.getOrNull()
            }
        }
        return null
    }
}
