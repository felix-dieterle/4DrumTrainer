package com.drumtrainer.model

/**
 * Identifies the physical drum instrument that should (or was) struck.
 *
 * Each entry carries a nominal frequency range that the [com.drumtrainer.audio.DrumHitClassifier]
 * uses to identify hits captured by the microphone.
 */
enum class DrumPart(
    val displayName: String,
    val freqRangeLowHz: Int,
    val freqRangeHighHz: Int
) {
    /** Bass drum / kick – deep thud, very low frequencies. */
    BASS_DRUM(displayName = "Bass Drum", freqRangeLowHz = 50, freqRangeHighHz = 200),

    /** Snare drum – sharp crack with a broad mid-range signature. */
    SNARE(displayName = "Snare", freqRangeLowHz = 150, freqRangeHighHz = 800),

    /** Hi-hat (closed) – short metallic click, high frequencies. */
    HI_HAT_CLOSED(displayName = "Hi-Hat (closed)", freqRangeLowHz = 3000, freqRangeHighHz = 8000),

    /** Hi-hat (open) – longer wash, slightly broader high-frequency spread. */
    HI_HAT_OPEN(displayName = "Hi-Hat (open)", freqRangeLowHz = 2000, freqRangeHighHz = 8000),

    /** Ride cymbal – sustained shimmer. */
    RIDE(displayName = "Ride", freqRangeLowHz = 300, freqRangeHighHz = 5000),

    /** Crash cymbal – wide-band explosive burst. */
    CRASH(displayName = "Crash", freqRangeLowHz = 200, freqRangeHighHz = 10000),

    /** High tom (rack tom 1) – highest-pitched tom, bright tonal attack. */
    TOM_HIGH(displayName = "High Tom", freqRangeLowHz = 200, freqRangeHighHz = 500),

    /** Mid tom (rack tom 2) – mid-pitched tonal thud. */
    TOM_MID(displayName = "Mid Tom", freqRangeLowHz = 100, freqRangeHighHz = 350),

    /** Floor tom – lowest-pitched tom, deep resonant thud. */
    TOM_FLOOR(displayName = "Floor Tom", freqRangeLowHz = 60, freqRangeHighHz = 200);
}
