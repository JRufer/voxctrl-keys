# Keeping in Sync with VoxCtrl

VoxCtrl Keys and VoxCtrl live in separate repositories and are released on
separate schedules. What joins them is small and specific. This page lists
every point of contact, who owns it, and what to do when one side changes.

The matching page in the VoxCtrl repository is
[`docs/clients.md`](https://github.com/JRufer/VoxCtrl/blob/master/docs/clients.md).
The two pages are meant to be read together, and a change to one usually means
a change to the other.

## Points of contact

| # | What | Owned by (VoxCtrl) | Mirrored here | Pinned by |
|---|---|---|---|---|
| 1 | The three endpoints the keyboard calls, and the shape of their answers | `crates/voxctrl-server/src/http.rs`; `docs/server.md` "API reference" | `core/.../ServerClient.kt`; [protocol.md](./protocol.md) | `ServerClientTest` |
| 2 | The error envelope and status codes | `http.rs` (`ApiError`) | `ServerClient.errorFor` | `ServerClientTest` |
| 3 | Accepted audio: WAV, 16 kHz mono 16-bit PCM | `crates/voxctrl-server/src/audio.rs` | `core/.../Wav.kt`, `app/.../Recorder.kt` | `WavTest` |
| 4 | The pairing-code format | `crates/voxctrl-pairing/src/lib.rs` | `core/.../PairingCode.kt` | `PairingCodeTest` |
| 5 | Certificate pinning: SHA-256 of the leaf certificate's DER, no name check, no downgrade | `voxctrl-pairing` (`PinnedCertificate`, `pin_for`) | `ServerClient.pinnedContext`, `requireUsable` | `ServerClientTest` (encrypted cases) |
| 6 | How a typed address is completed; the default port 8787 | `src/lib/Settings/server-setup.ts` (`normalizeServerAddress`); `voxctrl-inference` `remote_openai.rs` (`normalize_*_url`) | `core/.../Endpoint.kt` | `EndpointTest` |
| 7 | Connect timeout (4 s) | `remote_openai.rs` (`CONNECT_TIMEOUT`) | `ServerClient.CONNECT_TIMEOUT_MS` | — |
| 8 | Live text by re-sending the recording so far, each clip marked `X-VoxCtrl-Purpose: live`; a busy server answers a live clip, or a dictation past a full queue, with `503` | `docs/server.md` "Live text and server load"; `crates/voxctrl-server/src/engine.rs` (`Gate`); `http.rs` (`purpose`) | `core/.../LiveClips.kt`; `ServerClient.transcribe(live = …)`, `Failure.BUSY` | `LiveClipsTest`, `ServerClientTest` |
| 9 | The look: palette, icon | `VoxCtrl-Website` `base.css`; `src-tauri/icons/` | `app/src/main/res/values/colors.xml`, `drawable-nodpi/ic_voxctrl.png`, `mipmap-*` | — |
| 10 | Minimum server version | Release history | `Compatibility.MIN_SERVER_VERSION` | `CompatibilityTest` |
| 11 | The contract revision the server reports (`contract` in `/health`, from 0.9.19) | `http.rs` (`CONTRACT_REVISION`); `docs/clients.md` | `Compatibility.speaks`, `ServerInfo.contract` | `CompatibilityTest`, `ServerClientTest` |

## The client contract revision

`Compatibility.PROTOCOL_REVISION` (currently **1**) names the version of
points 1 to 5 that this keyboard was written against. It appears on the
settings screen's version line, so a bug report says which contract a build
speaks. VoxCtrl's `docs/clients.md` records the same number with the server
release that introduced it. From server 0.9.19, `/health` reports it as
`contract`, and a keyboard that finds a higher number than its own asks to be
updated rather than failing in some stranger way.

Raise the revision, in both repositories together, when the server changes
something in points 1 to 5 in a way an existing keyboard would notice. Do not
raise it for additions an old keyboard ignores (a new endpoint, a new optional
field, a new key in the pairing code).

## When VoxCtrl changes

Before merging a VoxCtrl change that touches any file in the "Owned by"
column, VoxCtrl's `docs/clients.md` asks the author to check it against the
contract. On this side:

1. Read the change against [protocol.md](./protocol.md). Does anything the
   keyboard sends stop being accepted? Does anything it reads change name,
   type or meaning?
2. If not, nothing to do here. Additive changes are invisible to the keyboard.
3. If so:
   - update `core` and the stand-in server in `ServerClientTest` to the new
     behaviour, keeping the old one working if servers in the field still
     have it;
   - update [protocol.md](./protocol.md) and, if needed,
     `Compatibility.MIN_SERVER_VERSION`;
   - raise `PROTOCOL_REVISION` here and the revision table in VoxCtrl's
     `docs/clients.md`;
   - release the keyboard **before or together with** the server change, never
     after, so no user is left with a server their keyboard cannot use.

### Changes that are safe for the keyboard

- New endpoints.
- New fields in `/health`, in a transcription answer, or in the error object.
- New keys in the pairing code's JSON.
- New optional request fields on `/v1/audio/transcriptions`.
- Accepting more audio formats.
- New server settings, engines, models.

### Changes that break the keyboard

- Renaming or removing `text` in the transcription answer.
- Requiring a request field the keyboard does not send (it sends `file`,
  `response_format`, and optionally `prompt` and `language`).
- Refusing 16 kHz mono 16-bit PCM WAV, or a `Content-Length` upload.
- Changing the `Authorization: Bearer` scheme, or requiring a new header on
  the three endpoints (such as `X-VoxCtrl-Client`).
- Moving `/health`, `/v1/models` or `/v1/audio/transcriptions`.
- Using a status other than 401 or 403 for a refused key.
- Changing the pairing code's prefix, its encoding, or the meaning of `h`,
  `p`, `k` or `f`.
- Changing what the fingerprint is computed over.

## When the keyboard changes

A keyboard change needs something from VoxCtrl only when it starts using more
of the server:

1. If it uses an endpoint or field already in the server, add it to
   [protocol.md](./protocol.md) and to the "What clients rely on" table in
   VoxCtrl's `docs/clients.md`, so the server's authors know it is now
   load-bearing. Raise `MIN_SERVER_VERSION` to the release that introduced it.
2. If it needs something the server does not have, the server change goes in a
   VoxCtrl pull request first. App changes stay in VoxCtrl; keyboard changes
   stay here.

## A manual end-to-end check

There is no automated test that runs this keyboard against a real server.
Until there is, run this before a keyboard release and after any server
release that touched the files above:

1. Start the server version in question and print a pairing code.
2. Pair a phone, or exercise the same requests with `curl` as below.
3. Dictate a sentence with live text on, and one with it off.
4. Turn the server's encryption on, pair again, dictate.
5. Stop the server, dictate, start it, tap **Retry**.

The requests, for checking a server without a phone:

```bash
KEY=vx_…; SERVER=http://homeserver:8787

curl -s $SERVER/health
curl -s -H "Authorization: Bearer $KEY" $SERVER/v1/models
curl -s -H "Authorization: Bearer $KEY" \
     -F response_format=json -F "file=@dictation.wav;type=audio/wav" \
     $SERVER/v1/audio/transcriptions
```

## Shared look

The bar uses VoxCtrl's palette and icon so it reads as part of the same
product. The values are copied, not linked: when the website's `base.css` or
the app icon changes, update `colors.xml` and the icon files here (point 9).
The typeface is the phone's own; the Geist fonts are not bundled.
