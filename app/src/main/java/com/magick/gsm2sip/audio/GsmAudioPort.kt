package com.magick.gsm2sip.audio

import com.magick.gsm2sip.util.GatewayLog
import com.magick.gsm2sip.util.LogTag
import org.pjsip.pjsua2.AudioMediaPort
import org.pjsip.pjsua2.MediaFrame
import org.pjsip.pjsua2.MediaFrameType
import org.pjsip.pjsua2.MediaFormatAudio
import java.util.concurrent.ArrayBlockingQueue

/**
 * A pjsua2 [AudioMediaPort] that bridges the SIP call's PCM stream to the GSM
 * side via two lock-free-ish ring buffers filled/drained by [AudioBridge].
 *
 *  - [onFrameReceived] : PCM arriving FROM the SIP call (remote SIP party voice)
 *                        -> queued to [toGsm], later played into the GSM uplink.
 *  - [onFrameRequested] : PCM the SIP call wants to SEND -> pulled from [fromGsm],
 *                        which [AudioBridge] fills from the GSM downlink capture.
 *
 * All buffers hold signed 16-bit little-endian mono PCM at [clockRate].
 */
class GsmAudioPort(
    private val clockRate: Int,
    private val samplesPerFrame: Int,
) : AudioMediaPort() {

    private val frameBytes = samplesPerFrame * 2
    // ~10 frames (200ms @ 20ms ptime) of jitter buffering each way.
    val toGsm = ArrayBlockingQueue<ByteArray>(10)
    val fromGsm = ArrayBlockingQueue<ByteArray>(10)

    private val silence = ByteArray(frameBytes)

    fun format(): MediaFormatAudio = MediaFormatAudio().apply {
        type = 0                    // PJMEDIA_TYPE_AUDIO
        clockRate = this@GsmAudioPort.clockRate.toLong()
        channelCount = 1L
        bitsPerSample = 16L
        frameTimeUsec = (samplesPerFrame.toLong() * 1_000_000L) / this@GsmAudioPort.clockRate
    }

    override fun onFrameReceived(frame: MediaFrame) {
        if (frame.type != MediaFrameType.PJMEDIA_FRAME_TYPE_AUDIO) return
        val data = frame.buf.toByteArray()
        // Drop oldest on overflow to keep latency bounded rather than blocking PJSIP.
        if (!toGsm.offer(data)) {
            toGsm.poll()
            toGsm.offer(data)
        }
    }

    override fun onFrameRequested(frame: MediaFrame) {
        frame.type = MediaFrameType.PJMEDIA_FRAME_TYPE_AUDIO
        val data = fromGsm.poll() ?: silence
        frame.buf = data.toByteVectorSafe(frameBytes)
    }

    /** Feed one captured GSM-downlink frame toward the SIP transmit path. */
    fun pushFromGsm(pcm: ByteArray) {
        if (!fromGsm.offer(pcm)) {
            fromGsm.poll()
            fromGsm.offer(pcm)
        }
    }

    /** Take one frame of SIP-received audio to play into the GSM uplink. */
    fun pollToGsm(): ByteArray? = toGsm.poll()

    fun reset() {
        toGsm.clear()
        fromGsm.clear()
        GatewayLog.d(LogTag.AUDIO, "audio port buffers reset")
    }
}
