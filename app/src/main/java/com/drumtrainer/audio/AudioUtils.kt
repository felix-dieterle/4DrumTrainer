package com.drumtrainer.audio

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Shared signal-processing utilities for the audio pipeline.
 */
internal object AudioUtils {

    /**
     * Frequency (Hz) below which a hit's energy is considered "low-frequency"
     * for multi-feature instrument discrimination.  Captures the fundamental
     * resonance of bass drum and floor tom.
     */
    const val FEATURE_LOW_HZ = 300

    /**
     * Frequency (Hz) above which a hit's energy is considered "high-frequency"
     * for multi-feature instrument discrimination.  Captures cymbal shimmer and
     * snare-wire sizzle above the mid-range tonal content.
     */
    const val FEATURE_HIGH_HZ = 2_000

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

    /**
     * Computes a three-element spectral feature vector from a **pre-windowed**
     * PCM snippet (Hann window already applied by the caller):
     *
     * * **[0] Spectral centroid** – frequency-weighted mean of the DFT magnitude
     *   spectrum in Hz.  A stable, timbrally rich summary that varies far less
     *   across repeated hits of the same instrument than the DFT peak frequency
     *   (which can jump erratically for instruments with multiple harmonic
     *   resonances, e.g. snare drum or crash cymbal).
     *
     * * **[1] Low-energy ratio** – fraction of total spectral energy carried by
     *   bins below [FEATURE_LOW_HZ].  High for bass drum and floor tom; near
     *   zero for cymbals.
     *
     * * **[2] High-energy ratio** – fraction of total spectral energy carried by
     *   bins above [FEATURE_HIGH_HZ].  High for cymbals and snare; low for bass
     *   drum.
     *
     * Together the three features form a compact signature in a space where
     * each drum instrument occupies a distinct region, enabling reliable
     * nearest-neighbour classification even when two instruments share similar
     * dominant peak frequencies (e.g. bass drum and floor tom both resonating
     * near 100–150 Hz).
     *
     * @param windowed     Pre-windowed PCM snippet (Hann window applied).
     * @param sampleRateHz Recording sample rate used to convert DFT bin indices
     *                     to Hz.
     * @return FloatArray of size 3: [centroidHz, lowEnergyRatio, highEnergyRatio].
     *         Returns all zeros when [windowed] is empty or silent.
     */
    fun computeSpectralFeatures(windowed: FloatArray, sampleRateHz: Int): FloatArray {
        val n = windowed.size
        if (n == 0) return FloatArray(3)

        val lowBin  = (FEATURE_LOW_HZ.toDouble()  * n / sampleRateHz).toInt().coerceIn(1, n / 2)
        val highBin = (FEATURE_HIGH_HZ.toDouble() * n / sampleRateHz).toInt().coerceIn(1, n / 2)

        var totalMag = 0.0
        var weightedFreq = 0.0
        var lowMag = 0.0
        var hiMag  = 0.0

        for (k in 1..(n / 2)) {
            var re = 0.0
            var im = 0.0
            val angle = 2.0 * Math.PI * k / n
            for (i in windowed.indices) {
                re += windowed[i] * Math.cos(angle * i)
                im += windowed[i] * Math.sin(angle * i)
            }
            val mag  = sqrt(re * re + im * im)
            val freq = k.toDouble() * sampleRateHz / n

            totalMag     += mag
            weightedFreq += mag * freq
            if (k <= lowBin)  lowMag += mag
            if (k >= highBin) hiMag  += mag
        }

        if (totalMag == 0.0) return FloatArray(3)

        return floatArrayOf(
            (weightedFreq / totalMag).toFloat(),
            (lowMag       / totalMag).toFloat(),
            (hiMag        / totalMag).toFloat()
        )
    }
}
