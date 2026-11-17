package ai.voxctrl.keys.core

import ai.voxctrl.keys.core.KeyboardLayout.Page
import ai.voxctrl.keys.core.SwipeDecoder.Point
import ai.voxctrl.keys.core.Typist.Shift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A text field that behaves like Android's: composing text is replaced, committed or left in place. */
class FakeField : Typist.Editor {
    private val done = StringBuilder()
    var composing = ""
        private set
    var enters = 0

    /** What the field shows. */
    val text get() = done.toString() + composing

    override fun textBeforeCursor(n: Int): CharSequence = text.takeLast(n)

    override fun setComposing(text: String) {
        composing = text
    }

    override fun commit(text: String) {
        composing = ""
        done.append(text)
    }

    override fun finishComposing() {
        done.append(composing)
        composing = ""
    }

    override fun deleteBefore() {
        if (done.isNotEmpty()) done.setLength(done.length - 1)
    }

    override fun enter() {
        enters++
        done.append('\n')
    }

    /** Sentence case, as TextUtils.getCapsMode reports it. */
    override fun capsAtCursor(): Boolean {
        val trimmed = text.trimEnd(' ')
        return trimmed.isEmpty() || trimmed.last() in ".!?\n" && text.endsWith(" ") || text.endsWith("\n")
    }
}

class TypistTest {
    private val field = FakeField()
    private val typist = Typist(SwipeTesting.words, field).apply { startField(wordMode = true, autoCaps = true) }
    private val keys = KeyboardLayout.keys(Page.LETTERS, 412f)

    private fun type(s: String) {
        for (c in s) {
            when (c) {
                ' ' -> typist.tapSpace()
                else -> typist.tapCharacter(c.toString())
            }
        }
    }

    private fun swipe(word: String) =
        typist.swipe(word.map { c -> keys.keyFor(c)!!.let { Point(it.centreX, it.centreY) } }, keys)

    @Test
    fun aFieldStartsWithACapital() {
        assertEquals(Shift.ONCE, typist.shift)
        type("hi there")
        assertEquals("Hi there", field.text)
        assertEquals(Shift.OFF, typist.shift)
    }

    @Test
    fun aNewSentenceStartsWithACapital() {
        type("ok. so")
        assertEquals("Ok. So", field.text)
    }

    @Test
    fun noCapitalsWhenTheFieldDoesNotAskForThem() {
        typist.startField(wordMode = true, autoCaps = false)
        type("hi")
        assertEquals("hi", field.text)
    }

    @Test
    fun theTypedWordIsComposedAndSuggested() {
        type("hel")
        assertEquals("Hel", field.composing)
        assertEquals("Hel", typist.suggestions.first())
        assertTrue(typist.suggestions.drop(1).all { it.startsWith("Hel") && it.length > 3 })
    }

    @Test
    fun pickingASuggestionTypesItWithASpace() {
        typist.startField(wordMode = true, autoCaps = false)
        type("hel")
        val word = typist.suggestions[1]
        typist.pickSuggestion(1)
        assertEquals("$word ", field.text)
        assertFalse(typist.isComposing)
    }

    @Test
    fun punctuationAfterAPickedWordTakesTheSpacesPlace() {
        typist.startField(wordMode = true, autoCaps = false)
        type("hel")
        val word = typist.suggestions[1]
        typist.pickSuggestion(1)
        typist.tapCharacter(".")
        assertEquals("$word. ", field.text)
    }

    @Test
    fun deleteRemovesOneTypedLetterThenOrdinaryText() {
        typist.startField(wordMode = true, autoCaps = false)
        type("ab cd")
        typist.tapDelete()
        assertEquals("ab c", field.text)
        assertEquals("c", field.composing)
        typist.tapDelete()
        typist.tapDelete()
        assertEquals("ab", field.text)
        assertFalse(typist.isComposing)
    }

    @Test
    fun shiftOnceThenOffAndADoubleTapLocks() {
        typist.startField(wordMode = true, autoCaps = false)
        typist.tapShift(1_000)
        assertEquals(Shift.ONCE, typist.shift)
        type("ab")
        assertEquals("Ab", field.text)
        typist.tapShift(5_000)
        typist.tapShift(5_200)
        assertEquals(Shift.LOCKED, typist.shift)
        type(" cd")
        assertEquals("Ab CD", field.text)
        typist.tapShift(9_000)
        assertEquals(Shift.OFF, typist.shift)
        // Two taps far apart: on, then off.
        typist.tapShift(10_000)
        typist.tapShift(11_000)
        assertEquals(Shift.OFF, typist.shift)
    }

    @Test
    fun aSwipedWordIsComposedAndTheNextOneGetsASpace() {
        assertTrue(swipe("hello"))
        assertEquals("Hello", field.text)
        assertTrue(swipe("there"))
        assertEquals("Hello there", field.text)
        assertEquals("there", field.composing)
    }

    @Test
    fun aSwipeAfterTextAddsASpaceOnlyWhenNeeded() {
        typist.startField(wordMode = true, autoCaps = false)
        type("say ")
        swipe("hello")
        assertEquals("say hello", field.text)
        typist.tapCharacter("(")
        swipe("hello")
        assertEquals("say hello(hello", field.text)
    }

    @Test
    fun suggestionsSwapTheSwipedWord() {
        typist.startField(wordMode = true, autoCaps = false)
        swipe("hello")
        val other = typist.suggestions[1]
        typist.pickSuggestion(1)
        assertEquals(other, field.text)
        assertEquals(other, field.composing)
    }

    @Test
    fun deleteRemovesAWholeSwipedWord() {
        typist.startField(wordMode = true, autoCaps = false)
        type("say ")
        swipe("hello")
        typist.tapDelete()
        assertEquals("say ", field.text)
    }

    @Test
    fun tappingALetterAfterASwipeStartsANewWord() {
        typist.startField(wordMode = true, autoCaps = false)
        swipe("hello")
        type("x")
        assertEquals("hello x", field.text)
    }

    @Test
    fun punctuationAfterASwipeSticksToTheWord() {
        typist.startField(wordMode = true, autoCaps = false)
        swipe("hello")
        typist.tapCharacter("!")
        assertEquals("hello!", field.text)
    }

    @Test
    fun enterFinishesTheWordFirst() {
        typist.startField(wordMode = true, autoCaps = false)
        type("hi")
        typist.tapEnter()
        assertEquals("hi\n", field.text)
        assertEquals(1, field.enters)
    }

    @Test
    fun passwordFieldsTypeLettersStraightInWithNoSwipe() {
        typist.startField(wordMode = false, autoCaps = true)
        assertEquals(Shift.OFF, typist.shift)
        type("abc")
        assertEquals("abc", field.text)
        assertEquals("", field.composing)
        assertTrue(typist.suggestions.isEmpty())
        assertFalse(typist.canSwipe)
        assertFalse(swipe("hello"))
    }

    @Test
    fun symbolsAreTypedAtOnceAndDoNotSwipe() {
        typist.startField(wordMode = true, autoCaps = false)
        typist.changePage(Page.SYMBOLS)
        assertFalse(typist.canSwipe)
        type("12")
        assertEquals("12", field.text)
        assertEquals("", field.composing)
    }

    @Test
    fun finishingLeavesTheWordAndForgettingDropsIt() {
        typist.startField(wordMode = true, autoCaps = false)
        type("hi")
        typist.finishComposing()
        assertEquals("hi", field.text)
        assertEquals("", field.composing)
        type("yo")
        typist.forgetComposing()
        assertFalse(typist.isComposing)
        assertTrue(typist.suggestions.isEmpty())
    }

    @Test
    fun withoutAWordListLettersStillType() {
        val bare = FakeField()
        val plain = Typist(null, bare).apply { startField(wordMode = true, autoCaps = false) }
        plain.tapCharacter("a")
        plain.tapSpace()
        assertEquals("a ", bare.text)
        assertFalse(plain.canSwipe)
        plain.useWords(SwipeTesting.words)
        assertTrue(plain.canSwipe)
    }
}
