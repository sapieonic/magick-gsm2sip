# Magick GSM2SIP

A Kotlin/Android GSM-to-SIP gateway that registers to a **VoBiz** SIP trunk over
[PJSIP / pjsua2](https://www.pjsip.org/) and bridges audio between a cellular
(GSM) call and a SIP call on the same device.

It works in both directions:

- **Outbound (SIP → GSM):** the trunk sends a SIP `INVITE` carrying an
  `X-GSM-Forward` header with a phone number. The gateway answers the SIP leg,
  places a matching call on the cellular radio, and bridges the two.
- **Inbound (GSM → SIP):** an incoming cellular call is auto-answered and a
  matching SIP call is originated to the trunk, then the two legs are bridged.

Media negotiates **G.722** (wideband) by preference, falling back to **G.711**
A-law / U-law, with Opus available. Signalling and call control run on stock
Android; **full bidirectional audio bridging requires root** (see
[Known limitations](#known-limitations)).

---

## Features

- SIP registration to a VoBiz trunk (UDP / TCP / TLS) via PJSIP `pjsua2`.
- Bidirectional call bridging:
  - SIP → GSM triggered by the `X-GSM-Forward` INVITE header.
  - GSM → SIP triggered by an inbound cellular call.
- Codec priority ordering: G.722 → G.711 A-law → G.711 U-law → Opus.
- One-tap **"Apply VoBiz preset"** button that fills in the documented trunk
  defaults while preserving your credentials.
- Default-dialer integration (Telecom `InCallService` / self-managed
  `ConnectionService`) for observing, auto-answering, and controlling GSM calls.
- Foreground service with wake + Wi-Fi locks so registration and RTP survive the
  screen turning off; optional auto-start on boot.
- STUN with fallback, RFC 2833 (telephone-event) DTMF, keep-alive OPTIONS.
- Acoustic echo cancellation on the bridged audio path where the device supports it.
- Jetpack Compose UI (Status / Settings / History / Log screens) built on MVVM.
- Call history persisted in Room; settings persisted in DataStore.
- Runtime detection of audio-bridging support with the result surfaced in the UI.
- **Stub build path** so the project compiles and unit tests run without the
  native PJSIP libraries present (useful for CI).

---

## Architecture

The app follows an **MVVM** structure. Feature logic lives in packages under
`com.magick.gsm2sip`:

| Package     | Responsibility |
|-------------|----------------|
| `sip/`      | PJSIP `pjsua2` facade: `SipStack`, `SipAccount`, `SipCall`, plus `SipListener` events. Owns the endpoint, transport, codec priorities, registration, and parses the `X-GSM-Forward` header. |
| `telecom/`  | Android Telecom integration: `GsmInCallService` (default-dialer hook), `GsmConnectionService` (self-managed connection), `GsmDialer` (places cellular calls), and the `TelecomBridge` singleton rendezvous. |
| `audio/`    | `AudioBridge` and `GsmAudioPort` — the PCM bridge between the SIP media and `AudioRecord`/`AudioTrack`, with echo cancellation. |
| `gateway/`  | Orchestration: `CallOrchestrator` (call-bridging state machine), `GatewayController` (process-wide facade + state), `GatewayService` (foreground service), `BootReceiver`. |
| `data/`     | `SipConfig`, `VoBizPreset`, `SettingsRepository` (DataStore), and Room call history. |
| `ui/`       | Compose screens + `MainViewModel`. |

### Call flows

```
Outbound  (SIP -> GSM)
──────────────────────
  VoBiz trunk ── INVITE (X-GSM-Forward: <number>) ──► SipStack/SipAccount
                                                          │ onIncomingCall(number)
                                                          ▼
                                                   CallOrchestrator
                                            (Session: SIP_TO_GSM)
                                              │ ring SIP  │ dial GSM
                             ┌────────────────┘           └───────────────┐
                             ▼                                             ▼
                       SipStack.ring()                          GsmDialer.placeGsmCall()
                                                                          │
                                                          (system) GsmInCallService
                                                                          │ Active
                             ▼  SIP answered when GSM active              ▼
                       sipConfirmed ──────────► maybeBridge() ◄────── gsmActive
                                                     │
                                                     ▼
                                                 AudioBridge  (G.722/G.711 PCM)

Inbound  (GSM -> SIP)
─────────────────────
  Cellular call ──► GsmInCallService.onCallAdded(incoming)
                          │ TelecomBridge.GsmEvent.Added
                          ▼
                    CallOrchestrator.startGsmToSip(number)
                          │ SipStack.makeCall(sip:number@domain)
                          ▼
              (Session: GSM_TO_SIP)  gsmActive + sipConfirmed ─► maybeBridge() ─► AudioBridge
```

`CallOrchestrator` handles **exactly one** bridged session at a time (a
single-SIM wholesale gateway bridges one call); concurrent INVITEs are declined
with `486 Busy Here`, and an INVITE with no `X-GSM-Forward` header is rejected
with `488 Not Acceptable Here`.

`GatewayController` is the singleton that owns the long-lived collaborators
(`SipStack`, `CallOrchestrator`, `AudioBridge`) and exposes a single observable
`GatewayState`. `GatewayService` merely keeps the process alive in the foreground.

For internals see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Requirements

- Android **12 – 14** (API **31 – 34**; `minSdk = 31`, `targetSdk = 34`).
- The app **must be set as the default phone/dialer app** to observe and control
  GSM call state (`GsmInCallService` binds only when the app holds the dialer role).
- The PJSIP `pjsua2` native libraries and Java bindings — **bundled in this repo**
  (jar in git, `.so` via Git LFS). ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- For real audio bridging: a **rooted** device with a Magisk module that disables
  Android's audio-concurrency lock (see [Known limitations](#known-limitations)
  and [docs/MAGISK.md](docs/MAGISK.md)).
- JDK 17, Android Gradle Plugin toolchain (Gradle wrapper included).

---

## Build & setup

### 1. Clone (Git LFS required)

The prebuilt PJSIP native libraries (`libpjsua2.so`, ~96 MB across three ABIs)
are committed via **[Git LFS](https://git-lfs.com/)**. Install it *before*
cloning, otherwise the `.so` files arrive as small text pointer stubs and PJSIP
fails to load at runtime.

```bash
brew install git-lfs      # macOS  (Linux: apt-get install git-lfs)
git lfs install           # one-time per machine
git clone git@github.com:sapieonic/magick-gsm2sip.git
```

Already cloned before installing LFS? Run `git lfs install && git lfs pull`.
Verify you have real binaries, not pointers:

```bash
git lfs ls-files                                   # lists the 6 .so files
file app/src/main/jniLibs/arm64-v8a/libpjsua2.so   # -> "ELF ... shared object"
```

### 2. What's bundled

PJSIP is checked in, so the project builds with SIP enabled out of the box — no
manual PJSIP build needed:

```
app/libs/pjsua2.jar                            # pjsua2 Java bindings (regular git)
app/src/main/jniLibs/<abi>/libpjsua2.so        # native PJSIP (Git LFS)
app/src/main/jniLibs/<abi>/libc++_shared.so    # NDK C++ runtime (Git LFS)
```

ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`. Codecs compiled in: **G.711, G.722,
Opus**, plus **OpenSSL** for SIP/TLS. `app/build.gradle.kts` auto-selects the
real bindings when `app/libs/pjsua2.jar` (or a `pjsua2-release.aar`) is present,
otherwise it falls back to the stub (below).

### 3. Build & test

```bash
./gradlew assembleDebug     # build the debug APK (with real PJSIP)
./gradlew installDebug      # build + install onto a connected device/emulator
./gradlew test              # run JVM unit tests
```

Install the APK, launch the app, and set it as the default dialer when prompted.
Confirm PJSIP loaded: `adb logcat | grep libpjsua2` → `loaded libpjsua2.so`.

### 4. Stub fallback (building without the native libs)

If the jar/AAR is absent (e.g. CI without LFS, or when editing non-SIP code) the
build compiles against an API-compatible stub source set
(`app/src/pjsipStub/java`) so the project **builds and unit tests run without the
native code**. At runtime `System.loadLibrary("pjsua2")` fails gracefully
(logged in `App.loadNativeLibraries()`) and SIP stays disabled. The stub mirrors
real pjsua2's **int-constant enum** API.

### 5. Rebuilding PJSIP (only if you need to)

You only need this to change codecs, ABIs, or the PJSIP version — the prebuilt
artifacts above cover normal development. Full, reproducible instructions
(Docker + `pjsip-android-builder`, compiling the jar, copying the `.so` into
`jniLibs`, re-committing via LFS) are in
[docs/PJSIP_BUILD.md](docs/PJSIP_BUILD.md).

---

## VoBiz configuration

Open the **Settings** screen and either fill fields manually or tap
**"Apply VoBiz preset"**, which fills in `sip.vobiz.com`, STUN, 300 s
registration expiry, 60 s keep-alive, and the G.722 → G.711 codec order while
preserving your username/password. Enter your VoBiz digest credentials and tap
**Save**, then start the gateway from the Status screen.

Full field-by-field guidance, including the exact `X-GSM-Forward` header VoBiz
must send, is in [docs/VOBIZ_SETUP.md](docs/VOBIZ_SETUP.md).

---

## Runtime permissions

| Permission | Why it is needed |
|------------|------------------|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` | SIP signalling + RTP over IP; track/keep the network up. |
| `WAKE_LOCK` | Keep the CPU awake so registration and media survive screen-off. |
| `CALL_PHONE` | Place the outbound cellular leg (`GsmDialer.placeGsmCall`). |
| `ANSWER_PHONE_CALLS` | Auto-answer inbound GSM calls. |
| `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `READ_PRECISE_PHONE_STATE` | Observe call state and read the caller number for GSM → SIP. |
| `MANAGE_OWN_CALLS` | Register/use the self-managed `ConnectionService`. |
| `MODIFY_PHONE_STATE` | **Signature/privileged** — deeper call-state control; only granted to platform-signed/rooted builds. |
| `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | Capture/route audio for the bridge. |
| `CAPTURE_AUDIO_OUTPUT` | **Signature/privileged** — tap the GSM downlink stream; works only on rooted/platform-signed devices. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Run the gateway as a foreground service of the correct types. |
| `POST_NOTIFICATIONS` | Show the ongoing gateway-status notification. |
| `RECEIVE_BOOT_COMPLETED` | Auto-start the gateway on boot when enabled. |

The app must also hold the **default dialer role** (requested at runtime), which
is what routes GSM calls into `GsmInCallService`.

---

## Known limitations

### Audio bridging requires root

Full bidirectional audio bridging captures the GSM **voice** stream
(`VOICE_CALL` / `VOICE_DOWNLINK`) and injects into the uplink at the same time as
running VoIP media. Stock Android **blocks** this via read-only audio-concurrency
system properties. To bridge audio these must be set **false**:

```
voice.voip.conc.disabled=false
voice.record.conc.disabled=false
voice.playback.conc.disabled=false
```

These are read-only props, so setting them needs **root + a Magisk module**
(a `resetprop`-based module). See [docs/MAGISK.md](docs/MAGISK.md).

**On a non-rooted device the gateway still works for signalling:** SIP
registration, receiving INVITEs, placing/answering the GSM leg, and call
teardown all function — but **audio is NOT bridged**. The app detects this at
runtime via `AudioBridge.isSupported()` (it probes a `VOICE_CALL` `AudioRecord`)
and surfaces it: the in-call state shows *"In call (no audio)"* rather than
*"In call (bridged)"*.

### Privileged permissions

`MODIFY_PHONE_STATE` and `CAPTURE_AUDIO_OUTPUT` are **signature/privileged**
permissions. They are declared in the manifest but are only granted to
platform-signed or rooted builds; a normally-installed APK will not receive them.

### Credential storage

The SIP password is stored in **Jetpack DataStore preferences**, which live in
the app's private storage but are **not hardware-encrypted**. For production,
wrap the password with EncryptedSharedPreferences or the Android Keystore. Left
as plain DataStore here to keep the sample self-contained.

See [docs/MAGISK.md](docs/MAGISK.md) for root setup and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for internals.

---

## Disclaimer / legal

Bridging and/or recording telephone calls is regulated in many jurisdictions and
may require the consent of all parties. This project is provided for
**authorized testing, lab, and educational use only**. You are responsible for
operating it lawfully, including obtaining any required consents and complying
with your carrier's and SIP provider's terms of service. The rooting and Magisk
steps described here modify device internals at your own risk.
