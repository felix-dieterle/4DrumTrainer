package com.drumtrainer.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class InstrumentCalibratorTest {

    private val sampleRate = 44_100
    private val calibrator = InstrumentCalibrator(sampleRateHz = sampleRate)

    @Test
    fun `findPeakFrequency returns frequency near dominant tone`() {
        val targetHz = 440
        val n = 512
        val signal = FloatArray(n) { i ->
            (0.8f * sin(2.0 * Math.PI * targetHz * i / sampleRate)).toFloat()
        }

        val peak = calibrator.findPeakFrequency(signal)
        // DFT bin resolution at 512 samples = 44100/512 ≈ 86 Hz — allow ±1 bin
        val binResolution = sampleRate / n
        assertTrue(
            "Peak frequency $peak Hz should be near $targetHz Hz (±$binResolution Hz)",
            peak in (targetHz - binResolution)..(targetHz + binResolution)
        )
    }

    @Test
    fun `findPeakFrequency on silence returns non-negative frequency`() {
        val silent = FloatArray(512) { 0f }
        val peak = calibrator.findPeakFrequency(silent)
        assertTrue("Peak frequency should be non-negative", peak >= 0)
    }

    @Test
    fun `findPeakFrequency low-frequency tone gives lower peak than high-frequency`() {
        val n = 512
        val lowTone = FloatArray(n) { i ->
            (0.8f * sin(2.0 * Math.PI * 200 * i / sampleRate)).toFloat()
        }
        val highTone = FloatArray(n) { i ->
            (0.8f * sin(2.0 * Math.PI * 4_000 * i / sampleRate)).toFloat()
        }

        val lowPeak  = calibrator.findPeakFrequency(lowTone)
        val highPeak = calibrator.findPeakFrequency(highTone)

        assertTrue(
            "Low-tone peak ($lowPeak Hz) should be less than high-tone peak ($highPeak Hz)",
            lowPeak < highPeak
        )
    }

    @Test
    fun `findPeakFrequency result is within audible search range`() {
        val n = 512
        val signal = FloatArray(n) { i ->
            (0.5f * sin(2.0 * Math.PI * 1_000 * i / sampleRate)).toFloat()
        }
        val peak = calibrator.findPeakFrequency(signal)
        // Search is bounded to [50 Hz, 10 000 Hz]
        assertTrue("Peak $peak Hz should be >= 50 Hz", peak >= 50)
        assertTrue("Peak $peak Hz should be <= 10 000 Hz", peak <= 10_000)
    }
}
