package com.drumtrainer.model

/**
 * Represents the medal earned after completing a lesson attempt.
 *
 * Award rules:
 *  - [BRONZE] for an overall score in the range 50–90 %
 *  - [SILVER] for a score strictly above 90 % and below 100 %
 *  - [GOLD]   for a perfect score of 100 %
 *  - [NONE]   for a score below 50 %
 */
enum class Medal { NONE, BRONZE, SILVER, GOLD }

/** Returns the [Medal] that corresponds to the given [overallScore] (0–100). */
fun medalForScore(overallScore: Int): Medal = when {
    overallScore == 100 -> Medal.GOLD
    overallScore > 90   -> Medal.SILVER
    overallScore >= 50  -> Medal.BRONZE
    else                -> Medal.NONE
}
