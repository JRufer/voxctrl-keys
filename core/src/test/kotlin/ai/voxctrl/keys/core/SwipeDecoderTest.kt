package ai.voxctrl.keys.core

import ai.voxctrl.keys.core.KeyboardLayout.Page
import ai.voxctrl.keys.core.SwipeDecoder.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import kotlin.random.Random

class WordListTest {
    private val small = WordList.read(BufferedReader(StringReader("the\t77\nhello\t47\nhelp\t58\nI'm\t62\nits\t50\nit's\t60\nbad line\n")))

    @Test
    fun readsWordsAndFrequencies() {
        assertEquals(6, small.size)
        assertEquals("the", small.entries.first().word)
        assertEquals(4.7f, small.find("hello")!!.zipf, 0.001f)
    }

    @Test
    fun lettersIgnoreCaseAndApostrophes() {
        assertEquals("im", small.find("im")!!.letters)
        assertEquals("I'm", small.find("IM")!!.word)
        // Where two words share their letters, the more common one is found.
        assertEquals("it's", small.find("its")!!.word)
    }

    @Test
    fun completionsAreTheMostCommonLongerWords() {
        assertEquals(listOf("help", "hello"), small.completions("hel", 3).map { it.word })
        assertEquals(listOf("hello"), small.completions("hell", 3).map { it.word })
        assertTrue(small.completions("", 3).isEmpty())
    }

    @Test
    fun theBundledListIsWhole() {
        val words = SwipeTesting.words
        assertEquals(50_000, words.size)
        for (word in listOf("the", "I", "I'm", "don't", "hello", "Monday", "keyboard", "voice")) {
            assertNotNull(word, words.find(word))
        }
        assertEquals("I", words.find("i")!!.word)
    }
}

class SwipeDecoderTest {
    private val keys = KeyboardLayout.keys(Page.LETTERS, 412f)
    private val decoder = SwipeDecoder(SwipeTesting.words)

    private fun best(path: List<Point>, on: KeyboardLayout.Keys = keys) = decoder.decode(path, on).map { it.entry.word }

    private fun through(word: String, on: KeyboardLayout.Keys = keys): List<Point> =
        word.map { c -> on.keyFor(c)!!.let { Point(it.centreX, it.centreY) } }

    @Test
    fun aCleanSwipeThroughTheLettersIsTheWord() {
        for (word in listOf("hello", "the", "keyboard", "voice", "swipe", "typing", "quick", "brown", "fox", "jumps")) {
            assertEquals(word, best(through(word)).first())
        }
    }

    @Test
    fun aDoubledLetterIsOneKey() {
        assertEquals("hello", best(through("helo")).first())
        assertEquals("letter", best(through("leter")).first())
    }

    @Test
    fun contractionsAndCapitalsComeOutAsTheyAreWritten() {
        assertTrue(best(through("dont")).contains("don't"))
        assertTrue(best(through("im")).contains("I'm"))
    }

    @Test
    fun theSameSwipeDecodesTheSameOnANarrowOrAWideKeyboard() {
        val wide = KeyboardLayout.keys(Page.LETTERS, 440f, KeyboardLayout.ROW_SHORT_DP)
        assertEquals("keyboard", best(through("keyboard", wide), wide).first())
    }

    /**
     * Simulated sloppy swipes over the 1,500 most common words. A real
     * finger is not simulated by this, so it guards against regressions in
     * the decoder rather than promising an accuracy on a phone.
     */
    @Test
    fun commonWordsSurviveASloppyFinger() {
        val sample = SwipeTesting.words.entries.filter { it.letters.length >= 2 }.take(1500).filterIndexed { i, _ -> i % 3 == 0 }
        val random = Random(7)
        var first = 0
        var inStrip = 0
        for (entry in sample) {
            val found = best(SwipeTesting.swipe(entry.word, keys, random, wobble = 0.3f))
            if (found.firstOrNull() == entry.word) first++
            if (entry.word in found) inStrip++
        }
        assertTrue("first choice $first of ${sample.size}", first >= sample.size * 0.93)
        assertTrue("in the strip $inStrip of ${sample.size}", inStrip >= sample.size * 0.98)
    }

    @Test
    fun nothingPlausibleGivesNothing() {
        assertTrue(decoder.decode(listOf(Point(10f, 10f)), keys).isEmpty())
        // From q straight down off the keyboard: no word starts at q and ends at the space bar.
        assertTrue(decoder.decode(listOf(Point(20f, 26f), Point(20f, 400f)), keys).isEmpty())
    }

    @Test
    fun resamplingSpacesPointsEvenly() {
        val points = SwipeDecoder.resample(listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f)), 5)
        assertEquals(5, points.size)
        assertEquals(Point(0f, 0f), points.first())
        assertEquals(Point(10f, 10f), points.last())
        assertEquals(5f, points[1].x, 0.001f)
        assertEquals(0f, points[2].y, 0.001f)
    }
}
