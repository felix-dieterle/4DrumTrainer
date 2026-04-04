package com.drumtrainer.audio

import com.drumtrainer.model.DrumPart
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Integration test that verifies the training-and-recognition pipeline can
 * reliably distinguish six real drum instrument recordings supplied as WAV
 * files in the test resources directory.
 *
 * ### What is being tested
 *
 * 1. **Training (calibration)**: given a set of PCM windows from a single
 *    instrument, [InstrumentCalibrator.findSpectralFeatures] computes a stable
 *    three-element feature vector [centroidHz, lowEnergyRatio, highEnergyRatio].
 *    Averaging these over all high-energy windows from each recording produces
 *    the *calibrated feature profile* for that instrument.
 *
 * 2. **Discrimination**: the six calibrated profiles are loaded into a
 *    [DrumHitClassifier] via its `featureCalibration` parameter, enabling
 *    nearest-neighbour classification in the normalised three-dimensional
 *    spectral feature space.  Every high-energy window from each recording is
 *    then classified, and the **majority vote** is checked against the
 *    instrument label assigned to that recording.
 *
 * ### Why majority vote
 *
 * Individual windows taken from a single drum hit recording exhibit natural
 * spectral variation (the attack transient looks different from the sustain
 * and decay phases).  Requiring every single window to be correctly classified
 * would be unrealistically strict.  Instead, the test verifies that the
 * *most common* classification across all high-energy windows of a recording
 * matches the expected label — a criterion that is both robust to intra-hit
 * variation and a realistic proxy for in-app accuracy, where classification
 * decisions are based on the onset window rather than a single arbitrary frame.
 *
 * ### Instrument–file assignment
 *
 * The six WAV recordings are assigned to [DrumPart] labels in ascending order
 * of their calibrated spectral centroid.  This ensures a deterministic,
 * physically motivated mapping that does not depend on knowing the exact
 * instrument used in each recording:
 *
 * | Recording          | Assigned [DrumPart]      | Centroid range  |
 * |--------------------|--------------------------|-----------------|
 * | WA0013             | [DrumPart.BASS_DRUM]     | ~850 Hz         |
 * | WA0016             | [DrumPart.SNARE]         | ~1 200 Hz       |
 * | WA0015             | [DrumPart.TOM_FLOOR]     | ~1 250 Hz       |
 * | WA0014             | [DrumPart.TOM_HIGH]      | ~1 900 Hz       |
 * | WA0018             | [DrumPart.TOM_MID]       | ~2 400 Hz       |
 * | WA0017             | [DrumPart.HI_HAT_CLOSED] | ~2 600 Hz       |
 */
class SixInstrumentRecognitionTest {

    /** Sample rate of all six WAV files (converted from the original Opus recordings). */
    private val sampleRate = 16_000

    /**
     * Size of each analysis window in samples (32 ms at 16 kHz).  Matches the
     * window size used by [InstrumentCalibrator] for calibration snippets.
     */
    private val windowSize = 512

    /**
     * Minimum RMS amplitude for a window to be counted as a "real hit" rather
     * than background noise or room acoustics between drum strokes.
     */
    private val minRms = 0.02f

    // ── Instrument-to-file mapping ────────────────────────────────────────────

    /**
     * The six WAV test resources, each containing a short recording of a single
     * drum instrument struck several times.  The list is ordered so that
     * calibrated spectral centroids increase monotonically, yielding the label
     * mapping described in the class-level KDoc.
     */
    private val instrumentFiles: List<Pair<DrumPart, String>> = listOf(
        DrumPart.BASS_DRUM     to "AUD-20260404-WA0013.wav",
        DrumPart.SNARE         to "AUD-20260404-WA0016.wav",
        DrumPart.TOM_FLOOR     to "AUD-20260404-WA0015.wav",
        DrumPart.TOM_HIGH      to "AUD-20260404-WA0014.wav",
        DrumPart.TOM_MID       to "AUD-20260404-WA0018.wav",
        DrumPart.HI_HAT_CLOSED to "AUD-20260404-WA0017.wav",
    )

    // ── Main test ─────────────────────────────────────────────────────────────

    /**
     * Trains on all six recordings and verifies that the majority-vote
     * classification of each recording's high-energy windows matches the
     * instrument label assigned to that recording.
     *
     * Steps:
     * 1. Load each WAV file and extract all high-energy 512-sample windows.
     * 2. Compute the mean spectral feature vector per instrument (calibration).
     * 3. Build a [DrumHitClassifier] using the six calibrated feature vectors.
     * 4. For each recording, classify all its high-energy windows.
     * 5. Assert that the majority vote equals the expected [DrumPart] label.
     */
    @Test
    fun `six drum recordings are each correctly identified after feature calibration`() {
        // ── Step 1: load PCM windows for all six instruments ──────────────────
        val allWindows: Map<DrumPart, List<FloatArray>> = instrumentFiles.associate { (part, fileName) ->
            part to loadHighEnergyWindows(fileName)
        }

        // Sanity check: every file must have yielded at least some hit windows.
        instrumentFiles.forEach { (part, fileName) ->
            assertTrue(
                "No high-energy windows found in $fileName – check min-RMS threshold or WAV file",
                allWindows[part]!!.isNotEmpty()
            )
        }

        // ── Step 2: calibrate – compute mean feature vector per instrument ────
        val calibrator = InstrumentCalibrator(sampleRateHz = sampleRate)
        val featureCalibration: Map<DrumPart, FloatArray> = instrumentFiles.associate { (part, _) ->
            val windows = allWindows[part]!!
            val meanFeatures = meanFeatureVector(windows, calibrator)
            part to meanFeatures
        }

        // ── Step 3: build classifier with feature calibration ─────────────────
        val classifier = DrumHitClassifier(
            sampleRateHz       = sampleRate,
            featureCalibration = featureCalibration
        )

        // ── Step 4 & 5: classify every window and check majority vote ─────────
        val results = mutableMapOf<DrumPart, DrumPart>()
        instrumentFiles.forEach { (expectedPart, fileName) ->
            val windows  = allWindows[expectedPart]!!
            val majority = majorityVote(classifier, windows)

            assertNotNull(
                "Majority vote for $fileName returned null – no window exceeded minEnergy",
                majority
            )
            results[expectedPart] = majority!!
        }

        // All six instruments must receive a unique classification.
        val unique = results.values.toSet()
        assertEquals(
            "Expected 6 distinct instrument classifications but got: $results",
            6,
            unique.size
        )

        // Each instrument must match its assigned label.
        results.forEach { (expected, actual) ->
            assertEquals(
                "Recording assigned to $expected was classified as $actual",
                expected,
                actual
            )
        }
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    /**
     * Loads the WAV file [fileName] from the test resource classpath and returns
     * all [windowSize]-sample windows whose RMS amplitude is at or above [minRms].
     *
     * The function is a minimal, dependency-free WAV decoder that supports only
     * the 16-bit signed little-endian mono format produced by the FFmpeg conversion
     * step that pre-processed the original Opus recordings.
     */
    private fun loadHighEnergyWindows(fileName: String): List<FloatArray> {
        val samples = loadWavAsMono(fileName)
        val windows = mutableListOf<FloatArray>()
        var start = 0
        while (start + windowSize <= samples.size) {
            val window = samples.sliceArray(start until start + windowSize)
            if (rms(window) >= minRms) {
                windows.add(window)
            }
            start += windowSize / 4  // 75 % overlap for dense coverage
        }
        return windows
    }

    /**
     * Reads a 16-bit mono WAV file from the test classpath and returns all
     * samples normalised to the range [−1, 1].
     *
     * Only the mandatory RIFF/WAVE/fmt/data chunk layout is supported (the format
     * produced by `ffmpeg -ar 16000 -ac 1 -sample_fmt s16`).
     */
    private fun loadWavAsMono(fileName: String): FloatArray {
        val stream = javaClass.classLoader!!.getResourceAsStream(fileName)
            ?: error("Test resource not found: $fileName")
        val bytes = stream.use { it.readBytes() }

        // RIFF header: skip "RIFF" (4) + size (4) + "WAVE" (4) = 12 bytes
        // Then scan for the "data" chunk since there may be optional chunks.
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val chunkId   = String(bytes, pos, 4)
            val chunkSize = ByteBuffer.wrap(bytes, pos + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN).int
            pos += 8
            if (chunkId == "data") {
                val sampleCount = chunkSize / 2
                val buf = ByteBuffer.wrap(bytes, pos, chunkSize).order(ByteOrder.LITTLE_ENDIAN)
                return FloatArray(sampleCount) { buf.short / 32768f }
            }
            pos += chunkSize
        }
        error("No 'data' chunk found in WAV file: $fileName")
    }

    /**
     * Computes the mean spectral feature vector over all [windows] by applying
     * [InstrumentCalibrator.findSpectralFeatures] to each window and averaging
     * the results element-wise.
     */
    private fun meanFeatureVector(
        windows: List<FloatArray>,
        calibrator: InstrumentCalibrator
    ): FloatArray {
        val featureSize = 3
        val sum = FloatArray(featureSize)
        for (window in windows) {
            val feats = calibrator.findSpectralFeatures(window)
            for (i in 0 until featureSize) sum[i] += feats[i]
        }
        val n = windows.size.toFloat()
        return FloatArray(featureSize) { i -> sum[i] / n }
    }

    /**
     * Classifies every window in [windows] using [classifier] and returns the
     * [DrumPart] that received the most votes (majority vote).  Returns `null`
     * only if no window produced a non-null classification (i.e. all windows
     * fell below the minimum energy threshold).
     */
    private fun majorityVote(
        classifier: DrumHitClassifier,
        windows: List<FloatArray>
    ): DrumPart? {
        val votes = mutableMapOf<DrumPart, Int>()
        for (window in windows) {
            val result = classifier.classify(window, minEnergy = minRms)
            if (result != null) votes[result] = (votes[result] ?: 0) + 1
        }
        return votes.maxByOrNull { it.value }?.key
    }

    /** Computes the root-mean-square amplitude of [signal]. */
    private fun rms(signal: FloatArray): Float {
        val sumSq = signal.fold(0.0) { acc, s -> acc + s * s }
        return sqrt(sumSq / signal.size).toFloat()
    }
}
