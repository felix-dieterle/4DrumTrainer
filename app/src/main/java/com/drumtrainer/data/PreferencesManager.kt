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

    companion object {
        private const val PREFS_FILE          = "drumtrainer_prefs"
        private const val KEY_ACTIVE_STUDENT  = "active_student_id"
        private const val KEY_FIRST_LAUNCH    = "first_launch"
        private const val KEY_LAST_LESSON     = "last_lesson_id"
        private const val KEY_PROFILE_PIC     = "profile_picture_uri"

        private fun calKeyLow(part: DrumPart)  = "cal_${part.name}_low"
        private fun calKeyHigh(part: DrumPart) = "cal_${part.name}_high"
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
}
