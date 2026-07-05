package com.magick.gsm2sip.sip

import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import org.pjsip.pjsua2.LogEntry
import org.pjsip.pjsua2.LogWriter

/**
 * Pipes PJSIP's internal log output into [GatewayLog] so the in-app debug log
 * shows raw SIP traffic (REGISTER, INVITE, 200 OK, etc.). Registered via
 * [org.pjsip.pjsua2.LogConfig.setWriter].
 */
object PjLogBridge : LogWriter() {
    override fun write(entry: LogEntry) {
        val msg = entry.msg.trimEnd()
        when {
            entry.level <= 2 -> GatewayLog.w(LogTag.SIP, msg)
            else -> GatewayLog.d(LogTag.SIP, msg)
        }
    }
}
