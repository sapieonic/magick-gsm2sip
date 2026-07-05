# Building / obtaining PJSIP (pjsua2) for Android

Magick GSM2SIP talks to PJSIP through the `pjsua2` Java bindings
(`org.pjsip.pjsua2.*`) backed by the native `libpjsua2.so`. These are **not**
checked into the repo — you build them once and drop the artifacts into
`app/libs/`. This guide covers building them with the codecs the app expects
(**opus, g722, g711**) and where to place the output.

---

## Option A — pjsip-android-builder (recommended)

[pjsip-android-builder](https://github.com/vpandey-om/pjsip-android-builder) is a
scripted wrapper around the official [pjproject](https://github.com/pjsip/pjproject)
that cross-compiles PJSIP + `pjsua2` for Android and produces the SWIG Java
bindings automatically.

1. Install the Android NDK and set the paths in the builder's `config.conf`.
2. Point it at a recent `pjproject` release (2.13+ recommended).
3. Set the target ABIs to match `app/build.gradle.kts`:

   ```
   arm64-v8a  armeabi-v7a  x86_64
   ```

4. Enable the codecs (see [Codec configuration](#codec-configuration) below).
5. Run the build. It produces the SWIG-generated `pjsua2` Java sources/jar and
   one `libpjsua2.so` per ABI.

## Option B — build pjproject directly

If you build [pjproject](https://github.com/pjsip/pjproject) by hand:

1. Configure with the Android target and NDK toolchain
   (`./configure-android`), then `make dep && make`.
2. Build the `pjsua2` SWIG Java bindings under `pjsip-apps/src/swig` (`make` in
   the `java` target). This yields the `org.pjsip.pjsua2` classes and
   `libpjsua2.so`.
3. Repeat per ABI (arm64-v8a, armeabi-v7a, x86_64).

---

## Artifacts produced

Either route gives you:

- **Java bindings** — the `org.pjsip.pjsua2.*` classes, packaged either inside an
  **AAR** or as a plain **`pjsua2.jar`**.
- **Native libraries** — `libpjsua2.so`, one per ABI. (This single `.so` also
  pulls in the pjlib/pjmedia/codec code linked statically.)

---

## Where to place the artifacts

`app/build.gradle.kts` picks a dependency in this priority order:

```kotlin
when {
    file("libs/pjsua2-release.aar").exists() -> implementation(files("libs/pjsua2-release.aar"))
    file("libs/pjsua2.jar").exists()         -> implementation(files("libs/pjsua2.jar"))
    else -> /* stub source set */ android.sourceSets.getByName("main").java.srcDir("src/pjsipStub/java")
}
```

### Preferred: prebuilt AAR

Bundle the bindings and all `.so` files into one AAR and place it at:

```
app/libs/pjsua2-release.aar
```

The `flatDir` repo declared in `settings.gradle.kts` (`dirs("app/libs")`)
resolves it. This is the least error-prone option because the ABIs travel with
the bindings.

### Alternative: jar + jniLibs

If you have a raw jar instead, place it plus the native libraries manually:

```
app/libs/pjsua2.jar
app/src/main/jniLibs/arm64-v8a/libpjsua2.so
app/src/main/jniLibs/armeabi-v7a/libpjsua2.so
app/src/main/jniLibs/x86_64/libpjsua2.so
```

`packaging.jniLibs.useLegacyPackaging = false` keeps `libpjsua2.so` uncompressed
for fast load. `App.loadNativeLibraries()` calls `System.loadLibrary("pjsua2")`
at startup.

---

## The stub fallback (CI / compile without native)

When **neither** artifact is present, the build adds `app/src/pjsipStub/java` as
a source set. This stub provides API-compatible, no-op declarations of the
`org.pjsip.pjsua2.*` types the app references (`Endpoint`, `Account`, `Call`,
`AudioMedia`, `AudioMediaPort`, `CallOpParam`, the enums, etc.) so that:

- the project **compiles** without the native toolchain, and
- **JVM unit tests run** (e.g. header-parsing logic in `SipAccount`).

At runtime, with the stub in place, `System.loadLibrary("pjsua2")` fails (there
is no `.so`), which `App` catches and logs; SIP features stay disabled and the
rest of the app (settings, UI, permissions) still works. Drop a real
`pjsua2-release.aar` into `app/libs/` and rebuild to enable SIP. This path is
intended for CI and for developers working on non-SIP code.

---

## Codec configuration

The app enables and prioritises codecs at runtime in `SipStack.applyCodecPriority`
using the default list from `SipConfig.DEFAULT_CODEC_PRIORITY`:

```
G722/16000  ->  PCMA/8000  ->  PCMU/8000  ->  opus/48000
```

For these to be *available* to enable, the PJSIP build must be compiled with the
corresponding codecs **linked in**:

| Codec | pjproject build flag / notes |
|-------|------------------------------|
| **G.722** (wideband) | Built in by default (`PJMEDIA_HAS_G722_CODEC=1`). |
| **G.711** A-law/U-law (PCMA/PCMU) | Built in by default (`PJMEDIA_HAS_G711_CODEC=1`). |
| **Opus** | Requires the Opus library and `PJMEDIA_HAS_OPUS_CODEC=1`; in pjsip-android-builder enable the Opus dependency so libopus is cross-compiled and linked. |

The endpoint runs at a **16 kHz** clock rate (`medConfig.clockRate`) to favour
G.722. Any codec not in the priority list is disabled so the SDP offer stays
clean.
