package com.keyguard.ime.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * KeyCategory
 *
 * Distinct key classifications mapped to differentiated tactile sensations
 * conforming to KeyGuard PRD §5.1.
 */
enum class KeyCategory {
    STANDARD,
    SPACEBAR,
    BACKSPACE,
    ENTER
}

/**
 * HapticManager
 *
 * Hardware vibration manager leveraging Android's native Vibrator and VibratorManager APIs.
 * Generates tactile haptic responses directly through the Android OS HAL without
 * spawning custom background threads.
 */
class HapticManager(context: Context) {

    companion object {
        private const val TAG = "HapticManager"
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isEnabled: Boolean = true

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .build()

    // 1. Standard keys: 8ms light tap
    private val standardEffect: VibrationEffect by lazy {
        VibrationEffect.createOneShot(8L, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    // 2. Spacebar: 16ms resonant pulse
    private val spacebarEffect: VibrationEffect by lazy {
        VibrationEffect.createOneShot(16L, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    // 3. Backspace: Warning double-pulse (10ms pulse, 25ms gap, 12ms pulse)
    private val backspaceEffect: VibrationEffect by lazy {
        val timings = longArrayOf(0L, 10L, 25L, 12L)
        if (vibrator?.hasAmplitudeControl() == true) {
            val amplitudes = intArrayOf(0, 180, 0, 220)
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
    }

    // 4. Enter: 24ms heavy confirmation thud
    private val enterEffect: VibrationEffect by lazy {
        if (vibrator?.hasAmplitudeControl() == true) {
            VibrationEffect.createOneShot(24L, 255)
        } else {
            VibrationEffect.createOneShot(24L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    /**
     * Executes the hardware tactile pulse for the specified key category.
     * Uses native OS APIs directly with no background thread overhead.
     *
     * @param category The classification of the key pressed.
     */
    fun performHaptic(category: KeyCategory) {
        if (!isEnabled || vibrator == null || !vibrator.hasVibrator()) {
            return
        }

        val effect = when (category) {
            KeyCategory.STANDARD -> standardEffect
            KeyCategory.SPACEBAR -> spacebarEffect
            KeyCategory.BACKSPACE -> backspaceEffect
            KeyCategory.ENTER -> enterEffect
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val vibrationAttributes = android.os.VibrationAttributes.Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                    .build()
                vibrator.vibrate(effect, vibrationAttributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audioAttributes)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to perform haptic feedback for category: $category", e)
        }
    }
}
