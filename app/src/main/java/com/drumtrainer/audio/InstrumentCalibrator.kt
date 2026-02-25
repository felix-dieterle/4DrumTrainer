package com.drumtrainer.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Records a short microphone sample while the user strikes a single drum
 * instrument, then estimates the dominant frequency band for that instrument.
 *
 * Typical usage:
 * ```
 * val calibrator = InstrumentCalibrator()
 * Thread {
 *     calibrator.record(durationMs = 3000, onProgress = { pct -> ... }) { lowHz, highHz ->
 *         // store calibrated range for DrumPart.SNARE
 *     }
 * }.start()
 * ```
 *
 * **Must be called from a background thread** – the [record] function blocks
 * for [durationMs] milliseconds while the microphone is open.
 *
 * @param sampleRateHz  Recording sample rate in Hz (default: 44 100).
 * @param onsetDetector Onset detector instance (injectable for testing).
 */
class InstrumentCalibrator(
    private val sampleRateHz: Int = 44_100,
    private val onsetDetector: OnsetDetector = OnsetDetector(sampleRateHz)
) {
    /**
     * Records [durationMs] milliseconds of audio and analyses onset snippets
     * to estimate the dominant frequency band.
     *
     * @param durationMs  How long to record in milliseconds (default: 3 000).
     * @param onProgress  Optional callback (0–100) invoked as recording progresses.
     * @param onComplete  Invoked on the calling thread when done.  Receives the
     *                    estimated (lowHz, highHz), or (null, null) if no hits
     *                    were detected during the recording window.
     */
    fun record(
        durationMs: Int = 3_000,
        onProgress: ((pct: Int) -> Unit)? = null,
        onComplete: (lowHz: Int?, highHz: Int?) -> Unit
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
        val peakFrequencies = mutableListOf<Int>()

        onsetDetector.onOnset = {
            val size = minOf(512, snippetBuffer.size)
            val start = (snippetWritePos - size).coerceAtLeast(0)
            val snippet = FloatArray(size) { snippetBuffer[(start + it) % snippetBuffer.size] }
            val peak = findPeakFrequency(snippet)
            if (peak > 0) peakFrequencies.add(peak)
        }

        audioRecord.startRecording()

        val rawBuffer   = ShortArray(bufferSize / 2)
        val floatBuffer = FloatArray(bufferSize / 2)
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
        }

        audioRecord.stop()
        audioRecord.release()

        if (peakFrequencies.isEmpty()) {
            onComplete(null, null)
            return
        }

        val medianPeak = peakFrequencies.sorted()[peakFrequencies.size / 2]
        // Band: half the peak frequency as lower bound, triple as upper bound,
        // clamped to audible/practical limits.
        val low  = (medianPeak / 2).coerceAtLeast(20)
        val high = (medianPeak * 3).coerceAtMost(20_000)
        onComplete(low, high)
    }

    /**
     * Returns the frequency (Hz) of the DFT bin with the highest energy in
     * [snippet].  Searches between 50 Hz and 10 000 Hz.
     *
     * This is `internal` so it can be tested directly without starting the mic.
     */
    internal fun findPeakFrequency(snippet: FloatArray): Int {
        val n = snippet.size
        val lowBin  = (50.0     * n / sampleRateHz).toInt().coerceIn(1, n / 2)
        val highBin = (10_000.0 * n / sampleRateHz).toInt().coerceIn(lowBin + 1, n / 2)

        var peakBin    = lowBin
        var peakEnergy = 0.0

        for (k in lowBin..highBin) {
            var re = 0.0
            var im = 0.0
            val angle = 2.0 * Math.PI * k / n
            for (i in snippet.indices) {
                re += snippet[i] * cos(angle * i)
                im += snippet[i] * sin(angle * i)
            }
            val energy = re * re + im * im
            if (energy > peakEnergy) {
                peakEnergy = energy
                peakBin    = k
            }
        }

        return (peakBin.toDouble() * sampleRateHz / n).toInt()
    }
}
