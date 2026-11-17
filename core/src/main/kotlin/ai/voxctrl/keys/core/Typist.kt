package ai.voxctrl.keys.core

import ai.voxctrl.keys.core.KeyboardLayout.Page

/**
 * What the typing keyboard does with each tap, swipe and suggestion: the
 * word being composed, shift, the page of keys and the suggestions strip.
 * It talks to the text field only through [Editor], so every rule here is
 * tested on the JVM against a fake field (see TypistTest).
 *
 * The word being typed is shown as composing (underlined) text until it is
 * finished, so a suggestion can replace it. Nothing typed is kept, learned
 * or sent anywhere.
 */
class Typist(words: WordList?, private val editor: Editor) {
    /** The text field, as the typing keyboard needs it. */
    interface Editor {
        /** Up to [n] characters before the cursor; empty or null at the start of the field or when unknown. */
        fun textBeforeCursor(n: Int): CharSequence?

        /** Shows [text] as the composing (underlined) text, replacing any. */
        fun setComposing(text: String)

        /** Types [text] at the cursor, replacing the composing text if there is any. */
        fun commit(text: String)

        /** Leaves the composing text in place as ordinary text. */
        fun finishComposing()

        /** Deletes the character before the cursor, or the selection: a Delete key press. */
        fun deleteBefore()

        /** The Enter key: a new line, or the field's action (Send, Search, Go). */
        fun enter()

        /** True when the field asks for a capital at the cursor (start of a sentence). */
        fun capsAtCursor(): Boolean

        /** Runs [edits] as one change to the field, so it reports one cursor move, not several. */
        fun batch(edits: () -> Unit) = edits()
    }

    enum class Shift { OFF, ONCE, LOCKED }

    private enum class Source { NONE, TYPED, SWIPED }

    private var words: WordList? = words
    private var decoder: SwipeDecoder? = words?.let { SwipeDecoder(it) }

    /** The word list, once it has been read: until then letters type but there is no swipe or suggestions. */
    fun useWords(list: WordList) {
        words = list
        decoder = SwipeDecoder(list)
        onChange()
    }

    var page: Page = Page.LETTERS
        private set
    var shift: Shift = Shift.OFF
        private set

    /** Alternatives for the word being composed: [0] is the one shown in the field. */
    var suggestions: List<String> = emptyList()
        private set

    /** Called after anything the keyboard draws has changed. */
    var onChange: () -> Unit = {}

    /** False in password and no-suggestion fields: letters are typed as they are, with no swipe or suggestions. */
    var wordMode: Boolean = true
        private set
    private var autoCaps: Boolean = true

    private var composing = ""
    private var source = Source.NONE

    /** A space was typed after a picked suggestion; punctuation takes its place. */
    private var autoSpace = false
    private var lastShiftTap = Long.MIN_VALUE / 2

    val canSwipe get() = wordMode && decoder != null && page == Page.LETTERS

    /** A new field: forget everything and take the field's preferences. */
    fun startField(wordMode: Boolean, autoCaps: Boolean, page: Page = Page.LETTERS) {
        this.wordMode = wordMode
        this.autoCaps = autoCaps
        this.page = page
        composing = ""
        source = Source.NONE
        autoSpace = false
        suggestions = emptyList()
        shift = Shift.OFF
        refreshCaps()
        onChange()
    }

    fun changePage(target: Page) {
        page = target
        if (target == Page.LETTERS) refreshCaps()
        onChange()
    }

    fun tapShift(nowMs: Long) {
        shift = when (shift) {
            Shift.OFF -> Shift.ONCE
            Shift.ONCE -> if (nowMs - lastShiftTap < DOUBLE_TAP_MS) Shift.LOCKED else Shift.OFF
            Shift.LOCKED -> Shift.OFF
        }
        lastShiftTap = nowMs
        onChange()
    }

    fun tapCharacter(text: String) {
        editor.batch {
            val letter = text.length == 1 && text[0].isLetter()
            if (letter && wordMode && page == Page.LETTERS) {
                if (source == Source.SWIPED) {
                    editor.commit(composing)
                    editor.commit(" ")
                    clearComposing()
                }
                composing += applyShift(text)
                source = Source.TYPED
                autoSpace = false
                editor.setComposing(composing)
                updateTypedSuggestions()
                if (shift == Shift.ONCE) shift = Shift.OFF
                onChange()
                return@batch
            }
            commitComposing()
            val typed = if (letter) applyShift(text) else text
            if (autoSpace && text in CLOSING_PUNCTUATION) {
                // "word " + "." becomes "word. "
                editor.deleteBefore()
                editor.commit("$typed ")
            } else {
                editor.commit(typed)
            }
            autoSpace = false
            if (letter && shift == Shift.ONCE) shift = Shift.OFF
            if (text in SENTENCE_END) refreshCaps()
            onChange()
        }
    }

    fun tapSpace() {
        editor.batch {
            commitComposing()
            editor.commit(" ")
            autoSpace = false
            refreshCaps()
            onChange()
        }
    }

    fun tapEnter() {
        editor.batch {
            commitComposing()
            autoSpace = false
            editor.enter()
            refreshCaps()
            onChange()
        }
    }

    fun tapDelete() {
        editor.batch {
            when (source) {
                Source.SWIPED -> {
                    // A swiped word goes in one press, as it came in one stroke.
                    editor.setComposing("")
                    editor.finishComposing()
                    clearComposing()
                }
                Source.TYPED -> {
                    composing = composing.dropLast(1)
                    if (composing.isEmpty()) {
                        editor.setComposing("")
                        editor.finishComposing()
                        clearComposing()
                    } else {
                        editor.setComposing(composing)
                        updateTypedSuggestions()
                    }
                }
                Source.NONE -> editor.deleteBefore()
            }
            autoSpace = false
            if (source == Source.NONE) refreshCaps()
            onChange()
        }
    }

    /**
     * A swipe across the letter keys. The best word is shown as composing
     * text and the next best are offered as suggestions. Returns false when
     * no word fits, and nothing is typed.
     */
    fun swipe(path: List<SwipeDecoder.Point>, keys: KeyboardLayout.Keys): Boolean {
        val decoder = decoder ?: return false
        if (!canSwipe) return false
        val found = decoder.decode(path, keys, limit = SUGGESTIONS)
        if (found.isEmpty()) return false
        editor.batch {
            commitComposing()
            if (TextJoiner.needsSpace(editor.textBeforeCursor(1), found.first().entry.word)) editor.commit(" ")
            suggestions = found.map { applyShift(it.entry.word) }
            composing = suggestions.first()
            source = Source.SWIPED
            autoSpace = false
            editor.setComposing(composing)
            if (shift == Shift.ONCE) shift = Shift.OFF
            onChange()
        }
        return true
    }

    /** A tap on the suggestions strip. */
    fun pickSuggestion(index: Int) {
        editor.batch {
            val word = suggestions.getOrNull(index) ?: return@batch
            when (source) {
                Source.SWIPED -> {
                    // Swap the swiped word for another reading; a later swipe or tap adds the space.
                    composing = word
                    editor.setComposing(word)
                }
                Source.TYPED -> {
                    editor.commit("$word ")
                    clearComposing()
                    autoSpace = true
                    refreshCaps()
                }
                Source.NONE -> return@batch
            }
            onChange()
        }
    }

    /** Leaves any composing word in the field as it is: before a dictation, or when the field changes. */
    fun finishComposing() {
        if (composing.isEmpty() && source == Source.NONE) return
        editor.finishComposing()
        clearComposing()
        autoSpace = false
        onChange()
    }

    /** The user moved the cursor away: the composing word is no longer ours to change. */
    fun forgetComposing() {
        if (composing.isEmpty() && source == Source.NONE && !autoSpace) return
        clearComposing()
        autoSpace = false
        onChange()
    }

    val isComposing get() = source != Source.NONE

    private fun commitComposing() {
        if (source == Source.NONE) return
        editor.finishComposing()
        clearComposing()
    }

    private fun clearComposing() {
        composing = ""
        source = Source.NONE
        suggestions = emptyList()
    }

    private fun updateTypedSuggestions() {
        val list = words ?: return
        val capital = composing.firstOrNull()?.isUpperCase() == true
        val completions = list.completions(composing, SUGGESTIONS).map { entry ->
            if (capital) entry.word.replaceFirstChar { it.uppercaseChar() } else entry.word
        }
        suggestions = (listOf(composing) + completions).distinct().take(SUGGESTIONS)
    }

    private fun applyShift(text: String) = when (shift) {
        Shift.OFF -> text
        Shift.ONCE -> text.replaceFirstChar { it.uppercaseChar() }
        Shift.LOCKED -> text.uppercase()
    }

    private fun refreshCaps() {
        if (shift == Shift.LOCKED) return
        shift = if (wordMode && autoCaps && page == Page.LETTERS && editor.capsAtCursor()) Shift.ONCE else Shift.OFF
    }

    companion object {
        const val SUGGESTIONS = 3
        const val DOUBLE_TAP_MS = 400L
        private const val CLOSING_PUNCTUATION = ".,!?;:"
        private const val SENTENCE_END = ".!?"
    }
}
