package com.drumtrainer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.drumtrainer.audio.AudioProcessor
import com.drumtrainer.audio.RhythmEvaluator
import com.drumtrainer.data.CurriculumProvider
import com.drumtrainer.data.DatabaseHelper
import com.drumtrainer.data.PreferencesManager
import com.drumtrainer.databinding.ActivityLessonBinding
import com.drumtrainer.model.AgeGroup
import com.drumtrainer.model.DrumPart
import com.drumtrainer.model.Lesson
import com.drumtrainer.model.LessonProgress
import com.drumtrainer.model.Student

/**
 * The core practice screen.
 *
 * Responsibilities:
 *  - Show the current lesson's pattern as a scrollable beat grid.
 *  - Run a visual metronome (flashing beat indicator).
 *  - Capture microphone audio and feed it to [AudioProcessor].
 *  - After the session ends, compute scores and navigate to [ResultActivity].
 */
class LessonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLessonBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var db: DatabaseHelper
    private lateinit var audioProcessor: AudioProcessor

    private var lesson: Lesson? = null
    private var student: Student? = null
    private var sessionStartMs: Long = 0L

    private val metronomeHandler = Handler(Looper.getMainLooper())
    private var currentBeat = 0
    private var isRecording = false

    private val expectedTimestamps = mutableListOf<Pair<Long, DrumPart>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs          = PreferencesManager(this)
        db             = DatabaseHelper(this)
        audioProcessor = AudioProcessor()

        loadLessonAndStudent()

        binding.buttonStart.setOnClickListener { requestMicPermissionAndStart() }
        binding.buttonStop.setOnClickListener  { stopSession() }
    }

    private fun loadLessonAndStudent() {
        val studentId = prefs.activeStudentId
        student = db.getAllStudents().firstOrNull { it.id == studentId }
        if (student == null) {
            Toast.makeText(this, R.string.error_no_student, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val passedIds = db.getPassedLessonIds(studentId)
        lesson = CurriculumProvider.curriculum.nextLesson(student!!, passedIds)
        if (lesson == null) {
            Toast.makeText(this, R.string.all_lessons_complete, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        updateLessonUI()
    }

    private fun updateLessonUI() {
        val l = lesson ?: return
        val s = student ?: return

        binding.textLessonTitle.text = getString(l.titleRes)
        binding.textLessonDesc.text  = getString(l.descriptionRes)

        // Adapt displayed BPM based on age group
        val bpm = when (s.ageGroup) {
            AgeGroup.CHILD          -> l.targetBpmMin
            AgeGroup.YOUNG          -> (l.targetBpmMin + l.targetBpmMax) / 2
            AgeGroup.TEEN_AND_ABOVE -> l.targetBpmMax
        }
        binding.textBpm.text = getString(R.string.bpm_display, bpm)
        binding.textEstimatedTime.text = getString(R.string.estimated_time, l.estimatedMinutes)

        // Build the beat grid
        PatternGridRenderer.render(binding.patternGridContainer, l.pattern, this)
    }

    private fun requestMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startSession()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startSession()
        } else {
            Toast.makeText(this, R.string.error_mic_permission, Toast.LENGTH_LONG).show()
        }
    }

    private fun startSession() {
        val l = lesson ?: return
        val s = student ?: return

        isRecording  = true
        sessionStartMs = System.currentTimeMillis()

        val bpm = when (s.ageGroup) {
            AgeGroup.CHILD          -> l.targetBpmMin
            AgeGroup.YOUNG          -> (l.targetBpmMin + l.targetBpmMax) / 2
            AgeGroup.TEEN_AND_ABOVE -> l.targetBpmMax
        }

        expectedTimestamps.clear()
        expectedTimestamps.addAll(
            audioProcessor.buildExpectedTimestamps(
                l.pattern, bpm, bars = s.ageGroup.repsPerExercise, sessionStartMs = sessionStartMs
            )
        )

        audioProcessor.startRecording(l.pattern, bpm) { _, _ ->
            // Real-time hit feedback could update the UI here
        }

        startMetronome(bpm)

        binding.buttonStart.visibility = View.GONE
        binding.buttonStop.visibility  = View.VISIBLE
        binding.textStatus.text        = getString(R.string.status_recording)
    }

    private fun startMetronome(bpm: Int) {
        val intervalMs = (60_000L / bpm)
        val beatsPerBar = lesson?.beatsPerBar ?: 4
        currentBeat = 0

        val tick = object : Runnable {
            override fun run() {
                if (!isRecording) return
                val isDownbeat = currentBeat % beatsPerBar == 0
                binding.metronomeIndicator.setBackgroundResource(
                    if (isDownbeat) R.drawable.beat_indicator_down else R.drawable.beat_indicator_up
                )
                metronomeHandler.postDelayed({
                    binding.metronomeIndicator.setBackgroundResource(R.drawable.beat_indicator_off)
                }, intervalMs / 4)

                currentBeat++
                metronomeHandler.postDelayed(this, intervalMs)
            }
        }
        metronomeHandler.post(tick)
    }

    private fun stopSession() {
        if (!isRecording) return
        isRecording = false
        metronomeHandler.removeCallbacksAndMessages(null)

        val result     = audioProcessor.stopRecording(expectedTimestamps)
        val durationSec = ((System.currentTimeMillis() - sessionStartMs) / 1000).toInt()
        val l = lesson ?: return
        val s = student ?: return

        val passed = result.overallScore >= l.passThresholdPct
        val progress = LessonProgress(
            studentId    = s.id,
            lessonId     = l.id,
            rhythmScore  = result.rhythmScore,
            pitchScore   = result.pitchScore,
            overallScore = result.overallScore,
            durationSec  = durationSec,
            passed       = passed
        )
        db.insertProgress(progress)

        if (passed) {
            val passedIds = db.getPassedLessonIds(s.id)
            val nextLesson = CurriculumProvider.curriculum.nextLesson(s, passedIds)
            if (nextLesson != null && nextLesson.levelIndex > l.levelIndex) {
                db.updateStudentLevel(s.id, nextLesson.levelIndex)
            }
        }

        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_RHYTHM_SCORE,  result.rhythmScore)
            putExtra(ResultActivity.EXTRA_PITCH_SCORE,   result.pitchScore)
            putExtra(ResultActivity.EXTRA_OVERALL_SCORE, result.overallScore)
            putExtra(ResultActivity.EXTRA_PASSED,        passed)
            putExtra(ResultActivity.EXTRA_LESSON_ID,     l.id)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        metronomeHandler.removeCallbacksAndMessages(null)
        if (isRecording) audioProcessor.stopRecording(emptyList())
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
    }
}
