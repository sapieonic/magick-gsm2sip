# Building / obtaining PJSIP (pjsua2) for Android

Magick GSM2SIP talks to PJSIP through the `pjsua2` Java bindings
(`org.pjsip.pjsua2.*`) backed by the native `libpjsua2.so`.

**These are now committed to the repo**, so a normal clone builds with SIP
enabled — you do **not** need to build PJSIP yourself for day-to-day work:

```
app/libs/pjsua2.jar                            # pjsua2 Java bindings (regular git, ~420 KB)
app/src/main/jniLibs/<abi>/libpjsua2.so        # native PJSIP  (Git LFS)
app/src/main/jniLibs/<abi>/libc++_shared.so    # NDK C++ runtime (Git LFS)
```

ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`. Built from **PJSIP 2.16** with
**G.711, G.722, Opus** codecs and **OpenSSL** (for SIP/TLS).

> The native `.so` files are stored in **Git LFS**. Install it before cloning
> (`brew install git-lfs && git lfs install`) or they arrive as pointer stubs and
> PJSIP won't load. Already cloned? `git lfs install && git lfs pull`.

The rest of this guide is only needed if you want to **rebuild** PJSIP — e.g. to
change codecs, ABIs, or the PJSIP version.

---

## Rebuilding PJSIP

### 1. Build with pjsip-android-builder (Docker)

[VoiSmart/pjsip-android-builder](https://github.com/VoiSmart/pjsip-android-builder)
cross-compiles PJSIP + `pjsua2` for Android and generates the SWIG Java bindings.
It downloads the NDK/SDK inside the container, so Docker is the only host
prerequisite.

```bash
git clone https://github.com/VoiSmart/pjsip-android-builder.git
cd pjsip-android-builder
```

Edit `config.conf` to match this app (defaults build extra ABIs/codecs we don't
ship):

```conf
TARGET_ARCHS=("armeabi-v7a" "arm64-v8a" "x86_64")   # drop x86
PJSIP_VERSION=2.16
ENABLE_OPUS=1                                        # Opus (G.711/G.722 are built in)
ENABLE_OPENSSL=1                                     # SIP/TLS
DOWNLOAD_OPENH264=0 ; ENABLE_OPENH264=0             # video: not used
DOWNLOAD_BCG729=0   ; ENABLE_BCG729=0              # G.729: not in our codec list
```

Run the build. The builder pins the **x86_64 Linux NDK**, so on Apple Silicon
(or any arm64 host) force the amd64 platform or the NDK's compilers won't run:

```bash
docker run --platform linux/amd64 --name pjsip-builder \
  -v "$PWD":/home ubuntu:22.04 \
  bash -lc "cd /home && ./prepare-build-system && ./build"
```

Output lands in `output/pjsip-build-output/`:

- `java/org/pjsip/...` — the SWIG-generated `pjsua2` Java sources
- `lib/<abi>/libpjsua2.so` and `lib/<abi>/libc++_shared.so`

### 2. Package the artifacts into the app

**Compile the Java bindings into `pjsua2.jar`** (the camera sources reference the
Android SDK, so put `android.jar` on the classpath):

```bash
SDK="$HOME/Library/Android/sdk"                       # your Android SDK
JHOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # any JDK 17+
OUT=output/pjsip-build-output
mkdir -p classes
find "$OUT/java" -name '*.java' > sources.txt
"$JHOME/bin/javac" --release 11 -nowarn \
  -classpath "$SDK/platforms/android-34/android.jar" -d classes @sources.txt
"$JHOME/bin/jar" --create --file /path/to/magick-gsm2sip/app/libs/pjsua2.jar -C classes .
```

**Copy the native libs** into `jniLibs` (both `libpjsua2.so` and its
`libc++_shared.so` dependency, per ABI):

```bash
APP=/path/to/magick-gsm2sip/app
for abi in armeabi-v7a arm64-v8a x86_64; do
  mkdir -p "$APP/src/main/jniLibs/$abi"
  cp "$OUT/lib/$abi/libpjsua2.so"    "$APP/src/main/jniLibs/$abi/"
  cp "$OUT/lib/$abi/libc++_shared.so" "$APP/src/main/jniLibs/$abi/"
done
```

### 3. Commit (native libs via Git LFS)

The `.gitattributes` already routes `app/src/main/jniLibs/**/*.so` through LFS, so
a normal `git add` stores them as LFS objects:

```bash
git add app/libs/pjsua2.jar app/src/main/jniLibs
git lfs status        # confirm the .so show as "LFS"
git commit -m "chore: rebuild PJSIP <version>"
```

### 4. Rebuild & verify

```bash
./gradlew clean installDebug
adb logcat | grep libpjsua2      # -> "loaded libpjsua2.so"
```

---

## Where `app/build.gradle.kts` looks

The build selects the PJSIP source in this priority order:

```kotlin
when {
    file("libs/pjsua2-release.aar").exists() -> implementation(files("libs/pjsua2-release.aar"))
    file("libs/pjsua2.jar").exists()         -> implementation(files("libs/pjsua2.jar"))
    else -> /* stub */ sourceSets.getByName("main").java.srcDir("src/pjsipStub/java")
}
```

An AAR (bindings + `.so` bundled) also works if you drop it at
`app/libs/pjsua2-release.aar`; it takes precedence over the jar.

---

## The stub, unit tests, and the enum API

`app/src/pjsipStub/java` is an **API-compatible, no-op** implementation of the
subset of `org.pjsip.pjsua2.*` the app uses. Two roles:

1. **No native libs present** (`neither aar nor jar`): the stub is added to the
   `main` source set so the project still **compiles**. At runtime
   `System.loadLibrary("pjsua2")` fails, `App.loadNativeLibraries()` logs it, and
   SIP stays disabled while the rest of the app works. Intended for CI / editing
   non-SIP code.
2. **Native libs present**: the `pjsua2.jar` classes require `libpjsua2.so`, which
   **cannot load in plain JVM / Robolectric unit tests**. So `build.gradle.kts`
   adds the stub to the `test` source set and filters the jar off the unit-test
   classpath — unit tests always run against the native-free stub. For this to
   work the stub is **binary-compatible** with the real SWIG classes it shadows
   (`MediaFrame`, `MediaFormatAudio`, `ByteVector`): same getter/setter shape,
   not public fields.

> **Enum shape:** real pjsua2 (SWIG) exposes C enums as **plain `int` constants**
> (e.g. `pjsip_status_code.PJSIP_SC_OK == 200`), not Java `enum`s — there is no
> `.swigValue()` / `.swigToEnum()`. The app code and the stub both follow this.

---

## Codec configuration

The app enables and prioritises codecs at runtime in `SipStack.applyCodecPriority`
from `SipConfig.DEFAULT_CODEC_PRIORITY`:

```
G722/16000  ->  PCMA/8000  ->  PCMU/8000  ->  opus/48000
```

For these to be *available*, PJSIP must be compiled with them linked in:

| Codec | pjproject build flag / notes |
|-------|------------------------------|
| **G.722** (wideband) | Built in by default (`PJMEDIA_HAS_G722_CODEC=1`). |
| **G.711** A-law/U-law (PCMA/PCMU) | Built in by default (`PJMEDIA_HAS_G711_CODEC=1`). |
| **Opus** | Needs libopus + `PJMEDIA_HAS_OPUS_CODEC=1`; set `ENABLE_OPUS=1` in the builder's `config.conf`. |

The endpoint runs at a **16 kHz** clock rate (`medConfig.clockRate`) to favour
G.722. Any codec not in the priority list is disabled so the SDP offer stays clean.
