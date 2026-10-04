package com.keyguard.ime

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import com.keyguard.ime.audio.NativeSoundBridge
import com.keyguard.ime.haptics.HapticManager
import com.keyguard.ime.haptics.KeyCategory
import com.keyguard.ime.permissions.PermissionTrampolineActivity
import com.keyguard.ime.ui.ImeLifecycleOwner
import com.keyguard.ime.ui.KeyboardScreen

/**
 * KeyGuardService
 *
 * Core InputMethodService implementation providing cybersecure, air-gapped text entry.
 * Integrates:
 *  1. Native C++ Oboe mechanical sound engine (sub-15ms touch-to-sound).
 *  2. Differentiated hardware haptic actuator patterns.
 *  3. Jetpack Compose UI hosted via ImeLifecycleOwner.
 *  4. Strict anti-keylogger password field detection.
 *  5. Proactive LMK (Low Memory Killer) cache eviction.
 *  6. Transparent PermissionTrampolineActivity for runtime RECORD_AUDIO requests.
 */
class KeyGuardService : InputMethodService() {

    companion object {
        private const val TAG = "KeyGuardService"
    }

    private val imeLifecycleOwner = ImeLifecycleOwner()
    private lateinit var hapticManager: HapticManager

    // Anti-Keylogger / Secure Field invariant state
    private var isSecureTargetState by mutableStateOf(false)

    // Microphone / Speech-to-text permission state
    private var isAudioPermissionGranted by mutableStateOf(false)

    // Volatile transient prediction/input buffer
    private val transientInputBuffer = StringBuilder()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PermissionTrampolineActivity.ACTION_RECORD_AUDIO_RESULT) {
                val granted = intent.getBooleanExtra(PermissionTrampolineActivity.EXTRA_IS_GRANTED, false)
                Log.i(TAG, "KeyGuardService received RECORD_AUDIO permission status: $granted")
                isAudioPermissionGranted = granted
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "KeyGuardService onCreate: Initializing lifecycle, audio, and haptics.")

        imeLifecycleOwner.onCreate()
        hapticManager = HapticManager(this)

        // Pre-initialize native Oboe audio engine
        NativeSoundBridge.ensureInitialized()

        // Register permission outcome receiver
        val filter = IntentFilter(PermissionTrampolineActivity.ACTION_RECORD_AUDIO_RESULT)
        ContextCompat.registerReceiver(
            this,
            permissionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onCreateInputView(): View {
        imeLifecycleOwner.onStart()
        imeLifecycleOwner.onResume()

        return ComposeView(this).apply {
            // Attach ViewTree owners for Compose resolution in Service context
            imeLifecycleOwner.attachToView(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                MaterialTheme {
                    KeyboardScreen(
                        isSecureTarget = isSecureTargetState,
                        onKeyDown = { category ->
                            // INVARIANT 3: Fire audio and haptic feedback immediately on touch-down
                            NativeSoundBridge.triggerClick()
                            hapticManager.playHaptic(category)
                        },
                        onKeyUp = { key, category ->
                            handleKeyCommit(key, category)
                        },
                        onDictationClick = {
                            requestAudioPermission()
                        }
                    )
                }
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        evaluateSecureTarget(attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        evaluateSecureTarget(info)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        imeLifecycleOwner.onStart()
        imeLifecycleOwner.onResume()
        NativeSoundBridge.ensureInitialized()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        imeLifecycleOwner.onPause()
        imeLifecycleOwner.onStop()
        wipeTransientBuffers()
    }

    /**
     * Anti-Keylogger & Secure Field Rule:
     * Inspects EditorInfo inputType flags to identify password, PIN, or credential entry fields.
     */
    private fun evaluateSecureTarget(editorInfo: EditorInfo?) {
        val inputType = editorInfo?.inputType ?: InputType.TYPE_NULL
        val maskClass = inputType and InputType.TYPE_MASK_CLASS
        val maskVariation = inputType and InputType.TYPE_MASK_VARIATION

        val isPasswordClass = when (maskClass) {
            InputType.TYPE_CLASS_TEXT -> {
                maskVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                maskVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                maskVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            }
            InputType.TYPE_CLASS_NUMBER -> {
                maskVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
            else -> false
        }

        val noLearningFlag = (editorInfo?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
        val isSecure = isPasswordClass || noLearningFlag

        isSecureTargetState = isSecure
        if (isSecure) {
            Log.i(TAG, "Secure target input field detected (InputType=0x${Integer.toHexString(inputType)}). Enforcing air-gap lockdown.")
            wipeTransientBuffers()
        }
    }

    /**
     * Dispatches key entry events to InputConnection upon key release.
     */
    private fun handleKeyCommit(key: String, category: KeyCategory) {
        val ic = currentInputConnection ?: return
        when (category) {
            KeyCategory.STANDARD -> {
                ic.commitText(key, 1)
                if (!isSecureTargetState) {
                    transientInputBuffer.append(key)
                }
            }
            KeyCategory.SPACEBAR -> {
                ic.commitText(" ", 1)
                if (!isSecureTargetState) {
                    transientInputBuffer.append(" ")
                }
            }
            KeyCategory.BACKSPACE -> {
                ic.deleteSurroundingText(1, 0)
                if (transientInputBuffer.isNotEmpty()) {
                    transientInputBuffer.deleteCharAt(transientInputBuffer.length - 1)
                }
            }
            KeyCategory.ENTER -> {
                sendKeyChar('\n')
                if (!isSecureTargetState) {
                    transientInputBuffer.setLength(0)
                }
            }
        }
    }

    /**
     * Wipes all volatile in-memory transient keystroke and prediction buffers.
     */
    private fun wipeTransientBuffers() {
        if (transientInputBuffer.isNotEmpty()) {
            for (i in 0 until transientInputBuffer.length) {
                transientInputBuffer.setCharAt(i, '\u0000')
            }
            transientInputBuffer.setLength(0)
        }
    }

    /**
     * Low Memory Killer (LMK) Resilience:
     * Drops non-essential audio streams and flushes transient caches under memory pressure.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "onTrimMemory received with level=$level. Evaluating cache eviction.")

        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE
        ) {
            Log.w(TAG, "High memory pressure: Tearing down audio stream and wiping transient caches.")
            wipeTransientBuffers()
            NativeSoundBridge.teardown()
            System.gc()
        }
    }

    /**
     * Solves InputMethodService permission catch-22:
     * Dispatches runtime RECORD_AUDIO permission request through transparent Activity trampoline.
     */
    fun requestAudioPermission() {
        if (isSecureTargetState) {
            Log.w(TAG, "Speech-to-text dictation blocked: Input field is marked as secure password/PIN.")
            return
        }
        PermissionTrampolineActivity.launch(this)
    }

    override fun onDestroy() {
        Log.i(TAG, "KeyGuardService onDestroy: Cleaning up audio, permissions, and lifecycle.")
        try {
            unregisterReceiver(permissionReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Permission receiver was not registered or already unregistered", e)
        }
        wipeTransientBuffers()
        NativeSoundBridge.teardown()
        imeLifecycleOwner.onDestroy()
        super.onDestroy()
    }
}
