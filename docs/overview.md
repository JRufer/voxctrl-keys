# Overview

VoxCtrl Keys puts VoxCtrl's dictation on a phone without putting a speech
model on the phone. It is an Android keyboard (an "input method") whose whole
interface is one short bar with a microphone button.

## What it does

- **Dictates into any app.** It is a system keyboard, so it works wherever a
  text field does: messages, notes, browsers, search boxes.
- **Shows words as you speak.** While you talk, what you have said so far is
  transcribed a few times a second and shown underlined in the text field. When
  you stop, the final transcript replaces it.
- **Uses your VoxCtrl server.** All speech recognition happens on the server
  you run. The keyboard holds no model and needs no powerful phone.
- **Stays out of the way.** The bar is about 63 dp tall, a fraction of a
  typing keyboard, so the app you are dictating into keeps the screen.
- **Tells you where your voice goes.** The bar always shows the server's name
  and whether it is reachable.
- **Survives a dropped connection.** If the server does not answer when you
  stop, the recording is kept in memory and **Retry** sends it again.

## What it deliberately does not do

- **No typing.** There are no letter keys. The button on the left switches to
  your previous keyboard, and holding it opens the system's keyboard picker.
  The one key it has is backspace, for fixing a wrong word without switching.
- **No on-device recognition.** With no server there is no dictation. A local
  fallback model, as the desktop app has, is not part of this version.
- **No cleanup, AI processing or text-to-speech.** The server offers these to
  the desktop app; the keyboard uses speech recognition only. Filler removal,
  snippets and custom vocabulary corrections, which the desktop app applies
  itself after transcription, are likewise not applied here. The **Names and
  jargon** setting is sent to the server as a recognition hint.
- **No accounts, analytics or crash reports.** See [Privacy](./privacy.md).

## Design principles

1. **Privacy first.** One network destination, chosen by the user. Audio in
   memory only. No text kept. Secrets encrypted and excluded from backups.
2. **A thin client.** Anything that can live on the server does. The phone's
   job is the microphone, the bar and typing the result.
3. **Speak the server's existing protocol.** The keyboard uses the same
   OpenAI-compatible endpoints as the desktop app and adds nothing to the
   server. The contract is written down in [Server Protocol](./protocol.md).
4. **Short and predictable.** One row, one primary action, the same three
   positions in every state and on every screen.
5. **Testable without a phone.** Everything that is not an Android API call
   lives in the `core` module and is unit-tested on the JVM, including the
   bar's layout on foldables.

## Relationship to VoxCtrl

| | Repository |
|---|---|
| The desktop app, the server, the pairing-code format, the server API | [JRufer/VoxCtrl](https://github.com/JRufer/VoxCtrl) |
| The Android keyboard | this repository |

The two are released separately. What ties them together is the server API;
[Keeping in Sync with VoxCtrl](./sync-with-voxctrl.md) says how that is kept
from drifting.
