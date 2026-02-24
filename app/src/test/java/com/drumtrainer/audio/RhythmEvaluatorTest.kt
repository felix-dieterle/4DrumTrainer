package com.drumtrainer.audio

import com.drumtrainer.model.DrumPart
import org.junit.Assert.*
import org.junit.Test

class RhythmEvaluatorTest {

    private val evaluator = RhythmEvaluator(timingWindowMs = 150)

    @Test
    fun `perfect timing and correct parts give 100 percent scores`() {
        val expected = listOf(
            0L   to DrumPart.BASS_DRUM,
            500L to DrumPart.SNARE,
            1000L to DrumPart.BASS_DRUM
        )
        val detected = listOf(
            0L   to DrumPart.BASS_DRUM,
            500L to DrumPart.SNARE,
            1000L to DrumPart.BASS_DRUM
        )
        val result = evaluator.evaluate(expected, detected)
        assertEquals(100, result.rhythmScore)
        assertEquals(100, result.pitchScore)
        assertEquals(100, result.overallScore)
    }

    @Test
    fun `hit within window is counted as in-time`() {
        val expected = listOf(0L to DrumPart.SNARE)
        val detected = listOf(100L to DrumPart.SNARE) // 100 ms late, within 150 ms window
        val result = evaluator.evaluate(expected, detected)
        assertEquals(100, result.rhythmScore)
    }

    @Test
    fun `hit outside window is counted as missed`() {
        val expected = listOf(0L to DrumPart.SNARE)
        val detected = listOf(200L to DrumPart.SNARE) // 200 ms late, beyond 150 ms window
        val result = evaluator.evaluate(expected, detected)
        assertEquals(0, result.rhythmScore)
    }

    @Test
    fun `wrong drum part reduces pitch score`() {
        val expected = listOf(0L to DrumPart.BASS_DRUM)
        val detected = listOf(0L to DrumPart.SNARE) // correct timing, wrong part
        val result = evaluator.evaluate(expected, detected)
        assertEquals(100, result.rhythmScore) // timing OK
        assertEquals(0, result.pitchScore)    // wrong instrument
    }

    @Test
    fun `empty detected hits give zero scores`() {
        val expected = listOf(0L to DrumPart.BASS_DRUM, 500L to DrumPart.SNARE)
        val result = evaluator.evaluate(expected, emptyList())
        assertEquals(0, result.rhythmScore)
        assertEquals(0, result.pitchScore)
    }

    @Test
    fun `empty expected list gives zero scores`() {
        val detected = listOf(0L to DrumPart.BASS_DRUM)
        val result = evaluator.evaluate(emptyList(), detected)
        assertEquals(0, result.rhythmScore)
        assertEquals(0, result.pitchScore)
    }

    @Test
    fun `overall score is weighted combination of rhythm and pitch`() {
        // rhythmScore=100, pitchScore=0 → overall = 100*0.6 + 0*0.4 = 60
        val expected = listOf(0L to DrumPart.BASS_DRUM)
        val detected = listOf(0L to DrumPart.SNARE) // in time, wrong part
        val result = evaluator.evaluate(expected, detected)
        assertEquals(100, result.rhythmScore)
        assertEquals(0, result.pitchScore)
        assertEquals(60, result.overallScore)
    }

    @Test
    fun `same hit is not matched twice`() {
        // Two expected beats, only one detected hit – second expected beat is a miss
        val expected = listOf(0L to DrumPart.SNARE, 0L to DrumPart.BASS_DRUM)
        val detected: List<Pair<Long, DrumPart?>> = listOf(0L to DrumPart.SNARE)
        val result = evaluator.evaluate(expected, detected)
        assertEquals(50, result.rhythmScore) // 1 out of 2 in time
    }
}
