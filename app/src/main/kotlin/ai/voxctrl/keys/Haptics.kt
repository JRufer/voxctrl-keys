package ai.voxctrl.keys

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * The keyboard's touch feedback: one firm click for every key and button.
 *
 * The system's key-tap feedback (`HapticFeedbackConstants.KEYBOARD_TAP`) is a
 * plain click whose strength an app cannot change, and it was too faint for a
 * keyboard. This plays Android's heavy click instead, the platform's stronger
 * version of the same click, through the vibrator directly. It is played as
 * touch feedback, so the system's own switch and intensity for touch
 * vibration still apply.
 */
class Haptics(context: Context) {
    private val resolver = context.contentResolver

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private val click: VibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    fun tap() {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system applies its touch-feedback switch and intensity to this usage itself.
            vibrator.vibrate(click, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else if (touchFeedbackOn()) {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                click,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build(),
            )
        }
    }

    /** Before Android 13: the system's "Touch feedback" switch, which performHapticFeedback obeyed too. */
    @Suppress("DEPRECATION")
    private fun touchFeedbackOn(): Boolean =
        Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
}
