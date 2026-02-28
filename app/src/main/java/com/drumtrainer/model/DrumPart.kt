package com.drumtrainer.model

/**
 * Identifies the physical drum instrument that should (or was) struck.
 *
 * Each entry carries a nominal frequency range that the [com.drumtrainer.audio.DrumHitClassifier]
 * uses to identify hits captured by the microphone, and a [isCymbal] flag that distinguishes
 * metallic cymbals from membrane-based drums.
 *
 * Drums and cymbals present a unique discrimination challenge compared with classical instruments:
 * classical instruments produce stable, harmonic tones that can be separated by their fundamental
 * frequency alone.  Percussion instruments produce only transient bursts of energy, and cymbals
 * (especially Crash and Ride) have extremely broad, inharmonic spectra that overlap the frequency
 * ranges of several drum parts.  The [isCymbal] flag lets the classifier apply a separate,
 * physics-motivated criterion — the ratio of high-frequency energy — to first determine the drum
 * *family* before comparing individual frequency bands within that family.
 */
enum class DrumPart(
    val displayName: String,
    val freqRangeLowHz: Int,
    val freqRangeHighHz: Int,
    /** `true` for metallic cymbals; `false` for membrane-based drums. */
    val isCymbal: Boolean
) {
    /** Bass drum / kick – deep thud, very low frequencies. */
    BASS_DRUM(displayName = "Bass Drum", freqRangeLowHz = 50, freqRangeHighHz = 200, isCymbal = false),

    /** Snare drum – sharp crack with a broad mid-range signature. */
    SNARE(displayName = "Snare", freqRangeLowHz = 150, freqRangeHighHz = 800, isCymbal = false),

    /** Hi-hat (closed) – short metallic click, high frequencies. */
    HI_HAT_CLOSED(displayName = "Hi-Hat (closed)", freqRangeLowHz = 3000, freqRangeHighHz = 8000, isCymbal = true),

    /** Hi-hat (open) – longer wash, slightly broader high-frequency spread. */
    HI_HAT_OPEN(displayName = "Hi-Hat (open)", freqRangeLowHz = 2000, freqRangeHighHz = 8000, isCymbal = true),

    /** Ride cymbal – sustained shimmer. */
    RIDE(displayName = "Ride", freqRangeLowHz = 300, freqRangeHighHz = 5000, isCymbal = true),

    /** Crash cymbal – wide-band explosive burst. */
    CRASH(displayName = "Crash", freqRangeLowHz = 200, freqRangeHighHz = 10000, isCymbal = true),

    /** High tom (rack tom 1) – highest-pitched tom, bright tonal attack. */
    TOM_HIGH(displayName = "High Tom", freqRangeLowHz = 200, freqRangeHighHz = 500, isCymbal = false),

    /** Mid tom (rack tom 2) – mid-pitched tonal thud. */
    TOM_MID(displayName = "Mid Tom", freqRangeLowHz = 100, freqRangeHighHz = 350, isCymbal = false),

    /** Floor tom – lowest-pitched tom, deep resonant thud. */
    TOM_FLOOR(displayName = "Floor Tom", freqRangeLowHz = 60, freqRangeHighHz = 200, isCymbal = false);
}
