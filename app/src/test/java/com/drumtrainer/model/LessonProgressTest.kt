package com.drumtrainer.model

import org.junit.Assert.*
import org.junit.Test

class LessonProgressTest {

    @Test
    fun `overallScore combines rhythm and pitch with correct weighting`() {
        val progress = LessonProgress(
            studentId    = 1,
            lessonId     = 1,
            rhythmScore  = 100,
            pitchScore   = 0,
            durationSec  = 60,
            passed       = false
        )
        // 100*0.6 + 0*0.4 = 60
        assertEquals(60, progress.overallScore)
    }

    @Test
    fun `overallScore with perfect scores is 100`() {
        val progress = LessonProgress(
            studentId    = 1,
            lessonId     = 1,
            rhythmScore  = 100,
            pitchScore   = 100,
            durationSec  = 60,
            passed       = true
        )
        assertEquals(100, progress.overallScore)
    }

    @Test
    fun `overallScore with both zero is 0`() {
        val progress = LessonProgress(
            studentId    = 1,
            lessonId     = 1,
            rhythmScore  = 0,
            pitchScore   = 0,
            durationSec  = 30,
            passed       = false
        )
        assertEquals(0, progress.overallScore)
    }
}
