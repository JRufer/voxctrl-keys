# Privacy & Security

A keyboard sees what you say and, in principle, everything you type. This page
states exactly what VoxCtrl Keys does with that, and how to check it.

## The short version

- Your voice goes to **one place: the VoxCtrl server whose address you
  entered**. The app has no other network destination. No analytics, no crash
  reporting, no update checks, no account.
- **Audio is held in memory** while you dictate and sent once you stop (and,
  with live text on, while you speak). It is never written to the phone's
  storage.
- **Text is not kept.** The transcript is typed into the app you are using and
  forgotten. There is no history.
- **What you type stays on the phone.** The typing keyboard sends nothing
  anywhere: swipes are decoded and suggestions looked up on the phone, from a
  word list that ships with the app. It keeps only the word being typed, in
  memory, and learns nothing. It reads the character before the cursor to
  decide spacing and capitals, as dictation does when it starts.
- **The microphone is open only between your tap to start and your tap to
  stop**, and closes immediately if the keyboard is hidden.

## What leaves the phone

Every request goes to the configured server and nowhere else.

| When | What is sent | Why |
|---|---|---|
| The keyboard appears (at most every 15 s) | `GET /health`, and `GET /v1/models` with the API key | To show whether the server is reachable |
| While you speak, with live text on | The recording so far, as a WAV file, a few times a second, plus the language and "names and jargon" settings if set | To show words as you speak |
| When you stop | The whole recording, as a WAV file, with the same two settings | To get the transcript |
| **Save and test** in the settings | The same two requests | To verify the address and key |

The requests carry a `User-Agent` of `voxctrl-keys` and nothing that
identifies you or the device. What the server does with a recording is the
server's side: `voxctrl-server` keeps audio in memory for the length of the
request and logs durations, never content. See VoxCtrl's
[server security notes](https://github.com/JRufer/VoxCtrl/blob/master/docs/server.md#security).

## The connection

Whether the audio is protected in transit depends on the address, and the
settings screen says which case you are in:

| Address | Protection |
|---|---|
| Paired with a server that has encryption on (`https://`, certificate pinned) | TLS. The keyboard accepts only the one certificate whose fingerprint came in the pairing code, and never falls back to `http://` for that server |
| A Tailscale address (`100.64.0.0/10`, `*.ts.net`, `fd7a:115c:a1e0::/48`) over `http://` | WireGuard, end to end between the phone and the server |
| A private or local address over `http://` | **None.** Fine on a network you trust; otherwise turn on the server's encryption |
| A public address over `http://` | **None, across the internet.** The settings screen shows a warning. Do not use this |
| Any other `https://` address (a reverse proxy) | TLS, verified against the phone's trusted certificate authorities |

The app permits plain HTTP (`usesCleartextTraffic`) because a tailnet or a
home network is the normal way to reach a VoxCtrl server, and on a tailnet a
second layer of TLS adds nothing.

**Pinning.** An encrypted VoxCtrl server makes its own certificate, which no
authority vouches for. Trust comes from the pairing code, which carries the
certificate's SHA-256 fingerprint. The keyboard compares the fingerprint of the
certificate the server presents and refuses anything else with *"The server's
certificate is not the one this keyboard was paired with."* The server's name
is not compared with the address, since a home server is reached by whatever
address it has that day; the TLS handshake still proves the server holds the
certificate's private key.

## What is stored on the phone

In the app's private preferences (`voxctrl_keys`), readable by this app only:

| | Stored as |
|---|---|
| Server address | Plain text |
| Certificate fingerprint (if paired with an encrypted server) | Plain text; it is not a secret |
| API key | **Encrypted** with AES-256-GCM under a key held in the Android Keystore, which never leaves the device's secure hardware where the phone has it |
| Live text on/off, language, names and jargon, microphone position | Plain text |

Nothing is backed up: `allowBackup` is off and the data-extraction rules
exclude everything from both cloud backup and device-to-device transfer, so a
new phone has to be paired again. Uninstalling removes it all.

No audio, no transcript and nothing typed is ever stored. The typing
keyboard's word list is read-only and part of the app; nothing is added to
it. A recording the server did not
answer for is kept **in memory** for the Retry button and is dropped when you
tap the X, when the keyboard is hidden, or when the app's process ends.

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | To record a dictation |
| `INTERNET` | To reach your server. Android has no narrower permission for "one host" |

That is the whole list. There is no storage, contacts, location, notification
or boot permission, and no foreground or background service besides the
keyboard itself, which Android runs only while it is the selected keyboard.

**Android's warning when you turn the keyboard on** ("this input method may be
able to collect all the text you type") is shown for every third-party
keyboard. For this one: what you tap or swipe on its typing keyboard is
turned into text on the phone and typed into the field, and is not stored or
sent; the only text that crosses the network is the transcript coming back
from your server.

## How to verify

- **Read the code.** Network access is in one class,
  `core/.../ServerClient.kt`; search the repository for `openConnection` to
  confirm there is no other. The manifest (`app/src/main/AndroidManifest.xml`)
  lists the permissions.
- **Watch the traffic.** With the phone on your tailnet, the server's log shows
  each request the keyboard makes (`Transcribed 1.4s of audio in … ms`).
  Tailscale's own app, or a firewall app, will show the keyboard connecting to
  that one address only.
- **Check the microphone indicator.** Android shows its green microphone dot
  whenever any app records. It should appear when you tap the microphone and
  disappear when you tap again or leave the app.

## Not yet done

- The app has not been through an independent security review.
- There is no option to require the screen to be unlocked before dictating
  from the lock screen's reply field; the keyboard behaves there as it does
  anywhere else.
