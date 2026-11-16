# VoxCtrl Keys

A privacy-first voice keyboard for Android. It is a thin client for
[VoxCtrl](https://github.com/JRufer/VoxCtrl): the phone records, your own
VoxCtrl server transcribes, and the words are typed into whatever app you are
using. No cloud service is involved, and the keyboard talks to nothing but the
server you point it at.

The keyboard is a single short bar with one job:

```
┌──────────────────────────────────────────────────────────┐
│ [⌨]  ( ◉  Tap to speak               ● homeserver )  [⌫] │
└──────────────────────────────────────────────────────────┘
```

Tap the microphone, speak, tap again. Your words appear in the text field as
you say them and are replaced by the final transcript when you stop. The button
on the left switches back to your typing keyboard.

> **Status: 0.1.0, first implementation.** It builds and its logic is tested
> in CI, but it has not yet been run on a phone. See
> [what is and is not verified](docs/development.md#what-is-verified).

## What you need

- An Android 10 or newer phone.
- A VoxCtrl server, version 0.9.0 or newer, that the phone can reach. The
  recommended way is [Tailscale](https://tailscale.com) on both, so the server
  is never exposed to the internet. See [docs/installation.md](docs/installation.md).

## Quick start

1. Install the APK from the [latest release](https://github.com/JRufer/voxctrl-keys/releases).
   (Before the first release is published: the `voxctrl-keys-debug-apk`
   artifact of a CI run.)
2. Open **VoxCtrl Keys**, turn the keyboard on in the system's keyboard list,
   and allow the microphone.
3. On the server, run `voxctrl-server --pairing-code`. Paste the code into the
   app and tap **Pair**.
4. In any app, switch to the VoxCtrl keyboard and tap the microphone.

## Documentation

Everything is in [`docs/`](docs/README.md):

| | |
|---|---|
| [Overview](docs/overview.md) | What it is, what it does and does not do |
| [Installation & setup](docs/installation.md) | Server, Tailscale, pairing, turning the keyboard on |
| [The keyboard bar](docs/ui.md) | States, layout, foldables and the Galaxy Z Fold 8 |
| [Privacy & security](docs/privacy.md) | Exactly what leaves the phone, and where it goes |
| [Configuration](docs/configuration.md) | Every setting and where it is stored |
| [Architecture](docs/architecture.md) | Modules, data flow, threading |
| [Server protocol](docs/protocol.md) | The contract with `voxctrl-server` |
| [Keeping in sync with VoxCtrl](docs/sync-with-voxctrl.md) | What to check, in which repository, when either side changes |
| [Development](docs/development.md) | Building, testing, releasing |
| [Building and signing releases](docs/release-signing.md) | The release APK, the signing key, verifying a download |

## Repository layout

```
voxctrl-keys/
├── app/      # The Android app: the keyboard service, the bar, the settings screen
├── core/     # Plain Kotlin, no Android: server client, pairing codes, layout rules. Unit-tested on the JVM
├── docs/     # Documentation (kept accurate with the code; see docs/development.md)
├── assets/   # The VoxCtrl icon
├── scripts/  # build-release.sh: the signed release APK
└── .github/  # CI (tests, debug APK, lint) and the Release workflow
```

## Reporting a problem

Open an issue on [GitHub](https://github.com/JRufer/voxctrl-keys/issues).
Problems with the server itself belong in
[VoxCtrl's issues](https://github.com/JRufer/VoxCtrl/issues).

## License

MIT, the same as VoxCtrl. See [LICENSE](LICENSE).
