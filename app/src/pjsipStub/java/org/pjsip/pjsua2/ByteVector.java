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
 * NOTE: this mirrors real pjsua2's SWIG signatures (AbstractList<Short> with an
 * explicit add(Short)/get(int)) so it is BINARY-compatible — unit tests run
 * against it even though app code was compiled against the real bindings.
 */
package org.pjsip.pjsua2;

public class ByteVector extends java.util.AbstractList<Short> implements java.util.RandomAccess {
    private final java.util.ArrayList<Short> backing = new java.util.ArrayList<Short>();
    public ByteVector() {}
    @Override public Short get(int index) { return backing.get(index); }
    @Override public int size() { return backing.size(); }
    @Override public boolean add(Short e) { return backing.add(e); }
    @Override public void add(int index, Short e) { backing.add(index, e); }
    @Override public Short set(int index, Short e) { return backing.set(index, e); }
    @Override public Short remove(int index) { return backing.remove(index); }
    @Override public void clear() { backing.clear(); }
}
