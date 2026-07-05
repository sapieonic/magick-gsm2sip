package com.magick.gsm2sip.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import org.pjsip.pjsua2.AudioMedia
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Bridges bidirectional audio between the confirmed SIP call ([AudioMedia]) and
 * the GSM voice call.
 *
 *  SIP-remote voice  --(PJSIP)-->  [GsmAudioPort.toGsm]  --> AudioTrack --> GSM uplink
 *  GSM-remote voice  --> AudioRecord --> [GsmAudioPort.fromGsm]  --(PJSIP)--> SIP tx
 *
 * IMPORTANT — SYSTEM AUDIO CONCURRENCY:
 * Capturing the GSM voice stream (VOICE_CALL / VOICE_DOWNLINK) and injecting into
 * the uplink is blocked by stock Android unless the read-only concurrency system
 * properties are disabled:
 *   voice.voip.conc.disabled / voice.record.conc.disabled / voice.playback.conc.disabled
 * That requires root + a Magisk module (see README). On unrooted devices
 * [isSupported] returns false and the gateway runs SIP+GSM signalling only, with
 * no audio bridge. This class fails gracefully in that case.
 */
class AudioBridge(private val audioManager: AudioManager) {

    private companion object {
        const val CLOCK_RATE = 16000          // wideband, favours G.722
        const val PTIME_MS = 20
        const val SAMPLES_PER_FRAME = CLOCK_RATE * PTIME_MS / 1000   // 320
        const val FRAME_BYTES = SAMPLES_PER_FRAME * 2                // 640
    }

    private val running = AtomicBoolean(false)
    private var port: GsmAudioPort? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var captureThread: Thread? = null
    private var playThread: Thread? = null

    val isRunning: Boolean get() = running.get()

    /** Probe whether GSM voice-stream capture is permitted on this device. */
    @SuppressLint("MissingPermission")
    fun isSupported(): Boolean = runCatching {
        val minBuf = AudioRecord.getMinBufferSize(
            CLOCK_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return@runCatching false
        val probe = AudioRecord(
            MediaRecorder.AudioSource.VOICE_CALL,
            CLOCK_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf
        )
        val ok = probe.state == AudioRecord.STATE_INITIALIZED
        probe.release()
        ok
    }.getOrDefault(false)

    /**
     * Wire the bridge to a live SIP [sipMedia]. Returns true if audio is actually
     * flowing (device supports concurrent GSM capture), false if we could only
     * set up signalling.
     */
    @SuppressLint("MissingPermission")
    fun start(sipMedia: AudioMedia): Boolean {
        if (running.get()) return true
        if (!isSupported()) {
            GatewayLog.w(LogTag.AUDIO, "GSM audio capture unsupported (non-rooted / concurrency locked)")
            return false
        }

        val p = GsmAudioPort(CLOCK_RATE, SAMPLES_PER_FRAME)
        runCatching { p.createPort("gsm_bridge", p.format()) }
            .onFailure { GatewayLog.e(LogTag.AUDIO, "createPort failed", it); return false }

        // Cross-connect: SIP rx -> port (onFrameReceived), port -> SIP tx (onFrameRequested).
        runCatching {
            sipMedia.startTransmit(p)
            p.startTransmit(sipMedia)
        }.onFailure { GatewayLog.e(LogTag.AUDIO, "media transmit wiring failed", it); return false }
        port = p

        if (!openDevices()) {
            stop()
            return false
        }

        running.set(true)
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        startPumps()
        GatewayLog.i(LogTag.AUDIO, "audio bridge started ($CLOCK_RATE Hz, $PTIME_MS ms)")
        return true
    }

    @SuppressLint("MissingPermission")
    private fun openDevices(): Boolean = runCatching {
        val minRec = AudioRecord.getMinBufferSize(
            CLOCK_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_BYTES * 4)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_CALL,
            CLOCK_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minRec
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return@runCatching false }
        record = rec

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(rec.audioSessionId)?.also {
                it.enabled = true
                GatewayLog.i(LogTag.AUDIO, "AcousticEchoCanceler enabled")
            }
        } else {
            GatewayLog.w(LogTag.AUDIO, "AcousticEchoCanceler not available on this device")
        }

        val minPlay = AudioTrack.getMinBufferSize(
            CLOCK_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_BYTES * 4)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(CLOCK_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minPlay)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        true
    }.onFailure { GatewayLog.e(LogTag.AUDIO, "openDevices failed", it) }.getOrDefault(false)

    private fun startPumps() {
        val p = port ?: return
        val rec = record ?: return
        val trk = track ?: return
        rec.startRecording()
        trk.play()

        // GSM downlink -> SIP
        captureThread = thread(name = "gsm-capture", isDaemon = true) {
            val buf = ByteArray(FRAME_BYTES)
            while (running.get()) {
                val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n > 0) p.pushFromGsm(buf.copyOf(n))
            }
        }
        // SIP -> GSM uplink
        playThread = thread(name = "gsm-play", isDaemon = true) {
            while (running.get()) {
                val frame = p.pollToGsm()
                if (frame != null) {
                    trk.write(frame, 0, frame.size, AudioTrack.WRITE_BLOCKING)
                } else {
                    // brief idle spin without a Thread.sleep dependency on time source
                    Thread.yield()
                }
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false) && port == null) return
        runCatching { captureThread?.join(200) }
        runCatching { playThread?.join(200) }
        captureThread = null
        playThread = null

        runCatching { record?.stop() }
        runCatching { record?.release() }
        record = null
        runCatching { aec?.release() }
        aec = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null

        runCatching { port?.reset(); port?.delete() }
        port = null

        runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
        GatewayLog.i(LogTag.AUDIO, "audio bridge stopped")
    }
}
