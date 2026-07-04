# PJSIP native library drop-in

Place the PJSIP `pjsua2` artifacts here to enable real SIP functionality:

- **Preferred:** `pjsua2-release.aar` (bindings + native `.so` for all ABIs), **or**
- `pjsua2.jar` here **and** `libpjsua2.so` per ABI under `app/src/main/jniLibs/<abi>/`.

Build these with [pjsip-android-builder](https://github.com/mhedwithout/pjsip-android-builder)
configured with the `opus`, `g722`, and `g711` codecs. See
[`docs/PJSIP_BUILD.md`](../../docs/PJSIP_BUILD.md).

If neither is present, the app compiles against the `src/pjsipStub` API stub so
CI/tests pass, and SIP is disabled at runtime.

> These binaries are git-ignored (see `.gitignore`) — they are large and
> device-specific, so each developer supplies their own.
