package com.drumtrainer.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StudentTest {

    private fun makeStudent(ageYears: Int): Student {
        val birthDate = LocalDate.now().minusYears(ageYears.toLong())
        return Student(name = "Test", birthDate = birthDate)
    }

    @Test
    fun `ageYears is computed correctly`() {
        val student = makeStudent(10)
        assertEquals(10, student.ageYears)
    }

    @Test
    fun `student aged 7 has CHILD age group`() {
        assertEquals(AgeGroup.CHILD, makeStudent(7).ageGroup)
    }

    @Test
    fun `student aged 11 has YOUNG age group`() {
        assertEquals(AgeGroup.YOUNG, makeStudent(11).ageGroup)
    }

    @Test
    fun `student aged 14 has TEEN_AND_ABOVE age group`() {
        assertEquals(AgeGroup.TEEN_AND_ABOVE, makeStudent(14).ageGroup)
    }
}
