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

public class pjsip_status_code {
    private final int value;
    private pjsip_status_code(int v) { this.value = v; }
    public int swigValue() { return value; }
    public static pjsip_status_code swigToEnum(int v) { return new pjsip_status_code(v); }
    public static final pjsip_status_code PJSIP_SC_TRYING = new pjsip_status_code(100);
    public static final pjsip_status_code PJSIP_SC_RINGING = new pjsip_status_code(180);
    public static final pjsip_status_code PJSIP_SC_OK = new pjsip_status_code(200);
    public static final pjsip_status_code PJSIP_SC_BUSY_HERE = new pjsip_status_code(486);
    public static final pjsip_status_code PJSIP_SC_NOT_ACCEPTABLE_HERE = new pjsip_status_code(488);
    public static final pjsip_status_code PJSIP_SC_DECLINE = new pjsip_status_code(603);
    public static final pjsip_status_code PJSIP_SC_SERVICE_UNAVAILABLE = new pjsip_status_code(503);
}
