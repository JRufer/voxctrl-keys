# The Keyboard Bar

The keyboard opens as one row, the voice bar. Its keyboard button shows a
full typing keyboard under the bar and hides it again. This page describes the
bar's parts, every state it can be in, where the typing keyboard goes, and how
both lay themselves out, including on a foldable. How the typing keyboard
types and swipes is in [The Typing Keyboard](./typing.md).

The design mockup is the "VoxCtrl voice keyboard for Android" canvas; the
colours and type follow the VoxCtrl design system (dark surfaces, cyan accent,
monospace for status).

**Contents**

1. [Anatomy](#anatomy)
2. [States](#states)
3. [Live text](#live-text)
4. [Layout rules](#layout-rules)
5. [The typing keyboard](#the-typing-keyboard)
6. [Galaxy Z Fold 8](#galaxy-z-fold-8)
7. [Accessibility](#accessibility)
8. [Where this lives in the code](#where-this-lives-in-the-code)

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
   keyboard          microphone pill                     backspace
   48×48dp           48dp tall                           48×48dp
```

Total height: 63 dp, plus the height of the system's navigation strip
beneath it. With the typing keyboard showing, add 44 dp of suggestions and
four rows of keys (52 dp each, or 42 dp on a screen less than 500 dp tall): about 320
dp in all on most phones.

**The strip under the row.** A keyboard's window reaches the bottom edge of
the screen. With gesture navigation, Android draws the gesture handle there
and, while a keyboard is showing, its own switch-keyboard button (bottom left)
and hide-keyboard button (bottom right). The bar pads itself by that inset, so
the row above is never under those buttons; the bar's background colour
continues behind them. The strip's height is the system's, not the bar's.

| Part | Tap | Hold |
|---|---|---|
| **Keyboard** (left) | Shows the typing keyboard under the bar, or hides it. Cyan while it shows. A dictation in progress carries on | The system's keyboard picker |
| **Microphone pill** (middle) | The primary action of the current state (see below) | — |
| **Right button** | Backspace (a letter of the word being typed, or a whole swiped word, like the typing keyboard's ⌫). While listening, or with a recording waiting for Retry: discard it | Backspace repeats |

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
| **Unreachable** | Offline glyph, "Can't reach *server*", `Retry`; dashed outline. "Server busy" instead when the server was up but had too many dictations waiting (HTTP 503) | Sends the held recording again, or re-checks the server if none is held | **Discard** if a recording is held, otherwise backspace |
| **Problem** | Greyed icon, "Check server settings", `Open`. "Update VoxCtrl Keys" when the server speaks a newer client contract | Opens the settings | Backspace |
| **Notice** | A short message ("Didn't catch that", "Microphone is in use") for 1.6 s | Nothing | Backspace |

Transitions worth knowing:

- A recording shorter than a third of a second is treated as a mis-tap and
  dropped without contacting the server.
- A recording stops by itself at five minutes and is sent.
- If the keyboard is hidden for any reason (you leave the app, switch
  keyboards, the screen locks), the typing keyboard is collapsed, and recording stops at once and anything not yet
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

- **System bars and cut-outs.** The bar is padded by the navigation bar and
  display cut-out insets: at the bottom always, and at the sides in landscape.
  Widths in the table above are measured inside that padding.
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

## The typing keyboard

**It always opens collapsed.** The keyboard button shows it; tapping the
button again, or the keyboard being hidden for any reason, collapses it. The
voice bar above it is the same whether it shows or not.

```
 ┌──────────────────────────────────────────────┐
 │ [⌨]  ( (◉) Tap to speak   ● homeserver )  [⌫] │  voice bar, unchanged
 ├──────────────────────────────────────────────┤
 │      help     │    hello    │     hero        │  suggestions, 44 dp
 │  q  w  e  r  t  y  u  i  o  p                 │
 │    a  s  d  f  g  h  j  k  l                  │  four rows of keys
 │  ⇧   z  x  c  v  b  n  m   ⌫                  │  52 dp each
 │  ?123  ,  [   English   ]  .  ↵               │
 └──────────────────────────────────────────────┘
```

Where it sits:

| Window | Rule |
|---|---|
| Narrower than 600 dp (**compact**) | Full width, like any phone keyboard. The hand setting does not apply |
| 600 dp or wider (**wide**) | A block 440 dp wide (about a phone keyboard), 10 dp from the edge of your hand's side: the right edge by default, the left edge with **Typing keyboard on wide screens** set to Left-handed. The other side is left empty |
| Wide, with a vertical fold that is **bent** | The block stays on your hand's half, 12 dp clear of the fold, as wide as that half allows up to 440 dp (never under 260 dp) |
| A screen less than 500 dp tall (a phone sideways) | Rows of 42 dp instead of 52, so the app keeps some of the screen |

The hand setting and **Microphone position on wide screens** are separate: a
left-handed user will usually want both on Left, so the pill and the keyboard
are both under the left thumb, next to the keyboard button.

These rules are `KeyboardPlacement.compute(widthDp, hand, fold)` and
`KeyboardLayout.rowHeightFor(screenHeightDp)`, tested in
`KeyboardLayoutTest`.

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

The typing keyboard on the same screens:

| How it is held | Typing keyboard |
|---|---|
| Closed, upright (cover screen, ~416 dp) | Full width |
| Closed sideways, or open flat (~616 to ~816 dp) | 440 dp block on the hand's side |
| Half open like a book | On the hand's half: about 385 dp wide at ~816 dp, about 285 dp at ~616 dp |
| Half open like a laptop | As open flat, on the lower half |

`BarLayoutTest` and `KeyboardPlacementTest` run each of these. On the device itself, so far:

| | Status |
|---|---|
| Cover screen, upright | **Seen working** (0.1.0): compact layout, server name and status dot shown, dictation typed into a text field |
| The row against the system's navigation strip | Overlapped in 0.1.0. Fixed in 0.1.1 by padding the bar by the inset; **the fix has not been seen on the device yet** |
| Cover screen sideways; inner screen, upright and sideways | Not yet seen |
| The typing keyboard (0.2.0), on any screen | Not yet seen. Rendered off-device from the app's own views for the 412 dp and 816 dp cases while it was built |
| Book and tabletop postures | Not yet seen. Still open: whether the Fold 8 reports its fold and posture to a keyboard at all. If it does not, the book-posture rule never triggers and the pill stays centred, which is usable but sits on the hinge |

## Accessibility

- Every control is a real button with a spoken description that changes with
  the state ("Start dictation", "Stop and insert text", "Discard this
  recording").
- The bar's touch targets are 48 × 48 dp or larger. The typing keyboard's keys
  are a tenth of its width (41 dp on a 412 dp phone, 44 dp on a wide screen)
  by 52 dp, like any QWERTY keyboard; the suggestions are 44 dp tall.
- Each key of the typing keyboard is a separate item for TalkBack, read out
  by name ("Shift on", "Delete", "Symbols"), and explore-by-touch then
  double-tap types it. Swiping is not available through TalkBack.
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
| `app/.../KeyboardBar.kt` | `BarState`, showing a state, applying the layout, padding for the system's insets, showing and placing the typing keyboard |
| `app/.../KeyboardView.kt` | The typing keyboard's keys; see [The Typing Keyboard](./typing.md#where-this-lives-in-the-code) |
| `app/.../WaveformView.kt` | The waveform |
| `app/.../FoldWatcher.kt` | Fold and posture, via `androidx.window` |
| `core/.../BarLayout.kt` | The bar's layout rules |
| `core/.../KeyboardPlacement.kt` | Where the typing keyboard goes |
| `core/src/test/.../BarLayoutTest.kt` | The Fold 8 cases |
| `app/src/main/res/values/dimens.xml` | Sizes. `bar_button`, `bar_gap` and `bar_padding_side` must add up to `BarLayout.SIDE_DP` |
