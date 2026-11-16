package com.drumtrainer.audio

/**
 * Lightweight single-pole IIR high-pass filter used to remove DC offset and
 * low-frequency rumble from raw PCM samples before onset detection.
 *
 * Transfer function: y[n] = x[n] - x[n-1] + alpha * y[n-1]
 *
 * At a 44.1 kHz sample rate the cutoff is approximately
 * `(1 − alpha) × sampleRate / (2π)`.  The default 0.99 therefore places the
 * cutoff at ≈70 Hz — low enough to preserve the fundamental energy of a bass
 * drum or floor tom while still rejecting DC offset and room rumble.  The
 * previous value of 0.95 produced a ≈350 Hz cutoff, which removed much of the
 * low-frequency energy that onset detection needs for kick and tom hits.
 *
 * @param alpha Pole position (0 < alpha < 1). Higher = more bass retained.
 *              Typical value: 0.99.
 */
class HighPassFilter(private val alpha: Float = 0.99f) {

    private var prevInput  = 0f
    private var prevOutput = 0f

    /** Processes a single [sample] and returns the filtered value. */
    fun process(sample: Float): Float {
        val output = sample - prevInput + alpha * prevOutput
        prevInput  = sample
        prevOutput = output
        return output
    }

    /** Resets the filter state (call between recording sessions). */
    fun reset() {
        prevInput  = 0f
        prevOutput = 0f
    }
}
