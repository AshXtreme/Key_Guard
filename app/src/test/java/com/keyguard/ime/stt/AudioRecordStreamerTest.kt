package com.keyguard.ime.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

/**
 * AudioRecordStreamerTest
 *
 * Verifies core STT invariants:
 *  1. Root-Mean-Square (RMS) energy calculation across PCM chunks.
 *  2. Linear PCM normalization from 16-bit integers to [-1.0f, 1.0f] floats.
 *  3. NIST SP 800-88 memory hygiene: immediate in-place zeroization of PCM buffers.
 */
class AudioRecordStreamerTest {

    @Test
    fun testRmsCalculation_silenceYieldsZero() {
        val silentBuffer = ShortArray(320) { 0 }
        val rms = AudioRecordStreamer.calculateRms(silentBuffer, silentBuffer.size)
        assertEquals(0.0f, rms, 0.0001f)
    }

    @Test
    fun testRmsCalculation_dcSignal() {
        val dcBuffer = ShortArray(320) { 1000 }
        val rms = AudioRecordStreamer.calculateRms(dcBuffer, dcBuffer.size)
        assertEquals(1000.0f, rms, 0.1f)
    }

    @Test
    fun testNormalizePcm_convertsShortRangeToFloatRange() {
        val input = shortArrayOf(-32768, 0, 16384, 32767)
        val output = FloatArray(4)

        AudioRecordStreamer.normalizePcm(input, output, 4)

        assertEquals(-1.0f, output[0], 0.001f)
        assertEquals(0.0f, output[1], 0.001f)
        assertEquals(0.5f, output[2], 0.001f)
        assertTrue("Max short should be close to 1.0f", output[3] > 0.999f && output[3] <= 1.0f)
    }

    @Test
    fun testMemoryHygiene_wipeBufferZerosShortArray() {
        val sensitivePcm = ShortArray(320) { (it * 10).toShort() }
        assertTrue("Buffer must contain non-zero audio initially", sensitivePcm.any { it != 0.toShort() })

        AudioRecordStreamer.wipeBuffer(sensitivePcm)

        assertTrue("NIST SP 800-88 Invariant: All short samples must be overwritten with 0", sensitivePcm.all { it == 0.toShort() })
    }

    @Test
    fun testMemoryHygiene_wipeBufferZerosFloatArray() {
        val sensitiveFloats = FloatArray(320) { 0.75f }
        assertTrue("Buffer must contain audio data initially", sensitiveFloats.any { it != 0f })

        AudioRecordStreamer.wipeBuffer(sensitiveFloats)

        assertTrue("NIST SP 800-88 Invariant: All float samples must be overwritten with 0.0f", sensitiveFloats.all { it == 0f })
    }
}
