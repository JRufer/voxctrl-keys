package ai.voxctrl.keys.core

/**
 * A small JSON reader, enough for what a VoxCtrl server answers with and for
 * the body of a pairing code. It exists so the core module needs no library
 * and behaves the same on a desktop JVM (the tests) and on Android.
 *
 * Objects become `Map<String, Any?>`, arrays `List<Any?>`, numbers `Double`,
 * and the rest `String`, `Boolean` or `null`.
 */
object Json {
    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.value()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw ParseException("Unexpected text after the JSON value")
        return value
    }

    /** Parses [text] and returns it when it is an object, or null otherwise. */
    @Suppress("UNCHECKED_CAST")
    fun parseObject(text: String): Map<String, Any?>? =
        try {
            parse(text) as? Map<String, Any?>
        } catch (_: ParseException) {
            null
        }

    /** [text] as a JSON string literal, quotes included. */
    fun quote(text: String): String {
        val out = StringBuilder(text.length + 2)
        out.append('"')
        for (c in text) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }

    private class Reader(private val text: String) {
        private var pos = 0

        fun atEnd() = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun value(depth: Int = 0): Any? {
            if (depth > 64) throw ParseException("JSON is nested too deeply")
            if (atEnd()) throw ParseException("JSON ended early")
            return when (val c = text[pos]) {
                '{' -> obj(depth)
                '[' -> array(depth)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else throw ParseException("Unexpected character '$c'")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) throw ParseException("Unexpected text in JSON")
            pos += word.length
            return value
        }

        private fun number(): Double {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] in "+-.eE")) pos++
            return text.substring(start, pos).toDoubleOrNull() ?: throw ParseException("Not a number")
        }

        private fun string(): String {
            pos++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("A string was not closed")
                val c = text[pos++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("A string was not closed")
                        when (val e = text[pos++]) {
                            '"', '\\', '/' -> out.append(e)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (pos + 4 > text.length) throw ParseException("Bad \\u escape")
                                val code = text.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: throw ParseException("Bad \\u escape")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Bad escape '\\$e'")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun array(depth: Int): List<Any?> {
            pos++
            val items = ArrayList<Any?>()
            skipWhitespace()
            if (!atEnd() && text[pos] == ']') {
                pos++
                return items
            }
            while (true) {
                skipWhitespace()
                items.add(value(depth + 1))
                skipWhitespace()
                if (atEnd()) throw ParseException("An array was not closed")
                when (text[pos++]) {
                    ',' -> continue
                    ']' -> return items
                    else -> throw ParseException("Expected ',' or ']'")
                }
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            pos++
            val fields = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && text[pos] == '}') {
                pos++
                return fields
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || text[pos] != '"') throw ParseException("Expected a field name")
                val name = string()
                skipWhitespace()
                if (atEnd() || text[pos++] != ':') throw ParseException("Expected ':'")
                skipWhitespace()
                fields[name] = value(depth + 1)
                skipWhitespace()
                if (atEnd()) throw ParseException("An object was not closed")
                when (text[pos++]) {
                    ',' -> continue
                    '}' -> return fields
                    else -> throw ParseException("Expected ',' or '}'")
                }
            }
        }
    }
}
