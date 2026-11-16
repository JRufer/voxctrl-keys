# Working in this repository

VoxCtrl Keys is the Android voice keyboard for VoxCtrl. Start with
`docs/README.md`; the pages most often needed are `docs/architecture.md`,
`docs/protocol.md` and `docs/sync-with-voxctrl.md`.

Rules that are easy to break:

- **Keep `docs/` accurate.** A behaviour change updates its page in the same
  pull request. `docs/development.md` has a "What is verified" table: do not
  mark anything verified that was not actually run.
- **The server contract.** Anything the keyboard sends to or reads from
  `voxctrl-server` is documented in `docs/protocol.md` and in the VoxCtrl
  repository's `docs/clients.md`. Changing it means changing both, and
  `Compatibility.PROTOCOL_REVISION`. Server and desktop-app changes belong in
  JRufer/VoxCtrl, not here.
- **Logic goes in `core/` with a JVM test.** `core` must not import
  `android.*`.
- **One network destination.** `ServerClient` is the only code that opens a
  connection. No analytics, no logging of audio or text.
- **Releases.** Raise `VERSION_NAME` in `gradle.properties` (the only place
  the version is written) and add `docs/release-notes/<version>.md` for every
  change that is going to be released. The signing key is never in the
  repository; see `docs/release-signing.md`.
- **Branches.** `main` is the integration branch; work arrives as pull
  requests. Bug reports go through GitHub issues.

Build and test: `./gradlew :core:test :app:assembleDebug :app:lintDebug`.
