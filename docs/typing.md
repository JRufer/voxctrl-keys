# The Typing Keyboard

Under the voice bar is a full QWERTY keyboard with swipe typing, hidden until
you ask for it. This page describes how it types; where it sits on the screen
is in [The Keyboard Bar](./ui.md#the-typing-keyboard).

**Contents**

1. [Showing and hiding it](#showing-and-hiding-it)
2. [Keys and pages](#keys-and-pages)
3. [Tapping](#tapping)
4. [Swiping](#swiping)
5. [Suggestions](#suggestions)
6. [Fields that are not for words](#fields-that-are-not-for-words)
7. [Typing and dictating together](#typing-and-dictating-together)
8. [The word list](#the-word-list)
9. [What is not there yet](#what-is-not-there-yet)
10. [Where this lives in the code](#where-this-lives-in-the-code)

---

## Showing and hiding it

- The keyboard **always opens collapsed**: the voice bar alone, as before.
- **Tap the keyboard button** (left end of the bar) to show the typing
  keyboard under the bar. The button turns cyan while it shows. Tap it again
  to hide it. The bar itself does not change either way.
- **Hold the keyboard button** for the system's keyboard picker.
- Whenever the keyboard is hidden (you leave the app, the screen locks, you
  switch keyboards) it is collapsed again, so the next time it opens it is the
  voice bar alone. Moving from one text field to another while it stays up
  leaves it as it is.

Going back to your previous keyboard, which a tap on the button used to do, is
the system's own switch-keyboard button in the navigation strip, or the
picker.

## Keys and pages

```
 q w e r t y u i o p        1 2 3 4 5 6 7 8 9 0        ~ ` | • √ π ÷ × ¶ ∆
  a s d f g h j k l         @ # $ _ & - + ( ) /        £ € ¥ ¢ ^ ° = { } <
 ⇧  z x c v b n m  ⌫       =\<  * " ' : ; ! ?  ⌫      ?123 \ % © ® [ ] >  ⌫
 ?123 , [ space ] . ↵       ABC , [ space ] . ↵        ABC , [ space ] . ↵
     letters                     symbols                   more symbols
```

Each page is four rows on a grid ten letter-keys wide. Every touch lands on a
key: the gaps between keys belong to the nearest key.

| Key | Tap | Hold |
|---|---|---|
| Letter | Types it | Starts a swipe if the finger moves |
| ⇧ Shift | Next letter capital. Tap twice quickly (within 0.4 s) for caps lock; tap again to turn it off | — |
| ⌫ Delete | Deletes a letter, or a whole swiped word | Repeats |
| Space | Finishes the word and types a space | — |
| ↵ Enter | Finishes the word, then a new line or the field's own action (Send, Search, Go) | — |
| ?123 / ABC / =\\< | Changes page | — |

The ⇧ key shows its state: an outline when off, filled cyan for the next
letter, filled with a bar under it for caps lock.

## Tapping

- **The word being typed is underlined** (composing text) until you finish it
  with space, punctuation, Enter or a suggestion. Backspace takes one letter
  off it.
- **Capitals at the start of a sentence** come on by themselves where the field
  asks for them (most message and note fields do); the field decides, through
  Android's `getCursorCapsMode`.
- **No autocorrect.** Space types exactly what you tapped. To take a
  correction or completion, tap it in the suggestions strip.
- **A picked suggestion is followed by a space**, and a full stop, comma, `?`,
  `!`, `;` or `:` typed straight after it takes that space's place: picking
  "hello" then tapping `.` gives "hello. ".
- **Two thumbs:** a second finger going down finishes the first as a tap, so
  fast alternating thumbs do not lose letters.

## Swiping

Put a finger on the first letter of a word, slide through the others without
lifting, and lift on the last. The swipe draws a cyan trail.

- A finger counts as swiping once it has moved about half a key's width from
  where it went down, and only if it went down on a letter.
- **The best word is typed underlined**, with a space before it when it follows
  a word. The next best are in the suggestions strip; tapping one swaps it in.
- **The next swipe, or a tapped letter, adds the space** between words. A
  punctuation mark does not, so "hello" then `!` gives "hello!".
- **Delete removes the whole swiped word** in one press, as it came in one
  stroke.
- **Shift before a swipe** capitalises the word; caps lock writes it in
  capitals.
- Doubled letters ("ll" in "hello") need nothing special: the finger is on the
  same key.

**How words are found.** Every word has an ideal path, the line through the
centres of its letters' keys. The swipe and each candidate's path are
resampled to 48 evenly spaced points and compared point for point; the closest
path wins, weighed against how common the word is. A word is only considered
if it starts near where the finger went down, ends near where it lifted, and
each of its letters lies within a key's width of the swipe. Distances are in
key widths, so a swipe decodes the same on a phone's keyboard and on the
narrower block of a wide screen. Decoding takes about a millisecond on a
desktop JVM.

The weights were tuned on simulated swipes (`SwipeDecoderTest`): straight
lines through each letter, missing its centre by up to a third of a key. On
the 1,500 most common words, the intended word comes first in about 97% of
them and is in the strip in all but a handful. **A real finger is sloppier
than that simulation**, cutting corners and overshooting, and the decoder has
not yet been tried on a phone; the numbers are a regression guard, not a
promise.

## Suggestions

The strip above the keys has three places. The middle one is the word that is
in the field now; the other two are alternatives.

| While | Middle | Left and right | Tapping one |
|---|---|---|---|
| Typing a word | What you typed | The two most common longer words starting with it | Types it and a space |
| After a swipe | The word that was typed | The next two readings of the swipe | Swaps it into the field (still underlined) |
| Otherwise | empty | empty | — |

Suggestions come only from the bundled word list. Nothing you type is added to
it, remembered or learned from.

## Fields that are not for words

| Field | Typing keyboard |
|---|---|
| Passwords (all kinds), email addresses, web addresses, fields that ask for no suggestions | Letters type as they are: no underline, no swipe, no suggestions, no automatic capitals |
| Numbers, phone numbers, dates and times | Opens on the symbols page, which has the digits |

## Typing and dictating together

- Starting a dictation finishes the word being typed as it is, and the
  dictation follows it.
- While a dictation is recording or being sent, and while a recording is held
  for Retry, the keys are dimmed and do nothing: the dictation's live text is
  in the field as underlined text, and typing would overwrite it. Finish,
  Retry or discard the dictation, and the keys come back.
- The bar's own backspace button (right end) works like the ⌫ key, and still
  becomes **Discard** while a dictation is held.

## The word list

`app/src/main/assets/words_en.txt`: the 50,000 most common English words, one
`word<TAB>frequency` per line, read into memory (a few MB) on a background
thread when the keyboard service starts. Until it has been read, letters still
type, without swipe or suggestions.

It is built by `scripts/build-wordlist.py` from two sources:

- **[wordfreq](https://github.com/rspeer/wordfreq) 3.1.1** by Robyn Speer, for
  the words and how common each is. Its data is licensed
  [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), and so is
  `words_en.txt`, which is derived from it. It in turn draws on Wikipedia,
  subtitles, news, books (Google Books Ngrams), the web and Twitter word
  counts; see wordfreq's README for the full credits.
- **[SCOWL](http://wordlist.aspell.net/)** by Kevin Atkinson (through the
  `wordlist-english` npm package, 1.2.1), to keep only real words: a word is
  kept if SCOWL lists it, if it is a contraction of words it lists ("don't",
  "I'm"), or if it is very common anyway (names like "london", "okay"). SCOWL's
  licence permits this use provided its copyright notice goes with it.

Both credits, the CC BY-SA 4.0 terms of `words_en.txt` and SCOWL's notice are
in `app/src/main/assets/words_en.LICENSE.txt`, which is packaged in the APK
next to the list. The rest of the repository stays under its MIT licence.

Words are stored in lower case except "I" and its contractions, days, months
and a few others. Proper names come out in lower case; Shift before the swipe
capitalises them. The list contains whatever common English contains,
including swear words; nothing is filtered out.

Rebuild it with the commands at the top of the script. The core tests read
the committed list, so a change to it is tested like a change to the code.

## What is not there yet

- Languages other than English, and layouts other than QWERTY.
- Autocorrect on space, next-word prediction, and learning new words.
- Long-press for accented letters, a key preview pop-up, emoji.
- Emptying the field with a swipe on Delete, or moving the cursor with the
  space bar.
- One-handed mode on a phone's narrow screen (only wide screens have a
  left-handed and right-handed placement).

## Where this lives in the code

| | |
|---|---|
| `core/.../KeyboardLayout.kt` | The pages, where each key is, hit-testing |
| `core/.../SwipeDecoder.kt` | Swipe to words |
| `core/.../WordList.kt` | Reading the word list, completions |
| `core/.../Typist.kt` | What each tap, swipe and suggestion does to the field; shift; pages |
| `core/.../KeyboardPlacement.kt` | Width and side of the keyboard; see [The Keyboard Bar](./ui.md#the-typing-keyboard) |
| `app/.../KeyboardView.kt` | Drawing the keys and the trail, touches, screen-reader support |
| `app/.../KeyboardBar.kt` | Showing and hiding, placing, the suggestions strip |
| `app/.../VoxKeyboardService.kt` | The toggle, the field (`FieldEditor`), field types, loading the list |
| `core/src/test/.../KeyboardLayoutTest.kt`, `SwipeDecoderTest.kt`, `TypistTest.kt` | The tests |
| `scripts/build-wordlist.py` | Builds the word list |
