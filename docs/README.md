# VoxCtrl Keys documentation

VoxCtrl Keys is a voice keyboard for Android and a thin client for a
[VoxCtrl](https://github.com/JRufer/VoxCtrl) server. The phone records, the
server transcribes, the keyboard types. The server is yours; nothing else is
contacted.

---

## Index

| Document | Description |
|---|---|
| [Overview](./overview.md) | What the keyboard does, what it leaves to other keyboards, design principles |
| [Installation & Setup](./installation.md) | Preparing the server, Tailscale, installing the APK, pairing, turning the keyboard on |
| [The Keyboard Bar](./ui.md) | The bar's parts and states, layout rules, foldables, the Galaxy Z Fold 8 in every posture |
| [Privacy & Security](./privacy.md) | What leaves the phone and where it goes, permissions, how to verify it |
| [Configuration](./configuration.md) | Every setting, its default, and where it is stored |
| [Architecture](./architecture.md) | Modules, classes, the life of a dictation, threading |
| [Server Protocol](./protocol.md) | The exact requests the keyboard makes to `voxctrl-server`, and what it expects back |
| [Keeping in Sync with VoxCtrl](./sync-with-voxctrl.md) | Which changes in either repository affect the other, and the checklist for each |
| [Development Guide](./development.md) | Building, testing, CI, versioning, releasing, what is and is not verified |
| [Release notes](./release-notes/) | One file per released version |

---

## Quick summary

```
        Phone                                        Your server
┌──────────────────────┐                      ┌──────────────────────┐
│ Microphone           │                      │                      │
│   │ 16 kHz mono PCM  │   WAV over HTTP(S)   │   voxctrl-server     │
│   ▼ (memory only)    │ ───────────────────▶ │   whisper.cpp /      │
│ VoxCtrl Keys         │   through a tailnet  │   Moonshine /        │
│   ▲                  │ ◀─────────────────── │   Parakeet           │
│   │ text             │        {"text"}      │                      │
│ Text field of the    │                      │                      │
│ app you are using    │                      │                      │
└──────────────────────┘                      └──────────────────────┘
```

**Tech stack**

- **Language:** Kotlin, JDK 17
- **App:** Android `InputMethodService` with plain Views (no Compose, no
  support library beyond `androidx.window` for fold awareness)
- **Core:** a pure Kotlin/JVM module with no dependencies: HTTP client
  (`HttpURLConnection`), certificate pinning, pairing codes, WAV encoding,
  layout rules
- **Build:** Gradle 8.14 (wrapper), Android Gradle Plugin 8.11, min SDK 29
  (Android 10), target SDK 36
- **Server:** VoxCtrl server 0.9.0 or newer, over its OpenAI-compatible API
