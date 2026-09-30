package com.keyguard.ime.audio

/**
 * NativeAudioEngine
 *
 * Kotlin interface to the low-latency C++ Oboe audio engine (libkeyguard_audio.so).
 * Operates lock-free on real-time audio threads to meet the <15ms latency SLA.
 */
object NativeAudioEngine {

    init {
        System.loadLibrary("keyguard_audio")
    }

    /**
     * Initializes the native Oboe audio stream with Exclusive low-latency attributes
     * and pre-loads the default synthetic click transient.
     *
     * @return true if stream was opened and started successfully.
     */
    external fun nativeInit(): Boolean

    /**
     * Triggers a low-latency mechanical keystroke click.
     * Lock-free, wait-free, zero-allocation call safe for invocation on UI/touch threads.
     */
    external fun nativeTriggerClick()

    /**
     * Loads a custom 16-bit 44.1kHz mono PCM sample buffer into the native engine.
     *
     * @param samples Contiguous PCM int16 audio frames.
     * @return true if sample was loaded successfully.
     */
    external fun nativeLoadSample(samples: ShortArray): Boolean

    /**
     * Stops the Oboe stream, sanitizes all volatile audio sample memory with zero-wipe,
     * and deallocates native resources.
     */
    external fun nativeTeardown()
}
