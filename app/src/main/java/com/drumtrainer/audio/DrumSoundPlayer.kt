package com.drumtrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.drumtrainer.model.DrumPart
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthesises and plays short percussion sounds for each [DrumPart].
 *
 * All sounds are generated on-the-fly using basic DSP (sine waves + filtered
 * noise + exponential decay) so no audio asset files are required.
 * Each call to [play] spawns a short-lived background thread.
 */
object DrumSoundPlayer {

    private const val SAMPLE_RATE = 44_100

    /** Play the characteristic synthesised sound for [part] in a background thread. */
    fun play(part: DrumPart) {
        val samples = when (part) {
            DrumPart.BASS_DRUM     -> bassDrum()
            DrumPart.SNARE         -> snare()
            DrumPart.HI_HAT_CLOSED -> hiHat(durationMs = 60)
            DrumPart.HI_HAT_OPEN   -> hiHat(durationMs = 220)
            DrumPart.TOM           -> tom()
            DrumPart.RIDE          -> cymbal(baseFreqHz = 800f, durationMs = 400)
            DrumPart.CRASH         -> cymbal(baseFreqHz = 500f, durationMs = 650)
        }
        Thread {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 4)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                Thread.sleep((samples.size * 1000L / SAMPLE_RATE) + 60L)
                track.stop()
                track.release()
            } catch (_: Exception) {
                // Ignore audio errors (e.g. no audio hardware in tests)
            }
        }.start()
    }

    // ── Synthesisers ──────────────────────────────────────────────────────────

    private fun bassDrum(): FloatArray {
        val size = SAMPLE_RATE * 200 / 1000
        return FloatArray(size) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 25.0)
            val freq = (80.0 - 50.0 * (t * 10.0).coerceAtMost(1.0)).coerceAtLeast(30.0)
            (sin(2.0 * PI * freq * t) * decay * 0.85).toFloat()
        }
    }

    private fun snare(): FloatArray {
        val size = SAMPLE_RATE * 160 / 1000
        val rng = java.util.Random(42L)
        return FloatArray(size) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 28.0)
            val tone  = sin(2.0 * PI * 200.0 * t) * 0.3
            val noise = (rng.nextFloat() * 2f - 1f) * 0.7f
            ((tone + noise) * decay * 0.75).toFloat()
        }
    }

    private fun hiHat(durationMs: Int): FloatArray {
        val size = SAMPLE_RATE * durationMs / 1000
        val rng = java.util.Random(7L)
        val decayRate = if (durationMs < 100) 85.0 else 18.0
        return FloatArray(size) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * decayRate)
            (rng.nextFloat() * 2f - 1f) * decay.toFloat() * 0.5f
        }
    }

    private fun tom(): FloatArray {
        val size = SAMPLE_RATE * 190 / 1000
        return FloatArray(size) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 18.0)
            val freq  = (150.0 - 80.0 * (t * 5.0).coerceAtMost(1.0)).coerceAtLeast(70.0)
            (sin(2.0 * PI * freq * t) * decay * 0.85).toFloat()
        }
    }

    private fun cymbal(baseFreqHz: Float, durationMs: Int): FloatArray {
        val size = SAMPLE_RATE * durationMs / 1000
        val rng = java.util.Random(13L)
        return FloatArray(size) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 7.0)
            val tone  = sin(2.0 * PI * baseFreqHz * t) * 0.3
            val noise = (rng.nextFloat() * 2f - 1f) * 0.7f
            ((tone + noise) * decay * 0.55).toFloat()
        }
    }
}
