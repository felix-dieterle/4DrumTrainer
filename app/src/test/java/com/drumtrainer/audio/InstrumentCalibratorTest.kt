package com.drumtrainer.audio

import org.junit.Assert.*
import org.junit.Test
import java.io.File
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

    // ── computeBand ───────────────────────────────────────────────────────────

    @Test
    fun `computeBand centers band on mean of measurements`() {
        // Five identical measurements → band must be centred on that frequency.
        val freq = 400
        val freqs = List(5) { freq }
        val (low, high) = calibrator.computeBand(freqs)
        val centre = (low + high) / 2
        // With zero stddev the span is 20 % of mean (80 Hz); centre should match mean.
        assertEquals("Band centre should equal the mean frequency", freq, centre)
    }

    @Test
    fun `computeBand with tight cluster is narrower than old IQR expansion`() {
        // Snare-like tight cluster around 400 Hz.
        val freqs = listOf(380, 390, 400, 410, 420)
        val (low, high) = calibrator.computeBand(freqs)
        val width = high - low
        // Old IQR approach: low = Q1/2 ≈ 190, high = Q3*2 ≈ 820 → width ≈ 630 Hz.
        // New mean±2σ approach must produce a narrower band.
        assertTrue(
            "Tight cluster band width ($width Hz) should be much narrower than old 630 Hz",
            width < 300
        )
    }

    @Test
    fun `computeBand lower bound is at least 20 Hz`() {
        // Very low frequency cluster: minimum lower bound is 20 Hz.
        val freqs = listOf(25, 25, 25, 25, 25)
        val (low, _) = calibrator.computeBand(freqs)
        assertTrue("Lower bound should be at least 20 Hz", low >= 20)
    }

    @Test
    fun `computeBand upper bound does not exceed 20000 Hz`() {
        val freqs = listOf(9_800, 9_900, 10_000, 10_100, 10_200)
        val (_, high) = calibrator.computeBand(freqs)
        assertTrue("Upper bound should not exceed 20 000 Hz", high <= 20_000)
    }

    @Test
    fun `computeBand single measurement produces minimum 20-percent span`() {
        val mean = 500
        val freqs = listOf(mean)
        val (low, high) = calibrator.computeBand(freqs)
        val minSpan = (mean * 0.20).toInt()
        // Band must span at least 20 % of mean on each side.
        assertTrue("Low should be at most mean - minSpan/2", low <= mean - minSpan / 2)
        assertTrue("High should be at least mean + minSpan/2", high >= mean + minSpan / 2)
    }

    @Test
    fun `computeBand low bound is strictly less than high bound`() {
        val freqs = listOf(300, 350, 400, 450, 500)
        val (low, high) = calibrator.computeBand(freqs)
        assertTrue("Low ($low) must be less than high ($high)", low < high)
    }

    // ── writeWavFile ──────────────────────────────────────────────────────────

    @Test
    fun `writeWavFile produces a file with correct RIFF and WAVE headers`() {
        val file = File.createTempFile("test_wav", ".wav")
        try {
            val pcm = ByteArray(4) { 0 } // 2 silent 16-bit samples
            calibrator.writeWavFile(file, pcm, 44_100)

            val bytes = file.readBytes()
            // Minimum WAV size: 44 bytes header + data
            assertTrue("WAV file must be at least 44 bytes", bytes.size >= 44)
            assertEquals("RIFF", String(bytes.copyOfRange(0, 4)))
            assertEquals("WAVE", String(bytes.copyOfRange(8, 12)))
            assertEquals("fmt ", String(bytes.copyOfRange(12, 16)))
            assertEquals("data", String(bytes.copyOfRange(36, 40)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `writeWavFile data chunk size matches pcm byte count`() {
        val file = File.createTempFile("test_wav_size", ".wav")
        try {
            val pcm = ByteArray(200) { it.toByte() }
            calibrator.writeWavFile(file, pcm, 44_100)

            val bytes = file.readBytes()
            // Bytes 40–43 are the data chunk size in little-endian.
            val dataSize = (bytes[40].toInt() and 0xFF) or
                ((bytes[41].toInt() and 0xFF) shl 8) or
                ((bytes[42].toInt() and 0xFF) shl 16) or
                ((bytes[43].toInt() and 0xFF) shl 24)
            assertEquals("Data chunk size must equal PCM byte count", pcm.size, dataSize)
        } finally {
            file.delete()
        }
    }
}
