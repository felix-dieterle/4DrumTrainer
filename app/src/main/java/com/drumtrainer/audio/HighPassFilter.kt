package com.drumtrainer.audio

/**
 * Lightweight single-pole IIR high-pass filter used to remove DC offset from
 * raw PCM samples before onset detection.
 *
 * Transfer function: y[n] = x[n] - x[n-1] + alpha * y[n-1]
 *
 * @param alpha Pole position (0 < alpha < 1). Higher = more bass retained.
 *              Typical value: 0.95 for removing DC only.
 */
class HighPassFilter(private val alpha: Float = 0.95f) {

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
