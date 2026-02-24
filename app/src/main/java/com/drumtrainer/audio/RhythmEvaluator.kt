package com.drumtrainer.audio

import com.drumtrainer.model.DrumPart
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Evaluates how well a student's detected hits match the expected pattern.
 *
 * The detector compares each expected beat event (given as a wall-clock
 * timestamp in milliseconds) against the nearest detected hit.  A hit is
 * considered "in rhythm" when it falls within [timingWindowMs] of the
 * expected beat.  Instrument classification adds the pitch score.
 *
 * @param timingWindowMs  Half-width of the acceptable timing window in ms
 *                        (default: 150 ms → ±150 ms around each expected beat).
 */
class RhythmEvaluator(private val timingWindowMs: Long = 150L) {

    data class HitResult(
        val expectedTimeMs: Long,
        val actualTimeMs: Long?,       // null = missed
        val expectedPart: DrumPart,
        val detectedPart: DrumPart?,   // null = missed
        val inTime: Boolean,
        val correctPart: Boolean
    )

    /**
     * Evaluates [detectedHits] against [expectedHits].
     *
     * @param expectedHits  List of (timestamp ms, DrumPart) pairs for the reference pattern.
     * @param detectedHits  List of (timestamp ms, DrumPart?) pairs from [AudioProcessor].
     * @return              A [HitResult] per expected event, plus summary scores.
     */
    fun evaluate(
        expectedHits: List<Pair<Long, DrumPart>>,
        detectedHits: List<Pair<Long, DrumPart?>>
    ): EvaluationResult {
        val used = mutableSetOf<Int>()
        val results = expectedHits.map { (expectedTime, expectedPart) ->
            // Find the closest not-yet-used detected hit within the timing window
            val bestIdx = detectedHits
                .indices
                .filter { it !in used && abs(detectedHits[it].first - expectedTime) <= timingWindowMs }
                .minByOrNull { abs(detectedHits[it].first - expectedTime) }

            if (bestIdx != null) {
                used += bestIdx
                val (actualTime, detectedPart) = detectedHits[bestIdx]
                HitResult(
                    expectedTimeMs  = expectedTime,
                    actualTimeMs    = actualTime,
                    expectedPart    = expectedPart,
                    detectedPart    = detectedPart,
                    inTime          = true,
                    correctPart     = detectedPart == expectedPart
                )
            } else {
                HitResult(
                    expectedTimeMs  = expectedTime,
                    actualTimeMs    = null,
                    expectedPart    = expectedPart,
                    detectedPart    = null,
                    inTime          = false,
                    correctPart     = false
                )
            }
        }

        val rhythmScore = if (results.isEmpty()) 0
        else ((results.count { it.inTime }.toDouble() / results.size) * 100).roundToInt()

        val pitchScore = if (results.isEmpty()) 0
        else ((results.count { it.correctPart }.toDouble() / results.size) * 100).roundToInt()

        return EvaluationResult(hitResults = results, rhythmScore = rhythmScore, pitchScore = pitchScore)
    }

    data class EvaluationResult(
        val hitResults: List<HitResult>,
        val rhythmScore: Int,   // 0–100
        val pitchScore: Int     // 0–100
    ) {
        val overallScore: Int = ((rhythmScore * 0.6) + (pitchScore * 0.4)).roundToInt()
    }
}
