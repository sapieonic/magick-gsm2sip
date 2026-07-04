# Architecture

Developer-facing internals of Magick GSM2SIP. Read the
[README](../README.md) first for the high-level overview and call flows.

---

## Layering (MVVM)

```
ui/           Compose screens + MainViewModel        (View / ViewModel)
gateway/      GatewayController, CallOrchestrator     (application / domain)
sip/  telecom/  audio/                                (SIP, GSM, media adapters)
data/         SipConfig, SettingsRepository, Room     (Model / persistence)
```

- **View** — Compose screens (`StatusScreen`, `SettingsScreen`, `HistoryScreen`,
  `LogScreen`) render state and forward user intents.
- **ViewModel** — `MainViewModel` exposes settings/state and delegates actions to
  `GatewayController`.
- **Domain** — `GatewayController` (process-wide facade) and `CallOrchestrator`
  (the bridging state machine).
- **Adapters** — `sip/` wraps PJSIP, `telecom/` wraps Android Telecom, `audio/`
  wraps `AudioRecord`/`AudioTrack` + PJSIP media ports.
- **Model** — `data/` holds the config value object, DataStore persistence, and
  Room call history.

---

## Package responsibilities

### `sip/`
Thin facade over PJSIP `pjsua2` so nothing else touches PJSIP directly.

- `SipStack` — owns the single process-wide `Endpoint`. Creates the transport
  (UDP/TCP/TLS), applies codec priorities (`applyCodecPriority`: enables only the
  wanted codecs, priority = `255 - index`), starts the library, and registers the
  account. Exposes `makeCall`, `answer`, `ring`, `hangup`, and `audioMedia(callId)`.
  Endpoint config uses a 16 kHz clock rate and a 200 ms echo-canceller tail.
- `SipAccount` — subclasses `pjsua2.Account`. Maps `onRegState` /
  `onIncomingCall` to `SipListener`, keeps a `ConcurrentHashMap<Int, SipCall>`
  registry keyed by PJSIP call id, and **parses the `X-GSM-Forward` header** out
  of the raw INVITE (`parseForwardHeader`, case-insensitive single-line).
  `buildConfig` assembles the `AccountConfig` (registrar URI, digest
  `AuthCredInfo`, STUN/ICE, keep-alive OPTIONS interval).
- `SipCall` — subclasses `pjsua2.Call`. Maps call/media state to `SipCallState`,
  and exposes `confirmedAudioMedia()` (the negotiated `AudioMedia`, or null).
  Deletes itself on disconnect (pjsua2 requirement).
- `SipListener` / `SipEvents` — the callback interface and state enums the stack
  raises to the orchestrator. **All callbacks arrive on a PJSIP worker thread.**

### `telecom/`
Android Telecom integration; active only when the app is the **default dialer**.

- `GsmInCallService` — the default-dialer hook. On `onCallAdded` it publishes a
  `TelecomBridge.GsmEvent.Added(number, incoming)`, registers itself on
  `TelecomBridge.inCallService`, forces the audio route to earpiece, and (if
  `autoAnswerGsm`) auto-answers a ringing call. Emits `Active` / `Ringing` /
  `Removed` events; `hangupCurrent()` lets the orchestrator drop the GSM leg.
- `GsmConnectionService` / `GsmConnection` — a **self-managed** ConnectionService
  representing the bridge session itself (so Android grants audio focus / shows it
  in the call stack). `audioModeIsVoip = true`.
- `GsmDialer` — registers the self-managed `PhoneAccountHandle` and places the
  real cellular call via `TelecomManager.placeCall` (routed through the SIM's
  ConnectionService). Requires `CALL_PHONE`.
- `TelecomBridge` — see below.

### `audio/`
- `AudioBridge` — see [Audio path](#audio-path).
- `GsmAudioPort` — a `pjsua2.AudioMediaPort` with two ring buffers.

### `gateway/`
- `GatewayController` — singleton owning the long-lived collaborators and the
  observable `GatewayState`. See [Listener-cycle break](#listener-cycle-break).
- `CallOrchestrator` — the [state machine](#callorchestrator-state-machine).
- `GatewayService` — `LifecycleService` that hosts the process in the foreground
  (phoneCall|microphone|connectedDevice types), holds wake + Wi-Fi locks, and
  keeps the notification text synced to `GatewayState`. All logic is delegated to
  `GatewayController`.
- `BootReceiver` — restarts the gateway on `BOOT_COMPLETED` when `autoStartOnBoot`.

### `data/`
- `SipConfig` — immutable config value object; `DEFAULT_CODEC_PRIORITY` =
  `G722/16000, PCMA/8000, PCMU/8000, opus/48000`. `isComplete` gates start.
- `VoBizPreset` — one-tap preset defaults (`applyTo` preserves credentials).
- `SettingsRepository` — DataStore persistence of `SipConfig` (password stored
  unencrypted — see README).
- Room `CallHistory` DAO/entities for the History screen.

---

## CallOrchestrator state machine

`CallOrchestrator` implements `SipListener` and subscribes to `TelecomBridge`
GSM events, coordinating **exactly one** `Session` at a time. The session tracks:

```kotlin
data class Session(
    val direction: CallDirection,   // SIP_TO_GSM or GSM_TO_SIP
    var sipCallId: Int,
    var gsmNumber: String,
    var sipRemote: String,
    var recordId: Long,
    var gsmActive: Boolean = false,     // GSM leg reached STATE_ACTIVE
    var sipConfirmed: Boolean = false,  // SIP leg reached CONFIRMED
    var bridged: Boolean = false,
)
```

### SIP → GSM (`onIncomingCall`)
1. Reject if no `X-GSM-Forward` → `488`, or if already busy → `486`.
2. Create `Session(SIP_TO_GSM)`, set state `Calling`, insert a history record.
3. `sipStack.ring(callId)` (180) and `gsmDialer.placeGsmCall(forwardNumber)`.
   Dial failure → `failSession` (hang up SIP with `503`, teardown).
4. When the GSM leg goes `Active`, set `gsmActive = true` and, since the GSM
   party answered, `sipStack.answer(sipCallId)` (200 OK).
5. SIP reaches `CONFIRMED` → `sipConfirmed = true`.
6. Any transition into the both-legs-up condition calls `maybeBridge()`.

### GSM → SIP (`onGsmEvent` → `Added(incoming)` while idle)
1. `startGsmToSip(number)` originates the SIP leg via
   `sipStack.makeCall(sip:number@domain)`; create `Session(GSM_TO_SIP)`.
2. GSM `Active` → `gsmActive = true`; SIP `CONFIRMED` → `sipConfirmed = true`.
3. `maybeBridge()`.

### The bridge gate: `maybeBridge()`
Bridging is gated on **both** legs being up **and** SIP media being ready:

```kotlin
if (s.bridged || !s.gsmActive || !s.sipConfirmed) return
val media = sipStack.audioMedia(s.sipCallId) ?: return   // defer until media ready
val bridged = audioBridge.start(media)
s.bridged = bridged
setState(GatewayState.InCall(s.sipRemote, audioBridged = bridged))
```

`audioBridge.start()` returns `false` on non-rooted devices (concurrency locked),
so the state becomes `InCall(..., audioBridged = false)` → UI shows
*"In call (no audio)"*. `maybeBridge()` is idempotent (guards on `s.bridged`) and
is called from `onCallState(CONFIRMED)`, `onCallMediaReady`, and GSM `Active`,
whichever completes the pair last.

### Teardown
- `onCallState(DISCONNECTED)` or GSM `Removed` → `teardown(reason)`: clears the
  session, stops the `AudioBridge`, best-effort hangs up both legs
  (`sipStack.hangup` + `TelecomBridge.inCallService?.hangupCurrent()`), marks the
  history record ended, and returns to `Registered` (or `Stopped`).
- `reset()` (on gateway stop) tears down any in-flight session.

---

## TelecomBridge rendezvous

`GsmInCallService` is instantiated by the Android Telecom framework, so it can't
be constructed with the gateway's dependencies. `TelecomBridge` is the singleton
that bridges the two:

- The InCallService **publishes** `GsmEvent`s to a `MutableSharedFlow`
  (`Added` / `Ringing` / `Active` / `Removed`) and registers itself on
  `TelecomBridge.inCallService`.
- `CallOrchestrator.observeGsmEvents()` **collects** that flow on its coroutine
  scope.
- The orchestrator drives the GSM leg back through
  `TelecomBridge.inCallService?.hangupCurrent()`.
- `hasActiveGsmCall` / `currentGsmCall` expose GSM presence to the UI.

Kept a singleton because there is exactly one active InCallService per process.

---

## Listener-cycle break

`SipStack` needs a `SipListener` at construction, but the `CallOrchestrator`
(which *implements* `SipListener`) needs the `SipStack`. `GatewayController`
breaks this chicken-and-egg cycle with a **forwarding holder**: it passes
`SipStack` an anonymous `SipListener` (`orchestratorHolder()`) whose methods
forward to a lazily-assigned `@Volatile pendingOrchestrator`. The real
orchestrator is constructed with the stack, then assigned to
`pendingOrchestrator`, after which the holder resolves to it. This keeps both
objects final/immutable in their own right without a constructor cycle.

---

## Audio path

`AudioBridge` connects the confirmed SIP `AudioMedia` to the GSM voice call
through `GsmAudioPort`, a `pjsua2.AudioMediaPort`:

```
GSM downlink  --AudioRecord(VOICE_CALL)--> pushFromGsm() --> [fromGsm queue]
                                                                   |
                                        onFrameRequested() <-------+   (SIP wants TX)
                                                |
                                          SIP transmit  (p.startTransmit(sipMedia))

SIP receive   --onFrameReceived()--> [toGsm queue] --> pollToGsm()
                                                            |
                                              AudioTrack(uplink) --> GSM uplink
```

Key details:

- **Format:** 16 kHz, mono, 16-bit little-endian PCM, **20 ms frames**
  (`SAMPLES_PER_FRAME = 320`, `FRAME_BYTES = 640`). `GsmAudioPort.format()`
  reports PJMEDIA PCM with the matching `frameTimeUsec`.
- **Cross-connect:** `sipMedia.startTransmit(port)` and
  `port.startTransmit(sipMedia)` wire both directions inside PJSIP.
- **Ring buffers:** two `ArrayBlockingQueue<ByteArray>(10)` (~200 ms jitter each
  way). On overflow the **oldest frame is dropped** rather than blocking PJSIP,
  keeping latency bounded. `onFrameRequested` returns silence when `fromGsm` is
  empty.
- **Pumps:** two daemon threads — `gsm-capture` (`AudioRecord.read` →
  `pushFromGsm`) and `gsm-play` (`pollToGsm` → `AudioTrack.write`).
- **Echo cancellation:** `AcousticEchoCanceler` is attached to the record
  session when available; PJSIP also has its own 200 ms EC tail configured.
- **Audio mode:** set to `MODE_IN_COMMUNICATION` while bridging, restored to
  `MODE_NORMAL` on stop.
- **Support probe:** `isSupported()` opens a `VOICE_CALL` `AudioRecord` and checks
  `STATE_INITIALIZED`; `start()` returns `false` (signalling-only) when that
  fails, which is the non-rooted / audio-concurrency-locked case (see
  [docs/MAGISK.md](MAGISK.md)).
