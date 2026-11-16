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
| `app/build.gradle.kts` | Android application: `compileSdk` 36, `minSdk` 29, `versionName` |

## Tests

All tests are in `core/src/test` and run on the JVM.

| Test | Covers |
|---|---|
| `ServerClientTest` | The client against a stand-in HTTP and HTTPS server: probe, upload format, errors, an unreachable server, a pinned certificate, a wrong certificate, a refused downgrade |
| `PairingCodeTest` | Reading and refusing pairing codes; which address wins |
| `EndpointTest` | Address completion, URLs, tailnet / local / public |
| `BarLayoutTest` | The bar on every Galaxy Z Fold 8 screen and posture, and a sweep of widths |
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

## What is verified

Be precise about this when changing or releasing anything.

| | Status |
|---|---|
| `core` logic | Unit-tested, run in CI on every push and pull request |
| The app compiles, passes lint and packages into an APK | CI |
| The keyboard on a real phone: recording, typing into apps, live text, switching keyboards | **Not yet verified** |
| The bar on a Galaxy Z Fold 8: cover screen, inner screen, landscape, book and tabletop postures | Layout rules unit-tested; **not yet seen on the device**. See [The Keyboard Bar](./ui.md#galaxy-z-fold-8) for the two things to look at first |
| Against a real `voxctrl-server` | **Not yet verified.** The stand-in server in the tests is written from the server's source and documentation |

When one of these is verified, update this table.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes to `main` and on every pull request:
core tests, the debug APK, lint. The APK and the lint report are uploaded as
artifacts of the run. Like VoxCtrl's CI, it runs for pull requests against any
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

`versionName` and `versionCode` are in `app/build.gradle.kts`. For every
change that is going to be released:

1. Raise `versionName` (semantic versioning) and `versionCode` (by one).
2. Add `docs/release-notes/<versionName>.md`.
3. If the server contract changed, follow
   [Keeping in Sync with VoxCtrl](./sync-with-voxctrl.md).

There is no signed release build yet. CI produces a debug APK signed with the
runner's debug key, which differs between runs, so one CI build cannot be
installed over another. Setting up a release keystore (kept out of the
repository; `.gitignore` already excludes `*.jks`, `*.keystore` and
`keystore.properties`) and a release workflow is the next step before handing
the app to anyone else.

## Troubleshooting the build

- **`SDK location not found`**: set `ANDROID_HOME`, or create
  `local.properties` with `sdk.dir=/path/to/Android/Sdk`.
- **`Failed to find target with hash string 'android-36'`**: install platform
  36 with the SDK manager.
- **A Kotlin or AGP version error after changing `libs.versions.toml`**: the
  Android Gradle Plugin, Kotlin and Gradle versions are tied together; check
  the plugin's compatibility table before raising one alone.
