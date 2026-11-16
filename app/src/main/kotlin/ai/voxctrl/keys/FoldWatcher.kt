package ai.voxctrl.keys

import android.content.Context
import android.os.Build
import androidx.core.util.Consumer
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo

/**
 * Tells the bar where the fold is and whether the device is bent, so the
 * microphone pill can keep off the hinge (see BarLayout in the core module).
 *
 * Fold information reaches a keyboard only on Android 11 and newer, and only
 * on devices that report it. Anywhere else this does nothing and the bar lays
 * itself out by width alone, which is always usable.
 */
class FoldWatcher(private val context: Context, private val onChange: (FoldingFeature?) -> Unit) {
    private var adapter: WindowInfoTrackerCallbackAdapter? = null
    private val consumer = Consumer<WindowLayoutInfo> { info ->
        onChange(info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull())
    }

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || adapter != null) return
        try {
            val created = WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(context))
            created.addWindowLayoutInfoListener(context, context.mainExecutor, consumer)
            adapter = created
        } catch (_: Throwable) {
            adapter = null
        }
    }

    fun stop() {
        try {
            adapter?.removeWindowLayoutInfoListener(consumer)
        } catch (_: Throwable) {
        }
        adapter = null
    }
}
