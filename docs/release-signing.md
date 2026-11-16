# Building and Signing Releases

How the APK that users install is produced, how it is signed, the one-time
setup that signing needs, and how to check a download.

**Contents**

1. [What is produced](#what-is-produced)
2. [The release process](#the-release-process)
3. [One-time setup: the signing key](#one-time-setup-the-signing-key)
4. [Making a release](#making-a-release)
5. [Building a release APK on your own machine](#building-a-release-apk-on-your-own-machine)
6. [Verifying a download](#verifying-a-download)
7. [If the key is lost or leaked](#if-the-key-is-lost-or-leaked)

---

## What is produced

| | Built by | Signed with | For |
|---|---|---|---|
| `VoxCtrl-Keys-<version>.apk`, with a `.sha256` beside it | The **Release** workflow, or `scripts/build-release.sh` | The release key | Installing and updating. This is the deliverable |
| `app-debug.apk` (artifact `voxctrl-keys-debug-apk`) | The **CI** workflow, on every push and pull request | A debug key made fresh on each run | Trying a branch. Cannot be installed over another build |

Android accepts an update only if it is signed with the same key as the
installed app and has a higher version code. That is why the release key must
stay the same for the life of the app, and why a debug build and a release
build cannot replace each other: switching between them means uninstalling
first, which forgets the pairing.

## The release process

`scripts/build-release.sh` is the whole process, and both the workflow and a
developer run the same script:

1. Refuses to start without the four signing variables.
2. Reads the version from `VERSION_NAME` in `gradle.properties`.
3. Runs the core tests and `:app:assembleRelease`.
4. Copies the result to `dist/VoxCtrl-Keys-<version>.apk`.
5. Verifies the signature with `apksigner` and reads the signing certificate's
   SHA-256. An APK that does not verify is never reported as built.
6. Writes `dist/VoxCtrl-Keys-<version>.apk.sha256`.

`.github/workflows/release.yml` wraps it:

- **When it runs:** on every push to `main`, and by hand from the Actions tab
  on any branch.
- **Before building:** it checks that `docs/release-notes/<version>.md`
  exists, and that the signing secrets are set. Without the secrets it stops
  with an error that names the missing ones. It never falls back to another
  key.
- **After building:** it uploads the APK to the run's Artifacts and creates a
  **draft** GitHub release tagged `v<version>`, with the release notes, the
  APK's SHA-256, the signing certificate's SHA-256 and the commit it was built
  from. Further pushes at the same version refresh that draft.
- **It never publishes.** Publishing the draft is a deliberate click on
  GitHub. Once a version is published the workflow leaves it alone and says
  so; raise the version to release again.

The CI workflow rehearses the same script on every pull request with a key it
makes and throws away, so a broken release process shows up in review rather
than on release day. Nothing from the rehearsal is published.

### The version

`VERSION_NAME` in `gradle.properties` is the only place the version is
written. `app/build.gradle.kts` derives both Android values from it:

| `VERSION_NAME` | `versionName` | `versionCode` |
|---|---|---|
| `0.1.0` | `0.1.0` | 100 |
| `0.9.18` | `0.9.18` | 918 |
| `1.4.12` | `1.4.12` | 10412 |

`versionCode` is `MAJOR × 10000 + MINOR × 100 + PATCH`, so it rises whenever
the version does and the two cannot be forgotten separately. Minor and patch
numbers must stay below 100; the build fails with a message otherwise.

## One-time setup: the signing key

Done once, by the repository's owner. Until it is done the Release workflow
fails with *"The release signing key is not set up"* and the CI debug APK is
the build to test with.

**1. Create the key.** On your own machine, with a JDK installed:

```bash
keytool -genkeypair -v \
  -keystore voxctrl-keys-release.p12 -storetype PKCS12 \
  -alias voxctrl-keys \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=VoxCtrl Keys, O=VoxCtrl"
```

It asks for a password. Use a long random one from a password manager. With a
PKCS12 keystore the key's password is the same as the keystore's.

**2. Back it up.** Put `voxctrl-keys-release.p12` and its password somewhere
safe and offline: a password manager that stores files, or an encrypted drive.
**If this file is lost, no update can ever be installed over an existing
install** (see [below](#if-the-key-is-lost-or-leaked)). Do not commit it:
`.gitignore` excludes `*.p12`, `*.jks`, `*.keystore` and
`keystore.properties`, but the safest place for it is outside the repository
folder altogether.

**3. Give it to GitHub Actions** as four repository secrets (Settings →
Secrets and variables → Actions → New repository secret), or with the GitHub
CLI:

```bash
base64 -w0 voxctrl-keys-release.p12 | gh secret set VOXKEYS_KEYSTORE_BASE64 -R JRufer/voxctrl-keys
gh secret set VOXKEYS_KEYSTORE_PASSWORD -R JRufer/voxctrl-keys   # prompts for the value
gh secret set VOXKEYS_KEY_PASSWORD      -R JRufer/voxctrl-keys   # the same password
gh secret set VOXKEYS_KEY_ALIAS         -R JRufer/voxctrl-keys --body voxctrl-keys
```

(`base64 -w0` is the Linux spelling; on macOS use `base64 -i voxctrl-keys-release.p12`.)

| Secret | Value |
|---|---|
| `VOXKEYS_KEYSTORE_BASE64` | The keystore file, base64-encoded on one line |
| `VOXKEYS_KEYSTORE_PASSWORD` | The keystore's password |
| `VOXKEYS_KEY_ALIAS` | `voxctrl-keys` |
| `VOXKEYS_KEY_PASSWORD` | The key's password (the same, for PKCS12) |

**4. Run the workflow.** Actions → Release → Run workflow. Its summary shows
the signing certificate's SHA-256. Note it down next to the backup: it is how
anyone can tell a genuine build from an imitation.

## Making a release

1. Raise `VERSION_NAME` in `gradle.properties`.
2. Add `docs/release-notes/<version>.md`.
3. If the server contract changed, follow
   [Keeping in Sync with VoxCtrl](./sync-with-voxctrl.md).
4. Merge to `main`. The Release workflow builds the APK and creates the draft
   release.
5. On GitHub, open the draft under Releases, read it over, and publish it.

To get a signed APK from a branch without touching the releases: Actions →
Release → Run workflow, choose the branch, and turn **publish** off. The APK
is in that run's Artifacts.

## Building a release APK on your own machine

With the keystore file at hand and the Android SDK installed:

```bash
export VOXKEYS_KEYSTORE_FILE=/path/to/voxctrl-keys-release.p12
export VOXKEYS_KEY_ALIAS=voxctrl-keys
read -rs VOXKEYS_KEYSTORE_PASSWORD && export VOXKEYS_KEYSTORE_PASSWORD
export VOXKEYS_KEY_PASSWORD="$VOXKEYS_KEYSTORE_PASSWORD"

scripts/build-release.sh
```

The result is `dist/VoxCtrl-Keys-<version>.apk`. Signed with the same key, it
is interchangeable with the workflow's build and installs over it.

## Verifying a download

Each release lists two fingerprints.

**The file is intact:**

```bash
sha256sum -c VoxCtrl-Keys-0.1.0.apk.sha256
```

**It was signed with the release key**, which is what proves who built it:

```bash
apksigner verify --print-certs VoxCtrl-Keys-0.1.0.apk
```

The `Signer #1 certificate SHA-256 digest` should be the same for every
release of VoxCtrl Keys. Android enforces this itself on an update: it refuses
an APK signed with a different key than the installed app. The check matters
most for a first install, when there is nothing to compare against.

## If the key is lost or leaked

- **Lost** (no backup, and the secrets cannot be read back out of GitHub): a
  new key has to be made. Everyone with the app installed must uninstall it
  and install the new build, losing their pairing. There is no way around
  this; it is how Android protects installed apps.
- **Leaked:** whoever has it can sign an APK that Android will accept as an
  update. Make a new key, replace the four secrets, release, and tell users to
  uninstall and reinstall, giving the new certificate fingerprint.

Either way, record the new certificate's SHA-256 in the release notes of the
first release signed with it.
