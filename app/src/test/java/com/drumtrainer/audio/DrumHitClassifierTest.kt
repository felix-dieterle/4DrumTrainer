package com.drumtrainer.audio

import com.drumtrainer.model.DrumPart
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class DrumHitClassifierTest {

    private val sampleRate = 44_100

    /** Generates a sine-wave snippet at [freqHz] with amplitude 1.0. */
    private fun sineSnippet(freqHz: Int, n: Int = 512): FloatArray =
        FloatArray(n) { i -> sin(2.0 * Math.PI * freqHz * i / sampleRate).toFloat() }

    // ── Basic energy-based classification ────────────────────────────────────

    @Test
    fun `low-frequency signal classified as bass drum when calibration matches`() {
        // Full calibration: BASS_DRUM covers 100 Hz, all others are mapped to
        // a high-frequency range so there is no ambiguity.
        val cal = DrumPart.values().associate { part ->
            part to if (part == DrumPart.BASS_DRUM) (50 to 200) else (4000 to 4001)
        }
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate, calibration = cal)
        val snippet = sineSnippet(100) // 100 Hz – squarely inside bass drum range
        val result = classifier.classify(snippet)
        assertEquals(DrumPart.BASS_DRUM, result)
    }

    @Test
    fun `silence returns null`() {
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate)
        val result = classifier.classify(FloatArray(512) { 0f })
        assertNull("Silence should not be classified as any drum", result)
    }

    // ── Confidence-ratio check ────────────────────────────────────────────────

    @Test
    fun `ambiguous hit returns null when confidence ratio not met`() {
        // Two adjacent, identical bands → best and 2nd-best will have similar energies.
        val cal = mapOf(
            DrumPart.BASS_DRUM to (50 to 500),   // both bands cover 100 Hz
            DrumPart.SNARE     to (50 to 500)
        )
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate, calibration = cal)
        val snippet = sineSnippet(100) // energy split equally between both bands
        // With confidenceRatio = 1.5 the tie should be rejected.
        val result = classifier.classify(snippet, confidenceRatio = 1.5f)
        assertNull("Ambiguous classification should return null", result)
    }

    @Test
    fun `confidence ratio 1_0 disables ambiguity check and always returns a part`() {
        // Even with equal bands, ratio=1.0 means any best-energy part is accepted.
        val cal = mapOf(
            DrumPart.BASS_DRUM to (50 to 500),
            DrumPart.SNARE     to (50 to 500)
        )
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate, calibration = cal)
        val snippet = sineSnippet(100)
        val result = classifier.classify(snippet, minEnergy = 0.01f, confidenceRatio = 1.0f)
        assertNotNull("With ratio=1.0 a non-silent hit should always be classified", result)
    }

    @Test
    fun `well-separated calibration passes confidence check`() {
        // Provide a full calibration where only BASS_DRUM covers the 100 Hz range;
        // all other parts are mapped to a high-frequency band far from 100 Hz so
        // that the confidence ratio is easily met.
        val cal = DrumPart.values().associate { part ->
            part to if (part == DrumPart.BASS_DRUM) (50 to 200) else (4000 to 4001)
        }
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate, calibration = cal)
        val snippet = sineSnippet(100) // 100 Hz is squarely inside bass drum range only
        val result = classifier.classify(snippet, confidenceRatio = 1.5f)
        assertEquals(DrumPart.BASS_DRUM, result)
    }
}
