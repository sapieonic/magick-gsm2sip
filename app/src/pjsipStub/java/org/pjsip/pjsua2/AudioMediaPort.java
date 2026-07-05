/*
 * COMPILE-TIME STUB — NOT the real PJSIP.
 *
 * This package mirrors the subset of the pjsua2 API used by Magick GSM2SIP so
 * the project compiles and unit-tests run without the native libpjsua2.so.
 * When a real PJSIP AAR is dropped into app/libs/, this stub source set is
 * excluded (see app/build.gradle.kts) and the genuine bindings are used.
 * At runtime with the stub, System.loadLibrary("pjsua2") fails and SIP is
 * disabled gracefully.
 */
package org.pjsip.pjsua2;

public class AudioMediaPort extends AudioMedia {
    public AudioMediaPort() {}
    public void createPort(String name, MediaFormatAudio fmt) throws Exception {}
    public void onFrameReceived(MediaFrame frame) {}
    public void onFrameRequested(MediaFrame frame) {}
    public void delete() {}
}
