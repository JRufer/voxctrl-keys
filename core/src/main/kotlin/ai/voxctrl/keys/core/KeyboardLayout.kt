package ai.voxctrl.keys.core

/**
 * The typing keyboard's keys: which keys each page has and where each one is,
 * in dp, for a given width. Kept free of Android so hit-testing and the swipe
 * decoder's key positions are tested on the JVM.
 *
 * Every page has four rows on a grid of ten units across:
 *
 * ```
 *  q w e r t y u i o p
 *   a s d f g h j k l
 *  ⇧  z x c v b n m  ⌫
 *  ?123 , [ space ] . ↵
 * ```
 */
object KeyboardLayout {
    enum class Page { LETTERS, SYMBOLS, MORE_SYMBOLS }

    enum class Kind {
        /** Types [Key.text]. */
        CHARACTER,
        SHIFT,
        DELETE,
        SPACE,
        ENTER,

        /** Changes to [Key.target]. */
        PAGE,
    }

    data class Key(
        val kind: Kind,
        /** What is drawn on the key; empty for keys drawn with an icon. */
        val label: String,
        /** What a [Kind.CHARACTER] key types. */
        val text: String = label,
        val target: Page? = null,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) {
        val centreX get() = x + width / 2
        val centreY get() = y + height / 2

        fun contains(px: Float, py: Float) = px >= x && px < x + width && py >= y && py < y + height
    }

    /**
     * The keys of one page, laid out for a keyboard [widthDp] wide with rows
     * [rowHeightDp] tall. Each key's box includes the gap around it, so the
     * boxes tile the keyboard with no dead space between keys: a touch
     * anywhere lands on a key.
     */
    class Keys(val page: Page, val widthDp: Float, val rowHeightDp: Float, val keys: List<Key>) {
        val heightDp get() = rowHeightDp * ROWS

        /** The width of one letter key, the unit the swipe decoder measures distance in. */
        val unitDp get() = widthDp / UNITS

        /** The key under a touch; touches outside the keyboard go to the nearest edge key. */
        fun keyAt(x: Float, y: Float): Key {
            val row = (y / rowHeightDp).toInt().coerceIn(0, ROWS - 1)
            val inRow = keys.filter { (it.y / rowHeightDp + 0.5f).toInt() == row }
            val cx = x.coerceIn(0f, widthDp - 0.01f)
            return inRow.firstOrNull { cx >= it.x && cx < it.x + it.width }
                ?: inRow.minBy { kotlin.math.abs(it.centreX - cx) }
        }

        /** The key that types [char] (lower case), if this page has one. */
        fun keyFor(char: Char): Key? = keys.firstOrNull { it.kind == Kind.CHARACTER && it.text.length == 1 && it.text[0] == char }
    }

    const val ROWS = 4
    const val UNITS = 10f

    /** Row height on a screen with room for it, and on a short one (a phone sideways). */
    const val ROW_DP = 52f
    const val ROW_SHORT_DP = 42f

    /** Screens less tall than this get [ROW_SHORT_DP] rows, so the app keeps some room. */
    const val SHORT_SCREEN_DP = 500

    fun rowHeightFor(screenHeightDp: Int) = if (screenHeightDp < SHORT_SCREEN_DP) ROW_SHORT_DP else ROW_DP

    fun keys(page: Page, widthDp: Float, rowHeightDp: Float = ROW_DP): Keys {
        val unit = widthDp / UNITS
        val rows = when (page) {
            Page.LETTERS -> listOf(
                chars("qwertyuiop"),
                listOf(Spec.Gap(0.5f)) + chars("asdfghjkl") + Spec.Gap(0.5f),
                listOf(Spec.K(Kind.SHIFT, "", 1.5f)) + chars("zxcvbnm") + Spec.K(Kind.DELETE, "", 1.5f),
                bottomRow(Page.SYMBOLS, "?123"),
            )
            Page.SYMBOLS -> listOf(
                chars("1234567890"),
                chars("@#\$_&-+()/"),
                listOf(Spec.K(Kind.PAGE, "=\\<", 1.5f, Page.MORE_SYMBOLS)) + chars("*\"':;!?") + Spec.K(Kind.DELETE, "", 1.5f),
                bottomRow(Page.LETTERS, "ABC"),
            )
            Page.MORE_SYMBOLS -> listOf(
                chars("~`|•√π÷×¶∆"),
                chars("£€¥¢^°={}<"),
                listOf(Spec.K(Kind.PAGE, "?123", 1.5f, Page.SYMBOLS)) + chars("\\%©®[]>") + Spec.K(Kind.DELETE, "", 1.5f),
                bottomRow(Page.LETTERS, "ABC"),
            )
        }
        val keys = mutableListOf<Key>()
        rows.forEachIndexed { r, specs ->
            // Rows narrower than ten units (the second letter row) are centred.
            val total = specs.sumOf { it.units.toDouble() }.toFloat()
            var x = (UNITS - total) / 2 * unit
            for (spec in specs) {
                val w = spec.units * unit
                if (spec is Spec.K) {
                    keys += Key(
                        kind = spec.kind,
                        label = spec.label,
                        text = spec.text,
                        target = spec.target,
                        x = x,
                        y = r * rowHeightDp,
                        width = w,
                        height = rowHeightDp,
                    )
                }
                x += w
            }
        }
        // The half-key gaps either side of "a" and "l" belong to those keys,
        // so a touch at the very edge of the middle row still lands.
        val tiled = keys.map { key ->
            if (page == Page.LETTERS && key.text == "a") key.copy(x = 0f, width = key.width + key.x)
            else if (page == Page.LETTERS && key.text == "l") key.copy(width = widthDp - key.x)
            else key
        }
        return Keys(page, widthDp, rowHeightDp, tiled)
    }

    private sealed class Spec(val units: Float) {
        class K(val kind: Kind, val label: String, units: Float = 1f, val target: Page? = null, val text: String = label) : Spec(units)

        class Gap(units: Float) : Spec(units)
    }

    private fun chars(s: String) = s.map { Spec.K(Kind.CHARACTER, it.toString()) }

    private fun bottomRow(target: Page, label: String) = listOf(
        Spec.K(Kind.PAGE, label, 1.5f, target),
        Spec.K(Kind.CHARACTER, ","),
        Spec.K(Kind.SPACE, "", 5f, text = " "),
        Spec.K(Kind.CHARACTER, "."),
        Spec.K(Kind.ENTER, "", 1.5f),
    )
}
