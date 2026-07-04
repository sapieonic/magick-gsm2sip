package com.magick.gsm2sip.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Severity of a [LogEntry]. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/** Category so the UI log view can filter SIP vs GSM vs audio flow. */
enum class LogTag { SIP, GSM, AUDIO, GATEWAY, SYSTEM }

data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: LogTag,
    val message: String,
) {
    val time: String get() = TIME_FMT.format(Date(timestampMillis))

    private companion object {
        val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}

/**
 * In-memory, process-wide ring buffer of log lines mirrored to logcat. The UI
 * observes [entries] to render the real-time debug log. Kept intentionally
 * simple (no persistence) — it's a live troubleshooting aid, not an audit trail.
 */
object GatewayLog {

    private const val MAX_ENTRIES = 1000
    private const val LOGCAT_TAG = "Gsm2Sip"

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun d(tag: LogTag, message: String) = add(LogLevel.DEBUG, tag, message)
    fun i(tag: LogTag, message: String) = add(LogLevel.INFO, tag, message)
    fun w(tag: LogTag, message: String) = add(LogLevel.WARN, tag, message)
    fun e(tag: LogTag, message: String, t: Throwable? = null) =
        add(LogLevel.ERROR, tag, if (t != null) "${'$'}message: ${'$'}{t.message}" else message)

    fun clear() = _entries.update { emptyList() }

    private fun add(level: LogLevel, tag: LogTag, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        when (level) {
            LogLevel.DEBUG -> Log.d(LOGCAT_TAG, "[${'$'}tag] ${'$'}message")
            LogLevel.INFO -> Log.i(LOGCAT_TAG, "[${'$'}tag] ${'$'}message")
            LogLevel.WARN -> Log.w(LOGCAT_TAG, "[${'$'}tag] ${'$'}message")
            LogLevel.ERROR -> Log.e(LOGCAT_TAG, "[${'$'}tag] ${'$'}message")
        }
        _entries.update { current ->
            val next = if (current.size >= MAX_ENTRIES) current.drop(current.size - MAX_ENTRIES + 1) else current
            next + entry
        }
    }
}
