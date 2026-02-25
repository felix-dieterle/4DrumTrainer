package com.drumtrainer.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AgeGroupTest {

    @Test
    fun `age 7 maps to CHILD`() {
        assertEquals(AgeGroup.CHILD, AgeGroup.forAge(7))
    }

    @Test
    fun `age 9 maps to CHILD`() {
        assertEquals(AgeGroup.CHILD, AgeGroup.forAge(9))
    }

    @Test
    fun `age 10 maps to YOUNG`() {
        assertEquals(AgeGroup.YOUNG, AgeGroup.forAge(10))
    }

    @Test
    fun `age 12 maps to YOUNG`() {
        assertEquals(AgeGroup.YOUNG, AgeGroup.forAge(12))
    }

    @Test
    fun `age 13 maps to TEEN_AND_ABOVE`() {
        assertEquals(AgeGroup.TEEN_AND_ABOVE, AgeGroup.forAge(13))
    }

    @Test
    fun `age 18 maps to TEEN_AND_ABOVE`() {
        assertEquals(AgeGroup.TEEN_AND_ABOVE, AgeGroup.forAge(18))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `age below minimum throws`() {
        AgeGroup.forAge(6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `age 0 throws`() {
        AgeGroup.forAge(0)
    }

    @Test
    fun `CHILD age group has lower bpm than TEEN`() {
        assertTrue(AgeGroup.CHILD.maxBpm < AgeGroup.TEEN_AND_ABOVE.maxBpm)
    }

    @Test
    fun `CHILD age group has fewer reps than TEEN`() {
        assertTrue(AgeGroup.CHILD.repsPerExercise < AgeGroup.TEEN_AND_ABOVE.repsPerExercise)
    }
}
