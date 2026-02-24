package com.drumtrainer.model

import java.time.LocalDate
import java.time.Period

/**
 * Represents a student/learner profile.
 *
 * @param id            Unique identifier stored in local Room database.
 * @param name          Display name of the student.
 * @param birthDate     Birth date used to calculate age and select the correct curriculum tier.
 * @param currentLevel  Current curriculum level (0-based index into [Curriculum.levels]).
 * @param createdAt     ISO date when the profile was first created.
 */
data class Student(
    val id: Long = 0,
    val name: String,
    val birthDate: LocalDate,
    val currentLevel: Int = 0,
    val createdAt: LocalDate = LocalDate.now()
) {
    /** Age in full years at the time of the call. */
    val ageYears: Int
        get() = Period.between(birthDate, LocalDate.now()).years

    /**
     * Returns the [AgeGroup] that governs tempo, repetition count and difficulty
     * scaling for this student.
     */
    val ageGroup: AgeGroup
        get() = AgeGroup.forAge(ageYears)
}
