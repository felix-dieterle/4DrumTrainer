package com.drumtrainer.model

/**
 * A group of thematically related [Lesson]s at the same difficulty level.
 *
 * @param index          0-based position in the [Curriculum].
 * @param titleRes       String resource ID for the level name (e.g. "Level 1 – Basic Beat").
 * @param descriptionRes String resource ID for a short description shown in the level list.
 * @param lessons        Ordered lessons that must be completed in sequence.
 * @param recommendedAgeMin  Earliest recommended age for this level.
 */
data class CurriculumLevel(
    val index: Int,
    val titleRes: Int,
    val descriptionRes: Int,
    val lessons: List<Lesson>,
    val recommendedAgeMin: Int = AgeGroup.MINIMUM_AGE
) {
    /** True when all [lessons] in this level have been passed. */
    fun isCompleted(passedLessonIds: Set<Long>): Boolean =
        lessons.all { it.id in passedLessonIds }
}
