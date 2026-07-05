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

public class MediaFrame {
    private int type = pjmedia_frame_type.PJMEDIA_FRAME_TYPE_NONE;
    private long size = 0;
    private ByteVector buf = new ByteVector();
    public MediaFrame() {}
    public int getType() { return type; }
    public void setType(int value) { this.type = value; }
    public long getSize() { return size; }
    public void setSize(long value) { this.size = value; }
    public ByteVector getBuf() { return buf; }
    public void setBuf(ByteVector value) { this.buf = value; }
}
