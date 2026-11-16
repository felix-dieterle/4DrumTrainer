package com.drumtrainer.audio

import org.junit.Assert.*
import org.junit.Test

class SnippetCollectorTest {

    @Test
    fun `hit is not ready before the post-onset delay has elapsed`() {
        val collector = SnippetCollector(
            sampleRateHz = 1_000,
            windowSize = 8,
            preRollSamples = 2
        )
        collector.append(FloatArray(100))
        collector.markOnsetFromTimestamp(100)

        collector.append(FloatArray(5))
        assertFalse("Window must not be ready before onset + delay", collector.isReady())
        assertNull(collector.extractReadyHit())
    }

    @Test
    fun `ready hit contains the full window ending after the delay`() {
        val collector = SnippetCollector(
            sampleRateHz = 1_000,
            windowSize = 8,
            preRollSamples = 2
        )
        collector.append(FloatArray(105) { it.toFloat() })
        collector.markOnsetFromTimestamp(100)
        collector.append(floatArrayOf(105f))

        assertTrue("Window should be ready at onset + (window - preRoll)", collector.isReady())
        val hit = collector.extractReadyHit()
        assertNotNull(hit)
        val snippet = hit!!.snippet
        assertEquals(8, snippet.size)
        // Last eight samples: 98..105.
        assertEquals(98f, snippet.first(), 0f)
        assertEquals(105f, snippet.last(), 0f)
        assertEquals(100L, hit.onsetTimestampMs)
        assertFalse("Pending onset must be cleared after extraction", collector.isReady())
    }

    @Test
    fun `reset clears buffer and pending onset`() {
        val collector = SnippetCollector(sampleRateHz = 1_000, windowSize = 4, preRollSamples = 1)
        collector.markOnsetFromTimestamp(10)
        collector.append(FloatArray(20) { 1f })
        collector.reset()

        assertFalse(collector.isReady())
        assertNull(collector.extractReadyHit())
    }
}
