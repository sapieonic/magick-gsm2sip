package com.magick.gsm2sip.telecom

import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag

/**
 * Self-managed [ConnectionService]. Used to model the gateway's own bridged
 * session as a first-class telecom "call" so Android grants it audio focus and
 * shows it in the call stack. The real cellular leg is still placed through the
 * SIM's ConnectionService via [android.telecom.TelecomManager.placeCall]; this
 * service represents the SIP<->GSM bridge session itself.
 */
class GsmConnectionService : ConnectionService() {

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        GatewayLog.i(LogTag.GSM, "creating outgoing self-managed connection")
        return buildConnection(request).apply {
            setDialing()
        }
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        GatewayLog.i(LogTag.GSM, "creating incoming self-managed connection")
        return buildConnection(request).apply {
            setRinging()
        }
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        GatewayLog.w(LogTag.GSM, "outgoing self-managed connection failed")
    }

    private fun buildConnection(request: ConnectionRequest?): GsmConnection {
        return GsmConnection().apply {
            setAddress(request?.address, TelecomManager.PRESENTATION_ALLOWED)
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
            audioModeIsVoip = true
            connectionCapabilities = Connection.CAPABILITY_HOLD or Connection.CAPABILITY_SUPPORT_HOLD
            setInitialized()
        }
    }
}

/** A single self-managed connection representing the bridge session. */
class GsmConnection : Connection() {

    override fun onAnswer() {
        GatewayLog.d(LogTag.GSM, "self-managed connection answered")
        setActive()
    }

    override fun onDisconnect() {
        GatewayLog.d(LogTag.GSM, "self-managed connection disconnected")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onAbort() {
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    override fun onReject() {
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }
}
