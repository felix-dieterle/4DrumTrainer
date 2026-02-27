package com.drumtrainer.audio

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detects onset events (drum hits) in a stream of audio samples using a
 * combined energy + spectral-flux algorithm.
 *
 * Algorithm overview:
 *  1. Split PCM samples into overlapping frames of [frameSize] samples.
 *  2. Apply a single-pole IIR high-pass filter to remove DC offset and
 *     low-frequency rumble from each frame.
 *  3. Compute the RMS energy of each high-pass-filtered frame and maintain a
 *     rolling background average (background energy).
 *  4. Compute the half-wave-rectified spectral flux across [NUM_FLUX_BANDS]
 *     logarithmically-spaced frequency bands: for each band, accumulate only
 *     the *positive* change in DFT magnitude compared with the previous frame.
 *     Maintain a rolling background average of flux values.
 *  5. Declare an onset only when **both** conditions hold simultaneously:
 *       - current RMS > [onsetThresholdFactor] × background RMS  (energy spike)
 *       - current flux > [spectralFluxFactor] × background flux  (spectral change)
 *     Requiring both criteria dramatically reduces false positives from sustained
 *     ambient noise (HVAC, traffic), which has a constant spectral profile and
 *     therefore produces near-zero flux even at significant amplitude.
 *  6. Enforce a [minOnsetGapMs] cooldown between successive onsets to suppress
 *     the multi-trigger artefacts ("double-hits") common with acoustic drums.
 *
 * @param sampleRateHz         Recording sample rate in Hz (default: 44 100).
 * @param frameSize            Number of PCM samples per analysis frame (default: 512 ≈ 11.6 ms).
 * @param onsetThresholdFactor Ratio above rolling RMS required for the energy criterion (default: 3.0).
 * @param spectralFluxFactor   Ratio above rolling flux required for the flux criterion (default: 1.5).
 * @param minOnsetGapMs        Minimum gap between successive onsets in milliseconds (default: 80 ms).
 * @param backgroundFrames     Number of frames in the rolling background averages (default: 20).
 */
class OnsetDetector(
    private val sampleRateHz: Int = 44_100,
    private val frameSize: Int = 512,
    private val onsetThresholdFactor: Float = 3.0f,
    private val spectralFluxFactor: Float = 1.5f,
    private val minOnsetGapMs: Long = 80L,
    private val backgroundFrames: Int = 20
) {
    private val highPass = HighPassFilter()
    private val rmsHistory = ArrayDeque<Float>(backgroundFrames)

    // Spectral flux state: 8 logarithmically-spaced band-centre frequencies
    // covering the full drum frequency range (bass drum ~50 Hz → hi-hat ~8 kHz).
    private val NUM_FLUX_BANDS = 8
    private val FLUX_BAND_CENTERS = intArrayOf(50, 100, 220, 460, 960, 2000, 4200, 8800)
    private var prevBandMagnitudes = FloatArray(NUM_FLUX_BANDS)
    private val fluxHistory = ArrayDeque<Float>(backgroundFrames)

    /** Timestamp (ms) of the last detected onset; -1 if none yet. */
    private var lastOnsetTimeMs: Long = -1L

    /** Total number of processed samples – used to derive absolute timestamps. */
    private var totalSamplesProcessed: Long = 0L

    /**
     * Wall-clock time (ms) until which onset detection is suppressed.
     * Set by [suppressFor] to prevent acoustic feedback when the app plays back audio.
     */
    @Volatile private var suppressUntilMs: Long = 0L

    /**
     * Absolute minimum RMS below which frames are ignored (noise floor filter).
     * Set this to the noise threshold returned by [com.drumtrainer.audio.AdaptationManager]
     * to prevent ambient background noise from triggering spurious onsets.
     */
    var absoluteMinRms: Float = 0f

    /** Listener invoked on every confirmed onset. */
    var onOnset: ((timestampMs: Long) -> Unit)? = null

    /**
     * Feeds [samples] (16-bit PCM as [-1.0, 1.0] normalised floats) into the
     * detector.  Call this from a background thread in tight loops.
     */
    fun feed(samples: FloatArray) {
        var pos = 0
        while (pos + frameSize <= samples.size) {
            val frame = FloatArray(frameSize) { highPass.process(samples[pos + it]) }
            processFrame(frame, totalSamplesProcessed + pos)
            pos += frameSize
        }
        totalSamplesProcessed += samples.size
    }

    private fun processFrame(frame: FloatArray, frameStartSample: Long) {
        val rms = computeRms(frame)

        // --- Spectral flux (half-wave rectified) ---
        val magnitudes = computeBandMagnitudes(frame)
        var flux = 0f
        for (k in 0 until NUM_FLUX_BANDS) {
            flux += maxOf(0f, magnitudes[k] - prevBandMagnitudes[k])
        }
        flux /= NUM_FLUX_BANDS
        magnitudes.copyInto(prevBandMagnitudes)

        // --- Maintain rolling background averages (always updated) ---
        if (rmsHistory.size >= backgroundFrames) rmsHistory.removeFirst()
        val background = if (rmsHistory.isEmpty()) rms else rmsHistory.average().toFloat()
        rmsHistory.addLast(rms)

        if (fluxHistory.size >= backgroundFrames) fluxHistory.removeFirst()
        val avgFlux = if (fluxHistory.isEmpty()) flux else fluxHistory.average().toFloat()
        fluxHistory.addLast(flux)

        val timestampMs = (frameStartSample * 1000L) / sampleRateHz
        val gapOk = lastOnsetTimeMs < 0 || (timestampMs - lastOnsetTimeMs) >= minOnsetGapMs

        // Skip onset detection while suppressed (e.g. during playback to avoid feedback)
        if (System.currentTimeMillis() < suppressUntilMs) return

        // Skip if the frame is below the absolute noise floor (set during adaptation phase)
        if (rms <= absoluteMinRms) return

        // Require BOTH an energy spike AND a spectral change to fire an onset.
        // This prevents sustained ambient noise (constant spectrum) from triggering.
        val energyOnset = rms > onsetThresholdFactor * background
        val fluxOnset = flux > spectralFluxFactor * avgFlux
        if (energyOnset && fluxOnset && gapOk) {
            lastOnsetTimeMs = timestampMs
            onOnset?.invoke(timestampMs)
        }
    }

    /**
     * Computes the DFT magnitude at each of the [NUM_FLUX_BANDS] centre frequencies.
     * Uses the standard DFT formula evaluated at the single bin closest to each
     * target frequency — O(N) per band, with N = frame.size.
     */
    private fun computeBandMagnitudes(frame: FloatArray): FloatArray {
        val n = frame.size
        return FloatArray(NUM_FLUX_BANDS) { bandIdx ->
            val k = (FLUX_BAND_CENTERS[bandIdx].toDouble() * n / sampleRateHz)
                .toInt().coerceIn(1, n / 2)
            var re = 0.0
            var im = 0.0
            val angle = 2.0 * Math.PI * k / n
            for (i in frame.indices) {
                re += frame[i] * cos(angle * i)
                im += frame[i] * sin(angle * i)
            }
            sqrt(re * re + im * im).toFloat()
        }
    }

    private fun computeRms(frame: FloatArray): Float {
        val sumSq = frame.fold(0.0) { acc, s -> acc + s * s }
        return sqrt(sumSq / frame.size).toFloat()
    }

    /** Resets all state.  Must be called before each new recording session. */
    fun reset() {
        highPass.reset()
        rmsHistory.clear()
        fluxHistory.clear()
        prevBandMagnitudes.fill(0f)
        lastOnsetTimeMs = -1L
        totalSamplesProcessed = 0L
        suppressUntilMs = 0L
        absoluteMinRms = 0f
    }

    /**
     * Temporarily suppresses onset detection for [durationMs] milliseconds.
     * Call this whenever the app plays back audio to avoid acoustic feedback
     * from the speaker output being re-captured by the microphone and
     * producing spurious hit detections (acoustic feedback loop).
     */
    fun suppressFor(durationMs: Long) {
        suppressUntilMs = System.currentTimeMillis() + durationMs
    }
}
