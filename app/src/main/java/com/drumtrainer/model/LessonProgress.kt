package com.drumtrainer.model

import java.time.LocalDate

/**
 * Records the outcome of one attempt at a [Lesson] by a [Student].
 *
 * @param id            Database primary key.
 * @param studentId     Foreign key → [Student.id].
 * @param lessonId      Foreign key → [Lesson.id].
 * @param attemptDate   Calendar date of the attempt.
 * @param rhythmScore   Rhythm accuracy 0–100: percentage of hits within the timing window.
 * @param pitchScore    Instrument accuracy 0–100: percentage of hits on the correct drum part.
 * @param overallScore  Composite score = (rhythmScore * 0.6 + pitchScore * 0.4), rounded.
 * @param durationSec   How long the student practiced this lesson, in seconds.
 * @param passed        True when [overallScore] ≥ the lesson's [Lesson.passThresholdPct].
 */
data class LessonProgress(
    val id: Long = 0,
    val studentId: Long,
    val lessonId: Long,
    val attemptDate: LocalDate = LocalDate.now(),
    val rhythmScore: Int,
    val pitchScore: Int,
    val overallScore: Int = ((rhythmScore * 0.6) + (pitchScore * 0.4)).toInt(),
    val durationSec: Int,
    val passed: Boolean
)
