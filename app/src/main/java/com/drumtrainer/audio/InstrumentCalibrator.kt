package com.drumtrainer.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Records microphone audio while the user strikes a single drum instrument
 * [CALIBRATION_HITS] times, then estimates the dominant frequency band for
 * that instrument.
 *
 * Improvements over a naïve peak-frequency approach:
 * - A **Hann window** is applied to each snippet before the DFT to suppress
 *   spectral leakage, giving a more accurate dominant-frequency estimate.
 * - Recording stops automatically once [CALIBRATION_HITS] distinct onsets
 *   have been captured (up to [maxDurationMs] as a safety timeout).  Using a
 *   fixed hit count rather than a fixed time ensures a consistent number of
 *   samples regardless of how quickly the user strikes the instrument.
 * - The calibrated band is computed as **mean ± 2 × stddev** of the collected
 *   peak frequencies, with a minimum span of 20 % of the mean for robustness
 *   when the hit cluster is very tight.  This produces significantly tighter,
 *   instrument-specific bands than the previous IQR-with-large-expansion
 *   approach and reduces misclassification for instruments with adjacent
 *   default ranges (e.g. snare vs. hi-tom, or bass drum vs. floor tom).
 *
 * Typical usage:
 * ```
 * val calibrator = InstrumentCalibrator()
 * Thread {
 *     calibrator.record(
 *         onProgress    = { pct -> … },
 *         onHitDetected = { n   -> … }
 *     ) { result ->
 *         result?.let { prefs.setCalibration(part, it.lowHz, it.highHz) }
 *     }
 * }.start()
 * ```
 *
 * **Must be called from a background thread** – the [record] function blocks
 * until the required hits are collected or the timeout expires.
 *
 * @param sampleRateHz  Recording sample rate in Hz (default: 44 100).
 * @param onsetDetector Onset detector instance (injectable for testing).
 */
class InstrumentCalibrator(
    private val sampleRateHz: Int = 44_100,
    private val onsetDetector: OnsetDetector = OnsetDetector(sampleRateHz)
) {

    /**
     * All data produced by a successful calibration recording session.
     *
     * @property lowHz           Estimated lower bound of the dominant frequency band.
     * @property highHz          Estimated upper bound of the dominant frequency band.
     * @property meanHz          Mean of the collected peak frequencies.
     * @property stddevHz        Standard deviation of the collected peak frequencies.
     * @property peakFrequencies Raw peak-frequency measurements (one per detected hit).
     */
    data class CalibrationResult(
        val lowHz: Int,
        val highHz: Int,
        val meanHz: Double,
        val stddevHz: Double,
        val peakFrequencies: List<Int>
    )

    companion object {
        /** Number of hits required for a reliable per-instrument calibration. */
        const val CALIBRATION_HITS = 5

        /**
         * Minimum band span expressed as a fraction of the mean peak frequency.
         * Ensures that even a perfectly tight hit cluster produces a band wide
         * enough for the DFT bin resolution to yield meaningful results.
         */
        private const val MIN_SPAN_FRACTION = 0.20
    }

    /**
     * Records audio until [requiredHits] onsets are detected (or [maxDurationMs]
     * elapses) and analyses the snippets to estimate the dominant frequency band.
     *
     * @param requiredHits    Number of hits to collect before finishing (default: [CALIBRATION_HITS]).
     * @param maxDurationMs   Safety timeout in milliseconds (default: 10 000).
     * @param sensitivityFactor Onset threshold: ratio above rolling RMS required to count a hit.
     *                        Lower values → more sensitive (picks up quiet hits but also ambient noise).
     *                        Higher values → less sensitive (ignores noise but may miss soft hits).
     *                        Default: the detector's current [OnsetDetector.onsetThresholdFactor] (3.0).
     * @param onProgress      Optional callback (0–100) updated after each detected hit.
     * @param onHitDetected   Optional callback invoked after each hit, with the running hit count.
     * @param onComplete      Invoked on the calling thread when done.  Receives a
     *                        [CalibrationResult] with the estimated band, mean, stddev and
     *                        raw peak frequencies, or `null` if no hits were detected.
     */
    fun record(
        requiredHits: Int = CALIBRATION_HITS,
        maxDurationMs: Int = 10_000,
        sensitivityFactor: Float = onsetDetector.onsetThresholdFactor,
        onProgress: ((pct: Int) -> Unit)? = null,
        onHitDetected: ((hitCount: Int) -> Unit)? = null,
        onComplete: (CalibrationResult?) -> Unit
    ) {
        onsetDetector.onsetThresholdFactor = sensitivityFactor
        onsetDetector.reset()

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize * 4
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val snippetBuffer = FloatArray(2048)
        var snippetWritePos = 0
        val peakFrequencies = mutableListOf<Int>()

        onsetDetector.onOnset = {
            val size = minOf(512, snippetBuffer.size)
            val start = (snippetWritePos - size).coerceAtLeast(0)
            val snippet = FloatArray(size) { snippetBuffer[(start + it) % snippetBuffer.size] }
            val peak = findPeakFrequency(snippet)
            if (peak > 0) {
                peakFrequencies.add(peak)
                val count = peakFrequencies.size
                onHitDetected?.invoke(count)
                onProgress?.invoke((count * 100 / requiredHits).coerceIn(0, 100))
            }
        }

        audioRecord.startRecording()

        val rawBuffer    = ShortArray(bufferSize / 2)
        val floatBuffer  = FloatArray(bufferSize / 2)
        val totalSamples = sampleRateHz.toLong() * maxDurationMs / 1000
        var samplesRead  = 0L

        while (samplesRead < totalSamples && peakFrequencies.size < requiredHits) {
            val read = audioRecord.read(rawBuffer, 0, rawBuffer.size)
            if (read <= 0) continue
            for (i in 0 until read) {
                val f = rawBuffer[i] / 32768f
                floatBuffer[i] = f
                snippetBuffer[snippetWritePos % snippetBuffer.size] = f
                snippetWritePos++
            }
            onsetDetector.feed(floatBuffer.copyOf(read))
            samplesRead += read
        }

        audioRecord.stop()
        audioRecord.release()

        if (peakFrequencies.isEmpty()) {
            onComplete(null)
            return
        }

        val (low, high) = computeBand(peakFrequencies)
        val mean = peakFrequencies.average()
        val variance = peakFrequencies.fold(0.0) { acc, f -> acc + (f - mean) * (f - mean) } /
            peakFrequencies.size
        val stddev = sqrt(variance)
        onComplete(CalibrationResult(low, high, mean, stddev, peakFrequencies.toList()))
    }

    /**
     * Derives a frequency band from a list of peak-frequency measurements.
     *
     * Uses **mean ± 2 × stddev** with a minimum span of 20 % of the mean so
     * that very tightly-clustered measurements still produce a usable band.
     * This is significantly tighter than the previous IQR approach (which
     * expanded Q1 and Q3 by a factor of 2), reducing band overlap between
     * adjacent instruments and therefore improving classification accuracy.
     *
     * `internal` so it can be unit-tested directly.
     */
    internal fun computeBand(peakFrequencies: List<Int>): Pair<Int, Int> {
        val mean = peakFrequencies.average()
        val variance = peakFrequencies.fold(0.0) { acc, f -> acc + (f - mean) * (f - mean) } /
            peakFrequencies.size
        val stddev = sqrt(variance)
        // Band = mean ± 2 standard deviations, minimum MIN_SPAN_FRACTION of mean for robustness.
        val span = maxOf(2.0 * stddev, mean * MIN_SPAN_FRACTION)
        val low  = (mean - span).toInt().coerceAtLeast(20)
        val high = (mean + span).toInt().coerceAtMost(20_000)
        return low to high
    }

    /**
     * Returns the frequency (Hz) of the DFT bin with the highest energy in
     * [snippet].  Searches between 50 Hz and 10 000 Hz.
     *
     * A **Hann window** is applied before the DFT to suppress spectral leakage
     * caused by the implicit rectangular window of a finite sample block.
     * This improves the accuracy of the dominant-frequency estimate, especially
     * when the true frequency falls between two DFT bin centres.
     *
     * This is `internal` so it can be tested directly without starting the mic.
     */
    internal fun findPeakFrequency(snippet: FloatArray): Int {
        val n = snippet.size
        val lowBin  = (50.0     * n / sampleRateHz).toInt().coerceIn(1, n / 2)
        val highBin = (10_000.0 * n / sampleRateHz).toInt().coerceIn(lowBin + 1, n / 2)

        // Apply Hann window to reduce spectral leakage before the DFT.
        val windowed = AudioUtils.applyHannWindow(snippet)

        var peakBin    = lowBin
        var peakEnergy = 0.0

        for (k in lowBin..highBin) {
            var re = 0.0
            var im = 0.0
            val angle = 2.0 * Math.PI * k / n
            for (i in windowed.indices) {
                re += windowed[i] * cos(angle * i)
                im += windowed[i] * sin(angle * i)
            }
            val energy = re * re + im * im
            if (energy > peakEnergy) {
                peakEnergy = energy
                peakBin    = k
            }
        }

        return (peakBin.toDouble() * sampleRateHz / n).toInt()
    }
}
