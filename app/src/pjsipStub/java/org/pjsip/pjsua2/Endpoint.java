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

public class Endpoint {
    public Endpoint() {}
    public void libCreate() throws Exception {}
    public void libInit(EpConfig cfg) throws Exception {}
    public void libStart() throws Exception {}
    public void libDestroy() throws Exception {}
    public void delete() {}
    public int transportCreate(int type, TransportConfig cfg) throws Exception { return 0; }
    public void codecSetPriority(String codecId, short priority) throws Exception {}
    public CodecInfoVector codecEnum2() throws Exception { return new CodecInfoVector(); }
    public Version libVersion() { return new Version(); }
}
