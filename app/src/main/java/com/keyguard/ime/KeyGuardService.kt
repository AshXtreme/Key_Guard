package com.keyguard.ime

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import com.keyguard.ime.stt.AudioRecordStreamer
import com.keyguard.ime.stt.SherpaSpeechEngine
import com.keyguard.ime.stt.SpeechRecognitionListener
import com.keyguard.ime.stt.VoiceCommand
import com.keyguard.ime.ui.ImeLifecycleOwner
import com.keyguard.ime.ui.KeyboardScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
 *  7. Offline streaming Sherpa-ONNX speech-to-text with VAD and voice command execution.
 */
class KeyGuardService : InputMethodService() {

    companion object {
        private const val TAG = "KeyGuardService"
    }

    private val imeLifecycleOwner = ImeLifecycleOwner()
    private lateinit var hapticManager: HapticManager

    // Speech-to-Text & Audio Streaming
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var speechEngine: SherpaSpeechEngine
    private var audioRecordStreamer: AudioRecordStreamer? = null

    // Anti-Keylogger / Secure Field invariant state
    private var isSecureTargetState by mutableStateOf(false)

    // Microphone / Speech-to-text permission & dictation state
    private var isAudioPermissionGranted by mutableStateOf(false)
    private var isDictatingState by mutableStateOf(false)

    // Volatile transient prediction/input buffer
    private val transientInputBuffer = StringBuilder()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PermissionTrampolineActivity.ACTION_RECORD_AUDIO_RESULT) {
                val granted = intent.getBooleanExtra(PermissionTrampolineActivity.EXTRA_IS_GRANTED, false)
                Log.i(TAG, "KeyGuardService received RECORD_AUDIO permission status: $granted")
                isAudioPermissionGranted = granted
                if (granted && !isSecureTargetState && !isDictatingState) {
                    startDictation()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "KeyGuardService onCreate: Initializing lifecycle, audio, haptics, and STT engine.")

        imeLifecycleOwner.onCreate()
        hapticManager = HapticManager(this)

        // Initialize Sherpa-ONNX offline speech engine
        speechEngine = SherpaSpeechEngine(
            context = applicationContext,
            listener = object : SpeechRecognitionListener {
                override fun onPartialToken(token: String) {
                    serviceScope.launch(Dispatchers.Main) {
                        commitDictatedToken(token)
                    }
                }

                override fun onSegmentComplete(text: String) {
                    serviceScope.launch(Dispatchers.Main) {
                        commitDictatedToken(text)
                    }
                }

                override fun onVoiceCommand(command: VoiceCommand) {
                    serviceScope.launch(Dispatchers.Main) {
                        executeVoiceCommand(command)
                    }
                }

                override fun onError(errorMessage: String) {
                    Log.e(TAG, "SherpaSpeechEngine error: $errorMessage")
                    serviceScope.launch(Dispatchers.Main) {
                        stopDictation()
                    }
                }
            }
        )

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
                        isDictating = isDictatingState,
                        onKeyDown = { category ->
                            // INVARIANT 3: Fire audio and haptic feedback immediately on touch-down
                            NativeSoundBridge.triggerClick()
                            hapticManager.playHaptic(category)
                        },
                        onKeyUp = { key, category ->
                            handleKeyCommit(key, category)
                        },
                        onDictationClick = {
                            toggleDictation()
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
        stopDictation()
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
            stopDictation()
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
     * Streams recognized speech token directly into currentInputConnection.
     */
    private fun commitDictatedToken(token: String) {
        if (token.isEmpty() || isSecureTargetState) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(1, 0)
        val needsSpace = before != null && before.isNotEmpty() && before[0] != ' ' && before[0] != '\n'
        val textToCommit = if (needsSpace) " $token" else token
        ic.commitText(textToCommit, 1)
        if (!isSecureTargetState) {
            transientInputBuffer.append(textToCommit)
        }
    }

    /**
     * Executes recognized hands-free voice command (PRD §5.2).
     */
    private fun executeVoiceCommand(command: VoiceCommand) {
        val ic = currentInputConnection ?: return
        when (command) {
            VoiceCommand.DELETE_LAST_WORD -> {
                val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
                if (textBefore.isNotEmpty()) {
                    val trimmed = textBefore.trimEnd()
                    val lastWordIndex = trimmed.lastIndexOf(' ')
                    val charsToDelete = if (lastWordIndex == -1) {
                        textBefore.length
                    } else {
                        textBefore.length - (lastWordIndex + 1)
                    }
                    ic.deleteSurroundingText(charsToDelete, 0)
                    Log.i(TAG, "Voice command executed: DELETE_LAST_WORD ($charsToDelete chars removed)")
                }
            }
            VoiceCommand.NEW_LINE -> {
                ic.commitText("\n", 1)
                Log.i(TAG, "Voice command executed: NEW_LINE")
            }
            VoiceCommand.SPACE -> {
                ic.commitText(" ", 1)
                Log.i(TAG, "Voice command executed: SPACE")
            }
            VoiceCommand.CLEAR_ALL -> {
                val textBefore = ic.getTextBeforeCursor(2000, 0)?.length ?: 0
                val textAfter = ic.getTextAfterCursor(2000, 0)?.length ?: 0
                ic.deleteSurroundingText(textBefore, textAfter)
                Log.i(TAG, "Voice command executed: CLEAR_ALL")
            }
        }
    }

    /**
     * Toggles real-time streaming dictation session.
     * Hard-locked if isSecureTargetState is true.
     */
    fun toggleDictation() {
        if (isSecureTargetState) {
            Log.w(TAG, "Speech-to-text dictation blocked: Input field is marked as secure password/PIN.")
            return
        }

        if (isDictatingState) {
            stopDictation()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                startDictation()
            } else {
                PermissionTrampolineActivity.launch(this)
            }
        }
    }

    /**
     * Starts 16kHz mono audio capture and Sherpa-ONNX streaming session.
     */
    private fun startDictation() {
        if (isSecureTargetState) {
            Log.w(TAG, "startDictation rejected: Secure password target.")
            return
        }
        if (isDictatingState) return

        speechEngine.initialize()
        val streamer = AudioRecordStreamer(
            listener = { samples, isSpeech, _ ->
                speechEngine.acceptWaveform(samples, isSpeech)
            }
        )
        audioRecordStreamer = streamer

        val started = streamer.start(serviceScope)
        if (started) {
            isDictatingState = true
            Log.i(TAG, "Offline streaming dictation started.")
        } else {
            Log.e(TAG, "Failed to start AudioRecordStreamer.")
            isDictatingState = false
            audioRecordStreamer = null
        }
    }

    /**
     * Stops audio capture, releases microphone hardware, and resets recognizer session.
     */
    private fun stopDictation() {
        if (!isDictatingState && audioRecordStreamer == null) return
        Log.i(TAG, "Stopping offline dictation.")
        audioRecordStreamer?.stop()
        audioRecordStreamer = null
        speechEngine.resetSession()
        isDictatingState = false
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
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            Log.w(TAG, "High memory pressure: Tearing down audio stream, STT session, and wiping transient caches.")
            stopDictation()
            speechEngine.release()
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
        Log.i(TAG, "KeyGuardService onDestroy: Cleaning up audio, permissions, STT session, and lifecycle.")
        try {
            unregisterReceiver(permissionReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Permission receiver was not registered or already unregistered", e)
        }
        stopDictation()
        speechEngine.release()
        serviceScope.cancel()
        wipeTransientBuffers()
        NativeSoundBridge.teardown()
        imeLifecycleOwner.onDestroy()
        super.onDestroy()
    }
}
