package com.drumtrainer.audio

import com.drumtrainer.model.DrumPart
import org.junit.Assert.*
import org.junit.Test

class DiagnosticsExporterTest {

    /** Column indices matching the HEADER order in [DiagnosticsExporter]. */
    private object Col {
        const val INSTRUMENT      = 0
        const val DEFAULT_LOW     = 1
        const val DEFAULT_HIGH    = 2
        const val CAL_LOW         = 3
        const val CAL_HIGH        = 4
        const val CAL_MEAN        = 5
        const val CAL_STDDEV      = 6
        const val CV_PCT          = 7
        const val EFFECTIVE_LOW   = 8
        const val EFFECTIVE_HIGH  = 9
        const val BAND_WIDTH      = 10
        const val OVERLAPS        = 11
        const val PEAKS           = 12
    }

    @Test
    fun `buildReport contains header row`() {
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        assertTrue("Report should start with header", csv.startsWith("Instrument,"))
        assertTrue("Header should contain Overlapping Instruments column",
            csv.contains("Overlapping Instruments"))
        assertTrue("Header should contain Band Width column", csv.contains("Band Width Hz"))
        assertTrue("Header should contain CV column", csv.contains("CV (%)"))
        assertTrue("Header should contain Peak Frequencies column", csv.contains("Peak Frequencies Hz"))
    }

    @Test
    fun `buildReport contains a row for every DrumPart`() {
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        DrumPart.values().forEach { part ->
            assertTrue(
                "Report should contain a row for ${part.displayName}",
                csv.contains(part.displayName)
            )
        }
    }

    @Test
    fun `buildReport shows default band for uncalibrated instrument`() {
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        // Bass drum default range: 50–200 Hz; calibrated columns should be empty.
        val bassRow = csv.lines().first { it.startsWith("Bass Drum") }
        // default low = 50, default high = 200
        assertTrue("Default low should appear", bassRow.contains("50"))
        assertTrue("Default high should appear", bassRow.contains("200"))
        // calibrated columns should be empty
        val fields = bassRow.split(",")
        assertEquals("Calibrated low should be empty for uncalibrated instrument", "", fields[Col.CAL_LOW])
        assertEquals("Calibrated high should be empty for uncalibrated instrument", "", fields[Col.CAL_HIGH])
    }

    @Test
    fun `buildReport shows calibrated band when calibration is present`() {
        val calibrations = mapOf(DrumPart.SNARE to (180 to 750))
        val csv = DiagnosticsExporter.buildReport(calibrations, emptyMap())
        val snareRow = csv.lines().first { it.startsWith("Snare") }
        assertTrue("Calibrated low should appear", snareRow.contains("180"))
        assertTrue("Calibrated high should appear", snareRow.contains("750"))
    }

    @Test
    fun `buildReport shows calibration stats when available`() {
        val stats = mapOf(DrumPart.SNARE to (465.0 to 38.5))
        val csv = DiagnosticsExporter.buildReport(emptyMap(), stats)
        val snareRow = csv.lines().first { it.startsWith("Snare") }
        assertTrue("Mean Hz should appear", snareRow.contains("465.0"))
        assertTrue("Stddev Hz should appear", snareRow.contains("38.5"))
    }

    @Test
    fun `buildReport effective band uses calibrated range over default`() {
        val calibrations = mapOf(DrumPart.BASS_DRUM to (80 to 250))
        val csv = DiagnosticsExporter.buildReport(calibrations, emptyMap())
        val bassRow = csv.lines().first { it.startsWith("Bass Drum") }
        val fields = bassRow.split(",")
        assertEquals("Effective low should use calibrated value", "80",  fields[Col.EFFECTIVE_LOW])
        assertEquals("Effective high should use calibrated value", "250", fields[Col.EFFECTIVE_HIGH])
    }

    @Test
    fun `buildReport lists overlapping instruments with overlap size`() {
        // With default ranges, Bass Drum (50–200) overlaps with Floor Tom (60–200)
        // and TOM_MID (100–350).
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        val bassRow = csv.lines().first { it.startsWith("Bass Drum") }
        assertTrue(
            "Bass Drum row should mention Floor Tom as overlapping",
            bassRow.contains("Floor Tom")
        )
        // The overlap amount in Hz should appear in the row.
        assertTrue(
            "Bass Drum row should include overlap size in Hz",
            bassRow.contains("Hz")
        )
    }

    @Test
    fun `buildReport non-overlapping instruments not listed as overlapping`() {
        // Bass Drum (50–200) and Hi-Hat Closed (3000–8000) do not overlap.
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        val bassRow = csv.lines().first { it.startsWith("Bass Drum") }
        assertFalse(
            "Bass Drum row should NOT mention Hi-Hat (closed) as overlapping",
            bassRow.contains("Hi-Hat (closed)")
        )
    }

    // ── bandsOverlap helper ───────────────────────────────────────────────────

    @Test
    fun `bandsOverlap returns true for partially overlapping bands`() {
        assertTrue(DiagnosticsExporter.bandsOverlap(100, 300, 200 to 400))
    }

    @Test
    fun `bandsOverlap returns true for fully contained band`() {
        assertTrue(DiagnosticsExporter.bandsOverlap(100, 500, 200 to 400))
    }

    @Test
    fun `bandsOverlap returns false for adjacent non-overlapping bands`() {
        // [100, 200] and [200, 300] share a boundary but do not overlap (exclusive end)
        assertFalse(DiagnosticsExporter.bandsOverlap(100, 200, 200 to 300))
    }

    @Test
    fun `bandsOverlap returns false for completely separate bands`() {
        assertFalse(DiagnosticsExporter.bandsOverlap(100, 200, 300 to 500))
    }

    @Test
    fun `buildReport shows band width for effective range`() {
        // Bass Drum default: 50–200 → band width = 150
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        val bassRow = csv.lines().first { it.startsWith("Bass Drum") }
        val fields = bassRow.split(",")
        assertEquals("Band width should be effHigh - effLow", "150", fields[Col.BAND_WIDTH])
    }

    @Test
    fun `buildReport shows CV percent when stats are available`() {
        // mean=400, stddev=40 → CV = 40/400*100 = 10.0%
        val stats = mapOf(DrumPart.SNARE to (400.0 to 40.0))
        val csv = DiagnosticsExporter.buildReport(emptyMap(), stats)
        val snareRow = csv.lines().first { it.startsWith("Snare") }
        assertTrue("CV% should appear in row", snareRow.contains("10.0"))
    }

    @Test
    fun `buildReport CV column is empty for uncalibrated instrument`() {
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        val bassRow = csv.lines().first { it.startsWith("Bass Drum") }
        val fields = bassRow.split(",")
        assertEquals("CV should be empty for uncalibrated instrument", "", fields[Col.CV_PCT])
    }

    @Test
    fun `buildReport shows peak frequencies when provided`() {
        val peaks = mapOf(DrumPart.SNARE to listOf(430, 445, 460, 452, 438))
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap(), peaks)
        val snareRow = csv.lines().first { it.startsWith("Snare") }
        assertTrue("Peak frequencies should appear in row", snareRow.contains("430"))
        assertTrue("All peaks should appear in row", snareRow.contains("460"))
    }

    @Test
    fun `buildReport peak frequencies column is empty when not provided`() {
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        val bassRow = csv.lines().first { it.startsWith("Bass Drum") }
        val fields = bassRow.split(",")
        assertEquals("Peaks should be empty when not calibrated", "", fields[Col.PEAKS])
    }

    // ── buildRefinementReport ─────────────────────────────────────────────────

    @Test
    fun `buildRefinementReport contains header row`() {
        val csv = DiagnosticsExporter.buildRefinementReport(emptyList())
        assertTrue("Report should start with header", csv.startsWith("Round,"))
        assertTrue("Header should contain Hit # column", csv.contains("Hit #"))
        assertTrue("Header should contain Detected Instrument column", csv.contains("Detected Instrument"))
    }

    @Test
    fun `buildRefinementReport lists one row per hit`() {
        val hits = listOf(
            Triple(1, 1, DrumPart.SNARE),
            Triple(1, 2, DrumPart.BASS_DRUM),
            Triple(2, 1, null)
        )
        val csv = DiagnosticsExporter.buildRefinementReport(hits)
        val dataRows = csv.lines().filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("Round") }
        assertEquals("One data row per hit", 3, dataRows.size)
    }

    @Test
    fun `buildRefinementReport encodes round and hit index correctly`() {
        val hits = listOf(Triple(2, 3, DrumPart.HI_HAT_CLOSED))
        val csv = DiagnosticsExporter.buildRefinementReport(hits)
        val dataRow = csv.lines().first { it.startsWith("2,") }
        assertTrue("Row should contain round 2", dataRow.startsWith("2,"))
        assertTrue("Row should contain hit index 3", dataRow.contains(",3,"))
        assertTrue("Row should contain instrument name", dataRow.contains("Hi-Hat (closed)"))
    }

    @Test
    fun `buildRefinementReport uses unknownLabel for null DrumPart`() {
        val hits = listOf(Triple(1, 1, null as DrumPart?))
        val csv = DiagnosticsExporter.buildRefinementReport(hits)
        assertTrue("Null hit should appear as Unknown", csv.contains("Unknown"))
    }

    @Test
    fun `buildRefinementReport uses custom unknownLabel when provided`() {
        val hits = listOf(Triple(1, 1, null as DrumPart?))
        val csv = DiagnosticsExporter.buildRefinementReport(hits, unknownLabel = "Unrecognised")
        assertTrue("Custom label should appear", csv.contains("Unrecognised"))
        assertFalse("Default label should not appear", csv.contains("Unknown"))
    }

    @Test
    fun `buildRefinementReport summary shows correct total hit count`() {
        val hits = listOf(
            Triple(1, 1, DrumPart.SNARE),
            Triple(1, 2, DrumPart.SNARE),
            Triple(1, 3, null as DrumPart?)
        )
        val csv = DiagnosticsExporter.buildRefinementReport(hits)
        assertTrue("Summary should show total of 3 hits", csv.contains("# Total hits,3"))
    }

    @Test
    fun `buildRefinementReport summary shows correct unrecognised count and percent`() {
        val hits = listOf(
            Triple(1, 1, DrumPart.SNARE),
            Triple(1, 2, null as DrumPart?),
            Triple(1, 3, null as DrumPart?)
        )
        val csv = DiagnosticsExporter.buildRefinementReport(hits)
        assertTrue("Summary should show 2 unrecognised hits", csv.contains("# Unrecognised hits,2"))
        assertTrue("Summary should show 66.7%", csv.contains("66.7%"))
    }

    @Test
    fun `buildRefinementReport summary shows per-instrument counts`() {
        val hits = listOf(
            Triple(1, 1, DrumPart.SNARE),
            Triple(1, 2, DrumPart.SNARE),
            Triple(1, 3, DrumPart.BASS_DRUM)
        )
        val csv = DiagnosticsExporter.buildRefinementReport(hits)
        assertTrue("Summary should list Snare count", csv.contains("Snare: 2"))
        assertTrue("Summary should list Bass Drum count", csv.contains("Bass Drum: 1"))
    }

    @Test
    fun `buildRefinementReport on empty list produces header and zero-count summary`() {
        val csv = DiagnosticsExporter.buildRefinementReport(emptyList())
        assertTrue("Report should still have header", csv.contains("Round,Hit #"))
        assertTrue("Summary should show 0 total hits", csv.contains("# Total hits,0"))
        assertTrue("Summary should show 0 unrecognised hits", csv.contains("# Unrecognised hits,0"))
    }
}
