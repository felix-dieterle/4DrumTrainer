package com.drumtrainer.audio

import com.drumtrainer.model.DrumPart
import java.util.Locale

/**
 * Builds a plain-text CSV diagnostic report covering the frequency-band
 * "footprint" and calibration statistics for every [DrumPart].
 *
 * The report is intended to help diagnose why two or more instruments cannot
 * be distinguished from each other: overlapping bands mean the classifier
 * sees the same dominant-energy region and must guess.
 *
 * The class is **pure** (no Android context dependency) so it can be
 * exercised by plain JUnit tests.
 *
 * Columns in the CSV:
 * - **Instrument** – [DrumPart.displayName]
 * - **Default Low Hz / Default High Hz** – nominal range from [DrumPart]
 * - **Calibrated Low Hz / Calibrated High Hz** – user-measured range, or
 *   empty if the instrument has not been calibrated
 * - **Cal. Mean Hz** – mean of the peak frequencies collected during
 *   calibration, or empty
 * - **Cal. Std Dev Hz** – standard deviation of those peak frequencies
 * - **CV (%)** – coefficient of variation = stddev/mean × 100; high values
 *   indicate inconsistent hits during calibration and therefore an unreliable
 *   calibrated band; empty if not calibrated
 * - **Effective Low Hz / Effective High Hz** – the range actually used by
 *   [DrumHitClassifier] (calibrated if available, otherwise default)
 * - **Band Width Hz** – width of the effective band (effHigh − effLow);
 *   very wide bands are more likely to overlap neighbouring instruments
 * - **Overlapping Instruments (overlap Hz)** – display name and overlap size
 *   in Hz for every other [DrumPart] whose effective band intersects this one
 * - **Peak Frequencies Hz** – the individual per-hit peak-frequency
 *   measurements recorded during calibration; useful to spot outliers that
 *   may have widened the calibrated band; empty if not calibrated
 */
object DiagnosticsExporter {

    private val HEADER = listOf(
        "Instrument",
        "Default Low Hz",
        "Default High Hz",
        "Calibrated Low Hz",
        "Calibrated High Hz",
        "Cal. Mean Hz",
        "Cal. Std Dev Hz",
        "CV (%)",
        "Effective Low Hz",
        "Effective High Hz",
        "Band Width Hz",
        "Overlapping Instruments (overlap Hz)",
        "Peak Frequencies Hz"
    ).joinToString(",")

    /**
     * Builds the CSV report.
     *
     * @param calibrations      Map of calibrated (lowHz, highHz) per [DrumPart], as
     *                          returned by [com.drumtrainer.data.PreferencesManager.getAllCalibrations].
     * @param stats             Map of calibration (meanHz, stddevHz) per [DrumPart], as
     *                          returned by [com.drumtrainer.data.PreferencesManager.getAllCalibrationStats].
     * @param peakFrequencies   Map of raw per-hit peak-frequency measurements per [DrumPart], as
     *                          returned by [com.drumtrainer.data.PreferencesManager.getAllPeakFrequencies].
     * @return                  Multi-line CSV string with a header row followed by one
     *                          row per [DrumPart].
     */
    fun buildReport(
        calibrations: Map<DrumPart, Pair<Int, Int>>,
        stats: Map<DrumPart, Pair<Double, Double>>,
        peakFrequencies: Map<DrumPart, List<Int>> = emptyMap()
    ): String {
        val parts = DrumPart.values()

        // Compute effective band for every part (calibrated takes precedence).
        val effectiveBands: Map<DrumPart, Pair<Int, Int>> = parts.associateWith { part ->
            calibrations[part] ?: (part.freqRangeLowHz to part.freqRangeHighHz)
        }

        val rows = parts.map { part ->
            val defLow  = part.freqRangeLowHz
            val defHigh = part.freqRangeHighHz
            val cal     = calibrations[part]
            val stat    = stats[part]
            val peaks   = peakFrequencies[part]
            val (effLow, effHigh) = effectiveBands.getValue(part)
            val bandWidth = effHigh - effLow

            // CV is undefined if mean is effectively zero (no valid calibration data).
            // Any real drum calibration mean will be far above 1 Hz.
            val cv = if (stat != null && stat.first > 1.0) {
                String.format(Locale.ROOT, "%.1f", stat.second / stat.first * 100.0)
            } else ""

            val overlaps = parts
                .filter { other -> other != part && bandsOverlap(effLow, effHigh, effectiveBands.getValue(other)) }
                .joinToString("; ") { other ->
                    val (oLow, oHigh) = effectiveBands.getValue(other)
                    val overlapHz = minOf(effHigh, oHigh) - maxOf(effLow, oLow)
                    "${other.displayName} (${overlapHz} Hz)"
                }

            val peaksCell = peaks?.joinToString(", ") ?: ""

            listOf(
                csvCell(part.displayName),
                defLow.toString(),
                defHigh.toString(),
                cal?.first?.toString()  ?: "",
                cal?.second?.toString() ?: "",
                stat?.first?.let  { String.format(Locale.ROOT, "%.1f", it) } ?: "",
                stat?.second?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "",
                cv,
                effLow.toString(),
                effHigh.toString(),
                bandWidth.toString(),
                csvCell(overlaps),
                csvCell(peaksCell)
            ).joinToString(",")
        }

        return (listOf(HEADER) + rows).joinToString("\n")
    }

    /** Returns `true` if [aLow, aHigh] overlaps [bLow, bHigh]. */
    internal fun bandsOverlap(aLow: Int, aHigh: Int, band: Pair<Int, Int>): Boolean {
        val (bLow, bHigh) = band
        return aLow < bHigh && aHigh > bLow
    }

    /** Wraps [value] in double-quotes if it contains a comma or double-quote. */
    private fun csvCell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
