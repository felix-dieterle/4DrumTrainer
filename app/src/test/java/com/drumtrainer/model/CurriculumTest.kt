package com.drumtrainer.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CurriculumTest {

    private fun makeStudent(age: Int, level: Int = 0): Student {
        val birthDate = LocalDate.now().minusYears(age.toLong())
        return Student(id = 1, name = "Test", birthDate = birthDate, currentLevel = level)
    }

    private val dummyLesson = Lesson(
        id = 1, levelIndex = 0, lessonIndex = 0,
        titleRes = 0, descriptionRes = 0,
        targetBpmMin = 60, targetBpmMax = 80,
        pattern = emptyList()
    )

    private fun makeCurriculum(numLevels: Int): Curriculum {
        val levels = (0 until numLevels).map { idx ->
            CurriculumLevel(
                index = idx,
                titleRes = 0,
                descriptionRes = 0,
                recommendedAgeMin = 7,
                lessons = listOf(
                    dummyLesson.copy(id = (idx * 10 + 1).toLong(), levelIndex = idx)
                )
            )
        }
        return Curriculum(levels)
    }

    @Test
    fun `child student cannot access more than 4 levels`() {
        val curriculum = makeCurriculum(8)
        val student    = makeStudent(age = 7)
        val accessible = curriculum.accessibleLevels(student)
        assertTrue("Child should see at most 4 levels", accessible.size <= 4)
    }

    @Test
    fun `teen student can access all levels`() {
        val curriculum = makeCurriculum(8)
        val student    = makeStudent(age = 15)
        val accessible = curriculum.accessibleLevels(student)
        assertEquals(8, accessible.size)
    }

    @Test
    fun `nextLesson returns first lesson when none passed`() {
        val curriculum = makeCurriculum(3)
        val student    = makeStudent(age = 15)
        val next       = curriculum.nextLesson(student, emptySet())
        assertNotNull(next)
        assertEquals(1L, next?.id)
    }

    @Test
    fun `nextLesson skips passed lessons`() {
        val curriculum = makeCurriculum(3)
        val student    = makeStudent(age = 15)
        val passedIds  = setOf(1L) // first lesson passed
        val next       = curriculum.nextLesson(student, passedIds)
        assertNotNull(next)
        assertEquals(11L, next?.id) // second level's first lesson
    }

    @Test
    fun `nextLesson returns null when all lessons passed`() {
        val curriculum = makeCurriculum(2)
        val student    = makeStudent(age = 15)
        val passedIds  = setOf(1L, 11L) // all lessons
        val next       = curriculum.nextLesson(student, passedIds)
        assertNull(next)
    }

    @Test
    fun `CurriculumLevel isCompleted returns true when all lessons passed`() {
        val level = CurriculumLevel(
            index = 0, titleRes = 0, descriptionRes = 0,
            lessons = listOf(dummyLesson.copy(id = 1), dummyLesson.copy(id = 2))
        )
        assertTrue(level.isCompleted(setOf(1L, 2L)))
    }

    @Test
    fun `CurriculumLevel isCompleted returns false when only some lessons passed`() {
        val level = CurriculumLevel(
            index = 0, titleRes = 0, descriptionRes = 0,
            lessons = listOf(dummyLesson.copy(id = 1), dummyLesson.copy(id = 2))
        )
        assertFalse(level.isCompleted(setOf(1L)))
    }
}
