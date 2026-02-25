package com.drumtrainer.model

/**
 * The complete ordered list of [CurriculumLevel]s that make up the app's learning path.
 *
 * The curriculum is structured so that a student starting at age 7 can progress
 * through all levels at a comfortable pace:
 *
 * | Level | Name                    | BPM range  | Skills introduced                        |
 * |-------|-------------------------|------------|------------------------------------------|
 * |   0   | Getting Started         |  60 – 70   | Steady hi-hat quarter notes              |
 * |   1   | The Basic Beat          |  65 – 80   | Bass on 1 & 3, snare on 2 & 4           |
 * |   2   | Eighth-Note Hi-Hat      |  70 – 85   | Hi-hat eighth notes added to basic beat  |
 * |   3   | The Open Hi-Hat         |  75 – 90   | Open hi-hat on beat 2 & 4 variations    |
 * |   4   | Introducing the Toms    |  80 – 95   | Tom fills using high, mid, and floor toms|
 * |   5   | Crash & Ride            |  85 – 100  | Cymbal accents, ride pattern             |
 * |   6   | Sixteenth-Note Patterns |  90 – 110  | Sixteenth-note hi-hat, ghost notes       |
 * |   7   | Advanced Fills          |  95 – 120  | Multi-tom fills, syncopation             |
 *
 * @param levels Ordered list of [CurriculumLevel]; the first level is always unlocked.
 */
data class Curriculum(val levels: List<CurriculumLevel>) {

    /**
     * Returns levels that are accessible for the given [student].
     * Younger students (age group [AgeGroup.CHILD]) are capped at a lower
     * initial max level to prevent overwhelm.
     */
    fun accessibleLevels(student: Student): List<CurriculumLevel> {
        val maxLevel = when (student.ageGroup) {
            AgeGroup.CHILD         -> minOf(levels.lastIndex, 3)
            AgeGroup.YOUNG         -> minOf(levels.lastIndex, 5)
            AgeGroup.TEEN_AND_ABOVE -> levels.lastIndex
        }
        return levels.subList(0, maxLevel + 1)
    }

    /**
     * Finds the next lesson a student should attempt, taking into account
     * which lessons have already been passed.
     *
     * @param student          The current [Student].
     * @param passedLessonIds  Set of [Lesson.id] values the student has already passed.
     * @return The next un-passed [Lesson], or `null` if all accessible lessons are done.
     */
    fun nextLesson(student: Student, passedLessonIds: Set<Long>): Lesson? =
        accessibleLevels(student)
            .flatMap { it.lessons }
            .firstOrNull { it.id !in passedLessonIds }
}
