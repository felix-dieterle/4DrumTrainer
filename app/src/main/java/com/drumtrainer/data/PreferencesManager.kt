package com.drumtrainer.data

import android.content.Context
import android.content.SharedPreferences

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
    }
}
