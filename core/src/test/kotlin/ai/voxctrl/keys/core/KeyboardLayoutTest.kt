package ai.voxctrl.keys.core

import ai.voxctrl.keys.core.KeyboardLayout.Kind
import ai.voxctrl.keys.core.KeyboardLayout.Page
import ai.voxctrl.keys.core.KeyboardPlacement.Hand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {
    @Test
    fun lettersAreQwertyInFourRows() {
        val keys = KeyboardLayout.keys(Page.LETTERS, 400f)
        val rows = keys.keys.groupBy { it.y }.toSortedMap().values.map { row -> row.sortedBy { it.x } }
        assertEquals(4, rows.size)
        assertEquals("qwertyuiop", rows[0].joinToString("") { it.text })
        assertEquals("asdfghjkl", rows[1].joinToString("") { it.text })
        assertEquals(listOf(Kind.SHIFT) + List(7) { Kind.CHARACTER } + Kind.DELETE, rows[2].map { it.kind })
        assertEquals(listOf(Kind.PAGE, Kind.CHARACTER, Kind.SPACE, Kind.CHARACTER, Kind.ENTER), rows[3].map { it.kind })
    }

    @Test
    fun everyPageTilesTheKeyboardWithNoDeadSpace() {
        for (page in Page.values()) {
            for (width in listOf(300f, 412f, 440f)) {
                val keys = KeyboardLayout.keys(page, width)
                var y = 1f
                while (y < keys.heightDp) {
                    var x = 0.5f
                    while (x < width) {
                        val key = keys.keyAt(x, y)
                        assertTrue("$page $width: ($x, $y) fell outside ${key.label}", key.contains(x, y))
                        x += 3f
                    }
                    y += 5f
                }
            }
        }
    }

    @Test
    fun touchesJustOutsideGoToTheNearestKey() {
        val keys = KeyboardLayout.keys(Page.LETTERS, 400f)
        assertEquals("q", keys.keyAt(-5f, 10f).text)
        assertEquals("p", keys.keyAt(405f, 10f).text)
        assertEquals(Kind.ENTER, keys.keyAt(399f, keys.heightDp + 10).kind)
    }

    @Test
    fun aLetterKeyIsATenthOfTheWidth() {
        val keys = KeyboardLayout.keys(Page.LETTERS, 440f, KeyboardLayout.ROW_SHORT_DP)
        assertEquals(44f, keys.unitDp, 0.01f)
        assertEquals(44f, keys.keyFor('q')!!.width, 0.01f)
        assertEquals(4 * KeyboardLayout.ROW_SHORT_DP, keys.heightDp, 0.01f)
    }

    @Test
    fun pagesLeadToEachOther() {
        fun targets(page: Page) = KeyboardLayout.keys(page, 400f).keys.mapNotNull { it.target }.toSet()
        assertEquals(setOf(Page.SYMBOLS), targets(Page.LETTERS))
        assertEquals(setOf(Page.LETTERS, Page.MORE_SYMBOLS), targets(Page.SYMBOLS))
        assertEquals(setOf(Page.LETTERS, Page.SYMBOLS), targets(Page.MORE_SYMBOLS))
        assertNotNull(KeyboardLayout.keys(Page.SYMBOLS, 400f).keyFor('7'))
    }

    @Test
    fun shortScreensGetShorterRows() {
        assertEquals(KeyboardLayout.ROW_SHORT_DP, KeyboardLayout.rowHeightFor(411))
        assertEquals(KeyboardLayout.ROW_DP, KeyboardLayout.rowHeightFor(816))
    }
}

/** Where the keyboard sits, on the Galaxy Z Fold 8's screens and postures (widths as in BarLayoutTest). */
class KeyboardPlacementTest {
    private val coverPortrait = 416
    private val coverLandscape = 657
    private val innerPortrait = 616
    private val innerLandscape = 816

    @Test
    fun aPhoneScreenGetsAFullWidthKeyboardWhicheverHand() {
        for (hand in Hand.values()) {
            assertEquals(KeyboardPlacement.Result(coverPortrait, 0), KeyboardPlacement.compute(coverPortrait, hand))
        }
    }

    @Test
    fun wideScreensDockTheKeyboardOnTheHandsSide() {
        for (width in listOf(coverLandscape, innerPortrait, innerLandscape)) {
            val right = KeyboardPlacement.compute(width, Hand.RIGHT)
            assertEquals(KeyboardPlacement.MAX_DP, right.widthDp)
            assertEquals(width - KeyboardPlacement.EDGE_DP, right.leftDp + right.widthDp)

            val left = KeyboardPlacement.compute(width, Hand.LEFT)
            assertEquals(KeyboardPlacement.MAX_DP, left.widthDp)
            assertEquals(KeyboardPlacement.EDGE_DP, left.leftDp)
        }
    }

    @Test
    fun aFlatFoldIsIgnored() {
        val flat = BarLayout.VerticalFold(innerLandscape / 2, innerLandscape / 2, bent = false)
        assertEquals(KeyboardPlacement.compute(innerLandscape, Hand.LEFT), KeyboardPlacement.compute(innerLandscape, Hand.LEFT, flat))
    }

    @Test
    fun bookPostureKeepsTheKeyboardOnTheHandsHalfClearOfTheHinge() {
        for (width in listOf(innerPortrait, innerLandscape)) {
            val fold = BarLayout.VerticalFold(width / 2 - 2, width / 2 + 2, bent = true)

            val left = KeyboardPlacement.compute(width, Hand.LEFT, fold)
            assertEquals(KeyboardPlacement.EDGE_DP, left.leftDp)
            assertTrue("$width left: ${left.widthDp}", left.leftDp + left.widthDp <= fold.leftDp - KeyboardPlacement.FOLD_CLEARANCE_DP)

            val right = KeyboardPlacement.compute(width, Hand.RIGHT, fold)
            assertTrue("$width right: ${right.leftDp}", right.leftDp >= fold.rightDp + KeyboardPlacement.FOLD_CLEARANCE_DP)
            assertEquals(width - KeyboardPlacement.EDGE_DP, right.leftDp + right.widthDp)
        }
    }

    @Test
    fun theKeyboardNeverLeavesTheWindow() {
        for (width in 200..1400 step 7) {
            for (hand in Hand.values()) {
                val place = KeyboardPlacement.compute(width, hand)
                assertTrue(place.leftDp >= 0 && place.leftDp + place.widthDp <= width)
                assertTrue(place.widthDp >= minOf(width, KeyboardPlacement.MIN_DP))
            }
        }
    }
}
