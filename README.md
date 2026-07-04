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
- The PJSIP `pjsua2` native libraries and Java bindings. ABIs built by default:
  `arm64-v8a`, `armeabi-v7a`, `x86_64`.
- For real audio bridging: a **rooted** device with a Magisk module that disables
  Android's audio-concurrency lock (see [Known limitations](#known-limitations)
  and [docs/MAGISK.md](docs/MAGISK.md)).
- JDK 17, Android Gradle Plugin toolchain (Gradle wrapper included).

---

## Build & setup

### 1. Provide PJSIP (recommended: prebuilt AAR)

The `pjsua2` bindings (`org.pjsip.pjsua2.*`) plus `libpjsua2.so` must be built
with the **opus, g722, and g711** codecs enabled. See
[docs/PJSIP_BUILD.md](docs/PJSIP_BUILD.md) for how to build them with
[pjsip-android-builder](https://github.com/vpandey-om/pjsip-android-builder) (or
the official [pjproject](https://github.com/pjsip/pjproject)).

Drop the result into `app/libs/`:

```
app/libs/pjsua2-release.aar        # preferred: single AAR (bindings + .so)
```

or, alternatively, the raw jar plus native libraries:

```
app/libs/pjsua2.jar
app/src/main/jniLibs/arm64-v8a/libpjsua2.so
app/src/main/jniLibs/armeabi-v7a/libpjsua2.so
app/src/main/jniLibs/x86_64/libpjsua2.so
```

`app/build.gradle.kts` selects the dependency automatically:
`pjsua2-release.aar` if present, else `pjsua2.jar`, else the **stub**.

### 2. Stub fallback (no native libs)

If neither artifact is present, the build compiles against an API-compatible
stub source set (`app/src/pjsipStub/java`) so the project **builds and unit
tests run without the native code**. At runtime `System.loadLibrary("pjsua2")`
fails gracefully (logged in `App.loadNativeLibraries()`) and SIP features stay
disabled until a real AAR is dropped in. This is intended for CI and for editing
non-SIP code. See [docs/PJSIP_BUILD.md](docs/PJSIP_BUILD.md).

### 3. Build & test

```bash
./gradlew assembleDebug     # build the debug APK
./gradlew test              # run JVM unit tests (works with the stub)
```

Install the APK, launch the app, and set it as the default dialer when prompted.

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
