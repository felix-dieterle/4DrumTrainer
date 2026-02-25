package com.drumtrainer.model

/**
 * A single drum lesson.
 *
 * Lessons are arranged inside a [CurriculumLevel] which belongs to the overall
 * [Curriculum]. Each lesson defines the expected beat pattern, the target BPM
 * range, and the accuracy threshold a student must reach to unlock the next lesson.
 *
 * @param id                  Stable identifier (used as primary key in Room).
 * @param levelIndex          Index of the [CurriculumLevel] this lesson belongs to (0-based).
 * @param lessonIndex         Position within its level (0-based).
 * @param titleRes            Android string resource ID for the lesson title.
 * @param descriptionRes      Android string resource ID for the lesson description shown to the student.
 * @param beatsPerBar         Number of beats per bar (typically 4 for 4/4 time).
 * @param subdivision         Subdivision value for the pattern (1=quarter, 2=eighth, 4=sixteenth notes).
 * @param targetBpmMin        Minimum BPM the student should aim for in this lesson.
 * @param targetBpmMax        Maximum BPM – the student should not rush above this value.
 * @param pattern             Ordered list of [BeatEvent]s defining what to play each bar.
 * @param passThresholdPct    Minimum accuracy percentage (0–100) to pass this lesson.
 * @param estimatedMinutes    Expected practice time in minutes per session.
 */
data class Lesson(
    val id: Long,
    val levelIndex: Int,
    val lessonIndex: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val beatsPerBar: Int = 4,
    val subdivision: Int = 1,
    val targetBpmMin: Int,
    val targetBpmMax: Int,
    val pattern: List<BeatEvent>,
    val passThresholdPct: Int = 70,
    val estimatedMinutes: Int = 10
)
