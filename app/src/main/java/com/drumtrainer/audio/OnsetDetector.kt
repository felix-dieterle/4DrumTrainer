package com.drumtrainer.audio

import kotlin.math.sqrt

/**
 * Detects onset events (drum hits) in a stream of audio samples using an
 * energy-based spectral-flux algorithm.
 *
 * Algorithm overview:
 *  1. Split PCM samples into overlapping frames of [frameSize] samples.
 *  2. Compute the root-mean-square (RMS) energy of each frame.
 *  3. Compare the current RMS against a rolling average (background energy).
 *  4. Declare an onset when current RMS exceeds [onsetThresholdFactor] × background.
 *  5. Enforce a [minOnsetGapMs] cooldown between successive onsets to suppress
 *     the multi-trigger artefacts ("double-hits") common with acoustic drums.
 *
 * @param sampleRateHz       Recording sample rate in Hz (default: 44 100).
 * @param frameSize          Number of PCM samples per analysis frame (default: 512 ≈ 11.6 ms).
 * @param onsetThresholdFactor  Ratio above rolling RMS required to trigger an onset (default: 3.0).
 * @param minOnsetGapMs      Minimum gap between successive onsets in milliseconds (default: 80 ms).
 * @param backgroundFrames   Number of frames used for the rolling background average (default: 20).
 */
class OnsetDetector(
    private val sampleRateHz: Int = 44_100,
    private val frameSize: Int = 512,
    private val onsetThresholdFactor: Float = 3.0f,
    private val minOnsetGapMs: Long = 80L,
    private val backgroundFrames: Int = 20
) {
    private val highPass = HighPassFilter()
    private val rmsHistory = ArrayDeque<Float>(backgroundFrames)

    /** Timestamp (ms) of the last detected onset; -1 if none yet. */
    private var lastOnsetTimeMs: Long = -1L

    /** Total number of processed samples – used to derive absolute timestamps. */
    private var totalSamplesProcessed: Long = 0L

    /**
     * Wall-clock time (ms) until which onset detection is suppressed.
     * Set by [suppressFor] to prevent acoustic feedback when the app plays back audio.
     */
    @Volatile private var suppressUntilMs: Long = 0L

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

        // Maintain rolling background energy
        if (rmsHistory.size >= backgroundFrames) rmsHistory.removeFirst()
        val background = if (rmsHistory.isEmpty()) rms else rmsHistory.average().toFloat()
        rmsHistory.addLast(rms)

        val timestampMs = (frameStartSample * 1000L) / sampleRateHz
        val gapOk = lastOnsetTimeMs < 0 || (timestampMs - lastOnsetTimeMs) >= minOnsetGapMs

        // Skip onset detection while suppressed (e.g. during playback to avoid feedback)
        if (System.currentTimeMillis() < suppressUntilMs) return

        if (rms > onsetThresholdFactor * background && gapOk) {
            lastOnsetTimeMs = timestampMs
            onOnset?.invoke(timestampMs)
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
        lastOnsetTimeMs = -1L
        totalSamplesProcessed = 0L
        suppressUntilMs = 0L
    }

    /**
     * Temporarily suppresses onset detection for [durationMs] milliseconds.
     * Call this whenever the app plays back audio to avoid acoustic feedback
     * from the speaker being re-captured by the microphone.
     */
    fun suppressFor(durationMs: Long) {
        suppressUntilMs = System.currentTimeMillis() + durationMs
    }
}
