package com.drumtrainer.audio

import kotlin.math.cos

/**
 * Shared signal-processing utilities for the audio pipeline.
 */
internal object AudioUtils {

    /**
     * Returns a new array containing [signal] multiplied sample-by-sample by
     * a Hann (raised-cosine) window.
     *
     * Applying a Hann window before a DFT suppresses the spectral leakage that
     * arises from the implicit rectangular window of a finite sample block.
     * This improves frequency-resolution accuracy, especially when the true
     * frequency of interest falls between two DFT bin centres.
     *
     * w[n] = 0.5 × (1 − cos(2πn / (N − 1)))
     */
    fun applyHannWindow(signal: FloatArray): FloatArray {
        val n = signal.size
        if (n == 0) return FloatArray(0)
        return FloatArray(n) { i ->
            signal[i] * (0.5f - 0.5f * cos(2.0 * Math.PI * i / (n - 1)).toFloat())
        }
    }
}
