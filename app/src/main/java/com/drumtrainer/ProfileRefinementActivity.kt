package com.drumtrainer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.drumtrainer.audio.DrumHitClassifier
import com.drumtrainer.audio.OnsetDetector
import com.drumtrainer.data.PreferencesManager
import com.drumtrainer.databinding.ActivityProfileRefinementBinding
import com.drumtrainer.model.DrumPart

/**
 * Lets the user run multiple live-detection rounds against their calibrated
 * instrument profiles.
 *
 * Each round records microphone audio, classifies every drum hit using
 * [DrumHitClassifier] with the calibrated frequency bands stored in
 * [PreferencesManager], and displays the result in real time.  Running several
 * rounds across all instruments helps verify that each calibration profile is
 * accurate and reveals any remaining frequency-band overlaps between instruments.
 *
 * This is the "second type of overall calibration" described in the feature
 * request: after the per-instrument calibration sessions have produced initial
 * profiles, the refinement screen gives the user a way to play freely and
 * confirm (or notice failures in) the cross-instrument discrimination.
 */
class ProfileRefinementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileRefinementBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var classifier: DrumHitClassifier

    private val sampleRateHz = 44_100
    private val onsetDetector = OnsetDetector(sampleRateHz)
    private val snippetBuffer = FloatArray(2048)
    private var snippetWritePos = 0

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    private var completedRounds = 0
    private var roundHitCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileRefinementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_profile_refinement)

        prefs = PreferencesManager(this)
        classifier = DrumHitClassifier(sampleRateHz, prefs.getAllCalibrations())

        populateCalibratedInstruments()

        binding.buttonStartRound.setOnClickListener {
            if (isRecording) stopRound() else requestMicAndStart()
        }
        binding.buttonDone.setOnClickListener { finish() }

        updateStatusUI()
    }

    private fun populateCalibratedInstruments() {
        val calibrations = prefs.getAllCalibrations()
        binding.textCalibratedInstruments.text = if (calibrations.isEmpty()) {
            getString(R.string.refinement_no_calibrations)
        } else {
            calibrations.entries.joinToString("\n") { (part, band) ->
                "• ${part.displayName}: ${band.first}–${band.second} Hz"
            }
        }
    }

    private fun requestMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startRound()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startRound()
        }
    }

    private fun startRound() {
        if (isRecording) return
        isRecording = true
        roundHitCount = 0
        onsetDetector.reset()
        snippetBuffer.fill(0f)
        snippetWritePos = 0

        // Insert a round-separator header into the results list.
        if (completedRounds > 0 || binding.resultsContainer.childCount > 0) {
            val divider = TextView(this).apply {
                text = getString(R.string.refinement_round_divider, completedRounds + 1)
                textSize = 13f
                alpha = 0.6f
                setPadding(0, dpToPx(8), 0, dpToPx(4))
            }
            binding.resultsContainer.addView(divider, 0)
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = minBufSize * 4
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )

        onsetDetector.onOnset = {
            val size = minOf(512, snippetBuffer.size)
            val start = (snippetWritePos - size).coerceAtLeast(0)
            val snippet = FloatArray(size) { snippetBuffer[(start + it) % snippetBuffer.size] }
            val detected = classifier.classify(
                snippet,
                confidenceRatio = if (classifier.isCalibrated)
                    DrumHitClassifier.CALIBRATED_CONFIDENCE_RATIO else 1.0f
            )
            runOnUiThread { addHitResult(detected) }
        }

        audioRecord?.startRecording()

        recordingThread = Thread {
            val rawBuffer = ShortArray(bufSize / 2)
            val floatBuffer = FloatArray(bufSize / 2)
            val ar = audioRecord ?: return@Thread
            val frameSize = onsetDetector.frameSize
            while (isRecording && ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = ar.read(rawBuffer, 0, rawBuffer.size)
                if (read <= 0) continue
                for (i in 0 until read) floatBuffer[i] = rawBuffer[i] / 32768f
                var pos = 0
                while (pos + frameSize <= read) {
                    for (i in 0 until frameSize) {
                        snippetBuffer[snippetWritePos % snippetBuffer.size] = floatBuffer[pos + i]
                        snippetWritePos++
                    }
                    onsetDetector.feed(floatBuffer.copyOfRange(pos, pos + frameSize))
                    pos += frameSize
                }
            }
        }.also { it.start() }

        updateStatusUI()
    }

    private fun stopRound() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread = null
        completedRounds++
        updateStatusUI()
    }

    private fun addHitResult(detected: DrumPart?) {
        roundHitCount++
        val label = detected?.displayName ?: getString(R.string.hit_unknown)
        val tv = TextView(this).apply {
            text = getString(R.string.refinement_hit_result, roundHitCount, label)
            textSize = 14f
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }
        // Insert newest result at the top so it is immediately visible.
        binding.resultsContainer.addView(tv, 0)
        binding.textResultsTitle.visibility = View.VISIBLE
        binding.textRoundStatus.text = getString(R.string.refinement_hit_detected, label)
    }

    private fun updateStatusUI() {
        binding.buttonStartRound.text = if (isRecording)
            getString(R.string.refinement_stop_round)
        else
            getString(R.string.refinement_start_round)

        if (!isRecording) {
            binding.textRoundStatus.text = when {
                completedRounds == 0 -> getString(R.string.refinement_idle)
                else -> getString(R.string.refinement_round_complete, completedRounds)
            }
        } else {
            binding.textRoundStatus.text = getString(R.string.refinement_listening)
        }

        if (completedRounds > 0) {
            binding.textRoundCount.visibility = View.VISIBLE
            binding.textRoundCount.text = getString(R.string.refinement_rounds_done, completedRounds)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) stopRound()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1003
    }
}
