package ai.voxctrl.keys

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * The moving bars in the pill while listening: the loudness of the last few
 * seconds, newest on the right. Decorative; the timer beside it and the
 * pill's description carry the state for a screen reader.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val barWidth = 3f * density
    private val gap = 3f * density
    private val minHeight = 4f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val active = context.getColor(R.color.cyan_0)
    private val quiet = context.getColor(R.color.line_2)

    private val levels = FloatArray(96)
    private var count = 0

    fun push(level: Float) {
        if (count < levels.size) {
            levels[count++] = level
        } else {
            System.arraycopy(levels, 1, levels, 0, levels.size - 1)
            levels[levels.size - 1] = level
        }
        invalidate()
    }

    fun clear() {
        count = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val slots = ((width + gap) / (barWidth + gap)).toInt().coerceAtMost(levels.size)
        if (slots <= 0) return
        val centre = height / 2f
        // Fill from the left until the row is full, then scroll.
        val first = maxOf(0, count - slots)
        for (slot in 0 until slots) {
            val index = first + slot
            val has = index < count
            val level = if (has) levels[index] else 0f
            val h = if (has) minHeight + (height - minHeight) * level else minHeight
            paint.color = if (has && level > 0.04f) active else quiet
            val left = slot * (barWidth + gap)
            canvas.drawRoundRect(left, centre - h / 2, left + barWidth, centre + h / 2, barWidth / 2, barWidth / 2, paint)
        }
    }
}
