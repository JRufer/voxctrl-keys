package ai.voxctrl.keys.core

import kotlin.math.hypot
import kotlin.math.ln

/**
 * Turns a swipe across the letter keys into words.
 *
 * The method is the classic one for shape writing: every word has an ideal
 * path, the line through the centres of its letters' keys. The swipe and each
 * candidate's ideal path are both resampled to [SAMPLES] evenly spaced points,
 * and the word whose path lies closest to the swipe, point for point, wins,
 * weighed against how common the word is. Distances are measured in key
 * widths, so the same swipe scores the same on a narrow or a wide keyboard.
 *
 * Only plausible candidates are scored: the word must start at a key near
 * where the finger went down, end at a key near where it lifted, and every
 * one of its letters must lie near the swipe.
 */
class SwipeDecoder(private val words: WordList) {
    data class Point(val x: Float, val y: Float)

    data class Candidate(val entry: WordList.Entry, val score: Double)

    /**
     * The best words for [path] (in the keyboard's dp) over the letter keys
     * [keys], best first. Empty when nothing plausible was found.
     */
    fun decode(path: List<Point>, keys: KeyboardLayout.Keys, limit: Int = 3): List<Candidate> {
        if (path.size < 2) return emptyList()
        val unit = keys.unitDp
        val gesture = resample(path.map { Point(it.x / unit, it.y / unit) }, SAMPLES)

        // Every letter key's centre, in key widths.
        val centres = HashMap<Char, Point>(32)
        for (c in 'a'..'z') {
            val key = keys.keyFor(c) ?: continue
            centres[c] = Point(key.centreX / unit, key.centreY / unit)
        }
        if (centres.isEmpty()) return emptyList()

        // How close the swipe comes to each key, once, for the pruning below.
        val nearest = HashMap<Char, Double>(32)
        for ((c, centre) in centres) nearest[c] = gesture.minOf { dist(it, centre) }

        val start = gesture.first()
        val end = gesture.last()
        val starts = centres.filter { dist(start, it.value) <= END_RADIUS }.keys
        val ends = centres.filter { dist(end, it.value) <= END_RADIUS }.keys

        val scored = ArrayList<Candidate>()
        for (first in starts) for (last in ends) {
            for (entry in words.startingAndEnding(first, last)) {
                val letters = entry.letters
                if (letters.length < 2) continue
                if (letters.any { (nearest[it] ?: Double.MAX_VALUE) > PASS_RADIUS }) continue
                val template = templateFor(letters, centres) ?: continue
                val distance = meanDistance(gesture, template)
                if (distance > MAX_MEAN_DISTANCE) continue
                // How far the swipe missed the letters it should pass through.
                val missed = letters.sumOf { nearest[it] ?: 0.0 } / letters.length
                // Where the finger went down and lifted are the surest points of a swipe.
                val ends = dist(start, template.first()) + dist(end, template.last())
                val score = -(distance * distance) / (2 * SIGMA * SIGMA) - MISS_WEIGHT * missed - END_WEIGHT * ends +
                    FREQUENCY_WEIGHT * entry.zipf * ln(10.0)
                scored += Candidate(entry, score)
            }
        }
        return scored.sortedByDescending { it.score }.take(limit)
    }

    private fun templateFor(letters: String, centres: Map<Char, Point>): List<Point>? {
        val points = ArrayList<Point>(letters.length)
        var previous: Char? = null
        for (c in letters) {
            if (c == previous) continue // "ll" in "hello" is one key
            points += centres[c] ?: return null
            previous = c
        }
        if (points.size < 2) {
            // A word on one key ("ll" alone, or a doubled single letter): a dot.
            return List(SAMPLES) { points.first() }
        }
        return resample(points, SAMPLES)
    }

    companion object {
        const val SAMPLES = 48

        /** The finger must go down and lift within this many key widths of the first and last letters. */
        const val END_RADIUS = 1.25

        /** Every letter must lie within this many key widths of the swipe. */
        const val PASS_RADIUS = 1.0

        /** Candidates further than this from the swipe, on average, are dropped. */
        const val MAX_MEAN_DISTANCE = 1.6

        const val SIGMA = 0.35
        const val MISS_WEIGHT = 3.0
        const val END_WEIGHT = 1.5

        /** How much a word's commonness counts against its fit: one Zipf step is a factor of ten. */
        const val FREQUENCY_WEIGHT = 0.40

        fun dist(a: Point, b: Point) = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

        fun meanDistance(a: List<Point>, b: List<Point>): Double {
            var sum = 0.0
            for (i in a.indices) sum += dist(a[i], b[i])
            return sum / a.size
        }

        /** [n] points spaced evenly along the polyline [points]. */
        fun resample(points: List<Point>, n: Int): List<Point> {
            val length = (1 until points.size).sumOf { dist(points[it - 1], points[it]) }
            if (length == 0.0) return List(n) { points.first() }
            val step = length / (n - 1)
            val out = ArrayList<Point>(n)
            out += points.first()
            var carried = 0.0
            var i = 1
            var from = points.first()
            while (i < points.size && out.size < n) {
                val to = points[i]
                val segment = dist(from, to)
                if (carried + segment >= step && segment > 0) {
                    val t = (step - carried) / segment
                    val p = Point((from.x + t * (to.x - from.x)).toFloat(), (from.y + t * (to.y - from.y)).toFloat())
                    out += p
                    from = p
                    carried = 0.0
                } else {
                    carried += segment
                    from = to
                    i++
                }
            }
            while (out.size < n) out += points.last()
            return out
        }
    }
}
