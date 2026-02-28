package com.drumtrainer.data

import android.content.Context
import android.content.SharedPreferences
import com.drumtrainer.model.DrumPart

/**
 * Thin wrapper around [SharedPreferences] for simple key-value persistence.
 * Uses the "drumtrainer_prefs" preferences file.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // ── Active student ────────────────────────────────────────────────────────

    var activeStudentId: Long
        get()      = prefs.getLong(KEY_ACTIVE_STUDENT, -1L)
        set(value) = prefs.edit().putLong(KEY_ACTIVE_STUDENT, value).apply()

    // ── First-launch flag ─────────────────────────────────────────────────────

    var isFirstLaunch: Boolean
        get()      = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    // ── Last-opened lesson ────────────────────────────────────────────────────

    var lastLessonId: Long
        get()      = prefs.getLong(KEY_LAST_LESSON, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_LESSON, value).apply()

    // ── Profile picture ───────────────────────────────────────────────────────

    var profilePictureUri: String
        get()      = prefs.getString(KEY_PROFILE_PIC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROFILE_PIC, value).apply()

    // ── Cheat mode ────────────────────────────────────────────────────────────

    var cheatModeEnabled: Boolean
        get()      = prefs.getBoolean(KEY_CHEAT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_CHEAT_MODE, value).apply()

    /**
     * Persisted SeekBar progress (0–10) for the microphone sensitivity slider
     * shown in [com.drumtrainer.CalibrationActivity].  Default is 5 (medium).
     * A lower value means less sensitive (higher onset threshold, fewer false positives
     * from ambient noise); a higher value means more sensitive (lower threshold, detects
     * softer hits).
     */
    var micSensitivity: Int
        get()      = prefs.getInt(KEY_MIC_SENSITIVITY, 5)
        set(value) = prefs.edit().putInt(KEY_MIC_SENSITIVITY, value).apply()

    companion object {
        private const val PREFS_FILE          = "drumtrainer_prefs"
        private const val KEY_ACTIVE_STUDENT  = "active_student_id"
        private const val KEY_FIRST_LAUNCH    = "first_launch"
        private const val KEY_LAST_LESSON     = "last_lesson_id"
        private const val KEY_PROFILE_PIC     = "profile_picture_uri"
        private const val KEY_CHEAT_MODE      = "cheat_mode"
        private const val KEY_MIC_SENSITIVITY = "mic_sensitivity"

        private fun calKeyLow(part: DrumPart)    = "cal_${part.name}_low"
        private fun calKeyHigh(part: DrumPart)   = "cal_${part.name}_high"
        private fun calKeyMean(part: DrumPart)   = "cal_${part.name}_mean"
        private fun calKeyStddev(part: DrumPart) = "cal_${part.name}_stddev"
        private fun calKeyPeaks(part: DrumPart)  = "cal_${part.name}_peaks"
    }

    // ── Instrument calibration ────────────────────────────────────────────────

    /**
     * Persists a calibrated frequency band for [part].
     *
     * @param part   The drum instrument being calibrated.
     * @param lowHz  Detected lower bound of the dominant frequency band.
     * @param highHz Detected upper bound of the dominant frequency band.
     */
    fun setCalibration(part: DrumPart, lowHz: Int, highHz: Int) {
        prefs.edit()
            .putInt(calKeyLow(part), lowHz)
            .putInt(calKeyHigh(part), highHz)
            .apply()
    }

    /**
     * Returns the previously saved calibration for [part], or `null` if the
     * instrument has never been calibrated.
     */
    fun getCalibration(part: DrumPart): Pair<Int, Int>? {
        val low  = prefs.getInt(calKeyLow(part),  -1)
        val high = prefs.getInt(calKeyHigh(part), -1)
        return if (low > 0 && high > 0) low to high else null
    }

    /** Removes the saved calibration for [part], restoring the default range. */
    fun clearCalibration(part: DrumPart) {
        prefs.edit()
            .remove(calKeyLow(part))
            .remove(calKeyHigh(part))
            .remove(calKeyMean(part))
            .remove(calKeyStddev(part))
            .remove(calKeyPeaks(part))
            .apply()
    }

    /**
     * Returns a map of all [DrumPart]s that have been calibrated to their
     * saved (lowHz, highHz) ranges.  Parts that have not been calibrated are
     * absent from the map.
     */
    fun getAllCalibrations(): Map<DrumPart, Pair<Int, Int>> =
        DrumPart.values().mapNotNull { part ->
            getCalibration(part)?.let { part to it }
        }.toMap()

    // ── Calibration statistics ────────────────────────────────────────────────

    /**
     * Persists the statistical summary of a calibration session for [part].
     *
     * @param part     The drum instrument that was calibrated.
     * @param meanHz   Mean of the collected peak frequencies in Hz.
     * @param stddevHz Standard deviation of the collected peak frequencies in Hz.
     */
    fun setCalibrationStats(part: DrumPart, meanHz: Double, stddevHz: Double) {
        prefs.edit()
            .putFloat(calKeyMean(part),   meanHz.toFloat())
            .putFloat(calKeyStddev(part), stddevHz.toFloat())
            .apply()
    }

    /**
     * Returns the previously saved calibration statistics for [part], or `null`
     * if the instrument has never been calibrated.
     *
     * @return Pair of (meanHz, stddevHz) or `null`.
     */
    fun getCalibrationStats(part: DrumPart): Pair<Double, Double>? {
        val mean   = prefs.getFloat(calKeyMean(part),   -1f)
        val stddev = prefs.getFloat(calKeyStddev(part), -1f)
        return if (mean >= 0f && stddev >= 0f) mean.toDouble() to stddev.toDouble() else null
    }

    /**
     * Returns a map of all [DrumPart]s that have saved calibration statistics.
     * Parts without statistics are absent from the map.
     */
    fun getAllCalibrationStats(): Map<DrumPart, Pair<Double, Double>> =
        DrumPart.values().mapNotNull { part ->
            getCalibrationStats(part)?.let { part to it }
        }.toMap()

    // ── Raw peak frequencies ──────────────────────────────────────────────────

    /**
     * Persists the raw per-hit peak frequencies recorded during calibration of [part].
     * Stored as a comma-separated string so the full measurement set is available
     * for export and diagnosis without re-recording.
     *
     * @param part             The drum instrument that was calibrated.
     * @param peakFrequencies  Ordered list of peak-frequency measurements (one per hit).
     */
    fun setPeakFrequencies(part: DrumPart, peakFrequencies: List<Int>) {
        prefs.edit()
            .putString(calKeyPeaks(part), peakFrequencies.joinToString(","))
            .apply()
    }

    /**
     * Returns the raw peak frequencies saved during calibration of [part], or `null`
     * if the instrument has never been calibrated.
     *
     * Returns `null` (not an empty list) if the stored value is absent or unparseable,
     * because calibration always records at least one hit — an empty result implies the
     * key was never written.
     */
    fun getPeakFrequencies(part: DrumPart): List<Int>? {
        val raw = prefs.getString(calKeyPeaks(part), null) ?: return null
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.isNotEmpty() }
    }

    /**
     * Returns a map of all [DrumPart]s that have saved raw peak frequencies.
     * Parts without saved peaks are absent from the map.
     */
    fun getAllPeakFrequencies(): Map<DrumPart, List<Int>> =
        DrumPart.values().mapNotNull { part ->
            getPeakFrequencies(part)?.let { part to it }
        }.toMap()
}
