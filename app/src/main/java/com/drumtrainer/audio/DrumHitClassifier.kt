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
 * adapt to the acoustic environment of each physical drum kit.  **Instruments
 * that have not been calibrated fall back to their factory default range** — a
 * partially calibrated kit therefore never collapses every hit onto the few
 * calibrated instruments.
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
 * ### Feature-vector calibration
 *
 * When [featureCalibration] is provided (non-empty), the classifier first tries a
 * **nearest-neighbour** strategy using three spectral features computed by
 * [AudioUtils.computeSpectralFeatures]:
 *
 * 1. **Spectral centroid** – frequency-weighted mean of the DFT magnitude spectrum.
 *    More stable across repeated hits than the raw DFT peak frequency, especially
 *    for instruments with complex multi-resonance spectra (e.g. snare drum).
 * 2. **Low-energy ratio** – fraction of energy below [AudioUtils.FEATURE_LOW_HZ].
 * 3. **High-energy ratio** – fraction of energy above [AudioUtils.FEATURE_HIGH_HZ].
 *
 * Each feature is normalised by the observed spread across the calibrated
 * instruments (with a minimum span per dimension) so that all three dimensions
 * contribute meaningfully.  The feature path is used only when it can actually
 * discriminate: at least two instruments must be feature-calibrated and the
 * absolute distance must be below [FEATURE_MAX_DISTANCE].  Hits that are far
 * away from every calibrated profile (i.e. an uncalibrated instrument) fall
 * back to band classification using calibrated ranges where available and
 * factory defaults for all other instruments.  This prevents a single
 * calibrated instrument — or a small, partially calibrated subset — from
 * swallowing every hit.  With a complete kit calibration, a hit that is
 * ambiguous between two instruments yields `null` (unknown) instead of a guess.
 *
 * @param sampleRateHz       Recording sample rate (default: 44 100 Hz).
 * @param calibration        Optional map of per-[DrumPart] (lowHz, highHz) overrides
 *                           produced by [com.drumtrainer.audio.InstrumentCalibrator].
 * @param featureCalibration Optional map of per-[DrumPart] three-element feature
 *                           vectors [centroidHz, lowEnergyRatio, highEnergyRatio]
 *                           produced by [com.drumtrainer.audio.InstrumentCalibrator].
 *                           When it contains at least two instruments it enables
 *                           nearest-neighbour feature matching as a first pass.
 */
class DrumHitClassifier(
    private val sampleRateHz: Int = 44_100,
    private val calibration: Map<DrumPart, Pair<Int, Int>> = emptyMap(),
    private val featureCalibration: Map<DrumPart, FloatArray> = emptyMap()
) {

    /**
     * Per-feature (centroid, lowRatio, hiRatio) normalisation span computed from
     * [featureCalibration], with a per-dimension minimum to keep partial
     * calibrations meaningful.
     */
    private val featureSpan: FloatArray

    init {
        featureSpan = FloatArray(3)
        if (featureCalibration.isNotEmpty()) {
            val vecs = featureCalibration.values
            for (i in 0 until 3) {
                val min = vecs.minOf { it[i] }
                val max = vecs.maxOf { it[i] }
                featureSpan[i] = maxOf(max - min, FEATURE_MIN_SPAN[i])
            }
        }
    }

    /**
     * Returns `true` when per-instrument calibration data has been provided, meaning
     * the frequency bands used for classification are kit-specific rather than the
     * broad factory defaults.  When calibrated, a higher [confidenceRatio] can be
     * used in [classify] to reject ambiguous hits rather than guess.
     */
    val isCalibrated: Boolean get() = calibration.isNotEmpty() || featureCalibration.isNotEmpty()

    companion object {
        /**
         * Frequency boundary (Hz) above which cymbals concentrate most of their
         * spectral energy.  Metallic cymbals (hi-hat, ride, crash) have strong
         * inharmonic partials in this region; membrane drums (bass drum, snare,
         * toms) do not.
         */
        const val CYMBAL_HF_THRESHOLD_HZ = 3_000

        /**
         * Minimum fraction of total spectral **energy** above
         * [CYMBAL_HF_THRESHOLD_HZ] at which a hit is treated as a cymbal rather
         * than a drum.  Using an energy fraction (not a ratio of RMS magnitudes)
         * avoids over-weighting the many high-frequency bins and therefore stops
         * bright snares from being mistaken for cymbals.
         */
        const val CYMBAL_HF_RATIO_THRESHOLD = 0.30f

        /**
         * Default [confidenceRatio] applied when per-instrument calibration data is
         * available ([isCalibrated] is `true`).  The winner's band energy must be at
         * least this many times the runner-up's energy; hits that fall in the overlap
         * between two calibrated bands return `null` rather than a wrong instrument.
         */
        const val CALIBRATED_CONFIDENCE_RATIO = 1.5f

        /** Minimum number of feature-calibrated instruments required for k-NN matching. */
        private const val MIN_FEATURE_PARTS = 2

        /**
         * Maximum normalised feature distance for a feature match to be trusted.
         * Hits farther away than this are assumed to belong to an uncalibrated
         * instrument and are classified by the band fallback instead.
         */
        private const val FEATURE_MAX_DISTANCE = 0.70f

        /**
         * Minimum distance advantage of the best feature match over the runner-up.
         * Closer matches are treated as ambiguous.
         */
        private const val FEATURE_MIN_MARGIN = 0.10f

        /**
         * Minimum normalisation span per feature dimension.  Without this a partial
         * calibration (e.g. only two instruments) could produce a degenerate range
         * and make every hit look like a perfect feature match.
         */
        private val FEATURE_MIN_SPAN = floatArrayOf(1_500f, 0.5f, 0.5f)
    }

    private data class FeatureMatch(
        val part: DrumPart,
        val distance: Float,
        val secondDistance: Float
    )

    /**
     * Classifies [snippet] (normalised float PCM) and returns the most likely
     * [DrumPart], or `null` if the energy is too low to be a meaningful hit or
     * if the classification is ambiguous.
     *
     * A Hann window is applied to [snippet] once before computing the spectrum
     * to suppress spectral leakage.
     *
     * When [confidenceRatio] > 1.0, the band winner must have at least that many
     * times the energy of the runner-up; otherwise the hit is considered
     * ambiguous and `null` is returned.
     *
     * @param snippet          Short PCM window (e.g. 2048 samples) centred on an onset.
     * @param minEnergy        Minimum RMS energy to consider a valid hit (default: 0.01).
     * @param confidenceRatio  Minimum ratio of best-band energy to runner-up energy
     *                         required for a confident band classification
     *                         (default: 1.0 = disabled).
     */
    fun classify(
        snippet: FloatArray,
        minEnergy: Float = 0.01f,
        confidenceRatio: Float = 1.0f
    ): DrumPart? {
        val rms = computeRms(snippet)
        if (rms < minEnergy) return null

        // Apply Hann window once to suppress spectral leakage before the FFT.
        val windowed = AudioUtils.applyHannWindow(snippet)
        val spectrum = AudioUtils.computeMagnitudeSpectrum(windowed)

        // --- Feature-vector path (nearest-neighbour in spectral-feature space) ----
        // Only trust this path when it can actually discriminate: at least two
        // calibrated instruments, a close absolute match, and a clear margin to
        // the runner-up.  Otherwise fall through to the band classifier, which
        // uses factory defaults for uncalibrated instruments.
        if (featureCalibration.size >= MIN_FEATURE_PARTS) {
            val match = classifyByFeatures(windowed)
            if (match != null && match.distance <= FEATURE_MAX_DISTANCE) {
                val marginOk = match.secondDistance - match.distance >= FEATURE_MIN_MARGIN
                if (marginOk) return match.part
                // A full-kit feature calibration that is genuinely ambiguous should
                // report "unknown" rather than guess a band from overlapping defaults.
                if (featureCalibration.size >= DrumPart.values().size) return null
                // With a partial calibration, trust the nearest calibrated instrument:
                // the alternative (falling back to factory ranges) is less accurate
                // for the instruments that *were* calibrated.
                return match.part
            }
        }

        val bandEnergies = DrumPart.values().associateWith { part ->
            val (low, high) = calibration[part] ?: (part.freqRangeLowHz to part.freqRangeHighHz)
            AudioUtils.bandRms(spectrum, sampleRateHz, low, high)
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
        val likelyCymbal = isLikelyCymbal(spectrum)
        if (best.key.isCymbal != likelyCymbal) {
            return sorted.firstOrNull { it.key.isCymbal == likelyCymbal }?.key
        }

        return best.key
    }

    /**
     * Classifies [windowed] (Hann-windowed PCM) using nearest-neighbour matching
     * in the three-dimensional spectral feature space.  Returns `null` when no
     * calibration data is usable.
     */
    private fun classifyByFeatures(windowed: FloatArray): FeatureMatch? {
        if (featureCalibration.isEmpty()) return null

        val feats = AudioUtils.computeSpectralFeatures(windowed, sampleRateHz)

        var bestPart: DrumPart? = null
        var bestDist = Float.MAX_VALUE
        var secondDist = Float.MAX_VALUE

        for ((part, calFeats) in featureCalibration) {
            val dist = normalizedFeatureDistance(feats, calFeats)
            if (dist < bestDist) {
                secondDist = bestDist
                bestDist = dist
                bestPart = part
            } else if (dist < secondDist) {
                secondDist = dist
            }
        }
        val part = bestPart ?: return null
        return FeatureMatch(part, bestDist, secondDist)
    }

    /**
     * Computes the Euclidean distance between feature vectors [a] and [b] in
     * normalised space.  Each dimension is scaled by the observed span across all
     * calibrated instruments (with a per-dimension minimum span) so that a unit
     * change in centroid Hz counts comparably to a unit change in the ratios.
     */
    private fun normalizedFeatureDistance(a: FloatArray, b: FloatArray): Float {
        var sumSq = 0.0
        for (i in 0 until 3) {
            val span = featureSpan[i]
            val diff = if (span > 0f) (a[i] - b[i]) / span else 0f
            sumSq += diff.toDouble() * diff.toDouble()
        }
        return sqrt(sumSq).toFloat()
    }

    /**
     * Returns `true` when the high-frequency **energy fraction** of [spectrum] is
     * at or above [CYMBAL_HF_RATIO_THRESHOLD], indicating the hit most likely came
     * from a cymbal rather than a membrane drum.
     */
    private fun isLikelyCymbal(spectrum: FloatArray): Boolean {
        val hfFraction = AudioUtils.bandEnergyFraction(
            spectrum, sampleRateHz, CYMBAL_HF_THRESHOLD_HZ, sampleRateHz / 2
        )
        return hfFraction >= CYMBAL_HF_RATIO_THRESHOLD
    }

    private fun computeRms(signal: FloatArray): Float {
        val sumSq = signal.fold(0.0) { acc, s -> acc + s * s }
        return sqrt(sumSq / signal.size).toFloat()
    }
}
