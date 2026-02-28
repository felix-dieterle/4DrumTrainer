package com.drumtrainer.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.drumtrainer.model.BeatEvent
import com.drumtrainer.model.DrumPart
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages the Android [AudioRecord] session and ties together [OnsetDetector],
 * [DrumHitClassifier] and [RhythmEvaluator].
 *
 * Typical usage:
 * ```
 * val processor = AudioProcessor()
 * processor.startRecording(pattern, bpm) { result ->
 *     // update UI with result
 * }
 * // ... after lesson ends:
 * val evaluation = processor.stopRecording()
 * ```
 *
 * **Requires** `android.permission.RECORD_AUDIO` to be granted before calling
 * [startRecording].
 *
 * @param sampleRateHz  Recording sample rate (default: 44 100 Hz).
 * @param onsetDetector Onset detector instance (injectable for testing).
 * @param classifier    Drum-hit classifier instance (injectable for testing).
 * @param evaluator     Rhythm evaluator instance (injectable for testing).
 */
class AudioProcessor(
    private val sampleRateHz: Int = 44_100,
    private val onsetDetector: OnsetDetector = OnsetDetector(sampleRateHz),
    private val classifier: DrumHitClassifier = DrumHitClassifier(sampleRateHz),
    private val evaluator: RhythmEvaluator = RhythmEvaluator()
) {
    private val CHANNEL_CONFIG  = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT    = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_MULTIPLIER = 4

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    /** Wall-clock time (ms) when recording started. */
    private var recordingStartMs: Long = 0L

    /** All detected hits accumulated during this session. */
    private val detectedHits = mutableListOf<Pair<Long, DrumPart?>>()

    /** Raw PCM ring buffer for classifier snippet extraction (last 2048 samples). */
    private val snippetBuffer = FloatArray(2048)
    private var snippetWritePos = 0

    /**
     * Starts recording and onset detection.
     *
     * @param pattern       Expected beat pattern converted to (timestampMs, DrumPart) pairs
     *                      by [buildExpectedTimestamps].
     * @param bpm           Target BPM used only for UI metronome; analysis is timing-window based.
     * @param onHitDetected Optional real-time callback invoked on every detected hit.
     *                      Provides the wall-clock timestamp, the classified [DrumPart] (or null),
     *                      and the normalised velocity (0.0–1.0) of the hit.
     */
    fun startRecording(
        pattern: List<BeatEvent>,
        bpm: Int,
        onHitDetected: ((timestampMs: Long, part: DrumPart?, velocity: Float) -> Unit)? = null
    ) {
        onsetDetector.reset()
        detectedHits.clear()
        snippetBuffer.fill(0f)
        snippetWritePos = 0
        recordingStartMs = System.currentTimeMillis()

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateHz, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize    = minBufferSize * BUFFER_MULTIPLIER

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize
        )

        onsetDetector.onOnset = { timestampMs ->
            val snippet  = extractSnippet()
            val part     = classifier.classify(snippet)
            val velocity = computeRms(snippet)
            val wallMs   = recordingStartMs + timestampMs
            synchronized(detectedHits) { detectedHits.add(wallMs to part) }
            onHitDetected?.invoke(wallMs, part, velocity)
        }

        audioRecord?.startRecording()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val rawBuffer  = ShortArray(bufferSize / 2)
            val floatBuffer = FloatArray(bufferSize / 2)
            val ar = audioRecord ?: return@launch
            val frameSize = onsetDetector.frameSize
            while (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = ar.read(rawBuffer, 0, rawBuffer.size)
                if (read <= 0) continue
                for (i in 0 until read) {
                    floatBuffer[i] = rawBuffer[i] / 32768f
                }
                // Process one detector frame at a time so that when onOnset fires,
                // the circular buffer contains exactly the onset frame – not samples
                // from a later frame that were written before feed() was called.
                var pos = 0
                while (pos + frameSize <= read) {
                    for (i in 0 until frameSize) {
                        snippetBuffer[snippetWritePos % snippetBuffer.size] = floatBuffer[pos + i]
                        snippetWritePos++
                    }
                    onsetDetector.feed(floatBuffer.copyOfRange(pos, pos + frameSize))
                    pos += frameSize
                }
                // Feed any remaining samples (< frameSize) to keep onset timestamps accurate.
                // OnsetDetector only processes complete frames, so no onset can fire here
                // and the snippet buffer is never consulted for this sub-frame remainder.
                if (pos < read) {
                    onsetDetector.feed(floatBuffer.copyOfRange(pos, read))
                }
            }
        }
    }

    /**
     * Stops recording and returns the [RhythmEvaluator.EvaluationResult] for
     * the session.
     *
     * @param expectedTimestamps  Pre-computed list of (wallClockMs, DrumPart) for the pattern.
     */
    fun stopRecording(
        expectedTimestamps: List<Pair<Long, DrumPart>>
    ): RhythmEvaluator.EvaluationResult {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingJob?.cancel()
        recordingJob = null

        val hits = synchronized(detectedHits) { detectedHits.toList() }
        return evaluator.evaluate(expectedTimestamps, hits)
    }

    /**
     * Temporarily suppresses onset detection for [durationMs] milliseconds.
     *
     * Call this immediately after triggering audio playback via [DrumSoundPlayer]
     * to prevent the speaker output from being re-captured by the microphone and
     * producing spurious hit detections (acoustic feedback loop).
     */
    fun suppressInputFor(durationMs: Long) {
        onsetDetector.suppressFor(durationMs)
    }

    /**
     * Sets the absolute noise floor threshold on the onset detector.
     *
     * Any audio frame whose RMS is at or below [threshold] will be ignored,
     * preventing ambient background noise from triggering spurious drum-hit
     * detections during a training session.  The value should come from
     * [com.drumtrainer.audio.AdaptationManager.Result.noiseThreshold].
     */
    fun setNoiseFloor(threshold: Float) {
        onsetDetector.absoluteMinRms = threshold
    }

    private fun extractSnippet(): FloatArray {
        val size = minOf(512, snippetBuffer.size)
        val start = (snippetWritePos - size).coerceAtLeast(0)
        return FloatArray(size) { snippetBuffer[(start + it) % snippetBuffer.size] }
    }

    private fun computeRms(signal: FloatArray): Float {
        val sumSq = signal.fold(0.0) { acc, s -> acc + s * s }
        return sqrt(sumSq / signal.size).toFloat()
    }

    /**
     * Converts a [BeatEvent] pattern at a given [bpm] into a list of
     * (wallClockMs from session start, DrumPart) timestamps.
     *
     * @param pattern        Beat events defining the pattern.
     * @param bpm            Target tempo.
     * @param bars           How many bars to generate (default: 4 for practice loops).
     * @param sessionStartMs Wall-clock time when the session started (default: 0 = relative).
     */
    fun buildExpectedTimestamps(
        pattern: List<BeatEvent>,
        bpm: Int,
        bars: Int = 4,
        sessionStartMs: Long = 0L
    ): List<Pair<Long, DrumPart>> {
        val quarterNoteMs = (60_000.0 / bpm).toLong()
        val result = mutableListOf<Pair<Long, DrumPart>>()
        repeat(bars) { bar ->
            pattern.forEach { event ->
                val subdivisionMs = quarterNoteMs / event.subdivision
                val offset = (bar * 4 * event.subdivision + event.beatIndex) * subdivisionMs
                result.add((sessionStartMs + offset) to event.part)
            }
        }
        return result.sortedBy { it.first }
    }
}
