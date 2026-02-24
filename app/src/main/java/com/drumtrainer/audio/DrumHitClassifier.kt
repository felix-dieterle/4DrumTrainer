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
 * This is a lightweight substitute for a full FFT; it uses Goertzel-like
 * band-pass accumulation that is fast enough to run synchronously on the
 * audio processing thread.
 *
 * @param sampleRateHz  Recording sample rate (default: 44 100 Hz).
 */
class DrumHitClassifier(private val sampleRateHz: Int = 44_100) {

    /**
     * Classifies [snippet] (normalised float PCM) and returns the most likely
     * [DrumPart], or `null` if the energy is too low to be a meaningful hit.
     *
     * @param snippet   Short PCM window (e.g. 512–2048 samples) centred on an onset.
     * @param minEnergy Minimum RMS energy to consider a valid hit (default: 0.01).
     */
    fun classify(snippet: FloatArray, minEnergy: Float = 0.01f): DrumPart? {
        val rms = computeRms(snippet)
        if (rms < minEnergy) return null

        val bandEnergies = DrumPart.values().associateWith { part ->
            bandRms(snippet, part.freqRangeLowHz, part.freqRangeHighHz)
        }

        return bandEnergies.maxByOrNull { it.value }?.key
    }

    /**
     * Estimates the RMS energy of [signal] within [lowHz]–[highHz] using a
     * simple DFT evaluated only at the bin indices corresponding to that range.
     */
    private fun bandRms(signal: FloatArray, lowHz: Int, highHz: Int): Float {
        val n = signal.size
        val lowBin  = (lowHz.toDouble()  * n / sampleRateHz).toInt().coerceIn(0, n / 2)
        val highBin = (highHz.toDouble() * n / sampleRateHz).toInt().coerceIn(0, n / 2)

        if (lowBin >= highBin) return 0f

        var sumSq = 0.0
        for (k in lowBin..highBin) {
            var re = 0.0
            var im = 0.0
            val angle = 2.0 * Math.PI * k / n
            for (i in signal.indices) {
                re += signal[i] * Math.cos(angle * i)
                im += signal[i] * Math.sin(angle * i)
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
