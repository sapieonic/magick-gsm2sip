package com.magick.gsm2sip.gateway

import android.content.Context
import android.media.AudioManager
import androidx.room.Room
import com.magick.gsm2sip.audio.AudioBridge
import com.magick.gsm2sip.data.CallHistoryDao
import com.magick.gsm2sip.data.GatewayDatabase
import com.magick.gsm2sip.data.SettingsRepository
import com.magick.gsm2sip.data.SipConfig
import com.magick.gsm2sip.sip.SipStack
import com.magick.gsm2sip.telecom.GsmDialer
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-wide façade that owns the gateway's long-lived collaborators (PJSIP
 * stack, orchestrator, audio bridge) and exposes a single observable
 * [GatewayState]. The UI and [GatewayService] both talk to this object; the
 * service merely keeps the process alive in the foreground while it runs.
 */
object GatewayController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<GatewayState>(GatewayState.Stopped)
    val state: StateFlow<GatewayState> = _state

    private lateinit var appContext: Context
    private lateinit var settings: SettingsRepository
    private lateinit var dao: CallHistoryDao
    private lateinit var dialer: GsmDialer
    private lateinit var audioBridge: AudioBridge

    private var sipStack: SipStack? = null
    private var orchestrator: CallOrchestrator? = null

    @Volatile private var initialized = false
    val isRunning: Boolean get() = sipStack?.isStarted == true

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        settings = SettingsRepository(appContext)
        dao = Room.databaseBuilder(appContext, GatewayDatabase::class.java, "gateway.db")
            .build().callHistoryDao()
        dialer = GsmDialer(appContext)
        audioBridge = AudioBridge(appContext.getSystemService(AudioManager::class.java))
        initialized = true
    }

    fun callHistoryDao(): CallHistoryDao = dao
    fun settingsRepository(): SettingsRepository = settings
    fun audioBridgeSupported(): Boolean = audioBridge.isSupported()

    /** Start the gateway: register the phone account, bring up PJSIP + orchestrator. */
    @Synchronized
    fun start() {
        if (isRunning) {
            GatewayLog.w(LogTag.GATEWAY, "start requested but already running")
            return
        }
        _state.value = GatewayState.Starting
        dialer.ensurePhoneAccountRegistered()

        scope.launch {
            val cfg: SipConfig = settings.config.first()
            if (!cfg.isComplete) {
                _state.value = GatewayState.Error("SIP credentials incomplete")
                return@launch
            }
            runCatching {
                val stack = SipStack(orchestratorHolder())
                val orch = CallOrchestrator(
                    scope = scope,
                    sipStack = stack,
                    gsmDialer = dialer,
                    audioBridge = audioBridge,
                    historyDao = dao,
                    config = { cfgSnapshot },
                    setState = { _state.value = it },
                )
                // Wire listener now that orchestrator exists.
                pendingOrchestrator = orch
                cfgSnapshot = cfg
                sipStack = stack
                orchestrator = orch
                orch.observeGsmEvents()
                stack.start(cfg)
            }.onFailure {
                GatewayLog.e(LogTag.GATEWAY, "gateway start failed", it)
                _state.value = GatewayState.Error(it.message ?: "start failed")
            }
        }
    }

    @Synchronized
    fun stop() {
        GatewayLog.i(LogTag.GATEWAY, "stopping gateway")
        orchestrator?.reset()
        audioBridge.stop()
        sipStack?.stop()
        sipStack = null
        orchestrator = null
        _state.value = GatewayState.Stopped
    }

    // --- Listener wiring ---------------------------------------------------
    // SipStack needs a SipListener at construction, but the orchestrator (which
    // implements it) also needs the stack. Break the cycle with a forwarding
    // holder resolved lazily.
    @Volatile private var pendingOrchestrator: CallOrchestrator? = null
    @Volatile private var cfgSnapshot: SipConfig = SipConfig()

    private fun orchestratorHolder() = object : com.magick.gsm2sip.sip.SipListener {
        override fun onRegState(state: com.magick.gsm2sip.sip.SipRegState) {
            pendingOrchestrator?.onRegState(state)
        }
        override fun onIncomingCall(callId: Int, remoteUri: String, forwardNumber: String?) {
            pendingOrchestrator?.onIncomingCall(callId, remoteUri, forwardNumber)
        }
        override fun onCallState(callId: Int, state: com.magick.gsm2sip.sip.SipCallState, code: Int) {
            pendingOrchestrator?.onCallState(callId, state, code)
        }
        override fun onCallMediaReady(callId: Int) {
            pendingOrchestrator?.onCallMediaReady(callId)
        }
    }
}
