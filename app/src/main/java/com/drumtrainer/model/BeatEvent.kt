package com.drumtrainer.model

/**
 * A single beat event within a [Lesson] pattern.
 *
 * @param beatIndex    0-based position within the bar (e.g. 0–3 for a 4/4 bar at quarter-note resolution).
 * @param subdivision  Number of equal subdivisions per beat (1 = quarter note, 2 = eighth note, 4 = sixteenth).
 * @param part         Which drum part should be struck on this event.
 */
data class BeatEvent(
    val beatIndex: Int,
    val subdivision: Int = 1,
    val part: DrumPart
)
