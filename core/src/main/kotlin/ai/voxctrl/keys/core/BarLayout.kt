package ai.voxctrl.keys.core

/**
 * Where the pieces of the bar go for a given window. Kept free of Android
 * types so every layout a foldable can produce is covered by plain unit tests
 * (see BarLayoutTest, which walks the Galaxy Z Fold 8's screens and postures).
 *
 * The bar is always one row: [switch keyboard] [microphone pill] [backspace].
 *
 * - **Compact** (narrower than [WIDE_DP]): the pill stretches between the two
 *   buttons. This is a phone, or a foldable's cover screen held upright.
 * - **Wide**: the two buttons stay at the edges, under the thumbs, and the
 *   pill takes a fixed width so the target does not become a 700 dp strip.
 *   Where it sits is the user's choice (centre by default).
 * - **Wide with a vertical fold that is bent** (book posture): the pill moves
 *   wholly onto one half, so it never straddles the hinge.
 *
 * A horizontal fold (tabletop posture) needs nothing: the system puts the
 * keyboard window on the lower half, and the bar is a single short row.
 */
object BarLayout {
    const val WIDE_DP = 600
    const val PILL_MAX_DP = 420
    const val PILL_MIN_DP = 160

    /** Below this the server name is dropped from the pill so the label fits. */
    const val SERVER_NAME_MIN_DP = 360

    /** Room the two side buttons and the gaps take, at each end of the bar. */
    const val SIDE_DP = 48 + 8 + 10

    enum class Position { LEFT, CENTER, RIGHT }

    /**
     * A fold crossing the keyboard window from top to bottom.
     * [leftDp]..[rightDp] is the strip it occupies, in the bar's own
     * coordinates; a seamless fold has zero width. [bent] is true when the
     * device is half-opened rather than flat.
     */
    data class VerticalFold(val leftDp: Int, val rightDp: Int, val bent: Boolean)

    data class Result(
        /** True when the pill stretches to fill the row; [pillWidthDp] is then ignored. */
        val pillFills: Boolean,
        val pillWidthDp: Int,
        /** Share of the free space put to the left of the pill, 0.0 to 1.0. */
        val leftSpaceShare: Float,
        val showServerName: Boolean,
    )

    fun compute(widthDp: Int, preferred: Position = Position.CENTER, fold: VerticalFold? = null): Result {
        if (widthDp < WIDE_DP) {
            return Result(
                pillFills = true,
                pillWidthDp = 0,
                leftSpaceShare = 0f,
                showServerName = widthDp >= SERVER_NAME_MIN_DP,
            )
        }
        val free = widthDp - 2 * SIDE_DP
        if (fold != null && fold.bent && fold.leftDp > SIDE_DP && fold.rightDp < widthDp - SIDE_DP) {
            // Keep the pill on one half. The side follows the preference;
            // "centre" means the right half, where most people's thumb is.
            val onLeft = preferred == Position.LEFT
            val clearance = 12
            val room = if (onLeft) fold.leftDp - SIDE_DP - clearance else widthDp - SIDE_DP - fold.rightDp - clearance
            val width = room.coerceIn(PILL_MIN_DP, PILL_MAX_DP)
            return Result(
                pillFills = false,
                pillWidthDp = width,
                leftSpaceShare = if (onLeft) 0f else 1f,
                showServerName = true,
            )
        }
        val width = free.coerceIn(PILL_MIN_DP, PILL_MAX_DP)
        return Result(
            pillFills = false,
            pillWidthDp = width,
            leftSpaceShare = when (preferred) {
                Position.LEFT -> 0f
                Position.CENTER -> 0.5f
                Position.RIGHT -> 1f
            },
            showServerName = true,
        )
    }
}
