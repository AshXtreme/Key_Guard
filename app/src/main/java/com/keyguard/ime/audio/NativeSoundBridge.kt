package com.keyguard.ime.audio

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * NativeSoundBridge
 *
 * Thread-safe singleton providing the bridge to the native C++ Oboe audio engine (libkeyguard_audio.so).
 * Exposes low-latency keystroke audio triggering and asset sample extraction.
 */
object NativeSoundBridge {

    private const val TAG = "NativeSoundBridge"

    @Volatile
    private var isLibraryLoaded = false

    private val isEngineInitialized = AtomicBoolean(false)

    init {
        try {
            System.loadLibrary("keyguard_audio")
            isLibraryLoaded = true
            Log.i(TAG, "libkeyguard_audio.so loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Fatal: Failed to load libkeyguard_audio.so", e)
        }
    }

    /**
     * Initializes the native Oboe stream if not already active.
     * Thread-safe; can be called concurrently.
     */
    fun ensureInitialized(): Boolean {
        if (!isLibraryLoaded) return false
        if (isEngineInitialized.get()) return true

        synchronized(this) {
            if (!isEngineInitialized.get()) {
                val success = nativeInit()
                if (success) {
                    isEngineInitialized.set(true)
                    Log.i(TAG, "Native Oboe audio engine initialized.")
                } else {
                    Log.e(TAG, "Failed to initialize native Oboe audio engine.")
                }
            }
        }
        return isEngineInitialized.get()
    }

    /**
     * Triggers a low-latency mechanical keystroke click.
     * Operates lock-free on UI/touch threads.
     */
    fun triggerClick() {
        if (!isLibraryLoaded) return
        if (!isEngineInitialized.get()) {
            if (!ensureInitialized()) return
        }
        nativeTriggerClick()
    }

    /**
     * Loads a 16-bit 44.1kHz mono PCM or WAV asset into the native audio ring buffer.
     * Automatically parses standard RIFF/WAVE headers or reads raw 16-bit PCM.
     *
     * @param context Application/service context for asset extraction.
     * @param assetPath Path within the app's assets/ directory (e.g. "sounds/cherry_mx_blue.wav").
     * @return true if sample was successfully parsed and loaded into native memory.
     */
    fun loadSampleFromAssets(context: Context, assetPath: String): Boolean {
        if (!isLibraryLoaded) return false
        ensureInitialized()

        return try {
            context.assets.open(assetPath).use { inputStream ->
                val rawBytes = inputStream.readBytes()
                val pcmSamples = parseWavOrPcm(rawBytes)
                if (pcmSamples.isNotEmpty()) {
                    val loaded = nativeLoadSample(pcmSamples)
                    if (loaded) {
                        Log.i(TAG, "Loaded sample from assets: $assetPath (${pcmSamples.size} frames)")
                    } else {
                        Log.e(TAG, "Native engine failed to load sample from: $assetPath")
                    }
                    loaded
                } else {
                    Log.e(TAG, "Parsed empty PCM sample buffer from: $assetPath")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading audio asset: $assetPath", e)
            false
        }
    }

    /**
     * Tears down the native audio engine and wipes sensitive memory buffers.
     */
    fun teardown() {
        if (!isLibraryLoaded) return
        synchronized(this) {
            if (isEngineInitialized.get()) {
                nativeTeardown()
                isEngineInitialized.set(false)
                Log.i(TAG, "Native audio engine torn down.")
            }
        }
    }

    /**
     * Parses standard 16-bit PCM WAV (RIFF) or treats byte array as raw little-endian 16-bit PCM.
     * Downmixes stereo to mono if necessary.
     */
    private fun parseWavOrPcm(bytes: ByteArray): ShortArray {
        if (bytes.size >= 44 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() && bytes[11] == 'E'.code.toByte()
        ) {
            val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 12
            var channels = 1
            var bitsPerSample = 16
            var dataOffset = -1
            var dataSize = 0

            while (offset + 8 <= bytes.size) {
                val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = byteBuffer.getInt(offset + 4)
                offset += 8

                when (chunkId) {
                    "fmt " -> {
                        channels = byteBuffer.getShort(offset + 2).toInt()
                        bitsPerSample = byteBuffer.getShort(offset + 14).toInt()
                    }
                    "data" -> {
                        dataOffset = offset
                        dataSize = chunkSize
                        break
                    }
                }
                offset += chunkSize
            }

            if (dataOffset != -1 && bitsPerSample == 16 && dataOffset + dataSize <= bytes.size) {
                val totalFrames = dataSize / (2 * channels)
                val samples = ShortArray(totalFrames)
                var readPtr = dataOffset

                for (i in 0 until totalFrames) {
                    if (channels == 1) {
                        samples[i] = byteBuffer.getShort(readPtr)
                        readPtr += 2
                    } else {
                        val left = byteBuffer.getShort(readPtr).toInt()
                        val right = byteBuffer.getShort(readPtr + 2).toInt()
                        samples[i] = ((left + right) / 2).toShort()
                        readPtr += channels * 2
                    }
                }
                return samples
            }
        }

        // Fallback: raw 16-bit PCM little-endian
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = bytes.size / 2
        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            samples[i] = byteBuffer.getShort(i * 2)
        }
        return samples
    }

    // JNI Native Declarations
    private external fun nativeInit(): Boolean
    private external fun nativeTriggerClick()
    private external fun nativeLoadSample(samples: ShortArray): Boolean
    private external fun nativeTeardown()
}
