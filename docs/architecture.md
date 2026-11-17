# Architecture

Two Gradle modules. `core` knows nothing about Android and holds every rule
that can be stated without it. `app` is the Android side: the keyboard
service, the views, the microphone and the settings. Its one asset is the
typing keyboard's word list.

```
voxctrl-keys/
├── core/                                   # Kotlin/JVM, no dependencies
│   └── src/main/kotlin/ai/voxctrl/keys/core/
│       ├── ServerClient.kt     # The HTTP client: probe, transcribe, certificate pinning, error mapping
│       ├── Endpoint.kt         # Tidying addresses, building URLs, tailnet / local / public
│       ├── PairingCode.kt      # Reading vxpair_ codes
│       ├── Compatibility.kt    # Protocol revision, minimum server version
│       ├── Wav.kt              # PCM samples to a WAV file
│       ├── Json.kt             # A small JSON reader
│       ├── LiveClips.kt        # When to send a live-text clip
│       ├── TextJoiner.kt       # Spacing between existing text and a dictation
│       ├── BarLayout.kt        # Where the pill goes for a width, a preference and a fold
│       ├── KeyboardPlacement.kt # Width and side of the typing keyboard
│       ├── KeyboardLayout.kt   # The typing keyboard's pages, key positions, hit-testing
│       ├── SwipeDecoder.kt     # A swipe's path to words
│       ├── WordList.kt         # The word list: lookup, completions
│       └── Typist.kt           # What taps, swipes and suggestions do to the field
│
└── app/                                    # Android application
    └── src/main/
        ├── AndroidManifest.xml
        ├── kotlin/ai/voxctrl/keys/
        │   ├── VoxKeyboardService.kt   # The InputMethodService: taps, the dictation, typing the result, the field for the Typist
        │   ├── KeyboardBar.kt          # BarState and the views that show it; showing and placing the typing keyboard
        │   ├── KeyboardView.kt         # The typing keyboard's keys: drawing, touches, swipes, TalkBack
        │   ├── Haptics.kt              # The click on every key and button
        │   ├── Recorder.kt             # AudioRecord into a memory buffer
        │   ├── WaveformView.kt         # The moving bars while listening
        │   ├── FoldWatcher.kt          # Fold and posture, via androidx.window
        │   ├── Prefs.kt                # Settings; the API key encrypted with the Keystore
        │   └── SettingsActivity.kt     # The one settings screen
        ├── assets/words_en.txt         # The typing keyboard's word list (CC BY-SA 4.0; see docs/typing.md)
        └── res/                        # Layouts, vector icons, strings, the VoxCtrl palette
```

## Why two modules

- **Tests without a device.** The server client is tested against a stand-in
  HTTP and HTTPS server on the JVM, including a pinned certificate, a wrong
  certificate and a refused downgrade. The layout rules are tested for every
  screen and posture of a foldable. None of that needs an emulator.
- **A hard boundary.** `core` cannot import `android.*`, so the rules cannot
  quietly grow a dependency on a view or a context.
- **One place for the server contract.** Everything the keyboard sends to or
  expects from `voxctrl-server` is in `core`, next to the tests that pin it.

`core` uses only the JDK (`HttpURLConnection`, `javax.net.ssl`,
`java.util.Base64`), which behaves the same on Android 10+ and on a desktop
JVM. It has its own small JSON reader for the same reason: Android's bundled
`org.json` is not available to plain JVM tests.

## The life of a dictation

```
 tap                                                tap
  │                                                  │
  ▼                                                  ▼
Idle ──────────────▶ Listening ──────────────▶ Finishing ──────▶ Idle
                        │                           │
        Recorder thread │ every 100 ms              │ network thread
                        ▼                           ▼
              buffer grows in memory        POST /v1/audio/transcriptions
                        │                     (the whole recording)
        if live text    │                           │
        and LiveClips   ▼                           ▼
        .shouldSend   POST …/transcriptions   commitText(final)
                      (recording so far)      replaces the composing text
                        │
                        ▼
              setComposingText(preview)
```

1. **Start.** `VoxKeyboardService.startListening` checks that a server is
   configured and the microphone permission is held, notes the character
   before the cursor, raises the generation counter and starts a `Recorder`.
2. **Recording.** The recorder's thread reads 100 ms chunks of 16 kHz mono
   16-bit PCM into a growing in-memory buffer and reports the running total
   and a loudness level. The service posts those to the main thread for the
   timer and the waveform.
3. **Live text.** On each chunk the service asks `LiveClips.shouldSend`. When
   it says yes, the buffer so far is encoded as a WAV file and sent on the
   network thread; the answer is shown with `setComposingText`.
4. **Stop.** The recorder stops, the whole buffer is encoded and sent, and the
   answer is typed with `commitText`, which replaces the composing text.
5. **Failure.** If the server cannot be reached, the WAV bytes are held in
   memory and the bar offers Retry. Any other failure leaves whatever live text
   is on screen in place as ordinary text and points the user at the settings.

### The generation counter

Requests take time, and the user can do anything meanwhile: discard, switch
keyboards, move to another app. Every answer from the server is therefore
checked against `generation`, an integer that goes up whenever a dictation
starts, is discarded, or is abandoned because the keyboard was hidden. An
answer whose request was made under an older generation is thrown away. This is
what guarantees a transcript is never typed into a field other than the one it
was dictated for.

## Typing

The typing keyboard is three pieces with one direction of travel:

```
KeyboardView ──taps, swipes──▶ Typist ──edits──▶ Typist.Editor ──▶ InputConnection
 (app: pixels,                  (core: what each                (app: FieldEditor in
  touches, trail)                action does)                    VoxKeyboardService)
        ▲                          │
        └────── onChange ──────────┘  (page, shift, suggestions → redraw)
```

- `Typist` holds the word being composed, shift, the page and the
  suggestions, and turns each action into edits on `Typist.Editor`, an
  interface with the six operations it needs (text before the cursor, set and
  finish composing text, commit, delete, Enter, caps mode). The tests drive it
  with a fake field.
- `FieldEditor` implements that interface over `currentInputConnection`, and
  wraps each action's edits in `beginBatchEdit`/`endBatchEdit`, so the field
  reports one cursor change per action.
- **Whose cursor move was it?** When the cursor moves (`onUpdateSelection`)
  and the composing word is no longer at the cursor, the user moved it, and
  the word is left as it is. But the field also reports the keyboard's own
  edits, and late. So each batch the keyboard makes is counted, and that many
  reports (within a second) are taken as its own before a report is trusted to
  mean the user moved the cursor.
- The word list is read on a `voxctrl-words` thread when the service starts,
  and handed to the `Typist` on the main thread.
- While a dictation's live text is in the field (listening, finishing, or held
  for Retry), `KeyboardBar` disables the keys, since the live text is the
  field's composing text too.

## Threading

| Thread | Does |
|---|---|
| Main | All view work, all `InputConnection` calls, all state |
| `voxctrl-recorder` | Blocks on `AudioRecord.read`; lives for one recording |
| `voxctrl-words` | Reads the word list once when the service starts |
| Network (one thread) | Every request to the server, one at a time. The server handles one transcription at a time anyway, and a single thread means a final upload queues behind a live clip instead of racing it |

State (`state`, `generation`, `recorder`, `heldAudio`) is touched only on the
main thread. The recorder's buffer is the one shared structure, guarded by a
lock inside `Recorder`.

There are no coroutines and no third-party libraries in the app beyond
`androidx.window` (and `androidx.core`, which it needs). The typing keyboard's
TalkBack support uses the framework's `AccessibilityNodeProvider` directly
rather than adding `androidx.customview`. The keyboard is small
enough that a handler and one executor are easier to follow than a framework.

## The bar and its layout

`KeyboardBar` owns the views. `show(BarState)` sets every view for a state from
scratch (defaults first, then the state's overrides), so no state depends on
which one came before. Layout is recomputed whenever the bar's width or the
fold changes: `BarLayout.compute` returns the pill's width and the share of
free space to put on its left, and `KeyboardBar.applyLayout` turns that into
`LinearLayout` weights. See [The Keyboard Bar](./ui.md#layout-rules).

`KeyboardBar` also listens for window insets and pads the bar by the
navigation bar and display cut-out, so the row never sits under the system's
own keyboard buttons along the bottom edge.

`FoldWatcher` wraps `androidx.window`'s `WindowInfoTracker`. It is started with
the input view and reports the first `FoldingFeature`, or null. Everything it
does is inside a `try`: on a device or Android version that gives a keyboard no
fold information, the bar simply lays itself out by width.

## Errors

`ServerClient` turns every way a request can fail into a `ServerException` with
one of six `Failure` values, each of which maps to something the user can do:

| `Failure` | The bar shows | The user's move |
|---|---|---|
| `NOT_CONFIGURED` | Set up your server | Enter an address |
| `UNREACHABLE` | Can't reach … · Retry | Check Tailscale and the server |
| `UNAUTHORIZED` | Check server settings | Pair again |
| `CERTIFICATE_MISMATCH` | Check server settings | Pair again, or investigate |
| `REFUSED_DOWNGRADE` | Check server settings | Use `https://`, or remove the pairing |
| `SERVER_ERROR` | Check server settings | Check the server's log |

The server sends its errors in OpenAI's envelope with a message written for a
person. **Save and test** in the settings shows that message as is.
