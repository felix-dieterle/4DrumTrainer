package com.drumtrainer.audio

import com.drumtrainer.model.DrumPart
import kotlin.math.sqrt

/**
 * Classifies a single drum hit from a short PCM snippet by analysing the
 * dominant frequency band using a bank of simple energy filters.
 *
 * Each [DrumPart] maps to a characteristic frequency range (see [DrumPart]).
 * The classifier computes the RMS energy in each band and picks the [DrumPart]
 * whose band has the highest relative energy.
 *
 * Default frequency ranges come from the [DrumPart] enum.  When a [calibration]
 * map is provided, those per-part ranges are used instead, allowing the app to
 * adapt to the acoustic environment of each physical drum kit.
 *
 * A **Hann window** is applied once to the snippet before any band computation,
 * reducing spectral leakage and improving discrimination between instruments
 * whose frequency ranges are adjacent (e.g. snare vs. hi-tom, or bass drum vs.
 * floor tom).
 *
 * @param sampleRateHz  Recording sample rate (default: 44 100 Hz).
 * @param calibration   Optional map of per-[DrumPart] (lowHz, highHz) overrides
 *                      produced by [com.drumtrainer.audio.InstrumentCalibrator].
 */
class DrumHitClassifier(
    private val sampleRateHz: Int = 44_100,
    private val calibration: Map<DrumPart, Pair<Int, Int>> = emptyMap()
) {

    /**
     * Classifies [snippet] (normalised float PCM) and returns the most likely
     * [DrumPart], or `null` if the energy is too low to be a meaningful hit.
     *
     * A Hann window is applied to [snippet] once before computing band energies
     * to suppress spectral leakage.
     *
     * @param snippet   Short PCM window (e.g. 512–2048 samples) centred on an onset.
     * @param minEnergy Minimum RMS energy to consider a valid hit (default: 0.01).
     */
    fun classify(snippet: FloatArray, minEnergy: Float = 0.01f): DrumPart? {
        val rms = computeRms(snippet)
        if (rms < minEnergy) return null

        // Apply Hann window once to suppress spectral leakage before any DFT.
        val windowed = AudioUtils.applyHannWindow(snippet)

        val bandEnergies = DrumPart.values().associateWith { part ->
            val (low, high) = calibration[part] ?: (part.freqRangeLowHz to part.freqRangeHighHz)
            bandRms(windowed, low, high)
        }

        return bandEnergies.maxByOrNull { it.value }?.key
    }

    /**
     * Estimates the RMS energy of [windowed] within [lowHz]–[highHz] using a
     * simple DFT evaluated only at the bin indices corresponding to that range.
     * Expects a pre-windowed signal (Hann window applied in [classify]).
     */
    private fun bandRms(windowed: FloatArray, lowHz: Int, highHz: Int): Float {
        val n = windowed.size
        val lowBin  = (lowHz.toDouble()  * n / sampleRateHz).toInt().coerceIn(0, n / 2)
        val highBin = (highHz.toDouble() * n / sampleRateHz).toInt().coerceIn(0, n / 2)

        if (lowBin >= highBin) return 0f

        var sumSq = 0.0
        for (k in lowBin..highBin) {
            var re = 0.0
            var im = 0.0
            val angle = 2.0 * Math.PI * k / n
            for (i in windowed.indices) {
                re += windowed[i] * Math.cos(angle * i)
                im += windowed[i] * Math.sin(angle * i)
            }
            sumSq += re * re + im * im
        }
        return sqrt(sumSq / (highBin - lowBin + 1)).toFloat()
    }

    private fun computeRms(signal: FloatArray): Float {
        val sumSq = signal.fold(0.0) { acc, s -> acc + s * s }
        return sqrt(sumSq / signal.size).toFloat()
    }
}
