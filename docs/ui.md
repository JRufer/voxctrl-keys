# The Keyboard Bar

The whole keyboard is one row. This page describes its parts, every state it
can be in, and how it lays itself out, including on a foldable.

The design mockup is the "VoxCtrl voice keyboard for Android" canvas; the
colours and type follow the VoxCtrl design system (dark surfaces, cyan accent,
monospace for status).

**Contents**

1. [Anatomy](#anatomy)
2. [States](#states)
3. [Live text](#live-text)
4. [Layout rules](#layout-rules)
5. [Galaxy Z Fold 8](#galaxy-z-fold-8)
6. [Accessibility](#accessibility)
7. [Where this lives in the code](#where-this-lives-in-the-code)

---

## Anatomy

```
 10dp                                                          10dp
 ├─┤                                                            ├─┤
 ┌────────────────────────────────────────────────────────────────┐ ─ 1dp line
 │                                                                │ ┬ 6dp
 │ ┌────┐  ╭──────────────────────────────────────────╮  ┌────┐   │ │
 │ │ ⌨  │  │ (◉)  Tap to speak          ● homeserver  │  │ ⌫  │   │ │ 48dp
 │ └────┘  ╰──────────────────────────────────────────╯  └────┘   │ │
 │                                                                │ ┴ 8dp
 └────────────────────────────────────────────────────────────────┘
   switch            microphone pill                     backspace
   48×48dp           48dp tall                           48×48dp
```

Total height: 63 dp.

| Part | Tap | Hold |
|---|---|---|
| **Switch** (left) | Back to the previous keyboard. Stops and discards a recording in progress | The system's keyboard picker |
| **Microphone pill** (middle) | The primary action of the current state (see below) | — |
| **Right button** | Backspace. While listening, or with a recording waiting for Retry: discard it | Backspace repeats |

The pill's icon is the VoxCtrl app icon. Its right-hand end shows the server's
name with a status dot: green when the last check succeeded, amber when it
failed, grey before the first answer.

## States

| State | Pill shows | Tapping the pill | Right button |
|---|---|---|---|
| **Set up** | Greyed icon, "Set up your server", `Open` | Opens the app's settings | Backspace |
| **Needs microphone** | Greyed icon, "Allow microphone", `Open` | Opens the settings and asks for the permission | Backspace |
| **Idle** | Icon, "Tap to speak", `● server` | Starts recording | Backspace |
| **Listening** | Stop square, moving waveform, timer; cyan outline | Stops and sends the recording | **Discard** |
| **Finishing** | Spinner, "Finishing up" | Nothing (disabled) | Backspace |
| **Unreachable** | Offline glyph, "Can't reach *server*", `Retry`; dashed outline | Sends the held recording again, or re-checks the server if none is held | **Discard** if a recording is held, otherwise backspace |
| **Problem** | Greyed icon, "Check server settings", `Open` | Opens the settings | Backspace |
| **Notice** | A short message ("Didn't catch that", "Microphone is in use") for 1.6 s | Nothing | Backspace |

Transitions worth knowing:

- A recording shorter than a third of a second is treated as a mis-tap and
  dropped without contacting the server.
- A recording stops by itself at five minutes and is sent.
- If the keyboard is hidden for any reason (you leave the app, switch
  keyboards, the screen locks), recording stops at once and anything not yet
  typed is dropped: a late answer is never typed into a different field. Live
  text already on screen stays.
- The server is checked when the keyboard appears, at most every 15 seconds
  while it is known to be up.

## Live text

With **Show words as I speak** on (the default), the text field shows what you
are saying while you say it, underlined, the way a typing keyboard shows a word
being composed. Each update replaces the whole underlined passage, so earlier
words can still be corrected as the model hears more. When you stop, the final
transcript replaces it and the underline goes.

Spacing follows the text already in the field: a space is added before the
dictation when it follows a word, and none at the start of a field or line,
after an opening bracket or quote, or when the dictation starts with
punctuation.

Limits: nothing is sent for the first 0.8 s, a new clip needs 0.6 s of new
audio, one request is in flight at a time, and live text stops updating after
45 s of continuous recording (the final transcript is unaffected).

## Layout rules

The row never changes its order or its height. What changes with the window's
width is the pill.

| Window | Rule |
|---|---|
| Narrower than 600 dp (**compact**) | The pill stretches between the two buttons |
| 600 dp or wider (**wide**) | The two buttons stay at the edges, under the thumbs. The pill is at most 420 dp wide and sits in the centre, or at the left or right if **Microphone position on wide screens** says so |
| Wide, with a vertical fold that is **bent** | The pill sits wholly on one half, 12 dp clear of the fold: the right half, or the left if the position setting is Left |
| Narrower than 360 dp | The server name is dropped from the pill so "Tap to speak" fits |

Other cases:

- **Landscape.** The keyboard never goes full-screen (`onEvaluateFullscreenMode`
  returns false), so the app stays visible above the one row.
- **Horizontal fold (tabletop posture).** Nothing special is needed. Android
  places the keyboard window on the lower half, and the bar is a single row.
- **A flat fold.** The pill may sit across it. A flat fold is an ordinary
  touch surface; only a bent one is avoided.
- **Split screen and pop-up windows.** The keyboard spans the display, not the
  app's window, so the rules above apply to the display width.

These rules are one pure function, `BarLayout.compute(widthDp, preferred,
fold)`, with a unit test for each row of the tables on this page.

## Galaxy Z Fold 8

The Fold 8 has a 5.5-inch cover screen (1,248 × 1,972) and a 7.6-inch inner
screen (1,848 × 2,448), both wider than earlier Folds. At a density of 3.0
those are about 416 × 657 dp and 616 × 816 dp. The density is an assumption
from the published resolutions and pixel densities, not a measurement, and
Samsung's **Screen zoom** setting changes it; the layout depends only on the
600 dp and 360 dp thresholds, so a different density moves a case between rows
of this table without breaking any.

| How it is held | Width | Layout |
|---|---|---|
| Closed, upright (cover screen) | ~416 dp | Compact: the pill fills the row |
| Closed, sideways (cover screen) | ~657 dp | Wide: buttons at the edges, 420 dp pill centred |
| Open flat, upright | ~616 dp | Wide: pill centred, across the flat fold |
| Open flat, sideways | ~816 dp | Wide: pill centred |
| Half open like a book (vertical fold) | ~616 or ~816 dp | Pill on the right half (or left, by setting), clear of the hinge |
| Half open like a laptop (tabletop, horizontal fold) | ~616 or ~816 dp | As open flat; the keyboard is on the lower half |
| Unfolding or folding while the keyboard is up | changes | The bar is rebuilt for the new width and fold. A recording in progress is meant to carry on across the change; this is the least certain case until it is tried on the device |

`BarLayoutTest` runs each of these. **None of it has been checked on a real
Fold 8 yet.** Two things in particular need a look on the device, because they
depend on what the phone reports rather than on this app's logic:

1. Whether the Fold 8 reports its fold and posture to a keyboard at all. If it
   does not, the book-posture rule never triggers and the pill stays centred,
   which is usable but sits on the hinge.
2. The bar's bottom edge against the gesture bar on both screens.

## Accessibility

- Every control is a real button with a spoken description that changes with
  the state ("Start dictation", "Stop and insert text", "Discard this
  recording").
- Touch targets are 48 × 48 dp or larger.
- State is never carried by colour alone: each state has its own glyph and
  wording, and the status dot is accompanied by the pill's text when the
  server is unreachable.
- Text is sized in sp. The row is 48 dp tall inside, which holds 15 sp text up
  to a font scale of about 2.
- The waveform is decorative and hidden from screen readers; the timer carries
  the same information.

## Where this lives in the code

| | |
|---|---|
| `app/src/main/res/layout/keyboard_bar.xml` | The row and its views |
| `app/.../KeyboardBar.kt` | `BarState`, showing a state, applying the layout |
| `app/.../WaveformView.kt` | The waveform |
| `app/.../FoldWatcher.kt` | Fold and posture, via `androidx.window` |
| `core/.../BarLayout.kt` | The layout rules |
| `core/src/test/.../BarLayoutTest.kt` | The Fold 8 cases |
| `app/src/main/res/values/dimens.xml` | Sizes. `bar_button`, `bar_gap` and `bar_padding_side` must add up to `BarLayout.SIDE_DP` |
