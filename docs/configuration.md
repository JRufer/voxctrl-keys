# Configuration

All settings are on the app's one screen (open **VoxCtrl Keys** from the app
drawer, or tap the bar when it says *Open*). There is no configuration file:
a phone keyboard has no convenient place for one, and the settings are few.

## Settings

### Your VoxCtrl server

| Setting | Default | Meaning |
|---|---|---|
| **Address** | empty | The server's name or address. A bare host is completed the same way the desktop app does it: `homeserver` becomes `http://homeserver:8787/v1`. A full URL is kept as typed (and given `/v1` if it has no path) |
| **Pairing code** | — | A `vxpair_…` code from `voxctrl-server --pairing-code`. **Pair** reads the port, API key and certificate from it. An address already entered is kept; otherwise the code's own address is used. The code itself is not stored |
| **API key** | empty | Filled in by pairing. Can be typed by hand for a server without encryption |
| **Save and test** | | Saves the address and key and checks them against the server |
| **Remove pairing** | | Shown when a certificate is saved. Forgets it, for going back to a server without encryption |

### Dictation

| Setting | Default | Meaning |
|---|---|---|
| **Show words as I speak** | on | Live text. Off: the words appear only when you stop, with one request per dictation instead of several a second |
| **Language** | empty | A language code (`en`, `de`, …) sent with each request. Empty uses the server's own setting. Only whisper.cpp models honour it |
| **Names and jargon** | empty | Words to bias recognition toward, sent as the request's `prompt`. Separate them with commas |
| **Microphone position on wide screens** | Centre | Where the microphone pill sits when the keyboard is 600 dp or wider: Left, Centre or Right. See [The Keyboard Bar](./ui.md#layout-rules) |
| **Typing keyboard on wide screens** | Right-handed | Which edge the typing keyboard sits against when the keyboard is 600 dp or wider: Right-handed or Left-handed. Narrower screens always get a full-width keyboard. See [The Keyboard Bar](./ui.md#the-typing-keyboard) |

Language, names and jargon save when you leave the field or the screen; the
rest save as you change them.

## Fixed values

These are constants in the code rather than settings. They are listed here so
that a behaviour can be traced to its number.

| Value | Where | Meaning |
|---|---|---|
| 8787 | `Endpoint.DEFAULT_PORT` | Port given to a bare host |
| 4 s | `ServerClient.CONNECT_TIMEOUT_MS` | How long to wait for the server to accept a connection (the desktop app's value) |
| 30 s | `ServerClient.READ_TIMEOUT_MS` | How long a transcription may take |
| 15 s | `VoxKeyboardService.PROBE_INTERVAL_MS` | Least time between checks of a server known to be up |
| 0.33 s | `VoxKeyboardService.MIN_SAMPLES` | Recordings shorter than this are dropped as mis-taps |
| 5 min | `Recorder.MAX_SAMPLES` | Longest recording, about 10 MB as a WAV |
| 0.8 s / 0.6 s / 45 s | `LiveClips` | Live text: first clip, least new audio between clips, and where it stops updating |
| 600 dp / 420 dp / 360 dp | `BarLayout` | Wide-layout threshold, widest pill, narrowest bar that shows the server name |
| 0.9.0 | `Compatibility.MIN_SERVER_VERSION` | Oldest server the keyboard accepts |
| 440 dp / 260 dp / 10 dp / 12 dp | `KeyboardPlacement` | Typing keyboard on a wide screen: widest, narrowest, gap to the screen's edge, gap to a bent fold |
| 52 dp / 42 dp / 500 dp | `KeyboardLayout` | Key row height, on a short screen, and what counts as short |
| 0.45 key | `KeyboardView.SWIPE_START` | How far a finger moves before a touch is a swipe |
| 4 dp | `KeyboardView.TRAIL_STEP_DP` | Spacing of the points the swipe trail is drawn through |
| Heavy click | `Haptics` | Touch feedback on every key and button (`EFFECT_HEAVY_CLICK`) |
| 0.4 s / 0.4 s, then 50 ms | `Typist.DOUBLE_TAP_MS`, `KeyboardView` | Double-tap Shift for caps lock; Delete starts repeating, then repeats every |
| 3 | `Typist.SUGGESTIONS` | Places in the suggestions strip |
| 50,000 | `words_en.txt` | Words the typing keyboard knows |

## Where settings are stored

In the app's private shared preferences, file `voxctrl_keys`:

| Key | Content |
|---|---|
| `endpoint` | The completed server address |
| `fingerprint` | The paired certificate's SHA-256, lowercase hex. Absent when not paired with an encrypted server |
| `api_key_sealed` | The API key, AES-256-GCM encrypted (12-byte IV followed by ciphertext, base64) under the Android Keystore key `voxctrl_keys_api_key` |
| `live_text` | boolean |
| `language`, `vocabulary` | strings |
| `pill_position` | `left`, `center` or `right` |
| `keyboard_hand` | `right` or `left` |

None of it is backed up or transferred to a new device. See
[Privacy & Security](./privacy.md#what-is-stored-on-the-phone).

## On the server

The keyboard needs nothing configured on the server beyond a working
`voxctrl-server`. Two server settings are worth knowing about:

- `public_host` (`VOXCTRL_SERVER_PUBLIC_HOST`): set it to the server's tailnet
  name and pairing codes will carry that address, so the **Address** box can be
  left empty when pairing.
- `[tls] enabled`: the server's own encryption. Not needed over Tailscale;
  recommended on any other network.

Both are documented in VoxCtrl's
[server configuration reference](https://github.com/JRufer/VoxCtrl/blob/master/docs/server.md#configuration-reference).
