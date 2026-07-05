# Playbook: Rooting an Android phone for audio bridging

> **Why this exists.** Magick GSM2SIP does signalling and call control on stock
> Android, but **full bidirectional audio bridging requires root** so the
> audio-concurrency system properties can be flipped (see
> [`docs/MAGISK.md`](../MAGISK.md)). This playbook is the end-to-end procedure to
> get a device rooted with **Magisk** so you can flash that concurrency-unlock
> module.
>
> **This is device-level surgery, external to the app.** The app neither performs
> nor requires these steps to build; it only *benefits* from the result.

---

## ⚠️ Read this before you start

- **You can permanently break ("brick") the device.** A wrong image or an
  interrupted flash can leave a phone that won't boot. Do not do this on a phone
  you can't afford to lose.
- **Unlocking the bootloader wipes all user data.** Back up everything first.
- **It voids warranties, breaks OTA updates, and lowers device security.**
  Banking/DRM apps using Play Integrity / SafetyNet may stop working.
- **Not every phone can be rooted.** Devices with locked or carrier-locked
  bootloaders (many US carrier variants, most modern Samsung US models) **cannot**
  run Magisk and therefore **cannot** bridge audio. See
  [Bootloader unlockability](#step-1-check-bootloader-unlockability).
- **At your own risk.** Nothing here is guaranteed for your specific
  model/ROM/SoC. When in doubt, stop and consult the XDA thread for your exact
  device.

Use a **dedicated test handset** with an **unlockable bootloader** (Google
Pixel and many Xiaomi/Motorola devices are the friendliest). This is strongly
recommended for a GSM2SIP gateway anyway.

---

## What "rooting with Magisk" actually is

Rooting here means installing **Magisk**, a systemless root solution. The flow is
always the same three moves:

1. **Unlock the bootloader** so the device will accept unsigned/modified boot
   images.
2. **Patch the boot image** (`boot.img` / `init_boot.img`) with Magisk so it
   injects `su` and the module system at boot.
3. **Flash the patched image** and boot into a rooted system.

Then you install modules — for this project, the `resetprop` concurrency-unlock
module from [`docs/MAGISK.md`](../MAGISK.md).

---

## Prerequisites

| Item | Notes |
|------|-------|
| A computer | Linux, macOS, or Windows. |
| USB cable | A known-good data cable (many cables are charge-only). |
| `adb` + `fastboot` | Android Platform Tools — see below. |
| The **Magisk APK** | Latest release from the official [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk) GitHub Releases. |
| Your device's **stock firmware** | To extract the exact `boot.img`/`init_boot.img` for your build number. |
| Full backup | Unlocking wipes the device. |
| ~1 hour and patience | Don't rush a flash. |

### Install Platform Tools (adb / fastboot)

Always use Google's official
[SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools);
distro packages are often outdated.

```sh
# Linux (Debian/Ubuntu) — quick path
sudo apt-get install android-tools-adb android-tools-fastboot
# macOS
brew install --cask android-platform-tools
# Or: download the official zip and add it to PATH (recommended, always current)
```

Verify:

```sh
adb version
fastboot --version
```

### Enable Developer Options + USB debugging (on the phone)

1. **Settings → About phone → Build number** — tap 7 times to unlock *Developer
   options*.
2. **Settings → System → Developer options**:
   - Enable **USB debugging**.
   - Enable **OEM unlocking** (if greyed out, the device/carrier blocks
     unlocking — stop, this device likely can't be rooted).
3. Plug in via USB, run `adb devices`, and **accept the RSA prompt** on the
   phone. You should see your serial listed as `device`.

---

## Step 1: Check bootloader unlockability

This is the make-or-break gate.

- If **OEM unlocking** in Developer options is greyed out / says "not allowed by
  carrier," the bootloader is locked and cannot be opened → **this device cannot
  be rooted or bridge audio.** Pick a different handset.
- Some OEMs require an extra step to obtain an unlock:
  - **Xiaomi/Redmi/POCO:** request an unlock token via Mi Unlock (mandatory wait
    period of days to a week).
  - **Motorola / older HTC / Sony:** OEM-issued unlock code from the vendor's
    site.
  - **Google Pixel:** no code needed; `fastboot flashing unlock` just works when
    OEM unlocking is on.

If unlocking is possible, continue.

---

## Step 2: Unlock the bootloader (⚠️ wipes the device)

Reboot into the bootloader and unlock:

```sh
adb reboot bootloader        # or hold Power + Volume Down from off
fastboot devices             # confirm the device is seen in fastboot mode

# Newer devices (Pixel 3+ and most 2019+ phones):
fastboot flashing unlock

# Older devices sometimes use:
fastboot oem unlock
```

On the phone, use the volume keys to select **Unlock the bootloader** and confirm
with Power. **This factory-resets the device.** Let it reboot, then redo the
Developer-options / USB-debugging setup from the prerequisites.

> Some OEMs (Xiaomi) require their own Mi Unlock tool over fastboot instead of the
> raw command — follow the vendor flow, then return here.

---

## Step 3: Get the correct boot image

Magisk patches the **boot partition** (`boot.img`). On devices launched with
Android 13+ that use a separate ramdisk, it's **`init_boot.img`** instead. You
must use the image that matches your **exact** installed build number.

Get it one of these ways:

- **Download the full stock firmware / factory image** for your exact model and
  build (e.g. Google's Pixel factory images, or your OEM's firmware site), unzip
  it, and extract `boot.img` (or `init_boot.img`).
- **Vendors that ship payload.bin** (OTA zip): extract with a `payload-dumper`
  tool to obtain `boot.img` / `init_boot.img`.

> Using the wrong image (wrong build, wrong slot, wrong partition) is the most
> common way to soft-brick. Match the build number in **Settings → About phone**
> exactly.

Copy the image to the phone:

```sh
adb push boot.img /sdcard/Download/
# (or init_boot.img)
```

---

## Step 4: Patch the boot image with Magisk

1. Install the **Magisk APK** on the phone:
   ```sh
   adb install Magisk-vXX.X.apk
   ```
2. Open **Magisk** → **Install** → **Select and Patch a File** → pick the
   `boot.img` / `init_boot.img` you pushed.
3. Magisk writes a patched image to `Download/`, named like
   `magisk_patched-XXXXX.img`.
4. Pull it back to your computer:
   ```sh
   adb pull /sdcard/Download/magisk_patched-XXXXX.img .
   ```

---

## Step 5: Flash the patched image

Reboot to the bootloader and flash the patched image to the correct partition:

```sh
adb reboot bootloader
fastboot devices

# If Magisk patched boot.img:
fastboot flash boot magisk_patched-XXXXX.img

# If Magisk patched init_boot.img (Android 13+ launch devices):
fastboot flash init_boot magisk_patched-XXXXX.img

fastboot reboot
```

> **Prefer test-then-flash on Pixels:** you can `fastboot boot
> magisk_patched-XXXXX.img` first (boots the patched image *once* without writing
> it). If the device boots fine and Magisk shows as installed, then flash it
> permanently. If it hangs, just reboot and you're back to stock.

### If it doesn't boot

- Re-flash the **stock** `boot.img`/`init_boot.img` to recover:
  `fastboot flash boot boot.img` (or `init_boot`), then `fastboot reboot`.
- Double-check you used the image from your **exact** build and the **right
  partition**.
- Consult the XDA thread for your specific model.

---

## Step 6: Verify root

After the phone boots:

1. Open the **Magisk** app — it should report **Installed: vXX.X**.
2. Check `su` over adb:
   ```sh
   adb shell su -c id
   # expect: uid=0(root) gid=0(root) ...
   ```
   Grant the root prompt on the phone if it appears.

Root is now active.

---

## Step 7: Apply the audio-concurrency unlock (project-specific)

This is the whole point for GSM2SIP. Follow [`docs/MAGISK.md`](../MAGISK.md) to
install a **`resetprop`-based Magisk module** that sets, at early boot:

```
voice.voip.conc.disabled=false
voice.record.conc.disabled=false
voice.playback.conc.disabled=false
```

Quick version of the module (see `docs/MAGISK.md` for the full skeleton and
caveats):

```sh
#!/system/bin/sh
# post-fs-data.sh — runs early, before the audio HAL initialises
resetprop voice.voip.conc.disabled false
resetprop voice.record.conc.disabled false
resetprop voice.playback.conc.disabled false
```

Zip it as a minimal Magisk module (`module.prop` + `post-fs-data.sh`), flash it in
**Magisk → Modules → Install from storage**, and reboot.

### Verify the unlock

```sh
adb shell getprop voice.voip.conc.disabled
adb shell getprop voice.record.conc.disabled
adb shell getprop voice.playback.conc.disabled
```

All three should print `false`. Then place/receive a bridged call: the app should
report **"In call (bridged)"** and `AudioBridge.isSupported()` returns `true`.

> **Device/ROM caveat:** on some SoCs voice audio is routed entirely inside the
> modem/DSP where these props have no effect. In that case rooting alone won't
> expose the stream — see the caveats in [`docs/MAGISK.md`](../MAGISK.md).

---

## Keeping OTA updates working (optional)

Rooting the boot partition breaks seamless OTAs. To update the OS afterward you
generally:

1. **Restore Images** in Magisk (reverts boot to stock) *before* taking the OTA,
   or
2. Re-extract the new build's `boot.img`/`init_boot.img`, re-patch, and re-flash
   after each system update.

Follow topjohnwu's official
[OTA guide](https://topjohnwu.github.io/Magisk/ota.html) for your device's
A/B vs. A-only layout.

---

## Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| `OEM unlocking` greyed out | Carrier/OEM-locked bootloader — device can't be rooted. |
| `fastboot devices` shows nothing | Bad cable, missing drivers (Windows), or wrong USB port. Try another cable/port; on Windows install the OEM/Google USB driver. |
| Bootloop after flashing | Wrong image or partition. Re-flash **stock** boot/init_boot, then redo with the correct one. |
| Magisk shows "N/A" / not installed | Patched the wrong partition, or the device uses `init_boot` and you flashed `boot` (or vice-versa). |
| `su` denied | Approve the Magisk superuser prompt on the phone; check **Magisk → Superuser**. |
| Props still `true` after reboot | Module ran too late — try a `system.prop` entry in the module instead of `post-fs-data.sh` (see `docs/MAGISK.md`). |
| Banking/DRM apps stopped working | Play Integrity detects root. Consider Magisk's DenyList / Zygisk (out of scope here). |

---

## See also

- [`docs/MAGISK.md`](../MAGISK.md) — why audio bridging needs these props and the
  concurrency-unlock module in detail.
- [`README.md`](../../README.md) — "Known limitations" for the permission and
  audio-bridging constraints.
- [topjohnwu/Magisk](https://github.com/topjohnwu/Magisk) — official Magisk
  releases and documentation.
- Your device's **XDA Developers** forum thread — the authoritative,
  model-specific source for unlock/root steps.
