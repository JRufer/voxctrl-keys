package ai.voxctrl.keys

import ai.voxctrl.keys.core.BarLayout
import ai.voxctrl.keys.core.KeyboardLayout
import ai.voxctrl.keys.core.KeyboardPlacement
import ai.voxctrl.keys.core.Typist
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Insets
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import androidx.window.layout.FoldingFeature
import kotlin.math.ceil

/** What the bar is showing. One of these at a time; the service decides which. */
sealed class BarState {
    /** No server address yet: the pill opens the settings. */
    object Setup : BarState()

    /** Microphone permission is missing: the pill opens the settings to ask for it. */
    object NeedsMic : BarState()

    /** Ready. [online] is null until the first check of the server comes back. */
    data class Idle(val server: String, val online: Boolean?) : BarState()

    object Listening : BarState()

    object Finishing : BarState()

    /**
     * The server did not answer, or was too busy to ([label] then says so).
     * [holdsAudio] is true when a recording is waiting to be sent again.
     */
    data class Unreachable(val server: String, val holdsAudio: Boolean, val label: String? = null) : BarState()

    /** Something the settings screen has to fix: a refused key, a changed certificate. */
    data class Problem(val label: String) : BarState()

    /** A short message shown for a moment, then back to idle. */
    data class Notice(val label: String) : BarState()
}

/**
 * The views of the keyboard: the voice bar, which shows a [BarState], and
 * under it the typing keyboard, shown while [expanded]. Reports taps, and
 * re-lays both out for the window they are in.
 */
@SuppressLint("ClickableViewAccessibility")
class KeyboardBar(
    val root: View,
    private val prefs: Prefs,
    private val haptics: Haptics,
    onPill: () -> Unit,
    onKeyboardButton: () -> Unit,
    onKeyboardButtonLong: () -> Unit,
    private val onRight: () -> Unit,
    private val onBackspace: () -> Unit,
) {
    private val row: LinearLayout = root.findViewById(R.id.bar_row)
    private val pill: LinearLayout = root.findViewById(R.id.pill)
    private val spaceLeft: Space = root.findViewById(R.id.space_left)
    private val spaceRight: Space = root.findViewById(R.id.space_right)
    private val icon: ImageView = root.findViewById(R.id.pill_icon)
    private val glyph: ImageView = root.findViewById(R.id.pill_glyph)
    private val spinner: ProgressBar = root.findViewById(R.id.pill_spinner)
    private val label: TextView = root.findViewById(R.id.pill_label)
    private val wave: WaveformView = root.findViewById(R.id.pill_wave)
    private val trailing: TextView = root.findViewById(R.id.pill_trailing)
    private val switchButton: ImageButton = root.findViewById(R.id.btn_switch)
    private val rightButton: ImageButton = root.findViewById(R.id.btn_right)
    private val panel: FrameLayout = root.findViewById(R.id.keyboard_panel)
    private val block: LinearLayout = root.findViewById(R.id.keyboard_block)
    private val keysView: KeyboardView = root.findViewById(R.id.keys)

    /** The suggestions strip, best in the middle: index 0 of the typist's list, then 1 on the left, 2 on the right. */
    private val suggestionViews: List<TextView> = listOf(
        root.findViewById(R.id.suggestion_middle),
        root.findViewById(R.id.suggestion_left),
        root.findViewById(R.id.suggestion_right),
    )
    private var typist: Typist? = null

    /** True while the typing keyboard shows under the bar. */
    var expanded = false
        private set

    private val context = root.context
    private val density = context.resources.displayMetrics.density
    private val grey = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    private var state: BarState = BarState.Setup
    private var fold: FoldingFeature? = null
    private var applied: BarLayout.Result? = null
    private var showServerName = true

    /** True while the right button is a backspace that repeats when held. */
    private var rightIsBackspace = true
    private val repeatBackspace = object : Runnable {
        override fun run() {
            onBackspace()
            rightButton.postDelayed(this, 50)
        }
    }

    init {
        // The VoxCtrl icon is a rounded square; show it as a circle.
        icon.outlineProvider = ViewOutlineProvider.BACKGROUND
        icon.clipToOutline = true

        pill.setOnClickListener { onPill() }
        switchButton.setOnClickListener { onKeyboardButton() }
        switchButton.setOnLongClickListener {
            onKeyboardButtonLong()
            true
        }
        suggestionViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                haptics.tap()
                typist?.pickSuggestion(index)
            }
        }
        rightButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    if (rightIsBackspace) {
                        onBackspace()
                        view.postDelayed(repeatBackspace, 400)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    view.removeCallbacks(repeatBackspace)
                    if (!rightIsBackspace) onRight()
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    view.removeCallbacks(repeatBackspace)
                }
            }
            true
        }
        // For a screen reader's double-tap, which sends a click rather than touches.
        rightButton.setOnClickListener { if (rightIsBackspace) onBackspace() else onRight() }

        // The keyboard's window reaches the bottom edge of the screen, under the
        // system's navigation bar, where Android also draws its own
        // switch-keyboard and hide-keyboard buttons. Pad the bar by that
        // inset so its row sits above them; the bar's background still
        // fills the strip behind them. The sides matter in landscape, for a
        // navigation bar or camera cut-out at the edge.
        //
        // On Android versions that still keep a keyboard clear of the bars
        // themselves, the inset arrives here as zero and nothing is added.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            } else {
                @Suppress("DEPRECATION")
                Insets.of(
                    insets.systemWindowInsetLeft,
                    0,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom,
                )
            }
            if (view.paddingLeft != bars.left || view.paddingRight != bars.right || view.paddingBottom != bars.bottom) {
                view.setPadding(bars.left, 0, bars.right, bars.bottom)
                applied = null // the room for the row changed
            }
            insets
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = view.requestApplyInsets()

            override fun onViewDetachedFromWindow(view: View) = Unit
        })

        root.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft || applied == null) root.post { applyLayout() }
        }
        show(state)
        setExpanded(false)
    }

    /** Connects the typing keyboard to the [Typist] that types for it. */
    fun attach(typist: Typist) {
        this.typist = typist
        keysView.attach(typist, haptics)
        refreshTyping()
    }

    /** Redraws the keys and the suggestions after the typist changed. */
    fun refreshTyping() {
        keysView.refresh()
        val words = typist?.suggestions.orEmpty()
        suggestionViews.forEachIndexed { index, view ->
            val word = words.getOrNull(index)
            view.text = word ?: ""
            view.isEnabled = word != null && keysView.isEnabled
            view.contentDescription = word?.let { context.getString(R.string.suggestion, it) }
        }
    }

    /** Shows or hides the typing keyboard; the voice bar above it does not change. */
    fun setExpanded(show: Boolean) {
        expanded = show
        panel.visibility = if (show) View.VISIBLE else View.GONE
        switchButton.setBackgroundResource(if (show) R.drawable.bg_button_on else R.drawable.bg_button)
        switchButton.imageTintList = ColorStateList.valueOf(context.getColor(if (show) R.color.cyan_0 else R.color.txt_1))
        switchButton.contentDescription =
            context.getString(if (show) R.string.hide_typing_keyboard else R.string.show_typing_keyboard)
        if (show) {
            placeKeyboard()
            refreshTyping()
        }
    }

    fun show(next: BarState) {
        state = next
        rightButton.removeCallbacks(repeatBackspace)

        // Defaults, overridden per state below.
        icon.visibility = View.VISIBLE
        icon.colorFilter = null
        icon.alpha = 1f
        glyph.visibility = View.GONE
        spinner.visibility = View.GONE
        label.visibility = View.VISIBLE
        wave.visibility = View.GONE
        trailing.visibility = View.GONE
        trailing.setCompoundDrawablesRelative(null, null, null, null)
        trailing.setTextColor(context.getColor(R.color.txt_1))
        pill.setBackgroundResource(R.drawable.bg_pill)
        pill.isEnabled = true
        setRight(backspace = true)

        when (next) {
            is BarState.Setup -> {
                dimIcon()
                label.setText(R.string.set_up_server)
                action(R.string.open)
            }
            is BarState.NeedsMic -> {
                dimIcon()
                label.setText(R.string.allow_microphone)
                action(R.string.open)
            }
            is BarState.Idle -> {
                label.setText(R.string.tap_to_speak)
                if (showServerName) {
                    trailing.visibility = View.VISIBLE
                    trailing.text = next.server
                    val colour = when (next.online) {
                        true -> R.color.good
                        false -> R.color.warn
                        null -> R.color.txt_2
                    }
                    val dot = context.getDrawable(R.drawable.dot)?.mutate()
                    dot?.setTintList(ColorStateList.valueOf(context.getColor(colour)))
                    dot?.setBounds(0, 0, dot.intrinsicWidth, dot.intrinsicHeight)
                    trailing.setCompoundDrawablesRelative(dot, null, null, null)
                }
                pill.contentDescription = context.getString(R.string.start_dictation)
            }
            is BarState.Listening -> {
                icon.visibility = View.INVISIBLE
                glyph.visibility = View.VISIBLE
                glyph.setImageResource(R.drawable.ic_stop)
                glyph.setBackgroundResource(R.drawable.bg_circle_cyan)
                glyph.imageTintList = ColorStateList.valueOf(context.getColor(R.color.bg_0))
                label.visibility = View.GONE
                wave.visibility = View.VISIBLE
                trailing.visibility = View.VISIBLE
                trailing.setTextColor(context.getColor(R.color.cyan_1))
                trailing.text = formatTime(0)
                pill.setBackgroundResource(R.drawable.bg_pill_listening)
                pill.contentDescription = context.getString(R.string.stop_and_insert)
                setRight(backspace = false)
            }
            is BarState.Finishing -> {
                icon.visibility = View.INVISIBLE
                spinner.visibility = View.VISIBLE
                label.setText(R.string.finishing_up)
                pill.isEnabled = false
                pill.contentDescription = context.getString(R.string.finishing_up)
            }
            is BarState.Unreachable -> {
                icon.visibility = View.INVISIBLE
                glyph.visibility = View.VISIBLE
                glyph.setImageResource(R.drawable.ic_offline)
                glyph.setBackgroundResource(R.drawable.bg_circle_muted)
                glyph.imageTintList = ColorStateList.valueOf(context.getColor(R.color.warn))
                label.text = when {
                    next.label != null -> next.label
                    showServerName && next.server.isNotEmpty() -> context.getString(R.string.cant_reach, next.server)
                    else -> context.getString(R.string.cant_reach_server)
                }
                action(R.string.retry)
                pill.setBackgroundResource(R.drawable.bg_pill_offline)
                // With a recording waiting, the right button throws it away.
                setRight(backspace = !next.holdsAudio)
            }
            is BarState.Problem -> {
                dimIcon()
                label.text = next.label
                action(R.string.open)
            }
            is BarState.Notice -> {
                dimIcon()
                label.text = next.label
                pill.contentDescription = next.label
            }
        }
        if (next !is BarState.Listening) wave.clear()

        // While a dictation is running, or waiting to be sent again, its live
        // text is the field's composing text: typing would overwrite it.
        val dictating = next is BarState.Listening || next is BarState.Finishing ||
            (next is BarState.Unreachable && next.holdsAudio)
        keysView.isEnabled = !dictating
        keysView.alpha = if (dictating) 0.45f else 1f
        refreshTyping()
    }

    fun pushLevel(level: Float) = wave.push(level)

    fun setSeconds(seconds: Int) {
        if (state is BarState.Listening) trailing.text = formatTime(seconds)
    }

    fun setFold(feature: FoldingFeature?) {
        fold = feature
        applyLayout()
    }

    /** Re-reads the position setting; call when the keyboard is shown again. */
    fun refreshLayout() {
        applied = null
        applyLayout()
    }

    private fun dimIcon() {
        icon.colorFilter = grey
        icon.alpha = 0.75f
    }

    /** The word at the end of the pill that says what a tap does. */
    private fun action(text: Int) {
        trailing.visibility = View.VISIBLE
        trailing.setText(text)
        trailing.setTextColor(context.getColor(R.color.cyan_0))
        pill.contentDescription = "${label.text}. ${context.getString(text)}"
    }

    private fun setRight(backspace: Boolean) {
        rightIsBackspace = backspace
        rightButton.setImageResource(if (backspace) R.drawable.ic_backspace else R.drawable.ic_discard)
        rightButton.contentDescription =
            context.getString(if (backspace) R.string.backspace else R.string.discard_recording)
    }

    private fun formatTime(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

    /** Sizes and places the typing keyboard for this window and hand; see [KeyboardPlacement]. */
    private fun placeKeyboard() {
        val widthPx = root.width - root.paddingLeft - root.paddingRight
        if (widthPx <= 0) return
        val place = KeyboardPlacement.compute((widthPx / density).toInt(), prefs.hand, verticalFold())
        val params = block.layoutParams as FrameLayout.LayoutParams
        val width = if (place.leftDp == 0 && place.widthDp * density >= widthPx - density) {
            FrameLayout.LayoutParams.MATCH_PARENT
        } else {
            (place.widthDp * density).toInt()
        }
        val left = (place.leftDp * density).toInt()
        keysView.rowHeightDp = KeyboardLayout.rowHeightFor(context.resources.configuration.screenHeightDp)
        if (params.width != width || params.leftMargin != left) {
            params.width = width
            params.leftMargin = left
            block.layoutParams = params
        }
    }

    /** Sizes the pill and the spaces beside it for this window; see [BarLayout]. */
    private fun applyLayout() {
        // The row's room: the bar's width less any side insets.
        val widthPx = root.width - root.paddingLeft - root.paddingRight
        if (widthPx <= 0) return
        if (expanded) placeKeyboard()
        val widthDp = (widthPx / density).toInt()
        val result = BarLayout.compute(widthDp, prefs.pillPosition, verticalFold())
        if (result == applied) return
        applied = result

        val pillParams = pill.layoutParams as LinearLayout.LayoutParams
        val leftParams = spaceLeft.layoutParams as LinearLayout.LayoutParams
        val rightParams = spaceRight.layoutParams as LinearLayout.LayoutParams
        if (result.pillFills) {
            pillParams.width = 0
            pillParams.weight = 1f
            leftParams.weight = 0f
            rightParams.weight = 0f
        } else {
            pillParams.width = (result.pillWidthDp * density).toInt()
            pillParams.weight = 0f
            leftParams.weight = result.leftSpaceShare
            rightParams.weight = 1f - result.leftSpaceShare
        }
        pill.layoutParams = pillParams
        spaceLeft.layoutParams = leftParams
        spaceRight.layoutParams = rightParams
        row.requestLayout()

        if (showServerName != result.showServerName) {
            showServerName = result.showServerName
            show(state)
        }
    }

    /** The fold as [BarLayout] wants it: only when it runs top to bottom through the bar. */
    private fun verticalFold(): BarLayout.VerticalFold? {
        val feature = fold ?: return null
        if (feature.orientation != FoldingFeature.Orientation.VERTICAL) return null
        val origin = IntArray(2)
        root.getLocationInWindow(origin)
        // In the row's own coordinates, which start after any left inset.
        val rowStart = origin[0] + root.paddingLeft
        val rowWidth = root.width - root.paddingLeft - root.paddingRight
        val left = feature.bounds.left - rowStart
        val right = feature.bounds.right - rowStart
        if (right <= 0 || left >= rowWidth) return null
        return BarLayout.VerticalFold(
            leftDp = (left / density).toInt(),
            rightDp = ceil(right / density).toInt(),
            bent = feature.state == FoldingFeature.State.HALF_OPENED,
        )
    }
}
