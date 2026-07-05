package com.magick.gsm2sip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.magick.gsm2sip.data.CallRecord
import com.magick.gsm2sip.data.SipConfig
import com.magick.gsm2sip.data.VoBizPreset
import com.magick.gsm2sip.gateway.GatewayController
import com.magick.gsm2sip.gateway.GatewayService
import com.magick.gsm2sip.gateway.GatewayState
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single view-model backing every screen (MVVM). Exposes gateway state,
 * persisted config, live logs, and call history as flows, and forwards user
 * intents to [GatewayController] / [GatewayService].
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = GatewayController.settingsRepository()
    private val historyDao = GatewayController.callHistoryDao()

    val state: StateFlow<GatewayState> = GatewayController.state

    val config: StateFlow<SipConfig> = settings.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SipConfig())

    val logs: StateFlow<List<LogEntry>> = GatewayLog.entries

    val history: StateFlow<List<CallRecord>> = historyDao.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val audioBridgeSupported: Boolean get() = GatewayController.audioBridgeSupported()

    fun saveConfig(config: SipConfig) = viewModelScope.launch { settings.save(config) }

    fun applyVoBizPreset(current: SipConfig) = viewModelScope.launch {
        settings.save(VoBizPreset.applyTo(current))
    }

    fun startGateway() = GatewayService.start(getApplication())

    fun stopGateway() = GatewayService.stop(getApplication())

    fun clearLogs() = GatewayLog.clear()

    fun clearHistory() = viewModelScope.launch { historyDao.clear() }
}
