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
 * - **Effective Low Hz / Effective High Hz** – the range actually used by
 *   [DrumHitClassifier] (calibrated if available, otherwise default)
 * - **Overlapping Instruments** – display names of every other [DrumPart]
 *   whose effective band intersects this one; helps pinpoint confusion pairs
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
        "Effective Low Hz",
        "Effective High Hz",
        "Overlapping Instruments"
    ).joinToString(",")

    /**
     * Builds the CSV report.
     *
     * @param calibrations  Map of calibrated (lowHz, highHz) per [DrumPart], as
     *                      returned by [com.drumtrainer.data.PreferencesManager.getAllCalibrations].
     * @param stats         Map of calibration (meanHz, stddevHz) per [DrumPart], as
     *                      returned by [com.drumtrainer.data.PreferencesManager.getAllCalibrationStats].
     * @return              Multi-line CSV string with a header row followed by one
     *                      row per [DrumPart].
     */
    fun buildReport(
        calibrations: Map<DrumPart, Pair<Int, Int>>,
        stats: Map<DrumPart, Pair<Double, Double>>
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
            val (effLow, effHigh) = effectiveBands.getValue(part)

            val overlaps = parts
                .filter { other -> other != part && bandsOverlap(effLow, effHigh, effectiveBands.getValue(other)) }
                .joinToString("; ") { it.displayName }

            listOf(
                csvCell(part.displayName),
                defLow.toString(),
                defHigh.toString(),
                cal?.first?.toString()  ?: "",
                cal?.second?.toString() ?: "",
                stat?.first?.let  { String.format(Locale.ROOT, "%.1f", it) } ?: "",
                stat?.second?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "",
                effLow.toString(),
                effHigh.toString(),
                csvCell(overlaps)
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
