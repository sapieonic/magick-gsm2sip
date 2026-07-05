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

public final class pjmedia_frame_type {
    public static final int PJMEDIA_FRAME_TYPE_NONE = 0;
    public static final int PJMEDIA_FRAME_TYPE_AUDIO = 1;
    public static final int PJMEDIA_FRAME_TYPE_EXTENDED = 2;
    public static final int PJMEDIA_FRAME_TYPE_VIDEO = 3;
}
