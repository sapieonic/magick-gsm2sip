package com.magick.gsm2sip.gateway

import com.magick.gsm2sip.audio.AudioBridge
import com.magick.gsm2sip.data.CallDirection
import com.magick.gsm2sip.data.CallHistoryDao
import com.magick.gsm2sip.data.CallRecord
import com.magick.gsm2sip.data.SipConfig
import com.magick.gsm2sip.sip.SipCallState
import com.magick.gsm2sip.sip.SipListener
import com.magick.gsm2sip.sip.SipRegState
import com.magick.gsm2sip.sip.SipStack
import com.magick.gsm2sip.telecom.GsmDialer
import com.magick.gsm2sip.telecom.TelecomBridge
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.pjsip.pjsua2.pjsip_status_code

/**
 * The heart of the gateway. Implements [SipListener] to react to SIP events and
 * subscribes to [TelecomBridge] for GSM events, coordinating the two legs of a
 * single bridged call and driving the [AudioBridge].
 *
 * Handles exactly one active session at a time (a wholesale trunk gateway on a
 * single SIM bridges one call). Concurrent invites are declined.
 */
class CallOrchestrator(
    private val scope: CoroutineScope,
    private val sipStack: SipStack,
    private val gsmDialer: GsmDialer,
    private val audioBridge: AudioBridge,
    private val historyDao: CallHistoryDao,
    private val config: () -> SipConfig,
    private val setState: (GatewayState) -> Unit,
) : SipListener {

    /** Snapshot of the single in-flight bridged call. */
    private data class Session(
        val direction: CallDirection,
        var sipCallId: Int = -1,
        var gsmNumber: String = "",
        var sipRemote: String = "",
        var recordId: Long = -1,
        var gsmActive: Boolean = false,
        var sipConfirmed: Boolean = false,
        var bridged: Boolean = false,
    )

    @Volatile private var session: Session? = null

    fun observeGsmEvents() {
        scope.launch {
            TelecomBridge.events.collect { onGsmEvent(it) }
        }
    }

    // ==================== SIP callbacks ====================

    override fun onRegState(state: SipRegState) {
        when (state) {
            SipRegState.Registered -> if (session == null) setState(GatewayState.Registered)
            SipRegState.InProgress -> setState(GatewayState.Starting)
            SipRegState.Unregistered -> setState(GatewayState.Stopped)
            is SipRegState.Failed ->
                setState(GatewayState.Error("Registration failed: ${state.code} ${state.reason}"))
        }
    }

    override fun onIncomingCall(callId: Int, remoteUri: String, forwardNumber: String?) {
        // Only accept an INVITE that carries a forwarding target and only when idle.
        if (forwardNumber.isNullOrBlank()) {
            GatewayLog.w(LogTag.GATEWAY, "INVITE without X-GSM-Forward -> declined")
            sipStack.hangup(callId, pjsip_status_code.PJSIP_SC_NOT_ACCEPTABLE_HERE.swigValue())
            return
        }
        if (session != null) {
            GatewayLog.w(LogTag.GATEWAY, "busy; declining INVITE from $remoteUri")
            sipStack.hangup(callId, pjsip_status_code.PJSIP_SC_BUSY_HERE.swigValue())
            return
        }

        val s = Session(
            direction = CallDirection.SIP_TO_GSM,
            sipCallId = callId,
            gsmNumber = forwardNumber,
            sipRemote = remoteUri,
        )
        session = s
        setState(GatewayState.Calling(forwardNumber))
        GatewayLog.i(LogTag.GATEWAY, "SIP->GSM: forwarding $remoteUri to GSM $forwardNumber")

        // Ring the SIP caller while we set up the cellular leg, then dial GSM.
        sipStack.ring(callId)
        scope.launch {
            s.recordId = historyDao.insert(
                CallRecord(
                    direction = CallDirection.SIP_TO_GSM,
                    sipRemote = remoteUri,
                    gsmNumber = forwardNumber,
                    startedAtMillis = System.currentTimeMillis(),
                )
            )
        }
        if (!gsmDialer.placeGsmCall(forwardNumber)) {
            failSession("GSM dial failed", sipCode = pjsip_status_code.PJSIP_SC_SERVICE_UNAVAILABLE.swigValue())
        }
    }

    override fun onCallState(callId: Int, state: SipCallState, code: Int) {
        val s = session ?: return
        if (callId != s.sipCallId) return
        when (state) {
            SipCallState.CONFIRMED -> {
                s.sipConfirmed = true
                GatewayLog.i(LogTag.GATEWAY, "SIP leg confirmed")
                maybeBridge()
            }
            SipCallState.DISCONNECTED -> {
                GatewayLog.i(LogTag.GATEWAY, "SIP leg disconnected ($code); tearing down GSM")
                teardown("sip_disconnected")
            }
            else -> Unit
        }
    }

    override fun onCallMediaReady(callId: Int) {
        val s = session ?: return
        if (callId == s.sipCallId) maybeBridge()
    }

    // ==================== GSM callbacks ====================

    private fun onGsmEvent(event: TelecomBridge.GsmEvent) {
        when (event) {
            is TelecomBridge.GsmEvent.Added -> {
                if (session == null && event.incoming) {
                    // Inbound GSM with no SIP session yet -> originate the SIP leg.
                    startGsmToSip(event.number)
                }
            }
            TelecomBridge.GsmEvent.Active -> {
                val s = session ?: return
                s.gsmActive = true
                GatewayLog.i(LogTag.GATEWAY, "GSM leg active")
                scope.launch { historyDao.markGsmConnected(s.recordId, System.currentTimeMillis()) }
                // For SIP->GSM, answer SIP now that the GSM party picked up.
                if (s.direction == CallDirection.SIP_TO_GSM && !s.sipConfirmed) {
                    sipStack.answer(s.sipCallId)
                }
                maybeBridge()
            }
            is TelecomBridge.GsmEvent.Removed -> {
                if (session != null) {
                    GatewayLog.i(LogTag.GATEWAY, "GSM leg removed; tearing down SIP")
                    teardown("gsm_ended")
                }
            }
            TelecomBridge.GsmEvent.Ringing -> Unit
        }
    }

    /** GSM->SIP: an inbound cellular call arrived; originate the matching SIP leg. */
    private fun startGsmToSip(gsmNumber: String) {
        val sipCallId = sipStack.makeCall(gsmNumber) ?: run {
            GatewayLog.e(LogTag.GATEWAY, "failed to originate SIP leg for inbound GSM")
            return
        }
        val s = Session(
            direction = CallDirection.GSM_TO_SIP,
            sipCallId = sipCallId,
            gsmNumber = gsmNumber,
            sipRemote = "sip:$gsmNumber@${config().domain}",
        )
        session = s
        setState(GatewayState.Calling(gsmNumber))
        scope.launch {
            s.recordId = historyDao.insert(
                CallRecord(
                    direction = CallDirection.GSM_TO_SIP,
                    sipRemote = s.sipRemote,
                    gsmNumber = gsmNumber,
                    startedAtMillis = System.currentTimeMillis(),
                )
            )
        }
    }

    // ==================== Bridging / teardown ====================

    private fun maybeBridge() {
        val s = session ?: return
        if (s.bridged || !s.gsmActive || !s.sipConfirmed) return
        val media = sipStack.audioMedia(s.sipCallId)
        if (media == null) {
            GatewayLog.d(LogTag.GATEWAY, "SIP media not ready yet; deferring bridge")
            return
        }
        val bridged = audioBridge.start(media)
        s.bridged = bridged
        setState(GatewayState.InCall(s.sipRemote, audioBridged = bridged))
        if (bridged) {
            GatewayLog.i(LogTag.GATEWAY, "audio bridge established")
        } else {
            GatewayLog.w(
                LogTag.GATEWAY,
                "signalling bridged but audio NOT flowing (device not rooted / concurrency locked)"
            )
        }
    }

    private fun failSession(reason: String, sipCode: Int) {
        val s = session ?: return
        GatewayLog.e(LogTag.GATEWAY, "session failed: $reason")
        sipStack.hangup(s.sipCallId, sipCode)
        teardown(reason)
    }

    private fun teardown(result: String) {
        val s = session ?: return
        session = null
        audioBridge.stop()
        // Best-effort hang up both legs.
        runCatching { sipStack.hangup(s.sipCallId) }
        TelecomBridge.inCallService?.hangupCurrent()
        scope.launch {
            historyDao.markEnded(s.recordId, System.currentTimeMillis(), s.bridged, result)
        }
        setState(if (sipStack.isStarted) GatewayState.Registered else GatewayState.Stopped)
    }

    /** Called on gateway stop to abandon any in-flight session. */
    fun reset() {
        session?.let { teardown("gateway_stopped") }
    }
}
