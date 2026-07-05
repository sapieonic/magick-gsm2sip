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

public class Call {
    public Call(Account acc) {}
    public Call(Account acc, int callId) {}
    public int getId() { return -1; }
    public CallInfo getInfo() throws Exception { return new CallInfo(); }
    public void answer(CallOpParam prm) throws Exception {}
    public void hangup(CallOpParam prm) throws Exception {}
    public void makeCall(String dst, CallOpParam prm) throws Exception {}
    public AudioMedia getAudioMedia(int medIdx) throws Exception { return new AudioMedia(); }
    public void delete() {}
    public void onCallState(OnCallStateParam prm) {}
    public void onCallMediaState(OnCallMediaStateParam prm) {}
}
