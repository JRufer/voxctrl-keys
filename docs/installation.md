# Installation & Setup

Four things, in order: a VoxCtrl server, a private way for the phone to reach
it, the app, and pairing the two.

**Contents**

1. [The server](#1-the-server)
2. [Reaching the server: Tailscale](#2-reaching-the-server-tailscale)
3. [Install the app](#3-install-the-app)
4. [Turn the keyboard on](#4-turn-the-keyboard-on)
5. [Pair with the server](#5-pair-with-the-server)
6. [Dictate](#6-dictate)
7. [Troubleshooting](#troubleshooting)

---

## 1. The server

You need `voxctrl-server` 0.9.0 or newer running on a machine of yours. Setting
one up is covered in VoxCtrl's
[server guide](https://github.com/JRufer/VoxCtrl/blob/master/docs/server.md):
from the AppImage (`--server`), with Docker, or as a system service.

For a phone, two things about the server matter:

- **Pick a fast model.** Live text re-sends what you have said so far a few
  times a second, so a model that answers quickly feels much better. Parakeet
  (`parakeet` / `tdt-0.6b-v3`) is a good choice on a CPU.
- **Leave the API key on.** It is on by default. The pairing code carries it
  to the phone.

Check the server from itself before going further:

```bash
curl http://localhost:8787/health
```

## 2. Reaching the server: Tailscale

The phone has to reach the server's port (8787 by default), at home and away,
without that port being open to the internet. [Tailscale](https://tailscale.com)
does exactly that: it gives your devices private addresses that work anywhere
and encrypts everything between them.

1. Install Tailscale on the server machine and sign in:
   <https://tailscale.com/download>.
2. Install the Tailscale app on the phone and sign in to the same account.
3. Find the server's tailnet address. Either works in the keyboard:
   - its MagicDNS name, such as `homeserver` or `homeserver.tailXXXX.ts.net`
   - its `100.x.y.z` address (`tailscale ip -4` on the server)
4. Make sure the server listens on the tailnet. The default
   `bind = "0.0.0.0:8787"` does. If you run Docker, publish the port as the
   server guide shows.
5. If the server runs a firewall, allow port 8787 on the `tailscale0`
   interface.

> **Tailscale must be connected on the phone when you dictate.** If it is off,
> the bar shows **Can't reach…** with a Retry button.

**Is it encrypted?** Over a tailnet, yes: WireGuard encrypts the connection
end to end, so the server's own encryption can stay off and the address can be
plain `http://`. The app's settings say which case applies to the address you
entered. See [Privacy & Security](./privacy.md#the-connection).

**Without Tailscale.** The keyboard works with any address the phone can
reach: the server's LAN address while on home Wi-Fi, another VPN, or an HTTPS
reverse proxy. On a plain LAN, turn on the
[server's encryption](https://github.com/JRufer/VoxCtrl/blob/master/docs/server.md#encryption)
if you do not fully trust the network. Never forward the port from the
internet.

## 3. Install the app

There is no store listing. Install the APK directly:

- **A release:** download `VoxCtrl-Keys-<version>.apk` from the
  [Releases page](https://github.com/JRufer/voxctrl-keys/releases) and open it
  on the phone (Android asks you to allow installing from that source).
  Releases are all signed with the same key, so a newer one installs over an
  older one and keeps your pairing. To check a download, see
  [Verifying a download](./release-signing.md#verifying-a-download).
- **A test build of a branch:** open a run of the
  [CI workflow](https://github.com/JRufer/voxctrl-keys/actions/workflows/ci.yml),
  download the `voxctrl-keys-debug-apk` artifact, unzip it, and open the
  `.apk`. Debug builds are signed with a different key on every run, so one
  cannot be installed over another or over a release: uninstall first, which
  forgets the pairing.
- **From source:** see the [Development Guide](./development.md#building).

## 4. Turn the keyboard on

Open **VoxCtrl Keys**. The first two sections walk you through it:

1. **Keyboard → Open keyboard list.** Turn on **VoxCtrl voice keyboard**.
   Android shows its standard warning that a keyboard can read what you type;
   see [Privacy & Security](./privacy.md) for what this one does with that.
2. **Microphone → Allow.**

You do not have to make it your default keyboard. Keep your usual keyboard as
the default and switch to VoxCtrl when you want to speak (step 6). VoxCtrl
also has a basic typing keyboard with swipe typing, behind the keyboard button
at the left of its bar, for a few words between dictations.

## 5. Pair with the server

1. On the server, print its pairing code:

   ```bash
   voxctrl-server --pairing-code                                # built from source
   ./VoxCtrl-linux-x86_64-vulkan.AppImage --server --pairing-code   # AppImage
   docker exec voxctrl-server voxctrl-server --pairing-code     # Docker
   sudo -u voxctrl -H voxctrl-server --pairing-code             # system service
   ```

   The code starts with `vxpair_`. It contains the API key: treat it like a
   password.
2. Get the code onto the phone. Any private route works: a note-syncing app, a
   message to yourself, or typing it.
3. In the app, under **Your VoxCtrl server**:
   - **Address:** enter the server's tailnet name or `100.x.y.z` address.
   - **Pairing code:** paste the code and tap **Pair**.

   The address you entered is kept; the code supplies the port, the API key
   and, if the server has encryption on, its certificate. If you leave
   **Address** empty, the address inside the code is used, which is normally
   the server's LAN address and will not work away from home.
4. The status line reads **Connected to …** with the model and server version.

**By hand, without a code:** enter the address and the API key (from the
server's log or `server.toml`) and tap **Save and test**. This does not work
for a server with encryption on, which needs the code for its certificate.

## 6. Dictate

1. Tap a text field in any app.
2. Switch to the VoxCtrl keyboard: tap the keyboard icon at the bottom corner
   of the screen (or your keyboard's own switch key) and choose **VoxCtrl voice
   keyboard**.
3. Tap the microphone, speak, tap it again.
4. To type, tap the keyboard button at the left of the bar: it returns to the
   keyboard you came from. Hold it for the full list.

The **Try it** box at the bottom of the app's settings is a convenient place
for a first test.

---

## Troubleshooting

### The bar says "Can't reach …"

The server did not answer within four seconds.

- Is Tailscale connected on the phone? Open its app and check.
- Is the server running? `curl http://localhost:8787/health` on the server.
- Is the address right? In the app, **Save and test** shows the reason.
- Did the server's firewall let the tailnet in on port 8787?

If you had just finished speaking, your recording is still held: fix the
connection and tap **Retry**. The X on the right discards it.

### The bar says "Check server settings"

The server answered but refused the keyboard: a wrong API key, or a
certificate that is not the paired one. Open the app (tap the bar) and tap
**Save and test** to see which, then pair again with a fresh code.

### "This server was paired with encryption on, so its address must start with https://"

The keyboard never talks unencrypted to a server it was paired with over
HTTPS. If you turned encryption off on the server, tap **Remove pairing**, then
pair again or enter the key by hand.

### "This server is version …. The keyboard needs 0.9.0 or newer."

Upgrade the server.

### Words appear late, or only when I stop

Live text depends on how fast the server answers. Use a faster model, or turn
off **Show words as I speak** in the app. Live text also stops updating after
45 seconds of continuous speech; the full transcript still arrives when you
stop.

### The microphone button says "Microphone is in use"

Another app is recording. Close it and try again.

### Nothing is typed in one particular app

Use the **Try it** box in the app's settings to confirm the keyboard itself
works. If it does, report the app it fails in.

### Reporting a problem

Open an issue at <https://github.com/JRufer/voxctrl-keys/issues> with the
phone model, Android version, the app's version line (bottom of the settings
screen) and the server version.
