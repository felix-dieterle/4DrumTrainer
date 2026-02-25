package com.drumtrainer.audio

import com.drumtrainer.model.BeatEvent
import com.drumtrainer.model.DrumPart
import org.junit.Assert.*
import org.junit.Test

class AudioProcessorTest {

    private val processor = AudioProcessor()

    @Test
    fun `buildExpectedTimestamps produces correct count for 1 bar`() {
        // Pattern: 4 quarter-note hi-hats in 1 bar at 60 BPM
        val pattern = (0..3).map { BeatEvent(it, 1, DrumPart.HI_HAT_CLOSED) }
        val timestamps = processor.buildExpectedTimestamps(pattern, bpm = 60, bars = 1)
        assertEquals(4, timestamps.size)
    }

    @Test
    fun `buildExpectedTimestamps produces correct count for multiple bars`() {
        val pattern = listOf(BeatEvent(0, 1, DrumPart.BASS_DRUM))
        val timestamps = processor.buildExpectedTimestamps(pattern, bpm = 60, bars = 4)
        assertEquals(4, timestamps.size)
    }

    @Test
    fun `buildExpectedTimestamps at 60 BPM has 1000ms between quarter notes`() {
        val pattern = listOf(
            BeatEvent(0, 1, DrumPart.BASS_DRUM),
            BeatEvent(1, 1, DrumPart.SNARE)
        )
        val timestamps = processor.buildExpectedTimestamps(pattern, bpm = 60, bars = 1)
        assertEquals(2, timestamps.size)
        val gap = timestamps[1].first - timestamps[0].first
        assertEquals(1000L, gap)
    }

    @Test
    fun `buildExpectedTimestamps at 120 BPM has 500ms between quarter notes`() {
        val pattern = listOf(
            BeatEvent(0, 1, DrumPart.BASS_DRUM),
            BeatEvent(1, 1, DrumPart.SNARE)
        )
        val timestamps = processor.buildExpectedTimestamps(pattern, bpm = 120, bars = 1)
        val gap = timestamps[1].first - timestamps[0].first
        assertEquals(500L, gap)
    }

    @Test
    fun `buildExpectedTimestamps with eighth notes has 500ms between at 60 BPM`() {
        // subdivision=2 means eighth notes; at 60 BPM 1 quarter = 1000ms → 1 eighth = 500ms
        val pattern = listOf(
            BeatEvent(0, 2, DrumPart.HI_HAT_CLOSED),
            BeatEvent(1, 2, DrumPart.HI_HAT_CLOSED)
        )
        val timestamps = processor.buildExpectedTimestamps(pattern, bpm = 60, bars = 1)
        val gap = timestamps[1].first - timestamps[0].first
        assertEquals(500L, gap)
    }

    @Test
    fun `buildExpectedTimestamps result is sorted by time`() {
        val pattern = listOf(
            BeatEvent(3, 1, DrumPart.SNARE),
            BeatEvent(0, 1, DrumPart.BASS_DRUM),
            BeatEvent(2, 1, DrumPart.TOM_HIGH),
            BeatEvent(1, 1, DrumPart.HI_HAT_CLOSED)
        )
        val timestamps = processor.buildExpectedTimestamps(pattern, bpm = 60, bars = 1)
        val times = timestamps.map { it.first }
        assertEquals(times.sorted(), times)
    }
}
