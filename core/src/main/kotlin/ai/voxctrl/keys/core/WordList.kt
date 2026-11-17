package ai.voxctrl.keys.core

import java.io.BufferedReader

/**
 * The words the typing keyboard knows, with how common each is. Read once
 * from the bundled list (`words_en.txt`, one `word<TAB>zipf×10` per line,
 * most common first; see scripts/build-wordlist.py). Nothing is ever added
 * to it: the keyboard does not learn from what is typed.
 */
class WordList(entries: List<Entry>) {
    data class Entry(
        /** As it is typed: "I'm", "Monday", "don't". */
        val word: String,
        /** Zipf frequency: 7 for "the", 2 for a rare word. */
        val zipf: Float,
    ) {
        /** The letters a swipe passes over, lower case, with any apostrophe left out. */
        val letters: String = word.lowercase().filter { it in 'a'..'z' }
    }

    /** All entries, most common first. */
    val entries: List<Entry> = entries.sortedByDescending { it.zipf }

    private val byFirstAndLast: Map<Pair<Char, Char>, List<Entry>> =
        this.entries.filter { it.letters.isNotEmpty() }.groupBy { it.letters.first() to it.letters.last() }

    private val byLetters: Map<String, Entry> = this.entries.reversed().associateBy { it.letters }

    val size get() = entries.size

    /** Words that start with [first] and end with [last], most common first. */
    fun startingAndEnding(first: Char, last: Char): List<Entry> = byFirstAndLast[first to last].orEmpty()

    /** The entry spelled with exactly these letters, ignoring case and apostrophes. */
    fun find(typed: String): Entry? = byLetters[typed.lowercase().filter { it in 'a'..'z' }]

    /**
     * Up to [limit] of the most common words that start with [prefix]
     * (ignoring case and apostrophes), not counting the prefix itself.
     */
    fun completions(prefix: String, limit: Int): List<Entry> {
        val key = prefix.lowercase().filter { it in 'a'..'z' }
        if (key.isEmpty()) return emptyList()
        val found = ArrayList<Entry>(limit)
        for (entry in entries) {
            if (entry.letters.length > key.length && entry.letters.startsWith(key)) {
                found += entry
                if (found.size == limit) break
            }
        }
        return found
    }

    companion object {
        fun read(reader: BufferedReader): WordList {
            val entries = ArrayList<Entry>(50_000)
            reader.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) {
                    val zipf = line.substring(tab + 1).trim().toIntOrNull() ?: return@forEachLine
                    entries += Entry(line.substring(0, tab), zipf / 10f)
                }
            }
            return WordList(entries)
        }
    }
}
