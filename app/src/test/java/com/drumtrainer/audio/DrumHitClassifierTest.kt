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

    // ── Drum-family (isCymbal) checks ─────────────────────────────────────────

    @Test
    fun `all cymbal DrumParts have isCymbal true`() {
        val cymbals = setOf(DrumPart.HI_HAT_CLOSED, DrumPart.HI_HAT_OPEN, DrumPart.RIDE, DrumPart.CRASH)
        cymbals.forEach { part ->
            assertTrue("${part.name} should be flagged as a cymbal", part.isCymbal)
        }
    }

    @Test
    fun `all drum DrumParts have isCymbal false`() {
        val drums = setOf(DrumPart.BASS_DRUM, DrumPart.SNARE, DrumPart.TOM_HIGH, DrumPart.TOM_MID, DrumPart.TOM_FLOOR)
        drums.forEach { part ->
            assertFalse("${part.name} should not be flagged as a cymbal", part.isCymbal)
        }
    }

    @Test
    fun `low-frequency hit is not classified as a cymbal`() {
        // A 100 Hz signal has almost no energy above the 3 kHz cymbal boundary,
        // so the drum-family filter must prevent it from being matched to any cymbal.
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate)
        val snippet = sineSnippet(100)
        val result = classifier.classify(snippet)
        assertNotNull("100 Hz hit should yield a classification", result)
        assertFalse("100 Hz hit should be classified as a drum, not a cymbal", result!!.isCymbal)
    }

    @Test
    fun `high-frequency hit is classified as a cymbal`() {
        // A 5000 Hz signal has nearly all energy above the 3 kHz cymbal boundary,
        // so the drum-family filter must direct classification to the cymbal family.
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate)
        val snippet = sineSnippet(5000)
        val result = classifier.classify(snippet)
        assertNotNull("5000 Hz hit should yield a classification", result)
        assertTrue("5000 Hz hit should be classified as a cymbal", result!!.isCymbal)
    }

    @Test
    fun `broad-band crash calibration does not mis-classify a low-frequency drum hit`() {
        // Simulate the classic cross-family confusion: CRASH has a very wide default
        // range (200–10 000 Hz) that spans several drum bands.  Even when CRASH is
        // explicitly calibrated to cover the bass-drum frequency (50–400 Hz), a
        // 100 Hz hit must still be returned as a drum part, not as the CRASH cymbal.
        val cal = DrumPart.values().associate { part ->
            part to when {
                part == DrumPart.BASS_DRUM -> (50 to 300)   // narrow drum band around 100 Hz
                part == DrumPart.CRASH     -> (50 to 400)   // intentionally overlaps bass drum
                else                       -> (8000 to 9000) // all other parts are far away
            }
        }
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate, calibration = cal)
        val snippet = sineSnippet(100)
        val result = classifier.classify(snippet)
        // The HF ratio for 100 Hz is near zero → drum family → CRASH must lose.
        assertFalse(
            "A 100 Hz hit must not be classified as a cymbal even when CRASH band overlaps",
            result?.isCymbal ?: false
        )
    }

    // ── isCalibrated property ─────────────────────────────────────────────────

    @Test
    fun `isCalibrated is false when no calibration is provided`() {
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate)
        assertFalse("No calibration map → isCalibrated should be false", classifier.isCalibrated)
    }

    @Test
    fun `isCalibrated is true when calibration map is non-empty`() {
        val cal = mapOf(DrumPart.BASS_DRUM to (50 to 200))
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate, calibration = cal)
        assertTrue("Non-empty calibration map → isCalibrated should be true", classifier.isCalibrated)
    }

    // ── CALIBRATED_CONFIDENCE_RATIO constant ──────────────────────────────────

    @Test
    fun `CALIBRATED_CONFIDENCE_RATIO is greater than 1`() {
        assertTrue(
            "CALIBRATED_CONFIDENCE_RATIO must be > 1.0 to reject ambiguous hits",
            DrumHitClassifier.CALIBRATED_CONFIDENCE_RATIO > 1.0f
        )
    }

    @Test
    fun `calibrated classifier rejects ambiguous hit using CALIBRATED_CONFIDENCE_RATIO`() {
        // Two overlapping bands → best and second-best have similar energies.
        val cal = mapOf(
            DrumPart.BASS_DRUM to (50 to 500),
            DrumPart.SNARE     to (50 to 500)
        )
        val classifier = DrumHitClassifier(sampleRateHz = sampleRate, calibration = cal)
        val snippet = sineSnippet(100)
        // Using the calibrated confidence ratio must reject the ambiguous tie.
        val result = classifier.classify(snippet, confidenceRatio = DrumHitClassifier.CALIBRATED_CONFIDENCE_RATIO)
        assertNull(
            "Calibrated confidence ratio should return null for equally-matched bands",
            result
        )
    }
}
