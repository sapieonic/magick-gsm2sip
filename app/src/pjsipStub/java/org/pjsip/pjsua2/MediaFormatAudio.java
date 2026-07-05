/*
 * COMPILE-TIME STUB — NOT the real PJSIP.
 *
 * This package mirrors the subset of the pjsua2 API used by Magick GSM2SIP so
 * the project compiles and unit-tests run without the native libpjsua2.so.
 * When a real PJSIP AAR is dropped into app/libs/, this stub source set is
 * excluded (see app/build.gradle.kts) and the genuine bindings are used.
 * At runtime with the stub, System.loadLibrary("pjsua2") fails and SIP is
 * disabled gracefully.
 *
 * NOTE: SWIG getter/setter shape so the stub is BINARY-compatible with the real
 * bindings (unit tests run against it after app code compiled against the jar).
 */
package org.pjsip.pjsua2;

public class MediaFormatAudio extends MediaFormat {
    private long clockRate;
    private long channelCount;
    private long bitsPerSample;
    private long frameTimeUsec;
    public MediaFormatAudio() {}
    public long getClockRate() { return clockRate; }
    public void setClockRate(long value) { this.clockRate = value; }
    public long getChannelCount() { return channelCount; }
    public void setChannelCount(long value) { this.channelCount = value; }
    public long getBitsPerSample() { return bitsPerSample; }
    public void setBitsPerSample(long value) { this.bitsPerSample = value; }
    public long getFrameTimeUsec() { return frameTimeUsec; }
    public void setFrameTimeUsec(long value) { this.frameTimeUsec = value; }
}
