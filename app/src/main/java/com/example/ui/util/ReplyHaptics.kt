package com.example.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Subtle Grok-style haptic pulses triggered when a companion bot starts thinking
 * or finishes delivering a reply. Uses gentle short waveforms / predefined effects
 * with graceful fallback to Compose HapticFeedback.
 */
object ReplyHaptics {

  fun triggerSoftTick(context: Context, fallbackHaptic: HapticFeedback? = null) {
    val vibrator = resolveVibrator(context)
    if (vibrator != null && vibrator.hasVibrator()) {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
          return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(12L, 75))
          return
        }
        @Suppress("DEPRECATION")
        vibrator.vibrate(12L)
        return
      } catch (_: SecurityException) {
        // Fallback below
      } catch (_: Throwable) {
        // Fallback below
      }
    }
    fallbackHaptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
  }

  fun triggerReplyPulse(context: Context, fallbackHaptic: HapticFeedback? = null) {
    val vibrator = resolveVibrator(context)
    if (vibrator != null && vibrator.hasVibrator()) {
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(
              longArrayOf(0L, 14L, 38L, 20L),
              intArrayOf(0, 80, 0, 125),
              -1,
            )
          } else {
            VibrationEffect.createOneShot(22L, VibrationEffect.DEFAULT_AMPLITUDE)
          }
          vibrator.vibrate(effect)
          return
        }
        @Suppress("DEPRECATION")
        vibrator.vibrate(longArrayOf(0L, 14L, 38L, 20L), -1)
        return
      } catch (_: SecurityException) {
        // Fallback below
      } catch (_: Throwable) {
        // Fallback below
      }
    }
    fallbackHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
  }

  private fun resolveVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      manager?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }
}
