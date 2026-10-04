package com.keyguard.ime.stt

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Arrays
import kotlin.math.max
import kotlin.math.sqrt

/**
 * AudioFrameListener
 *
 * Real-time callback for 16kHz mono audio frames and energy-based Voice Activity Detection.
 */
fun interface AudioFrameListener {
    /**
     * Invoked for every captured audio chunk.
     *
     * @param samples Normalized 16kHz audio samples in range [-1.0f, 1.0f].
     * @param isSpeech True if current chunk exceeds the VAD energy threshold or hangover window.
     * @param energyRms Root-Mean-Square energy metric of the chunk.
     */
    fun onAudioFrame(samples: FloatArray, isSpeech: Boolean, energyRms: Float)
}

/**
 * AudioRecordStreamer
 *
 * 16kHz Mono 16-bit PCM capture loop running on an IO dispatcher thread.
 * Features:
 *  1. 100% Air-Gapped: Audio data resides solely in local volatile memory.
 *  2. NIST SP 800-88 Memory Sanitization: Raw PCM buffers are zero-wiped with
 *     `Arrays.fill(pcmBuffer, 0)` immediately after passing to the decoder.
 *  3. Energy-Based Voice Activity Detection (VAD) with hangover frames to prevent
 *     chopping speech boundaries.
 */
class AudioRecordStreamer(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val vadThresholdRms: Float = 450f,
    private val listener: AudioFrameListener
) {
    companion object {
        private const val TAG = "AudioRecordStreamer"
        const val CHUNK_DURATION_MS = 20 // 20ms chunk = 320 samples at 16kHz

        /**
         * Calculates Root-Mean-Square (RMS) energy metric across PCM audio chunk.
         */
        fun calculateRms(buffer: ShortArray, readSamples: Int): Float {
            if (readSamples <= 0) return 0f
            var sumSquares = 0.0
            for (i in 0 until readSamples) {
                val s = buffer[i].toDouble()
                sumSquares += s * s
            }
            return sqrt(sumSquares / readSamples).toFloat()
        }

        /**
         * Normalizes 16-bit PCM samples [-32768, 32767] to [-1.0f, 1.0f].
         */
        fun normalizePcm(source: ShortArray, destination: FloatArray, count: Int) {
            for (i in 0 until count) {
                destination[i] = source[i] / 32768.0f
            }
        }

        /**
         * Overwrites ShortArray in-place with zeros (NIST SP 800-88).
         */
        fun wipeBuffer(buffer: ShortArray) {
            Arrays.fill(buffer, 0.toShort())
        }

        /**
         * Overwrites FloatArray in-place with zeros.
         */
        fun wipeBuffer(buffer: FloatArray) {
            Arrays.fill(buffer, 0f)
        }
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Starts the audio capture loop on Dispatchers.IO within the provided CoroutineScope.
     */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): Boolean {
        synchronized(this) {
            if (isRecording) return true

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "AudioRecord.getMinBufferSize reported invalid hardware configuration.")
                return false
            }

            val samplesPerChunk = (sampleRate * CHUNK_DURATION_MS) / 1000 // 320 samples
            val internalBufferSize = max(minBufferSize, samplesPerChunk * 4 * 2)

            return try {
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    internalBufferSize
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize hardware state.")
                    record.release()
                    return false
                }

                record.startRecording()
                audioRecord = record
                isRecording = true

                captureJob = scope.launch(Dispatchers.IO) {
                    captureLoop(record, samplesPerChunk)
                }

                Log.i(TAG, "AudioRecordStreamer started successfully (16kHz Mono 16-bit PCM).")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Exception during AudioRecord startup", e)
                stop()
                false
            }
        }
    }

    private fun captureLoop(record: AudioRecord, chunkSize: Int) {
        val rawPcmBuffer = ShortArray(chunkSize)
        val normalizedFloatBuffer = FloatArray(chunkSize)

        // Hangover counter: keep VAD active for ~300ms (15 frames @ 20ms) after speech dip
        var speechHangoverFrames = 0
        val maxHangover = 15

        try {
            while (captureJob?.isActive == true && isRecording) {
                val readSamples = record.read(rawPcmBuffer, 0, chunkSize)
                if (readSamples <= 0) continue

                // 1. Calculate Root-Mean-Square (RMS) energy
                val rms = calculateRms(rawPcmBuffer, readSamples)

                // 2. Energy-based VAD calculation with hangover smoothing
                val exceedsThreshold = rms >= vadThresholdRms
                if (exceedsThreshold) {
                    speechHangoverFrames = maxHangover
                } else if (speechHangoverFrames > 0) {
                    speechHangoverFrames--
                }
                val isSpeech = exceedsThreshold || speechHangoverFrames > 0

                // 3. Normalize 16-bit PCM samples [-32768, 32767] to [-1.0f, 1.0f]
                normalizePcm(rawPcmBuffer, normalizedFloatBuffer, readSamples)

                // INVARIANT 2: Wipe raw PCM buffer immediately from memory (NIST SP 800-88)
                wipeBuffer(rawPcmBuffer)

                // 4. Dispatch to frame listener
                listener.onAudioFrame(normalizedFloatBuffer, isSpeech, rms)

                // Zero-wipe normalized float buffer
                wipeBuffer(normalizedFloatBuffer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception encountered in AudioRecord capture loop", e)
        } finally {
            // Guarantee sanitization on loop termination
            wipeBuffer(rawPcmBuffer)
            wipeBuffer(normalizedFloatBuffer)
        }
    }

    /**
     * Stops audio recording and releases hardware microphone resources.
     */
    fun stop() {
        synchronized(this) {
            isRecording = false
            captureJob?.cancel()
            captureJob = null

            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Exception releasing AudioRecord instance", e)
            } finally {
                audioRecord = null
            }
            Log.i(TAG, "AudioRecordStreamer stopped and hardware audio buffer released.")
        }
    }
}
