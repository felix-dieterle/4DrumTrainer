package com.drumtrainer.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class OnsetDetectorTest {

    @Test
    fun `no onset detected in silent signal`() {
        val detector = OnsetDetector(sampleRateHz = 44_100)
        var count = 0
        detector.onOnset = { count++ }
        // Feed 1 second of silence
        detector.feed(FloatArray(44_100) { 0f })
        assertEquals("No onset should be detected in silence", 0, count)
    }

    @Test
    fun `single impulse triggers exactly one onset`() {
        val detector = OnsetDetector(
            sampleRateHz = 44_100,
            frameSize = 512,
            onsetThresholdFactor = 2.0f,
            minOnsetGapMs = 80
        )
        val onsets = mutableListOf<Long>()
        detector.onOnset = { ts -> onsets.add(ts) }

        // Build a signal: silence then a single loud impulse then silence again
        val signal = FloatArray(44_100)
        for (i in 2048..2560) signal[i] = 1.0f  // one pulse ~46 ms in

        detector.feed(signal)
        assertEquals("Exactly one onset should be detected", 1, onsets.size)
    }

    @Test
    fun `reset clears state and allows fresh detection`() {
        val detector = OnsetDetector(
            sampleRateHz = 44_100,
            frameSize = 512,
            onsetThresholdFactor = 2.0f
        )
        val onsets = mutableListOf<Long>()
        detector.onOnset = { ts -> onsets.add(ts) }

        val pulse = FloatArray(44_100)
        for (i in 2048..2560) pulse[i] = 1.0f

        detector.feed(pulse)
        val countBefore = onsets.size

        detector.reset()
        onsets.clear()
        detector.feed(pulse)

        assertTrue("Detection should work normally after reset", onsets.size >= countBefore)
    }

    @Test
    fun `min onset gap prevents double triggers`() {
        val detector = OnsetDetector(
            sampleRateHz = 44_100,
            frameSize = 512,
            onsetThresholdFactor = 2.0f,
            minOnsetGapMs = 500   // 500 ms gap required
        )
        val onsets = mutableListOf<Long>()
        detector.onOnset = { ts -> onsets.add(ts) }

        // Two pulses very close together (~23 ms apart)
        val signal = FloatArray(44_100)
        for (i in 1000..1512) signal[i] = 1.0f
        for (i in 2000..2512) signal[i] = 1.0f

        detector.feed(signal)
        assertTrue("Gap enforcement should suppress the second close trigger", onsets.size <= 1)
    }

    @Test
    fun `suppressFor blocks onsets during suppression window`() {
        val detector = OnsetDetector(
            sampleRateHz = 44_100,
            frameSize = 512,
            onsetThresholdFactor = 2.0f,
            minOnsetGapMs = 80
        )
        val onsets = mutableListOf<Long>()
        detector.onOnset = { ts -> onsets.add(ts) }

        // Suppress for 5 seconds (much longer than any realistic test)
        detector.suppressFor(5_000L)

        val signal = FloatArray(44_100)
        for (i in 2048..2560) signal[i] = 1.0f

        detector.feed(signal)
        assertEquals("No onset should fire while suppressed", 0, onsets.size)
    }

    @Test
    fun `reset clears suppression state`() {
        val detector = OnsetDetector(
            sampleRateHz = 44_100,
            frameSize = 512,
            onsetThresholdFactor = 2.0f,
            minOnsetGapMs = 80
        )
        val onsets = mutableListOf<Long>()
        detector.onOnset = { ts -> onsets.add(ts) }

        // Suppress for a long time, then reset – onsets should be detected again
        detector.suppressFor(60_000L)
        detector.reset()

        val signal = FloatArray(44_100)
        for (i in 2048..2560) signal[i] = 1.0f

        detector.feed(signal)
        assertTrue("Onset should be detected after suppression is cleared by reset", onsets.size >= 1)
    }

    @Test
    fun `spectral flux suppresses onset on gradual energy rise`() {
        // A signal that grows uniformly cannot cause the spectral flux to jump
        // above the rolling average flux, so the flux criterion should remain
        // unsatisfied and no onset should fire.
        val detector = OnsetDetector(
            sampleRateHz = 44_100,
            frameSize = 512,
            onsetThresholdFactor = 3.0f,
            spectralFluxFactor = 1.5f,
            minOnsetGapMs = 80
        )
        var count = 0
        detector.onOnset = { count++ }

        // ~0.5 s of a 440 Hz tone with amplitude growing linearly from 0 → 0.5
        val n = 22_050
        val signal = FloatArray(n) { i ->
            (i.toFloat() / n * 0.5f * sin(2.0 * Math.PI * 440.0 * i / 44_100)).toFloat()
        }

        detector.feed(signal)
        assertEquals("Gradual energy rise should not trigger onset", 0, count)
    }

    @Test
    fun `spectral flux parameter is accepted without changing basic detection`() {
        // Verify that providing an explicit spectralFluxFactor does not break
        // the fundamental impulse-detection behaviour.
        val detector = OnsetDetector(
            sampleRateHz = 44_100,
            frameSize = 512,
            onsetThresholdFactor = 2.0f,
            spectralFluxFactor = 1.5f,
            minOnsetGapMs = 80
        )
        val onsets = mutableListOf<Long>()
        detector.onOnset = { ts -> onsets.add(ts) }

        val signal = FloatArray(44_100)
        for (i in 2048..2560) signal[i] = 1.0f

        detector.feed(signal)
        assertEquals("Broadband impulse should still trigger exactly one onset", 1, onsets.size)
    }
}
