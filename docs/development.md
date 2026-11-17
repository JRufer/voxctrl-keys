# Development Guide

## Requirements

- JDK 17.
- The Android SDK with platform 36 (Android Studio installs it; on the command
  line, `sdkmanager "platforms;android-36"`).
- Nothing else: Gradle comes from the wrapper.

## Building

```bash
./gradlew :core:test          # unit tests
./gradlew :app:assembleDebug  # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug      # Android lint
scripts/build-release.sh      # the signed release APK, in dist/ (needs the signing key)
```

Install on a connected phone with `./gradlew :app:installDebug`, or
`adb install app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

See [Architecture](./architecture.md) for the classes. The build itself:

| File | |
|---|---|
| `settings.gradle.kts` | The two modules and the repositories (Google, Maven Central) |
| `gradle/libs.versions.toml` | Every version in one place |
| `core/build.gradle.kts` | Kotlin/JVM, JUnit 4 |
| `gradle.properties` | `VERSION_NAME`, the one place the version is written |
| `app/build.gradle.kts` | Android application: `compileSdk` 36, `minSdk` 29, release signing from the environment |
| `scripts/build-release.sh` | Builds, signs and verifies the release APK |

## Tests

All tests are in `core/src/test` and run on the JVM.

| Test | Covers |
|---|---|
| `ServerClientTest` | The client against a stand-in HTTP and HTTPS server: probe, upload format, errors, an unreachable server, a pinned certificate, a wrong certificate, a refused downgrade |
| `PairingCodeTest` | Reading and refusing pairing codes; which address wins |
| `EndpointTest` | Address completion, URLs, tailnet / local / public |
| `BarLayoutTest` | The bar on every Galaxy Z Fold 8 screen and posture, and a sweep of widths |
| `KeyboardLayoutTest`, `KeyboardPlacementTest` | The typing keyboard's pages and hit-testing; where it sits for each hand, width and fold |
| `SwipeDecoderTest`, `WordListTest` | Swipes to words against the real word list, including simulated sloppy swipes over the 1,500 most common words |
| `TypistTest` | Tapping, shift and caps lock, suggestions, swiping, delete, punctuation spacing, password fields, against a fake text field |
| `LiveClipsTest`, `TextJoinerTest`, `WavTest`, `JsonTest`, `CompatibilityTest` | The small rules |

`core/src/test/resources/test-server.p12` is a throwaway self-signed
certificate and key for the HTTPS stand-in (password `changeit`). It protects
nothing; regenerate it with:

```bash
keytool -genkeypair -alias server -keyalg EC -groupname secp256r1 -validity 36500 \
  -dname "CN=voxctrl-server test" -keystore core/src/test/resources/test-server.p12 \
  -storetype PKCS12 -storepass changeit -keypass changeit
```

The `app` module has no automated tests yet. Its logic is kept thin on
purpose: anything that can be tested is moved to `core`.

The typing tests read the committed word list,
`app/src/main/assets/words_en.txt` (the `core` test task is given its path).
To change the list, edit and rerun `scripts/build-wordlist.py` (it needs
Python with `wordfreq` and the `wordlist-english` npm package; the commands
are at the top of the script) and commit the result; never edit the list by
hand.

## What is verified

Be precise about this when changing or releasing anything.

| | Status |
|---|---|
| `core` logic | Unit-tested, run in CI on every push and pull request |
| The app compiles, passes lint and packages into an APK | CI |
| The release build script signs an APK and its signature verifies | CI, with a throwaway key on every pull request |
| The Release workflow with the real key: signing, and creating the draft release | Run for 0.1.0, which was then published |
| The keyboard on a real phone | **Partly.** Seen on a Galaxy Z Fold 8 cover screen with 0.1.0: the bar shows, the server's status shows, and a dictation was typed into the app's Try it box. Not yet confirmed: live text, other apps, switching keyboards, Retry |
| The bar on a Galaxy Z Fold 8 | Cover screen upright seen. The fix for the row overlapping the system's navigation strip (0.1.1) and every other screen and posture are **not yet seen on the device**. [The Keyboard Bar](./ui.md#galaxy-z-fold-8) has the list |
| Against a real `voxctrl-server` | Seen working once, as above. The stand-in server in the tests is written from the server's source and documentation |
| The live-clip header, `Server busy` and the contract check (0.1.2) | `core` unit tests against the stand-in server. **Not yet seen** on a phone or against a real 0.9.20 server |
| The typing keyboard (0.2.0) | Its rules (keys, placement, swipe decoding, editing) are `core` unit tests. The app's own layouts and views were rendered off-device (Robolectric) at 412 dp and 816 dp, collapsed, expanded, swiping, left- and right-handed and in book posture, while it was built; that render is not part of the repository. The smoothed swipe trail was rendered the same way, from simulated swipes with jitter. **Not yet run on a phone**: real touches and swipes, the swipe accuracy of a real finger, how the trail looks under a real finger, how strong the heavy-click feedback feels, typing into other apps, TalkBack, and the cursor-tracking in `onUpdateSelection` are all unconfirmed |

When one of these is verified, update this table.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes to `main` and on every pull request:
core tests, the debug APK, lint, and a rehearsal of the release build with a
throwaway key. The debug APK and the lint report are uploaded as artifacts of
the run.

`.github/workflows/release.yml` runs on pushes to `main` and by hand: it
builds the signed release APK and creates or refreshes the draft GitHub
release. See [Building and Signing Releases](./release-signing.md). Like VoxCtrl's CI, it runs for pull requests against any
branch, so a stacked change is still built on its own.

## Conventions

- **Docs stay accurate.** A change that alters behaviour updates the page in
  `docs/` that describes it, in the same pull request. The fixed values in
  [Configuration](./configuration.md#fixed-values) and the tables in
  [The Keyboard Bar](./ui.md) are the easiest to forget.
- **Rules go in `core`, with a test.** If a piece of logic does not need an
  Android class, it does not belong in `app`.
- **The server contract is documented before it is used.** A new request or a
  newly read field goes into [protocol.md](./protocol.md) and VoxCtrl's
  `docs/clients.md` in the same change. See
  [Keeping in Sync with VoxCtrl](./sync-with-voxctrl.md).
- **No new network destinations.** `ServerClient` is the only code that opens
  a connection, and only to the configured server.
- **No logging of audio or text.** The app writes nothing to logcat today;
  keep transcripts and recordings out of any logging that is added.
- **Branches and pull requests.** `main` is the integration branch. Work goes
  on a branch and arrives as a pull request.
- **Bug reports through GitHub issues**, not email.

## Versioning and releases

The version is `VERSION_NAME` in `gradle.properties`, and nowhere else:
`versionName` and `versionCode` are derived from it. For every change that is
going to be released:

1. Raise `VERSION_NAME` (semantic versioning).
2. Add `docs/release-notes/<version>.md`. The Release workflow refuses to
   build a version without one.
3. If the server contract changed, follow
   [Keeping in Sync with VoxCtrl](./sync-with-voxctrl.md).

Merging to `main` then builds the signed `VoxCtrl-Keys-<version>.apk` and puts
it on a draft GitHub release, which is published by hand. The whole process,
the signing key's one-time setup and how to build a release APK locally are in
[Building and Signing Releases](./release-signing.md).

## Troubleshooting the build

- **`SDK location not found`**: set `ANDROID_HOME`, or create
  `local.properties` with `sdk.dir=/path/to/Android/Sdk`.
- **`Failed to find target with hash string 'android-36'`**: install platform
  36 with the SDK manager.
- **A Kotlin or AGP version error after changing `libs.versions.toml`**: the
  Android Gradle Plugin, Kotlin and Gradle versions are tied together; check
  the plugin's compatibility table before raising one alone.
