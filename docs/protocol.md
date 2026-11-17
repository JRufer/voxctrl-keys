# Server Protocol

This is the keyboard's side of the contract with `voxctrl-server`: every
request it makes and everything it relies on in the answers. It is **client
contract revision 1** (`Compatibility.PROTOCOL_REVISION`).

The server's side is documented in the VoxCtrl repository:

- [`docs/server.md`, "API reference"](https://github.com/JRufer/VoxCtrl/blob/master/docs/server.md#api-reference):
  the full API.
- [`docs/clients.md`](https://github.com/JRufer/VoxCtrl/blob/master/docs/clients.md):
  the part of it that clients outside that repository depend on, and the rules
  for changing it.

If this page and the server disagree, the server's behaviour is the truth and
this page (and probably the code) has a bug. See
[Keeping in Sync with VoxCtrl](./sync-with-voxctrl.md).

**Contents**

1. [Summary](#summary)
2. [Addresses](#addresses)
3. [Authentication](#authentication)
4. [`GET /health`](#get-health)
5. [`GET /v1/models`](#get-v1models)
6. [`POST /v1/audio/transcriptions`](#post-v1audiotranscriptions)
7. [Errors](#errors)
8. [Live text](#live-text)
9. [Pairing codes](#pairing-codes)
10. [Encryption and pinning](#encryption-and-pinning)
11. [What the keyboard does not use](#what-the-keyboard-does-not-use)
12. [Differences from the desktop client](#differences-from-the-desktop-client)

---

## Summary

| Request | Key | Used for | Relies on |
|---|---|---|---|
| `GET /health` | No | Status dot; version check | JSON object. `version` (string), `model` (string), `cleanup`, `tts` (booleans), `contract` (number, from server 0.9.19). All optional to the keyboard |
| `GET /v1/models` | Yes | Proving the key is accepted | Status 200 means accepted; 401 means refused. `data[0].id` is used as the model name if `/health` gave none |
| `POST /v1/audio/transcriptions` | Yes | Live text and the final transcript | Status 200 with a JSON object whose `text` is a string. Live clips carry `X-VoxCtrl-Purpose: live` |

Minimum server version: **0.9.0** (`Compatibility.MIN_SERVER_VERSION`), the
first release with `voxctrl-server`. A server whose `/health` reports an older
version is refused in the settings; one that reports no readable version is
accepted.

**Contract revision.** From 0.9.19 a server's `/health` also reports
`contract`, the [client contract revision](https://github.com/JRufer/VoxCtrl/blob/master/docs/clients.md#client-contract-revisions)
it speaks. One higher than `Compatibility.PROTOCOL_REVISION` means the server
has changed something the keyboard relies on: the settings say to update the
keyboard, and the bar shows **Update VoxCtrl Keys** (`INCOMPATIBLE`). No
`contract` means revision 1.

## Addresses

The keyboard stores one address, the server's `/v1` base, for example
`http://homeserver:8787/v1`. What a user types is completed by the same rules
as the desktop app's `normalizeServerAddress`:

| Typed | Stored |
|---|---|
| `homeserver` | `http://homeserver:8787/v1` |
| `homeserver:9000` | `http://homeserver:9000/v1` |
| `https://voxctrl.example.com` | `https://voxctrl.example.com/v1` |
| `http://10.0.0.2:8787/v1/` | `http://10.0.0.2:8787/v1` |

Only an address typed without a scheme gets the default port 8787. With a
certificate fingerprint saved, a bare host is completed with `https://`.

URLs are built from the stored address as in the desktop app's
`normalize_transcription_url` and `normalize_models_url`:
`…/v1/audio/transcriptions` and `…/v1/models`. `/health` is at the root of the
same scheme, host and port.

## Authentication

`Authorization: Bearer <api key>` on every request except `/health`. A server
started with `require_api_key = false` hands out no key in its pairing code;
the keyboard then sends no `Authorization` header.

Every request also carries `Accept: application/json` and
`User-Agent: voxctrl-keys`. The keyboard does not send `X-VoxCtrl-Client`: that
header is for the voice endpoints, which it does not use.

## `GET /health`

```http
GET /health
```

```json
{"status":"ok","version":"0.9.20","contract":1,"min_client":"0.9.0","model":"parakeet/tdt-0.6b-v3","cleanup":false,"tts":true,"llm":false}
```

The keyboard reads `version` and `model` for the settings screen, and
`contract` for the compatibility check above. `min_client` names the oldest
VoxCtrl *desktop app* the server works with; the keyboard has its own version
numbers and does not read it. A non-200
answer or a body that is not a JSON object is not an error by itself: the
check goes on to `/v1/models`, so an OpenAI-compatible server without
`/health` still passes.

## `GET /v1/models`

```http
GET /v1/models
Authorization: Bearer vx_…
```

```json
{"object":"list","data":[{"id":"parakeet/tdt-0.6b-v3","object":"model","owned_by":"voxctrl"}]}
```

Anything other than 200 fails the check; see [Errors](#errors).

## `POST /v1/audio/transcriptions`

A `multipart/form-data` request with a fixed `Content-Length` (the keyboard
does not use chunked transfer).

| Field | Sent | Value |
|---|---|---|
| `response_format` | Always | `json` |
| `prompt` | When **Names and jargon** is not empty | That setting, as typed |
| `language` | When **Language** is not empty | That setting |
| `file` | Always, last | The recording. `filename="dictation.wav"`, `Content-Type: audio/wav` |

`model` is not sent; the server ignores it.

**Header.** A live-text clip carries `X-VoxCtrl-Purpose: live`; the final
upload carries no such header. See [Live text](#live-text).

**Audio.** A complete WAV file: RIFF header, PCM format tag 1, **16 kHz, mono,
16-bit**, little-endian. This is what the desktop app sends and what the
server accepts without converting. At most five minutes, about 9.6 MB, against
the server's default `max_upload_mb` of 256.

**Answer.**

```json
{"text":"Hello from the server.","language":"en","duration":2.4}
```

The keyboard uses `text` only, trimmed of surrounding whitespace. An empty
`text` is a valid answer meaning nothing was recognised.

**Timeouts.** 4 s to connect, 30 s for the answer.

## Errors

The keyboard relies on OpenAI's error envelope, which the server uses for all
its errors:

```json
{"error":{"message":"The API key was not accepted. Check it against the api_key in the server's server.toml.","type":"authentication_error"}}
```

| Status | The keyboard treats it as | Shown |
|---|---|---|
| 401, 403 | `UNAUTHORIZED` | `error.message`, or a default asking to pair again |
| 503 | `BUSY` | On the final upload, **Server busy** with Retry: the recording is kept and nothing was run. On a live clip, ignored |
| 426 | `SERVER_ERROR` | `error.message`: an encrypted server asked over plain HTTP says to paste its pairing code |
| Any other non-200 | `SERVER_ERROR` | `error.message`, or "The server answered with an error (HTTP *n*)" |
| No answer, refused connection, timeout | `UNREACHABLE` | "Can't reach *server*" with Retry |
| TLS failure caused by a certificate other than the pinned one | `CERTIFICATE_MISMATCH` | An explanation and a request to pair again |
| Any other TLS failure | `UNREACHABLE` | As above |

`error.type` is not read. `error.message` is shown to the user unchanged, so
the server's practice of writing each message to say what to do carries
through to the phone.

## Live text

There is no separate live endpoint. While recording, the keyboard sends the
recording **so far** to `POST /v1/audio/transcriptions`, exactly as above, and
shows the answer as composing text. This is the same approach as the desktop
app's live text with a remote engine.

- The first clip is sent after 0.8 s of audio.
- Each later clip needs at least 0.6 s of new audio.
- One request at a time: the next clip is not sent until the previous answer
  (or failure) has arrived, so the rate follows the server's speed.
- After 45 s of continuous recording no more live clips are sent.
- A failed live request is ignored. Only the final upload reports errors.
- Each live clip carries `X-VoxCtrl-Purpose: live`. A VoxCtrl server 0.9.20
  or newer runs it only when its model is free and no dictation is waiting,
  and otherwise answers `503` at once without running it. The keyboard
  ignores that like any failed live clip and sends the next one when it is
  due, so a live clip never delays anyone's dictation, and the final upload
  waits behind at most the one clip already running.
- Servers before 0.9.20 ignore the header and treat live clips as ordinary
  transcriptions, so the final upload may queue behind a clip still running.

## Pairing codes

Defined by the `voxctrl-pairing` crate. A code is the text `vxpair_` followed
by unpadded URL-safe base64 of a JSON object:

| Key | Type | Meaning |
|---|---|---|
| `h` | string, optional | The server's address, when it knows one clients can use |
| `p` | number | The port, 1 to 65535 |
| `k` | string, optional | The API key, when the server requires one |
| `f` | string, optional | SHA-256 of the server certificate's DER bytes, 64 hex digits. Present exactly when the server encrypts |

```
vxpair_eyJoIjoiaG9tZXNlcnZlciIsInAiOjg3ODcsImsiOiJ2eF9leGFtcGxlIn0
        └─ {"h":"homeserver","p":8787,"k":"vx_example"}
```

The keyboard refuses a code without the prefix, with a body that is not
base64 or not a JSON object, without a valid `p`, or with an `f` that is not
64 hex digits. Unknown keys are ignored, so the server can add to the code
without breaking older keyboards.

The resulting address is `https://` when `f` is present and `http://`
otherwise, with the port from `p`. An IPv6 host is put in brackets.

## Encryption and pinning

For a server paired with a fingerprint:

- The connection must be `https://`. With a fingerprint saved and an `http://`
  address, the keyboard sends nothing and reports `REFUSED_DOWNGRADE`.
- The server's leaf certificate is accepted if and only if the SHA-256 of its
  DER encoding equals the saved fingerprint (compared without regard to
  letter case). The chain, the validity dates and the name in the certificate
  are not checked, matching the desktop app's `PinnedCertificate` verifier.
- The TLS handshake itself is unchanged, so the server must hold the
  certificate's private key.

For an `https://` address without a fingerprint (a reverse proxy), the
platform's ordinary certificate checks apply.

## What the keyboard does not use

`/v1/cleanup`, `/v1/chat/completions`, `/v1/audio/speech` and `/v1/voices`.
The keyboard reads the `cleanup` and `tts` flags from `/health` but does
nothing with them in this version.

## Differences from the desktop client

Deliberate, and the reason for each:

| | Desktop app | Keyboard | Why |
|---|---|---|---|
| Upload | Can stream a recording with chunked transfer while the user speaks | Always a complete file with `Content-Length` | Simpler, and a phone dictation is short |
| Address when pairing | The code's `h` wins over a typed address | A typed address wins over the code's `h` | The code's `h` is usually the server's LAN address; a phone reaches it over a tailnet |
| Fallback engine | A local model can take over when the server is down | None; the recording is held for Retry | No model on the phone |
| Vocabulary | Sent as `prompt`, and corrections applied to the result | Sent as `prompt` only | Post-processing lives in the desktop app |
| Cleanup, AI processing, TTS, voices | Used when enabled | Not used | Out of scope for a dictation bar |
