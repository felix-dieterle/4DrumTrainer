package com.drumtrainer.audio

import com.drumtrainer.model.DrumPart
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class AdaptationManagerTest {

    private val manager = AdaptationManager()

    // ── computeRms ────────────────────────────────────────────────────────────

    @Test
    fun `computeRms of silence is zero`() {
        val signal = FloatArray(512) { 0f }
        assertEquals(0f, manager.computeRms(signal), 0f)
    }

    @Test
    fun `computeRms of constant signal equals its absolute value`() {
        val value = 0.5f
        val signal = FloatArray(512) { value }
        assertEquals(value, manager.computeRms(signal), 1e-6f)
    }

    @Test
    fun `computeRms of empty array returns zero`() {
        assertEquals(0f, manager.computeRms(FloatArray(0)), 0f)
    }

    @Test
    fun `computeRms of unit sine is approximately 0_707`() {
        val n = 512
        val sampleRateHz = 44_100
        val signal = FloatArray(n) { i ->
            Math.sin(2.0 * Math.PI * 1_000 * i / sampleRateHz).toFloat()
        }
        // RMS of a unit sine wave = 1/√2 ≈ 0.7071
        val expected = (1.0 / sqrt(2.0)).toFloat()
        assertEquals(expected, manager.computeRms(signal), 0.01f)
    }

    // ── computeUnrecognizedParts ──────────────────────────────────────────────

    @Test
    fun `computeUnrecognizedParts returns empty set when all parts detected`() {
        val enabled  = setOf(DrumPart.SNARE, DrumPart.BASS_DRUM)
        val detected = setOf(DrumPart.SNARE, DrumPart.BASS_DRUM)
        assertTrue(manager.computeUnrecognizedParts(enabled, detected).isEmpty())
    }

    @Test
    fun `computeUnrecognizedParts returns missing parts`() {
        val enabled  = setOf(DrumPart.SNARE, DrumPart.BASS_DRUM, DrumPart.CRASH)
        val detected = setOf(DrumPart.SNARE)
        val unrecognized = manager.computeUnrecognizedParts(enabled, detected)
        assertEquals(setOf(DrumPart.BASS_DRUM, DrumPart.CRASH), unrecognized)
    }

    @Test
    fun `computeUnrecognizedParts returns all parts when nothing detected`() {
        val enabled = setOf(DrumPart.SNARE, DrumPart.BASS_DRUM)
        val unrecognized = manager.computeUnrecognizedParts(enabled, emptySet())
        assertEquals(enabled, unrecognized)
    }

    @Test
    fun `computeUnrecognizedParts with empty enabled parts returns empty set`() {
        val detected = setOf(DrumPart.SNARE)
        assertTrue(manager.computeUnrecognizedParts(emptySet(), detected).isEmpty())
    }

    @Test
    fun `computeUnrecognizedParts treats HI_HAT_CLOSED and HI_HAT_OPEN as same pad`() {
        // If HI_HAT_CLOSED is enabled but HI_HAT_OPEN was detected, it should NOT be unrecognized
        val enabled  = setOf(DrumPart.HI_HAT_CLOSED)
        val detected = setOf(DrumPart.HI_HAT_OPEN)
        assertTrue(manager.computeUnrecognizedParts(enabled, detected).isEmpty())
    }

    @Test
    fun `computeUnrecognizedParts treats HI_HAT_OPEN and HI_HAT_CLOSED as same pad`() {
        // If HI_HAT_OPEN is enabled but HI_HAT_CLOSED was detected, it should NOT be unrecognized
        val enabled  = setOf(DrumPart.HI_HAT_OPEN)
        val detected = setOf(DrumPart.HI_HAT_CLOSED)
        assertTrue(manager.computeUnrecognizedParts(enabled, detected).isEmpty())
    }

    @Test
    fun `computeUnrecognizedParts HI_HAT is unrecognized when neither variant detected`() {
        val enabled  = setOf(DrumPart.HI_HAT_CLOSED, DrumPart.HI_HAT_OPEN)
        val detected = setOf(DrumPart.SNARE)
        val unrecognized = manager.computeUnrecognizedParts(enabled, detected)
        assertTrue(DrumPart.HI_HAT_CLOSED in unrecognized || DrumPart.HI_HAT_OPEN in unrecognized)
        // Both variants should appear since neither was struck
        assertEquals(setOf(DrumPart.HI_HAT_CLOSED, DrumPart.HI_HAT_OPEN), unrecognized)
    }

    // ── Result data class ─────────────────────────────────────────────────────

    @Test
    fun `Result noiseThreshold equals NOISE_SAFETY_FACTOR times average noise`() {
        val avgNoise = 0.02f
        val expected = avgNoise * AdaptationManager.NOISE_SAFETY_FACTOR
        val result = AdaptationManager.Result(
            noiseThreshold    = avgNoise * AdaptationManager.NOISE_SAFETY_FACTOR,
            detectedParts     = emptySet(),
            unrecognizedParts = emptySet()
        )
        assertEquals(expected, result.noiseThreshold, 1e-6f)
    }
}
