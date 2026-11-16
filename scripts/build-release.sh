#!/usr/bin/env bash
# Build the release APK: the file a user installs.
#
#   scripts/build-release.sh
#
# Produces dist/VoxCtrl-Keys-<version>.apk and its .sha256, signed with the
# release key, and checks the signature before reporting success. The version
# comes from VERSION_NAME in gradle.properties.
#
# The key is given through the environment, never stored in the repository:
#
#   VOXKEYS_KEYSTORE_FILE       path to the keystore (.jks or .p12)
#   VOXKEYS_KEYSTORE_PASSWORD   its password
#   VOXKEYS_KEY_ALIAS           the key's alias inside it
#   VOXKEYS_KEY_PASSWORD        the key's password
#
# The release workflow (.github/workflows/release.yml) runs exactly this, and
# CI runs it with a throwaway key so the process itself is tested on every
# pull request. See docs/release-signing.md.
set -euo pipefail

cd "$(dirname "$0")/.."

missing=()
for name in VOXKEYS_KEYSTORE_FILE VOXKEYS_KEYSTORE_PASSWORD VOXKEYS_KEY_ALIAS VOXKEYS_KEY_PASSWORD; do
    [ -n "${!name:-}" ] || missing+=("$name")
done
if [ ${#missing[@]} -gt 0 ]; then
    echo "error: the release signing key is not configured. Missing: ${missing[*]}" >&2
    echo "       See docs/release-signing.md for the one-time setup." >&2
    exit 1
fi
if [ ! -f "$VOXKEYS_KEYSTORE_FILE" ]; then
    echo "error: VOXKEYS_KEYSTORE_FILE points to '$VOXKEYS_KEYSTORE_FILE', which does not exist." >&2
    exit 1
fi

version=$(sed -n 's/^VERSION_NAME=//p' gradle.properties | tr -d '[:space:]')
if [ -z "$version" ]; then
    echo "error: VERSION_NAME is not set in gradle.properties." >&2
    exit 1
fi

./gradlew --no-daemon :core:test :app:assembleRelease

built=app/build/outputs/apk/release/app-release.apk
if [ ! -f "$built" ]; then
    echo "error: $built was not produced. An 'app-release-unsigned.apk' beside it means the key was not picked up." >&2
    ls -l app/build/outputs/apk/release/ >&2 || true
    exit 1
fi

# apksigner ships with the SDK's build-tools; use the newest installed.
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$sdk" ] && [ -f local.properties ]; then
    sdk=$(sed -n 's/^sdk\.dir=//p' local.properties)
fi
apksigner=$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)
if [ -z "$apksigner" ]; then
    echo "error: apksigner was not found under '$sdk/build-tools'. Set ANDROID_HOME." >&2
    exit 1
fi

mkdir -p dist
apk="dist/VoxCtrl-Keys-$version.apk"
cp "$built" "$apk"

# Refuse to hand over an APK whose signature does not verify.
"$apksigner" verify "$apk"
certificate=$("$apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
if [ -z "$certificate" ]; then
    echo "error: could not read the signing certificate from $apk." >&2
    exit 1
fi

(cd dist && sha256sum "$(basename "$apk")" > "$(basename "$apk").sha256")

echo
echo "Built   $apk"
echo "Version $version"
echo "SHA-256 $(cut -d' ' -f1 "$apk.sha256")"
echo "Signed by certificate (SHA-256) $certificate"

# For the workflow: later steps read these.
if [ -n "${GITHUB_OUTPUT:-}" ]; then
    {
        echo "version=$version"
        echo "apk=$apk"
        echo "sha256=$(cut -d' ' -f1 "$apk.sha256")"
        echo "certificate=$certificate"
    } >> "$GITHUB_OUTPUT"
fi
