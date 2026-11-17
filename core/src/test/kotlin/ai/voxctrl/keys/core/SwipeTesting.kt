package ai.voxctrl.keys.core

import ai.voxctrl.keys.core.SwipeDecoder.Point
import java.io.File
import kotlin.random.Random

/** The bundled word list and a way to fake a finger's swipe, shared by the typing tests. */
object SwipeTesting {
    val words: WordList by lazy {
        val path = System.getProperty("voxkeys.wordlist") ?: "../app/src/main/assets/words_en.txt"
        File(path).bufferedReader().use { WordList.read(it) }
    }

    /**
     * A swipe for [word] as a finger draws it: through each letter's key,
     * missing the centre by up to [wobble] key widths, sampled every few dp,
     * with a little tremor along the way.
     */
    fun swipe(word: String, keys: KeyboardLayout.Keys, random: Random, wobble: Float = 0.3f): List<Point> {
        val unit = keys.unitDp
        val corners = word.lowercase().filter { it in 'a'..'z' }.map { c ->
            val key = keys.keyFor(c)!!
            Point(
                key.centreX + (random.nextFloat() * 2 - 1) * wobble * unit,
                key.centreY + (random.nextFloat() * 2 - 1) * wobble * keys.rowHeightDp,
            )
        }
        val path = ArrayList<Point>()
        for (i in 0 until corners.size - 1) {
            val a = corners[i]
            val b = corners[i + 1]
            val steps = maxOf(2, (SwipeDecoder.dist(a, b) / 6).toInt())
            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                path += Point(
                    a.x + t * (b.x - a.x) + (random.nextFloat() - 0.5f) * 2,
                    a.y + t * (b.y - a.y) + (random.nextFloat() - 0.5f) * 2,
                )
            }
        }
        path += corners.last()
        return path
    }
}
