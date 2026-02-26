package com.drumtrainer.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.drumtrainer.model.DrumPart
import kotlin.math.sqrt

/**
 * Runs a short adaptation phase before a training session starts.
 *
 * During the phase the user is asked to hit every instrument in the active
 * drum set at least once.  The manager:
 * - Detects and classifies drum hits in real time using [OnsetDetector] and
 *   [DrumHitClassifier].
 * - Measures the ambient noise floor from audio frames that contain no onset.
 * - After [durationMs] milliseconds, reports which of the [enabledParts] were
 *   never struck (unrecognised instruments).
 *
 * Typical usage (from a background thread):
 * ```
 * val manager = AdaptationManager()
 * manager.run(
 *     enabledParts  = drumKitView.enabledParts,
 *     onProgress    = { pct -> runOnUiThread { … } },
 *     onHitDetected = { part -> runOnUiThread { … } }
 * ) { result ->
 *     runOnUiThread { handleResult(result) }
 * }
 * ```
 *
 * **Must be called from a background thread** – [run] blocks for [durationMs].
 *
 * @param sampleRateHz  Recording sample rate in Hz (default: 44 100).
 * @param onsetDetector Onset detector instance (injectable for testing).
 * @param classifier    Drum-hit classifier instance (injectable for testing).
 */
class AdaptationManager(
    private val sampleRateHz: Int = 44_100,
    private val onsetDetector: OnsetDetector = OnsetDetector(sampleRateHz),
    private val classifier: DrumHitClassifier = DrumHitClassifier(sampleRateHz)
) {

    /**
     * Result of the adaptation phase.
     *
     * @property noiseThreshold    Measured RMS noise floor × [NOISE_SAFETY_FACTOR].
     *                             Pass to [AudioProcessor.setNoiseFloor] so quiet
     *                             background noise does not trigger spurious onsets
     *                             during the training session.
     * @property detectedParts     Instruments that were hit and successfully
     *                             classified during the adaptation window.
     * @property unrecognizedParts Instruments from [enabledParts] that were never
     *                             detected.  These are shown to the user via the
     *                             wizard in [LessonActivity].
     */
    data class Result(
        val noiseThreshold: Float,
        val detectedParts: Set<DrumPart>,
        val unrecognizedParts: Set<DrumPart>
    )

    /**
     * Records [durationMs] milliseconds of microphone audio while the user
     * hits each instrument.
     *
     * @param enabledParts    Drum parts currently active in the drum set.
     * @param durationMs      Recording window length in milliseconds (default: 10 000).
     * @param onProgress      Optional callback with progress 0–100.
     * @param onHitDetected   Optional callback invoked each time a hit is detected.
     * @param onComplete      Invoked when the recording window ends.
     */
    fun run(
        enabledParts: Set<DrumPart>,
        durationMs: Int = 10_000,
        onProgress: ((pct: Int) -> Unit)? = null,
        onHitDetected: ((DrumPart) -> Unit)? = null,
        onComplete: (Result) -> Unit
    ) {
        onsetDetector.reset()

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize * 4
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val snippetBuffer = FloatArray(2048)
        var snippetWritePos = 0

        val detectedParts = mutableSetOf<DrumPart>()
        var noiseRmsSum = 0.0
        var noiseFrameCount = 0
        var lastOnsetTimeMs = -1L

        onsetDetector.onOnset = {
            lastOnsetTimeMs = System.currentTimeMillis()
            val size = minOf(512, snippetBuffer.size)
            val start = (snippetWritePos - size).coerceAtLeast(0)
            val snippet = FloatArray(size) { snippetBuffer[(start + it) % snippetBuffer.size] }
            val part = classifier.classify(snippet)
            if (part != null) {
                detectedParts.add(part)
                onHitDetected?.invoke(part)
            }
        }

        audioRecord.startRecording()

        val rawBuffer    = ShortArray(bufferSize / 2)
        val floatBuffer  = FloatArray(bufferSize / 2)
        val totalSamples = sampleRateHz.toLong() * durationMs / 1000
        var samplesRead  = 0L

        while (samplesRead < totalSamples) {
            val read = audioRecord.read(rawBuffer, 0, rawBuffer.size)
            if (read <= 0) continue
            for (i in 0 until read) {
                val f = rawBuffer[i] / 32768f
                floatBuffer[i] = f
                snippetBuffer[snippetWritePos % snippetBuffer.size] = f
                snippetWritePos++
            }
            onsetDetector.feed(floatBuffer.copyOf(read))
            samplesRead += read
            onProgress?.invoke(((samplesRead * 100) / totalSamples).toInt().coerceIn(0, 100))

            // Accumulate RMS from chunks that are well clear of any detected onset.
            // These chunks approximate the ambient noise floor.
            val nowMs = System.currentTimeMillis()
            if (lastOnsetTimeMs < 0 || nowMs - lastOnsetTimeMs > QUIET_GUARD_MS) {
                val rms = computeRms(floatBuffer.copyOf(read))
                noiseRmsSum += rms
                noiseFrameCount++
            }
        }

        audioRecord.stop()
        audioRecord.release()

        val avgNoise      = if (noiseFrameCount > 0) (noiseRmsSum / noiseFrameCount).toFloat() else 0f
        val noiseThreshold = avgNoise * NOISE_SAFETY_FACTOR
        val unrecognized  = computeUnrecognizedParts(enabledParts, detectedParts)

        onComplete(Result(noiseThreshold, detectedParts.toSet(), unrecognized))
    }

    /**
     * Returns the drum parts in [enabledParts] that are absent from [detectedParts].
     *
     * [DrumPart.HI_HAT_CLOSED] and [DrumPart.HI_HAT_OPEN] are treated as the same
     * physical pad: if either variant was detected, neither is considered unrecognised.
     *
     * This is `internal` so it can be tested directly without starting the microphone.
     */
    internal fun computeUnrecognizedParts(
        enabledParts: Set<DrumPart>,
        detectedParts: Set<DrumPart>
    ): Set<DrumPart> = enabledParts.filter { part ->
        val associated = when (part) {
            DrumPart.HI_HAT_CLOSED -> setOf(DrumPart.HI_HAT_CLOSED, DrumPart.HI_HAT_OPEN)
            DrumPart.HI_HAT_OPEN   -> setOf(DrumPart.HI_HAT_OPEN,   DrumPart.HI_HAT_CLOSED)
            else                   -> setOf(part)
        }
        associated.none { it in detectedParts }
    }.toSet()

    /**
     * Computes RMS of [signal].  `internal` for unit testing.
     */
    internal fun computeRms(signal: FloatArray): Float {
        if (signal.isEmpty()) return 0f
        val sumSq = signal.fold(0.0) { acc, s -> acc + s * s }
        return sqrt(sumSq / signal.size).toFloat()
    }

    companion object {
        /** Factor applied to the measured noise RMS to derive the suppression threshold. */
        const val NOISE_SAFETY_FACTOR = 2.5f

        /**
         * Minimum time (ms) that must elapse after the last onset before a chunk
         * is considered "quiet" and used for noise-floor estimation.
         */
        private const val QUIET_GUARD_MS = 500L
    }
}
