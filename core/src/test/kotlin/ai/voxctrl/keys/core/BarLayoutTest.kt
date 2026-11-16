package ai.voxctrl.keys.core

import ai.voxctrl.keys.core.BarLayout.Position
import ai.voxctrl.keys.core.BarLayout.VerticalFold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bar on a Galaxy Z Fold 8, in every way it can be held.
 *
 * The widths are the published panel sizes (cover 1,248 x 1,972, inner
 * 1,848 x 2,448) at a density of 3.0. The device's "screen zoom" setting
 * changes the density, so the tests also sweep a range of widths: the policy
 * depends on thresholds, never on one device's exact numbers.
 */
class BarLayoutTest {
    private val coverPortrait = 416
    private val coverLandscape = 657
    private val innerPortrait = 616
    private val innerLandscape = 816

    @Test
    fun coverScreenUprightIsCompact() {
        val bar = BarLayout.compute(coverPortrait)
        assertTrue(bar.pillFills)
        assertTrue(bar.showServerName)
    }

    @Test
    fun coverScreenSidewaysKeepsButtonsAtTheEdgesAndCentresThePill() {
        val bar = BarLayout.compute(coverLandscape)
        assertFalse(bar.pillFills)
        assertEquals(BarLayout.PILL_MAX_DP, bar.pillWidthDp)
        assertEquals(0.5f, bar.leftSpaceShare)
    }

    @Test
    fun innerScreenFlatCentresThePillInBothOrientations() {
        for (width in listOf(innerPortrait, innerLandscape)) {
            val bar = BarLayout.compute(width, fold = VerticalFold(width / 2, width / 2, bent = false))
            assertFalse(bar.pillFills)
            assertEquals(BarLayout.PILL_MAX_DP, bar.pillWidthDp)
            assertEquals(0.5f, bar.leftSpaceShare)
        }
    }

    @Test
    fun innerScreenFlatFollowsTheChosenSide() {
        assertEquals(0f, BarLayout.compute(innerLandscape, Position.LEFT).leftSpaceShare)
        assertEquals(1f, BarLayout.compute(innerLandscape, Position.RIGHT).leftSpaceShare)
    }

    @Test
    fun bookPostureKeepsThePillOffTheHinge() {
        for (width in listOf(innerPortrait, innerLandscape)) {
            val fold = VerticalFold(width / 2, width / 2, bent = true)
            for (preferred in Position.values()) {
                val bar = BarLayout.compute(width, preferred, fold)
                assertFalse(bar.pillFills)
                val free = width - 2 * BarLayout.SIDE_DP - bar.pillWidthDp
                val left = BarLayout.SIDE_DP + (free * bar.leftSpaceShare).toInt()
                val right = left + bar.pillWidthDp
                val clear = right <= fold.leftDp || left >= fold.rightDp
                assertTrue("pill $left..$right crosses the fold at ${fold.leftDp} ($width dp, $preferred)", clear)
                assertTrue(bar.pillWidthDp >= BarLayout.PILL_MIN_DP)
            }
        }
    }

    @Test
    fun bookPostureWithAHingeThatHasWidth() {
        val fold = VerticalFold(400, 416, bent = true)
        val right = BarLayout.compute(innerLandscape, Position.RIGHT, fold)
        assertEquals(1f, right.leftSpaceShare)
        assertTrue(innerLandscape - BarLayout.SIDE_DP - right.pillWidthDp >= fold.rightDp)
        val left = BarLayout.compute(innerLandscape, Position.LEFT, fold)
        assertEquals(0f, left.leftSpaceShare)
        assertTrue(BarLayout.SIDE_DP + left.pillWidthDp <= fold.leftDp)
    }

    @Test
    fun aNarrowWindowDropsTheServerName() {
        assertFalse(BarLayout.compute(320).showServerName)
        assertTrue(BarLayout.compute(360).showServerName)
    }

    @Test
    fun everyWidthGivesAPillThatFits() {
        for (width in 240..1400) {
            val bar = BarLayout.compute(width)
            if (!bar.pillFills) {
                assertTrue(bar.pillWidthDp in BarLayout.PILL_MIN_DP..BarLayout.PILL_MAX_DP)
                assertTrue(bar.pillWidthDp <= width - 2 * BarLayout.SIDE_DP)
            }
        }
    }
}
