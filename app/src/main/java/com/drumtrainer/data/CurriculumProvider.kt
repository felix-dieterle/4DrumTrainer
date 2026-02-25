package com.drumtrainer.data

import com.drumtrainer.R
import com.drumtrainer.model.*

/**
 * Provides the static [Curriculum] used by the app.
 *
 * Lessons are designed for students aged 7 and above. Tempo values are kept
 * conservative so a 7-year-old can succeed; students in higher age groups will
 * naturally play faster and can earn bonus stars for exceeding the minimum BPM.
 *
 * Pattern notation:
 *   beatIndex is 0-based within a bar.  subdivision=1 → quarter note resolution.
 *   subdivision=2 → eighth note resolution (beatIndex 0..7 for 4/4).
 */
object CurriculumProvider {

    val curriculum: Curriculum by lazy { buildCurriculum() }

    private fun buildCurriculum(): Curriculum = Curriculum(
        levels = listOf(
            level0_gettingStarted(),
            level1_basicBeat(),
            level2_eighthNoteHiHat(),
            level3_openHiHat(),
            level4_tomFills(),
            level5_crashAndRide(),
            level6_sixteenthNotes(),
            level7_advancedFills()
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Level 0 – Getting Started (age 7+, 60–70 BPM, quarter-note hi-hat only)
    // ─────────────────────────────────────────────────────────────────────────

    private fun level0_gettingStarted() = CurriculumLevel(
        index = 0,
        titleRes = R.string.level_0_title,
        descriptionRes = R.string.level_0_desc,
        recommendedAgeMin = 7,
        lessons = listOf(
            Lesson(
                id = 100,
                levelIndex = 0, lessonIndex = 0,
                titleRes = R.string.lesson_100_title,
                descriptionRes = R.string.lesson_100_desc,
                targetBpmMin = 60, targetBpmMax = 70,
                passThresholdPct = 60,
                estimatedMinutes = 5,
                pattern = quarterHiHats()
            ),
            Lesson(
                id = 101,
                levelIndex = 0, lessonIndex = 1,
                titleRes = R.string.lesson_101_title,
                descriptionRes = R.string.lesson_101_desc,
                targetBpmMin = 65, targetBpmMax = 75,
                passThresholdPct = 65,
                estimatedMinutes = 5,
                pattern = quarterHiHats()
            )
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Level 1 – Basic Beat (bass on 1&3, snare on 2&4 + hi-hat)
    // ─────────────────────────────────────────────────────────────────────────

    private fun level1_basicBeat() = CurriculumLevel(
        index = 1,
        titleRes = R.string.level_1_title,
        descriptionRes = R.string.level_1_desc,
        recommendedAgeMin = 7,
        lessons = listOf(
            Lesson(
                id = 200,
                levelIndex = 1, lessonIndex = 0,
                titleRes = R.string.lesson_200_title,
                descriptionRes = R.string.lesson_200_desc,
                targetBpmMin = 65, targetBpmMax = 80,
                passThresholdPct = 65,
                estimatedMinutes = 10,
                pattern = basicBeatPattern()
            ),
            Lesson(
                id = 201,
                levelIndex = 1, lessonIndex = 1,
                titleRes = R.string.lesson_201_title,
                descriptionRes = R.string.lesson_201_desc,
                targetBpmMin = 70, targetBpmMax = 85,
                passThresholdPct = 70,
                estimatedMinutes = 10,
                pattern = basicBeatPattern()
            )
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Level 2 – Eighth-Note Hi-Hat
    // ─────────────────────────────────────────────────────────────────────────

    private fun level2_eighthNoteHiHat() = CurriculumLevel(
        index = 2,
        titleRes = R.string.level_2_title,
        descriptionRes = R.string.level_2_desc,
        recommendedAgeMin = 8,
        lessons = listOf(
            Lesson(
                id = 300,
                levelIndex = 2, lessonIndex = 0,
                titleRes = R.string.lesson_300_title,
                descriptionRes = R.string.lesson_300_desc,
                targetBpmMin = 70, targetBpmMax = 85,
                subdivision = 2,
                passThresholdPct = 65,
                estimatedMinutes = 10,
                pattern = basicBeatWithEighthHiHat()
            ),
            Lesson(
                id = 301,
                levelIndex = 2, lessonIndex = 1,
                titleRes = R.string.lesson_301_title,
                descriptionRes = R.string.lesson_301_desc,
                targetBpmMin = 75, targetBpmMax = 90,
                subdivision = 2,
                passThresholdPct = 70,
                estimatedMinutes = 10,
                pattern = basicBeatWithEighthHiHat()
            )
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Level 3 – Open Hi-Hat
    // ─────────────────────────────────────────────────────────────────────────

    private fun level3_openHiHat() = CurriculumLevel(
        index = 3,
        titleRes = R.string.level_3_title,
        descriptionRes = R.string.level_3_desc,
        recommendedAgeMin = 9,
        lessons = listOf(
            Lesson(
                id = 400,
                levelIndex = 3, lessonIndex = 0,
                titleRes = R.string.lesson_400_title,
                descriptionRes = R.string.lesson_400_desc,
                targetBpmMin = 75, targetBpmMax = 90,
                subdivision = 2,
                passThresholdPct = 65,
                estimatedMinutes = 10,
                pattern = openHiHatPattern()
            )
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Level 4 – Tom Fills
    // ─────────────────────────────────────────────────────────────────────────

    private fun level4_tomFills() = CurriculumLevel(
        index = 4,
        titleRes = R.string.level_4_title,
        descriptionRes = R.string.level_4_desc,
        recommendedAgeMin = 10,
        lessons = listOf(
            Lesson(
                id = 500,
                levelIndex = 4, lessonIndex = 0,
                titleRes = R.string.lesson_500_title,
                descriptionRes = R.string.lesson_500_desc,
                targetBpmMin = 80, targetBpmMax = 95,
                subdivision = 2,
                passThresholdPct = 65,
                estimatedMinutes = 15,
                pattern = tomFillPattern()
            )
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Level 5 – Crash & Ride
    // ─────────────────────────────────────────────────────────────────────────

    private fun level5_crashAndRide() = CurriculumLevel(
        index = 5,
        titleRes = R.string.level_5_title,
        descriptionRes = R.string.level_5_desc,
        recommendedAgeMin = 11,
        lessons = listOf(
            Lesson(
                id = 600,
                levelIndex = 5, lessonIndex = 0,
                titleRes = R.string.lesson_600_title,
                descriptionRes = R.string.lesson_600_desc,
                targetBpmMin = 85, targetBpmMax = 100,
                subdivision = 2,
                passThresholdPct = 65,
                estimatedMinutes = 15,
                pattern = ridePatternWithCrash()
            )
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Level 6 – Sixteenth-Note Patterns
    // ─────────────────────────────────────────────────────────────────────────

    private fun level6_sixteenthNotes() = CurriculumLevel(
        index = 6,
        titleRes = R.string.level_6_title,
        descriptionRes = R.string.level_6_desc,
        recommendedAgeMin = 12,
        lessons = listOf(
            Lesson(
                id = 700,
                levelIndex = 6, lessonIndex = 0,
                titleRes = R.string.lesson_700_title,
                descriptionRes = R.string.lesson_700_desc,
                targetBpmMin = 90, targetBpmMax = 110,
                subdivision = 4,
                passThresholdPct = 65,
                estimatedMinutes = 15,
                pattern = sixteenthNoteHiHatPattern()
            )
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Level 7 – Advanced Fills
    // ─────────────────────────────────────────────────────────────────────────

    private fun level7_advancedFills() = CurriculumLevel(
        index = 7,
        titleRes = R.string.level_7_title,
        descriptionRes = R.string.level_7_desc,
        recommendedAgeMin = 13,
        lessons = listOf(
            Lesson(
                id = 800,
                levelIndex = 7, lessonIndex = 0,
                titleRes = R.string.lesson_800_title,
                descriptionRes = R.string.lesson_800_desc,
                targetBpmMin = 95, targetBpmMax = 120,
                subdivision = 4,
                passThresholdPct = 70,
                estimatedMinutes = 20,
                pattern = advancedFillPattern()
            )
        )
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Pattern helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** 4 quarter-note hi-hats per bar (beatIndex 0–3, subdivision=1). */
    private fun quarterHiHats(): List<BeatEvent> = (0..3).map {
        BeatEvent(beatIndex = it, subdivision = 1, part = DrumPart.HI_HAT_CLOSED)
    }

    /**
     * Classic rock beat at quarter-note resolution:
     *   Beat 1: bass drum + hi-hat
     *   Beat 2: snare + hi-hat
     *   Beat 3: bass drum + hi-hat
     *   Beat 4: snare + hi-hat
     */
    private fun basicBeatPattern(): List<BeatEvent> = listOf(
        BeatEvent(0, 1, DrumPart.BASS_DRUM),
        BeatEvent(0, 1, DrumPart.HI_HAT_CLOSED),
        BeatEvent(1, 1, DrumPart.SNARE),
        BeatEvent(1, 1, DrumPart.HI_HAT_CLOSED),
        BeatEvent(2, 1, DrumPart.BASS_DRUM),
        BeatEvent(2, 1, DrumPart.HI_HAT_CLOSED),
        BeatEvent(3, 1, DrumPart.SNARE),
        BeatEvent(3, 1, DrumPart.HI_HAT_CLOSED)
    )

    /**
     * Basic beat with eighth-note hi-hat (subdivision=2, beatIndex 0–7).
     * Hi-hat on every eighth note; bass on 0&4; snare on 2&6.
     */
    private fun basicBeatWithEighthHiHat(): List<BeatEvent> = buildList {
        for (i in 0..7) add(BeatEvent(i, 2, DrumPart.HI_HAT_CLOSED))
        add(BeatEvent(0, 2, DrumPart.BASS_DRUM))
        add(BeatEvent(2, 2, DrumPart.SNARE))
        add(BeatEvent(4, 2, DrumPart.BASS_DRUM))
        add(BeatEvent(6, 2, DrumPart.SNARE))
    }

    /** Open hi-hat on beats 2 & 6 (eighth resolution) instead of closed. */
    private fun openHiHatPattern(): List<BeatEvent> = buildList {
        for (i in 0..7) {
            val part = if (i == 2 || i == 6) DrumPart.HI_HAT_OPEN else DrumPart.HI_HAT_CLOSED
            add(BeatEvent(i, 2, part))
        }
        add(BeatEvent(0, 2, DrumPart.BASS_DRUM))
        add(BeatEvent(2, 2, DrumPart.SNARE))
        add(BeatEvent(4, 2, DrumPart.BASS_DRUM))
        add(BeatEvent(6, 2, DrumPart.SNARE))
    }

    /** Basic beat + single-stroke tom fill on beat 7 (eighth resolution). */
    private fun tomFillPattern(): List<BeatEvent> = buildList {
        addAll(basicBeatWithEighthHiHat())
        // Replace hi-hat on beats 6 & 7 with toms for the fill
        removeAll { it.beatIndex >= 6 && it.part == DrumPart.HI_HAT_CLOSED }
        add(BeatEvent(6, 2, DrumPart.TOM))
        add(BeatEvent(7, 2, DrumPart.TOM))
    }

    /** Ride cymbal pattern on all eighth notes, crash on beat 0. */
    private fun ridePatternWithCrash(): List<BeatEvent> = buildList {
        add(BeatEvent(0, 2, DrumPart.CRASH))
        for (i in 1..7) add(BeatEvent(i, 2, DrumPart.RIDE))
        add(BeatEvent(0, 2, DrumPart.BASS_DRUM))
        add(BeatEvent(2, 2, DrumPart.SNARE))
        add(BeatEvent(4, 2, DrumPart.BASS_DRUM))
        add(BeatEvent(6, 2, DrumPart.SNARE))
    }

    /** Sixteenth-note hi-hat (subdivision=4, beatIndex 0–15) with standard bass/snare. */
    private fun sixteenthNoteHiHatPattern(): List<BeatEvent> = buildList {
        for (i in 0..15) add(BeatEvent(i, 4, DrumPart.HI_HAT_CLOSED))
        add(BeatEvent(0,  4, DrumPart.BASS_DRUM))
        add(BeatEvent(4,  4, DrumPart.SNARE))
        add(BeatEvent(8,  4, DrumPart.BASS_DRUM))
        add(BeatEvent(12, 4, DrumPart.SNARE))
    }

    /** Advanced fill with toms on beats 12–15 (sixteenth resolution). */
    private fun advancedFillPattern(): List<BeatEvent> = buildList {
        addAll(sixteenthNoteHiHatPattern())
        removeAll { it.beatIndex >= 12 && it.part == DrumPart.HI_HAT_CLOSED }
        listOf(DrumPart.TOM, DrumPart.TOM, DrumPart.SNARE, DrumPart.BASS_DRUM)
            .forEachIndexed { idx, part -> add(BeatEvent(12 + idx, 4, part)) }
    }
}
