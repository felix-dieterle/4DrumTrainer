package com.drumtrainer.audio

import kotlin.math.cos
import kotlin.math.sin
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
        if (n == 1) return signal.copyOf()

        val window = hannWindow(n)
        return FloatArray(n) { i -> signal[i] * window[i] }
    }

    /** Cached Hann windows per length, since the same sizes are reused per hit. */
    private val hannCache = HashMap<Int, FloatArray>()

    private fun hannWindow(n: Int): FloatArray {
        synchronized(hannCache) {
            hannCache[n]?.let { return it }
            val window = FloatArray(n) { i ->
                (0.5f - 0.5f * cos(2.0 * Math.PI * i / (n - 1)).toFloat())
            }
            hannCache[n] = window
            return window
        }
    }

    /**
     * Returns the smallest power of two that is greater than or equal to [size].
     */
    fun nextPowerOfTwo(size: Int): Int {
        if (size <= 1) return 1
        var n = 1
        while (n < size) n = n shl 1
        return n
    }

    /**
     * Computes the single-sided magnitude spectrum of [signal] via an iterative
     * radix-2 Cooley–Tukey FFT.
     *
     * The input is zero-padded to the next power of two.  The returned array has
     * `fftSize / 2 + 1` entries (bins `0 .. fftSize/2`); the FFT size used is
     * therefore `(result.size - 1) * 2`.
     *
     * Using an FFT instead of evaluating the DFT at every bin of interest keeps
     * classification cheap even for 2048-sample windows, which are required to
     * resolve low-frequency drums such as the bass drum and floor tom.
     */
    fun computeMagnitudeSpectrum(signal: FloatArray): FloatArray {
        if (signal.isEmpty()) return FloatArray(1)

        val fftSize = nextPowerOfTwo(signal.size)
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        signal.copyInto(re, 0, 0, minOf(signal.size, fftSize))

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        // Butterfly stages.
        var len = 2
        while (len <= fftSize) {
            val angle = -2.0 * Math.PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var i = 0
            while (i < fftSize) {
                var curRe = 1f
                var curIm = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val evenRe = re[i + k]
                    val evenIm = im[i + k]
                    val oddRe = re[i + k + half]
                    val oddIm = im[i + k + half]
                    val vRe = oddRe * curRe - oddIm * curIm
                    val vIm = oddRe * curIm + oddIm * curRe
                    re[i + k] = evenRe + vRe
                    im[i + k] = evenIm + vIm
                    re[i + k + half] = evenRe - vRe
                    im[i + k + half] = evenIm - vIm

                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }

        val half = fftSize / 2
        return FloatArray(half + 1) { k -> sqrt(re[k] * re[k] + im[k] * im[k]) }
    }

    /**
     * Returns the RMS magnitude of [spectrum] within [lowHz]–[highHz].
     *
     * @param spectrum   Single-sided magnitude spectrum from [computeMagnitudeSpectrum].
     * @param sampleRateHz Recording sample rate.
     * @param lowHz      Lower band edge.
     * @param highHz     Upper band edge.
     */
    fun bandRms(
        spectrum: FloatArray,
        sampleRateHz: Int,
        lowHz: Int,
        highHz: Int
    ): Float {
        val fftSize = (spectrum.size - 1) * 2
        val lowBin  = (lowHz.toLong()  * fftSize / sampleRateHz).toInt()
            .coerceIn(0, spectrum.size - 1)
        val highBin = (highHz.toLong() * fftSize / sampleRateHz).toInt()
            .coerceIn(0, spectrum.size - 1)
        if (lowBin >= highBin) return 0f

        var sumSq = 0.0
        for (k in lowBin..highBin) {
            val m = spectrum[k].toDouble()
            sumSq += m * m
        }
        return sqrt(sumSq / (highBin - lowBin + 1)).toFloat()
    }

    /**
     * Returns the fraction of total spectral **energy** that lies within
     * [lowHz]–[highHz].  Unlike a ratio of RMS magnitudes, this is a true energy
     * ratio and therefore a physically meaningful drum-vs-cymbal criterion.
     */
    fun bandEnergyFraction(
        spectrum: FloatArray,
        sampleRateHz: Int,
        lowHz: Int,
        highHz: Int
    ): Float {
        var total = 0.0
        for (k in spectrum.indices) {
            val m = spectrum[k].toDouble()
            total += m * m
        }
        if (total <= 0.0) return 0f

        val fftSize = (spectrum.size - 1) * 2
        val lowBin  = (lowHz.toLong()  * fftSize / sampleRateHz).toInt()
            .coerceIn(0, spectrum.size - 1)
        val highBin = (highHz.toLong() * fftSize / sampleRateHz).toInt()
            .coerceIn(0, spectrum.size - 1)
        if (lowBin >= highBin) return 0f

        var band = 0.0
        for (k in lowBin..highBin) {
            val m = spectrum[k].toDouble()
            band += m * m
        }
        return (band / total).toFloat()
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
     * * **[1] Low-energy ratio** – fraction of the total magnitude carried by
     *   bins below [FEATURE_LOW_HZ].  High for bass drum and floor tom; near
     *   zero for cymbals.
     *
     * * **[2] High-energy ratio** – fraction of the total magnitude carried by
     *   bins above [FEATURE_HIGH_HZ].  High for cymbals and snare; low for bass
     *   drum.
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

        val spectrum = computeMagnitudeSpectrum(windowed)
        val fftSize = (spectrum.size - 1) * 2
        val lowBin  = (FEATURE_LOW_HZ.toDouble()  * fftSize / sampleRateHz).toInt()
            .coerceIn(1, spectrum.size - 1)
        val highBin = (FEATURE_HIGH_HZ.toDouble() * fftSize / sampleRateHz).toInt()
            .coerceIn(1, spectrum.size - 1)

        var totalMag = 0.0
        var weightedFreq = 0.0
        var lowMag = 0.0
        var hiMag  = 0.0

        for (k in 1 until spectrum.size) {
            val mag  = spectrum[k].toDouble()
            val freq = k.toDouble() * sampleRateHz / fftSize

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
