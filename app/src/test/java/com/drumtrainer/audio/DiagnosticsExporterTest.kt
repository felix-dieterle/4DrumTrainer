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
        const val EFFECTIVE_LOW   = 7
        const val EFFECTIVE_HIGH  = 8
        const val OVERLAPS        = 9
    }

    @Test
    fun `buildReport contains header row`() {
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        assertTrue("Report should start with header", csv.startsWith("Instrument,"))
        assertTrue("Header should contain Overlapping Instruments column", csv.contains("Overlapping Instruments"))
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
    fun `buildReport lists overlapping instruments`() {
        // With default ranges, Bass Drum (50–200) overlaps with Floor Tom (60–200)
        // and TOM_MID (100–350).
        val csv = DiagnosticsExporter.buildReport(emptyMap(), emptyMap())
        val bassRow = csv.lines().first { it.startsWith("Bass Drum") }
        assertTrue(
            "Bass Drum row should mention Floor Tom as overlapping",
            bassRow.contains("Floor Tom")
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
}
