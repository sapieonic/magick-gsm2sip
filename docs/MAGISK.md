# Root / Magisk requirements for audio bridging

> **This is external to the app.** Magick GSM2SIP does **not** ship or install a
> Magisk module and cannot change these properties itself. This document explains
> what has to be done at the device/ROM level so that audio can actually flow
> across the GSM ↔ SIP bridge. Signalling and call control work without any of
> this.

---

## Why audio bridging is blocked on stock Android

To bridge audio, the app must do two things at the same time a cellular call is
up:

1. **Capture** the GSM voice stream (`MediaRecorder.AudioSource.VOICE_CALL` /
   `VOICE_DOWNLINK`) — the far party's voice coming down the radio.
2. **Play** the SIP party's audio into the GSM **uplink**, while also running its
   own VoIP capture/playback for the SIP leg.

Modern Android audio HALs enforce an **audio-concurrency policy**: while a
telephony (voice) call is active, concurrent VoIP capture/playback and voice-call
recording are disallowed. This policy is gated by three **read-only** system
properties, which on most devices default to `true` (concurrency *disabled*):

```
voice.voip.conc.disabled
voice.record.conc.disabled
voice.playback.conc.disabled
```

While these are `true`, `AudioRecord(VOICE_CALL, ...)` either fails to initialise
or returns silence, and injecting into the uplink is refused. The app's
`AudioBridge.isSupported()` probes exactly this by trying to open a `VOICE_CALL`
`AudioRecord`; if it can't, the gateway runs signalling-only and the UI shows
*"In call (no audio)"*.

---

## What must change

All three properties must be set to **`false`** (concurrency *enabled*):

```
voice.voip.conc.disabled=false
voice.record.conc.disabled=false
voice.playback.conc.disabled=false
```

Because they are **read-only** (`ro.`-style, fixed at boot from the ROM's
`*.prop` files), a normal `setprop` will not take. You need **root** and a tool
that can override read-only props at boot — this is what Magisk's `resetprop`
provides.

---

## Using a Magisk module

A `resetprop`-based Magisk module rewrites these props early in boot, before the
audio HAL reads them. Conceptually such a module ships a boot script:

```sh
#!/system/bin/sh
# post-fs-data.sh (runs early, before most services start)
resetprop voice.voip.conc.disabled false
resetprop voice.record.conc.disabled false
resetprop voice.playback.conc.disabled false
```

`resetprop` (a Magisk built-in) can set values on otherwise read-only properties
by writing directly to the property area, so the audio HAL sees `false` when it
initialises.

You can use the concurrency-unlock module distributed with the upstream
[gsm2sip](https://github.com/) project or **any equivalent `resetprop` module**
that sets the three props above. To build your own, drop the script into a
minimal Magisk module skeleton (`module.prop` + `post-fs-data.sh`), zip it, and
flash it in the Magisk app, then reboot.

> Some ROMs read these props even earlier; if `post-fs-data.sh` is too late, a
> `system.prop` entry in the module (which Magisk applies during early boot) may
> be required instead. This is device/ROM-specific.

---

## Verifying

After flashing the module and rebooting, confirm the values with `getprop`:

```sh
adb shell getprop voice.voip.conc.disabled
adb shell getprop voice.record.conc.disabled
adb shell getprop voice.playback.conc.disabled
```

All three should print `false`. Then place/receive a bridged call: the app should
report *"In call (bridged)"* and `AudioBridge.isSupported()` returns `true`.

---

## Caveats

- **Device/ROM-dependent.** Exact property names, defaults, and the boot stage at
  which they're read vary between SoC vendors (Qualcomm, MediaTek, etc.) and ROMs.
  Some devices route voice audio entirely inside the modem/DSP where these props
  have no effect, in which case no amount of prop editing will expose the stream.
- **Unsupported on locked bootloaders.** Rooting requires an unlockable
  bootloader; devices with locked bootloaders (or where unlocking is blocked by
  the carrier/OEM) cannot run Magisk and therefore cannot bridge audio.
- **Signature/privileged permissions still apply.** `CAPTURE_AUDIO_OUTPUT` and
  `MODIFY_PHONE_STATE` are only granted to platform-signed or rooted builds; see
  the README's Known limitations.
- **At your own risk.** Rooting and modifying device internals can void
  warranties, break OTA updates, and reduce device security.
