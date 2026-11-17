package ai.voxctrl.keys

import ai.voxctrl.keys.core.KeyboardLayout
import ai.voxctrl.keys.core.KeyboardLayout.Kind
import ai.voxctrl.keys.core.KeyboardLayout.Page
import ai.voxctrl.keys.core.SwipeDecoder
import ai.voxctrl.keys.core.Typist
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import kotlin.math.hypot

/**
 * The typing keyboard's keys. Draws the page the [Typist] is on, turns
 * touches into taps and swipes, and draws the swipe's trail. The keys and
 * where they are come from [KeyboardLayout]; what a key does is the Typist's.
 */
class KeyboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private var typist: Typist? = null

    /** Row height in dp: shorter on a short screen. Set by [KeyboardBar]. */
    var rowHeightDp: Float = KeyboardLayout.ROW_DP
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    private var keys: KeyboardLayout.Keys? = null

    private val gap = resources.getDimension(R.dimen.key_gap)
    private val radius = resources.getDimension(R.dimen.key_radius)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.key_shadow) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.txt_0)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.swipe_trail)
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.swipe_trail)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val keyText = resources.getDimension(R.dimen.key_text)
    private val keyTextSmall = resources.getDimension(R.dimen.key_text_small)
    private val rect = RectF()

    // The one touch being followed. A second finger finishes the first as a tap.
    private var pointerId = -1
    private var downKey: KeyboardLayout.Key? = null
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private val points = ArrayList<SwipeDecoder.Point>()
    private val trail = Path()

    private val repeatDelete = object : Runnable {
        override fun run() {
            typist?.tapDelete()
            postDelayed(this, REPEAT_MS)
        }
    }

    fun attach(typist: Typist) {
        this.typist = typist
        refresh()
    }

    /** Redraws after the typist's page or shift changed. */
    fun refresh() {
        val page = typist?.page ?: Page.LETTERS
        val widthDp = width / density
        if (widthDp > 0 && (keys?.page != page || keys?.widthDp != widthDp || keys?.rowHeightDp != rowHeightDp)) {
            keys = KeyboardLayout.keys(page, widthDp, rowHeightDp)
            accessibility.keysChanged()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (rowHeightDp * KeyboardLayout.ROWS * density + gap).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = refresh()

    override fun onDraw(canvas: Canvas) {
        val keys = keys ?: return
        val shift = typist?.shift ?: Typist.Shift.OFF
        for (key in keys.keys) {
            rect.set(
                key.x * density + gap,
                key.y * density + gap,
                (key.x + key.width) * density - gap,
                (key.y + key.height) * density - gap,
            )
            // The visible key of a tiled key box: "a" and "l" own the half-gaps beside them.
            if (keys.page == Page.LETTERS && (key.text == "a" || key.text == "l")) {
                val visible = keys.unitDp * density - 2 * gap
                if (key.text == "a") rect.left = rect.right - visible else rect.right = rect.left + visible
            }
            val pressed = key == downKey && !swiping
            keyPaint.color = context.getColor(
                when {
                    pressed -> R.color.key_pressed
                    key.kind == Kind.ENTER -> R.color.key_enter
                    key.kind == Kind.CHARACTER || key.kind == Kind.SPACE -> R.color.key
                    else -> R.color.key_function
                },
            )
            canvas.drawRoundRect(rect.left, rect.top + density, rect.right, rect.bottom + density, radius, radius, shadowPaint)
            canvas.drawRoundRect(rect, radius, radius, keyPaint)
            drawFace(canvas, key, shift)
        }
        if (swiping) canvas.drawPath(trail, trailPaint)
    }

    private fun drawFace(canvas: Canvas, key: KeyboardLayout.Key, shift: Typist.Shift) {
        val icon = when (key.kind) {
            Kind.SHIFT -> when (shift) {
                Typist.Shift.OFF -> R.drawable.ic_shift
                Typist.Shift.ONCE -> R.drawable.ic_shift_on
                Typist.Shift.LOCKED -> R.drawable.ic_shift_lock
            }
            Kind.DELETE -> R.drawable.ic_backspace
            Kind.ENTER -> R.drawable.ic_enter
            else -> 0
        }
        if (icon != 0) {
            val drawable = context.getDrawable(icon)?.mutate() ?: return
            val tint = when {
                key.kind == Kind.ENTER -> R.color.txt_0
                key.kind == Kind.SHIFT && shift != Typist.Shift.OFF -> R.color.cyan_0
                else -> R.color.txt_1
            }
            drawable.setTint(context.getColor(tint))
            val size = (22 * density).toInt()
            val cx = rect.centerX().toInt()
            val cy = rect.centerY().toInt()
            drawable.bounds = Rect(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)
            drawable.draw(canvas)
            return
        }
        val label = when (key.kind) {
            Kind.SPACE -> context.getString(R.string.space_label)
            Kind.CHARACTER -> if (shift != Typist.Shift.OFF && key.text[0].isLetter()) key.label.uppercase() else key.label
            else -> key.label
        }
        val small = key.kind != Kind.CHARACTER
        textPaint.textSize = if (small) keyTextSmall else keyText
        textPaint.color = context.getColor(if (key.kind == Kind.SPACE) R.color.txt_2 else if (small) R.color.txt_1 else R.color.txt_0)
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val keys = keys ?: return false
        if (!isEnabled) {
            reset()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Fast two-thumb typing: a new finger finishes the one before as a tap.
                if (pointerId != -1 && !swiping) release(keys)
                if (swiping) return true
                val index = event.actionIndex
                pointerId = event.getPointerId(index)
                downX = event.getX(index)
                downY = event.getY(index)
                val key = keys.keyAt(downX / density, downY / density)
                downKey = key
                points.clear()
                points += SwipeDecoder.Point(downX / density, downY / density)
                trail.reset()
                trail.moveTo(downX, downY)
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (key.kind == Kind.DELETE) {
                    typist?.tapDelete()
                    postDelayed(repeatDelete, REPEAT_START_MS)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                for (h in 0 until event.historySize) addPoint(event.getHistoricalX(index, h), event.getHistoricalY(index, h))
                addPoint(event.getX(index), event.getY(index))
                val key = downKey
                if (!swiping && key != null && key.kind == Kind.CHARACTER && key.text[0].isLetter() &&
                    typist?.canSwipe == true &&
                    hypot(event.getX(index) - downX, event.getY(index) - downY) > keys.unitDp * density * SWIPE_START
                ) {
                    swiping = true
                    invalidate()
                }
                if (swiping) invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) release(keys)
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    private fun addPoint(x: Float, y: Float) {
        points += SwipeDecoder.Point(x / density, y / density)
        trail.lineTo(x, y)
    }

    /** The followed finger lifted: a swipe or a tap. */
    private fun release(keys: KeyboardLayout.Keys) {
        val key = downKey
        val typist = typist
        val swiped = swiping
        val path = ArrayList(points)
        reset()
        if (typist == null || key == null) return
        if (swiped) {
            typist.swipe(path, keys)
        } else {
            press(key)
        }
    }

    private fun press(key: KeyboardLayout.Key) {
        val typist = typist ?: return
        when (key.kind) {
            Kind.CHARACTER -> typist.tapCharacter(key.text)
            Kind.SHIFT -> typist.tapShift(SystemClock.uptimeMillis())
            Kind.DELETE -> Unit // already deleted when the finger went down
            Kind.SPACE -> typist.tapSpace()
            Kind.ENTER -> typist.tapEnter()
            Kind.PAGE -> key.target?.let { typist.changePage(it) }
        }
    }

    private fun reset() {
        removeCallbacks(repeatDelete)
        pointerId = -1
        downKey = null
        swiping = false
        points.clear()
        trail.reset()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    // ── Screen readers ────────────────────────────────────────────────────────

    private val accessibility = KeysAccessibility()

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = accessibility

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        val keys = keys ?: return super.dispatchHoverEvent(event)
        if (!accessibility.enabled()) return super.dispatchHoverEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                accessibility.hover(keys.keys.indexOf(keys.keyAt(event.x / density, event.y / density)))
            MotionEvent.ACTION_HOVER_EXIT -> accessibility.hover(-1)
        }
        return true
    }

    private fun describe(key: KeyboardLayout.Key): String {
        val shift = typist?.shift ?: Typist.Shift.OFF
        return when (key.kind) {
            Kind.CHARACTER -> if (shift != Typist.Shift.OFF && key.text[0].isLetter()) key.text.uppercase() else key.text
            Kind.SHIFT -> context.getString(
                when (shift) {
                    Typist.Shift.OFF -> R.string.key_shift
                    Typist.Shift.ONCE -> R.string.key_shift_on
                    Typist.Shift.LOCKED -> R.string.key_caps_lock
                },
            )
            Kind.DELETE -> context.getString(R.string.key_delete)
            Kind.SPACE -> context.getString(R.string.key_space)
            Kind.ENTER -> context.getString(R.string.key_enter)
            Kind.PAGE -> context.getString(
                when (key.target) {
                    Page.LETTERS -> R.string.key_letters
                    Page.SYMBOLS -> R.string.key_symbols
                    else -> R.string.key_more_symbols
                },
            )
        }
    }

    /** Each key as a virtual view, so TalkBack can read and press it. */
    private inner class KeysAccessibility : AccessibilityNodeProvider() {
        private var hovered = -1

        fun enabled(): Boolean {
            val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            return manager?.isTouchExplorationEnabled == true
        }

        fun keysChanged() {
            hovered = -1
            if (enabled()) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }

        fun hover(index: Int) {
            if (index == hovered) return
            val previous = hovered
            hovered = index
            if (previous >= 0) send(previous, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
            if (index >= 0) send(index, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        }

        private fun send(index: Int, type: Int) {
            val event = newEvent(type)
            event.packageName = context.packageName
            event.className = KeyboardView::class.java.name
            event.setSource(this@KeyboardView, index)
            keys?.keys?.getOrNull(index)?.let { event.text.add(describe(it)) }
            parent?.requestSendAccessibilityEvent(this@KeyboardView, event)
        }

        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            val keys = keys
            if (virtualViewId == HOST_VIEW_ID) {
                val info = newNode(HOST_VIEW_ID)
                onInitializeAccessibilityNodeInfo(info)
                keys?.keys?.indices?.forEach { info.addChild(this@KeyboardView, it) }
                return info
            }
            val key = keys?.keys?.getOrNull(virtualViewId) ?: return null
            val info = newNode(virtualViewId)
            info.packageName = context.packageName
            info.className = android.widget.Button::class.java.name
            info.contentDescription = describe(key)
            info.setParent(this@KeyboardView)
            val bounds = Rect(
                (key.x * density).toInt(),
                (key.y * density).toInt(),
                ((key.x + key.width) * density).toInt(),
                ((key.y + key.height) * density).toInt(),
            )
            val origin = IntArray(2)
            getLocationOnScreen(origin)
            bounds.offset(origin[0], origin[1])
            info.setBoundsInScreen(bounds)
            info.isEnabled = isEnabled
            info.isClickable = true
            info.isVisibleToUser = true
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
            return info
        }

        override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (virtualViewId == HOST_VIEW_ID) return performAccessibilityAction(action, arguments)
            val key = keys?.keys?.getOrNull(virtualViewId) ?: return false
            if (!isEnabled) return false
            if (action != AccessibilityNodeInfo.ACTION_CLICK) return false
            if (key.kind == Kind.DELETE) typist?.tapDelete() else press(key)
            send(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
            return true
        }
    }

    // The constructors arrived in Android 11; obtain() does the same before it.
    @Suppress("DEPRECATION")
    private fun newEvent(type: Int): AccessibilityEvent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) AccessibilityEvent(type) else AccessibilityEvent.obtain(type)

    @Suppress("DEPRECATION")
    private fun newNode(virtualViewId: Int): AccessibilityNodeInfo = when {
        virtualViewId == HOST_VIEW_ID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> AccessibilityNodeInfo(this)
        virtualViewId == HOST_VIEW_ID -> AccessibilityNodeInfo.obtain(this)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> AccessibilityNodeInfo(this, virtualViewId)
        else -> AccessibilityNodeInfo.obtain(this, virtualViewId)
    }

    private companion object {
        /** A finger that moves this many key widths from where it went down is swiping. */
        const val SWIPE_START = 0.45f
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 50L
        const val HOST_VIEW_ID = AccessibilityNodeProvider.HOST_VIEW_ID
    }
}
