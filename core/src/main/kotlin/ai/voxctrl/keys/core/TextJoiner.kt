package ai.voxctrl.keys.core

/**
 * How dictated text meets what is already in the field. A dictation is a
 * phrase, so it needs a space before it when it follows a word, and none at
 * the start of a field or line or after an opening bracket or quote.
 */
object TextJoiner {
    private const val NO_SPACE_AFTER = "([{“‘\"'/@#-—"

    /**
     * [dictated] ready to insert after [before], the text just ahead of the
     * cursor (null or empty when unknown or at the start of the field).
     */
    fun join(before: CharSequence?, dictated: String): String {
        val text = dictated.trim()
        if (text.isEmpty()) return ""
        if (before.isNullOrEmpty()) return text
        val last = before[before.length - 1]
        val startsWithPunctuation = text[0] in ".,;:!?)]}"
        return if (last.isWhitespace() || last in NO_SPACE_AFTER || startsWithPunctuation) text else " $text"
    }
}
