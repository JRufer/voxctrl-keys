package ai.voxctrl.keys.core

/**
 * Where the expanded typing keyboard sits under the voice bar.
 *
 * - **Compact** (narrower than [BarLayout.WIDE_DP]): the keyboard spans the
 *   window, like any phone keyboard.
 * - **Wide**: a full-width QWERTY keyboard is too wide for a thumb, so the
 *   keyboard is a block at most [MAX_DP] wide against the edge of the user's
 *   hand: the right edge for right-handed, the left edge for left-handed.
 * - **Wide with a bent vertical fold** (book posture): the block stays wholly
 *   on the hand's half, clear of the hinge, as the voice pill does.
 */
object KeyboardPlacement {
    enum class Hand { RIGHT, LEFT }

    /** The widest the keyboard gets on a wide screen: about a phone's keyboard. */
    const val MAX_DP = 440

    /** The narrowest it gets, even on a cramped half of a fold. */
    const val MIN_DP = 260

    /** Space between the block and the window's edge, as the bar's own side padding. */
    const val EDGE_DP = 10

    /** Space kept between the block and a bent fold. */
    const val FOLD_CLEARANCE_DP = 12

    data class Result(
        val widthDp: Int,
        /** Distance from the window's left edge to the keyboard's left edge. */
        val leftDp: Int,
    )

    fun compute(widthDp: Int, hand: Hand = Hand.RIGHT, fold: BarLayout.VerticalFold? = null): Result {
        if (widthDp < BarLayout.WIDE_DP) return Result(widthDp, 0)
        val onLeft = hand == Hand.LEFT
        val room = if (fold != null && fold.bent && fold.leftDp > EDGE_DP && fold.rightDp < widthDp - EDGE_DP) {
            if (onLeft) fold.leftDp - EDGE_DP - FOLD_CLEARANCE_DP else widthDp - EDGE_DP - fold.rightDp - FOLD_CLEARANCE_DP
        } else {
            widthDp - 2 * EDGE_DP
        }
        val width = room.coerceIn(MIN_DP, MAX_DP).coerceAtMost(widthDp)
        val left = if (onLeft) EDGE_DP else widthDp - EDGE_DP - width
        return Result(width, left.coerceAtLeast(0))
    }
}
