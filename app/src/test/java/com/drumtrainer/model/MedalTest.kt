package com.drumtrainer.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MedalTest {

    @Test
    fun `score below 50 yields no medal`() {
        assertEquals(Medal.NONE, medalForScore(0))
        assertEquals(Medal.NONE, medalForScore(49))
    }

    @Test
    fun `score 50 to 90 yields bronze`() {
        assertEquals(Medal.BRONZE, medalForScore(50))
        assertEquals(Medal.BRONZE, medalForScore(75))
        assertEquals(Medal.BRONZE, medalForScore(90))
    }

    @Test
    fun `score 91 to 99 yields silver`() {
        assertEquals(Medal.SILVER, medalForScore(91))
        assertEquals(Medal.SILVER, medalForScore(95))
        assertEquals(Medal.SILVER, medalForScore(99))
    }

    @Test
    fun `perfect score 100 yields gold`() {
        assertEquals(Medal.GOLD, medalForScore(100))
    }
}
