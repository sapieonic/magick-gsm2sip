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
 * NOTE: real pjsua2 (SWIG) exposes enums as plain int constants, not Java
 * enums — this stub matches that shape.
 */
package org.pjsip.pjsua2;

public final class pjsip_status_code {
    public static final int PJSIP_SC_TRYING = 100;
    public static final int PJSIP_SC_RINGING = 180;
    public static final int PJSIP_SC_OK = 200;
    public static final int PJSIP_SC_BUSY_HERE = 486;
    public static final int PJSIP_SC_NOT_ACCEPTABLE_HERE = 488;
    public static final int PJSIP_SC_SERVICE_UNAVAILABLE = 503;
    public static final int PJSIP_SC_DECLINE = 603;
}
