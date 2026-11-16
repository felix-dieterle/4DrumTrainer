package com.drumtrainer.audio

/**
 * Collects the PCM window used to classify one drum hit.
 *
 * Onset detectors necessarily fire slightly after the physical attack.  A short
 * 512-sample snippet (≈12 ms at 44.1 kHz) is far too short to resolve the
 * fundamental of a bass drum or floor tom, and it contains almost nothing but the
 * initial transient.  This collector therefore:
 *
 * 1. Keeps a rolling ring buffer of the most recent [windowSize] samples.
 * 2. Marks the sample index of a detected onset.
 * 3. Waits until enough samples *after* the onset have arrived so that the window
 *    can start [preRollSamples] before the onset and still span [windowSize]
 *    samples — e.g. 2048 samples ≈ 46 ms, with 512 samples ≈ 12 ms of pre-roll.
 *
 * The default 2048-sample window gives a frequency resolution of ≈21.5 Hz at
 * 44.1 kHz, which is sufficient to separate kick/floor-tom fundamentals.
 *
 * **Not thread-safe.**  Call [append], [markOnsetFromTimestamp] and
 * [extractReadyHit] from the same audio-processing thread.
 */
internal class SnippetCollector(
    private val sampleRateHz: Int = 44_100,
    val windowSize: Int = DEFAULT_WINDOW_SIZE,
    val preRollSamples: Int = DEFAULT_PRE_ROLL_SAMPLES
) {

    data class Hit(val snippet: FloatArray, val onsetTimestampMs: Long)

    private val ring = FloatArray(windowSize)
    private var writePos = 0
    private var totalSamples = 0L

    private var pendingOnsetSample = -1L
    private var pendingOnsetTimestampMs = -1L

    private val delaySamples: Long =
        (windowSize - preRollSamples).coerceAtLeast(0).toLong()

    /** Appends [samples] to the rolling buffer. */
    fun append(samples: FloatArray) {
        for (s in samples) {
            ring[writePos] = s
            writePos++
            if (writePos == windowSize) writePos = 0
        }
        totalSamples += samples.size
    }

    /**
     * Records that an onset occurred at [onsetTimestampMs] (the detector's
     * millisecond timestamp).  Any previously pending onset that has not yet been
     * extracted is replaced.
     */
    fun markOnsetFromTimestamp(onsetTimestampMs: Long) {
        pendingOnsetTimestampMs = onsetTimestampMs
        pendingOnsetSample = onsetTimestampMs * sampleRateHz / 1000
    }

    /** Returns `true` when a marked onset has a complete window available. */
    fun isReady(): Boolean =
        pendingOnsetSample >= 0 && totalSamples >= pendingOnsetSample + delaySamples

    /**
     * Returns the completed hit window (oldest sample first) and clears the pending
     * onset, or `null` when no window is ready yet.
     */
    fun extractReadyHit(): Hit? {
        if (!isReady()) return null
        val timestamp = pendingOnsetTimestampMs
        pendingOnsetSample = -1L
        pendingOnsetTimestampMs = -1L
        val snippet = FloatArray(windowSize) { i -> ring[(writePos + i) % windowSize] }
        return Hit(snippet, timestamp)
    }

    /** Clears the buffer and any pending onset. */
    fun reset() {
        ring.fill(0f)
        writePos = 0
        totalSamples = 0L
        pendingOnsetSample = -1L
        pendingOnsetTimestampMs = -1L
    }

    companion object {
        /** ≈46 ms at 44.1 kHz — long enough for low drum fundamentals. */
        const val DEFAULT_WINDOW_SIZE = 2048

        /** Pre-roll covering the worst-case onset-detector frame latency (≈12 ms). */
        const val DEFAULT_PRE_ROLL_SAMPLES = 512
    }
}
