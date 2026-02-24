package com.drumtrainer.model

/**
 * Age tiers that control tempo, repetition count and visual complexity.
 *
 * | Group         | Age range | Suggested BPM | Reps per exercise |
 * |---------------|-----------|---------------|-------------------|
 * | CHILD         |  7 – 9    |  60 – 80      |  4                |
 * | YOUNG         | 10 – 12   |  70 – 90      |  6                |
 * | TEEN_AND_ABOVE| 13+       |  80 – 120     |  8                |
 *
 * The minimum supported age is 7 years; callers MUST validate before creating
 * a [Student] profile.
 */
enum class AgeGroup(
    val minBpm: Int,
    val maxBpm: Int,
    val repsPerExercise: Int,
    val maxSimultaneousInstructions: Int
) {
    /** Ages 7–9: slow tempo, few instructions at once, large visual elements. */
    CHILD(minBpm = 60, maxBpm = 80, repsPerExercise = 4, maxSimultaneousInstructions = 1),

    /** Ages 10–12: moderate tempo, standard instruction density. */
    YOUNG(minBpm = 70, maxBpm = 90, repsPerExercise = 6, maxSimultaneousInstructions = 2),

    /** Ages 13 and above: full tempo range, richer patterns. */
    TEEN_AND_ABOVE(minBpm = 80, maxBpm = 120, repsPerExercise = 8, maxSimultaneousInstructions = 3);

    companion object {
        /** Minimum age allowed to use the app. */
        const val MINIMUM_AGE = 7

        /**
         * Returns the [AgeGroup] for a given [ageYears].
         * @throws IllegalArgumentException if [ageYears] is below [MINIMUM_AGE].
         */
        fun forAge(ageYears: Int): AgeGroup {
            require(ageYears >= MINIMUM_AGE) {
                "Student must be at least $MINIMUM_AGE years old, got $ageYears."
            }
            return when {
                ageYears <= 9  -> CHILD
                ageYears <= 12 -> YOUNG
                else           -> TEEN_AND_ABOVE
            }
        }
    }
}
