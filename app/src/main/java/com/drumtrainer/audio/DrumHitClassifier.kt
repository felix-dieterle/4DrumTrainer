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
 * ### Drum vs. cymbal discrimination
 *
 * Unlike classical instruments — which have stable harmonic spectra that can be
 * separated by their fundamental frequency — drums and cymbals produce only short
 * transient bursts.  Cymbals (especially Crash and Ride) have extremely broad,
 * inharmonic spectra that overlap several drum frequency bands.  Simple
 * band-energy comparison can therefore mis-classify a drum hit as a cymbal (or
 * vice-versa) when the wider band accumulates more total energy.
 *
 * To address this, the classifier first determines the *drum family* (cymbal vs.
 * membrane drum) by computing the **high-frequency energy ratio**: the fraction of
 * spectral energy above [CYMBAL_HF_THRESHOLD_HZ].  Cymbals concentrate the
 * majority of their energy above this boundary; drums do not.  When the
 * frequency-band winner belongs to the wrong family, the classifier instead
 * returns the best-ranked candidate from the family that matches the HF ratio
 * prediction.
 *
 * @param sampleRateHz  Recording sample rate (default: 44 100 Hz).
 * @param calibration   Optional map of per-[DrumPart] (lowHz, highHz) overrides
 *                      produced by [com.drumtrainer.audio.InstrumentCalibrator].
 */
class DrumHitClassifier(
    private val sampleRateHz: Int = 44_100,
    private val calibration: Map<DrumPart, Pair<Int, Int>> = emptyMap()
) {

    companion object {
        /**
         * Frequency boundary (Hz) above which cymbals concentrate most of their
         * spectral energy.  Metallic cymbals (hi-hat, ride, crash) have strong
         * inharmonic partials in this region; membrane drums (bass drum, snare,
         * toms) do not.
         */
        const val CYMBAL_HF_THRESHOLD_HZ = 3_000

        /**
         * Minimum ratio of high-frequency energy (above [CYMBAL_HF_THRESHOLD_HZ])
         * to total spectral energy at which a hit is treated as a cymbal rather than
         * a drum.  A value of 0.4 means at least 40 % of the spectral energy must
         * lie above [CYMBAL_HF_THRESHOLD_HZ] for the hit to be considered a cymbal.
         */
        const val CYMBAL_HF_RATIO_THRESHOLD = 0.4f
    }

    /**
     * Classifies [snippet] (normalised float PCM) and returns the most likely
     * [DrumPart], or `null` if the energy is too low to be a meaningful hit or
     * if the classification is ambiguous.
     *
     * A Hann window is applied to [snippet] once before computing band energies
     * to suppress spectral leakage.
     *
     * When [confidenceRatio] > 1.0, the winner must have at least that many
     * times the energy of the runner-up; otherwise the hit is considered
     * ambiguous and `null` is returned.  This prevents mis-classification when
     * two calibrated bands overlap.  With the default (1.0), every non-silent hit
     * yields a classification, preserving backward-compatible behaviour for
     * uncalibrated kits whose default frequency ranges overlap heavily.
     * Set [confidenceRatio] > 1.0 (e.g. 1.5) when the classifier is supplied
     * with a complete, non-overlapping calibration.
     *
     * After the confidence check, a **drum-family filter** is applied: the
     * high-frequency energy ratio of the snippet (fraction of energy above
     * [CYMBAL_HF_THRESHOLD_HZ]) is compared against [CYMBAL_HF_RATIO_THRESHOLD].
     * If the frequency-band winner belongs to the wrong family (e.g. a broad
     * Crash band accumulates more energy than a Snare band for what was clearly
     * a low-frequency drum hit), the highest-ranked candidate from the correct
     * family is returned instead.  This directly addresses the primary challenge
     * of drum/cymbal distinction: broad-band cymbals can outrank drums on total
     * band energy even when the transient's spectrum is dominated by low
     * frequencies — a situation that does not arise with classical instruments.
     *
     * @param snippet          Short PCM window (e.g. 512–2048 samples) centred on an onset.
     * @param minEnergy        Minimum RMS energy to consider a valid hit (default: 0.01).
     * @param confidenceRatio  Minimum ratio of best-band energy to runner-up energy
     *                         required for a confident classification (default: 1.0 = disabled).
     */
    fun classify(
        snippet: FloatArray,
        minEnergy: Float = 0.01f,
        confidenceRatio: Float = 1.0f
    ): DrumPart? {
        val rms = computeRms(snippet)
        if (rms < minEnergy) return null

        // Apply Hann window once to suppress spectral leakage before any DFT.
        val windowed = AudioUtils.applyHannWindow(snippet)

        val bandEnergies = DrumPart.values().associateWith { part ->
            val (low, high) = calibration[part] ?: (part.freqRangeLowHz to part.freqRangeHighHz)
            bandRms(windowed, low, high)
        }

        val sorted = bandEnergies.entries.sortedByDescending { it.value }
        val best   = sorted.firstOrNull() ?: return null
        val second = sorted.getOrNull(1)

        // Require the winner to be confidently ahead of the runner-up to avoid
        // mis-classifying hits that fall in an overlapping region of two bands.
        if (second != null && second.value > 0f && best.value / second.value < confidenceRatio) {
            return null
        }

        // --- Drum-family filter -----------------------------------------------
        // Cymbals concentrate most of their energy above CYMBAL_HF_THRESHOLD_HZ;
        // drums do not.  When the frequency-band winner belongs to the wrong family
        // (e.g. a broad Crash band wins for what is clearly a low-frequency drum
        // hit), return the top-ranked candidate from the correct family instead.
        val likelyCymbal = isLikelyCymbal(windowed)
        if (best.key.isCymbal != likelyCymbal) {
            return sorted.firstOrNull { it.key.isCymbal == likelyCymbal }?.key
        }

        return best.key
    }

    /**
     * Returns `true` when the high-frequency energy ratio of [windowed] is at or
     * above [CYMBAL_HF_RATIO_THRESHOLD], indicating the hit most likely came from
     * a cymbal rather than a membrane drum.
     *
     * The ratio is computed as:
     *   bandRms(windowed, CYMBAL_HF_THRESHOLD_HZ, Nyquist) / bandRms(windowed, 0, Nyquist)
     *
     * This exploits the key physical difference between drums and cymbals that
     * makes their discrimination harder than for classical instruments: unlike
     * strings or woodwinds (which have well-separated harmonic fundamentals),
     * cymbals produce broad inharmonic spectra with most energy above ~3 kHz,
     * while drums concentrate their energy below ~1 kHz.  Frequency-band energy
     * alone is not sufficient because wide-range cymbal bands (e.g. Crash: 200–
     * 10 000 Hz) can accumulate more total energy than a narrow drum band even
     * when the actual strike was on a drum.
     */
    private fun isLikelyCymbal(windowed: FloatArray): Boolean {
        val totalEnergy = bandRms(windowed, 0, sampleRateHz / 2)
        if (totalEnergy == 0f) return false
        val hfEnergy = bandRms(windowed, CYMBAL_HF_THRESHOLD_HZ, sampleRateHz / 2)
        return hfEnergy / totalEnergy >= CYMBAL_HF_RATIO_THRESHOLD
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
