# VoBiz SIP Trunk Setup

This guide walks through configuring Magick GSM2SIP against a **VoBiz** wholesale
SIP trunk. All values here are entered on the app's **Settings** screen and
persisted via `SettingsRepository`. Fields correspond one-to-one with
`data/SipConfig.kt`; the preset values come from `data/VoBizPreset.kt`.

---

## Quick start: the "Apply VoBiz preset" button

The Settings screen has a one-tap **"Apply VoBiz preset"** button. It merges the
documented VoBiz defaults into the current form **without touching your
username/password** (`VoBizPreset.applyTo` uses `existing.copy(...)`), setting:

| Field | Value set by preset |
|-------|---------------------|
| SIP server / domain | `sip.vobiz.com` |
| Port | `5060` (or `5061` if transport is already TLS) |
| Realm | `*` (accept any realm challenge) |
| STUN enabled | `true` |
| STUN server | `stun.vobiz.com` |
| Registration expiry | `300` seconds |
| Keep-alive OPTIONS | `60` seconds |
| Codec priority | `G722/16000` → `PCMA/8000` → `PCMU/8000` → `opus/48000` |
| RFC 2833 DTMF | `true` |

After tapping it, enter your credentials and tap **Save**. Every field remains
editable, so override anything your VoBiz portal specifies differently.

---

## Registrar & transport

- **Registrar / domain:** `sip.vobiz.com`. The account registers to
  `sip:sip.vobiz.com:<port>` (built in `SipAccount.buildConfig`).
- **Ports & transport** (Transport dropdown → `UDP` / `TCP` / `TLS`):

  | Transport | Port |
  |-----------|------|
  | UDP | `5060` |
  | TCP | `5060` |
  | TLS | `5061` |

  Use **TLS (5061)** where the trunk supports it for encrypted signalling.

---

## Field-by-field reference (Settings screen)

### SIP account

| Field | `SipConfig` property | Notes |
|-------|----------------------|-------|
| SIP server / domain | `domain` | `sip.vobiz.com`. Forms the account URI `sip:<username>@<domain>`. |
| Username | `username` | Your VoBiz trunk auth username / SIP user. |
| Password | `password` | Digest auth secret. Stored in DataStore (see README note on encryption). |
| Realm | `realm` | Digest realm. `*` accepts whatever realm the server challenges with; set a specific realm if VoBiz requires it. |
| Display name | `displayName` | Optional; used in the `From` display name. |
| Port | `port` | `5060` (UDP/TCP) or `5061` (TLS). |
| Transport | `transport` | `UDP`, `TCP`, or `TLS`. |

Digest credentials are registered as
`AuthCredInfo("digest", realm, username, 0, password)`.

### Outbound / caller ID

| Field | `SipConfig` property | Notes |
|-------|----------------------|-------|
| Outbound proxy (optional) | `outboundProxy` | If VoBiz gives you an SBC/edge proxy, put its SIP URI here; leave blank to route by domain. |
| Caller ID / DID (optional) | `callerId` | Your assigned DID / presentation number; blank falls back to the username. |

### NAT / STUN

| Field | `SipConfig` property | Notes |
|-------|----------------------|-------|
| Enable STUN | `stunEnabled` | On by default; enables ICE and STUN-based NAT traversal. |
| STUN server | `stunServer` | `stun.vobiz.com`. If unreachable, fall back to the public `stun.l.google.com:19302` (the app's default and documented fallback). |

### Registration

| Field | `SipConfig` property | Notes |
|-------|----------------------|-------|
| Registration expiry (s) | `regExpirySeconds` | `300` seconds. PJSIP re-registers before expiry; retry interval is 30 s. |
| Keep-alive OPTIONS (s) | `keepAliveSeconds` | `60` seconds. Sent as SIP keep-alive on the transport to hold NAT bindings open. |

### Behaviour

| Field | `SipConfig` property | Notes |
|-------|----------------------|-------|
| Auto-answer inbound GSM | `autoAnswerGsm` | When on, `GsmInCallService` auto-answers a ringing cellular call so it can be bridged to SIP. |
| RFC 2833 DTMF (telephone-event) | `dtmfRfc2833` | Out-of-band DTMF via RFC 2833 telephone-event (PJSIP's default). |
| Auto-start on boot | `autoStartOnBoot` | `BootReceiver` restarts the gateway after reboot when enabled. |

### Codec priority

Default order (highest first): **G.722 (wideband) → G.711 A-law → G.711 U-law →
Opus**, i.e. `G722/16000, PCMA/8000, PCMU/8000, opus/48000`. `SipStack`
enables only these codecs and disables everything else so the SDP offer stays
clean; priority is `255 - index` so earlier entries win negotiation. The endpoint
runs at a 16 kHz clock rate to favour G.722.

---

## The `X-GSM-Forward` header (outbound SIP → GSM)

To trigger an **outbound GSM call**, VoBiz must include a custom header in the
SIP `INVITE` it sends to this gateway:

```
X-GSM-Forward: <destination-phone-number>
```

For example, an INVITE that should ring the cellular number `+15551234567`:

```
INVITE sip:1000@<gateway-ip>:5060 SIP/2.0
Via: SIP/2.0/UDP sip.vobiz.com;branch=z9hG4bK...
From: "Trunk" <sip:trunk@sip.vobiz.com>;tag=...
To: <sip:1000@<gateway-ip>>
Call-ID: ...
CSeq: 1 INVITE
X-GSM-Forward: +15551234567
Contact: <sip:trunk@sip.vobiz.com>
Content-Type: application/sdp
...
```

Behaviour in the gateway (`SipAccount.parseForwardHeader` +
`CallOrchestrator.onIncomingCall`):

- The header name match is **case-insensitive**, single-line; the value is
  everything after the first `:`, trimmed.
- If the header is **present** and non-empty and the gateway is idle, it rings
  the SIP caller (`180 Ringing`), places the cellular call to that number, and
  answers the SIP leg (`200 OK`) once the GSM party picks up.
- If the header is **absent/empty**, the INVITE is rejected with
  `488 Not Acceptable Here` (a plain SIP call with no forwarding target).
- If the gateway is **already handling a call**, the INVITE is rejected with
  `486 Busy Here` (single active session).

Configure your VoBiz dial plan / trunk routing to inject `X-GSM-Forward` with the
E.164 number you want dialled on the cellular modem.
