package com.drumtrainer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.drumtrainer.audio.AudioProcessor
import com.drumtrainer.audio.DrumHitClassifier
import com.drumtrainer.audio.DrumSoundPlayer
import com.drumtrainer.data.CurriculumProvider
import com.drumtrainer.data.DatabaseHelper
import com.drumtrainer.data.PreferencesManager
import com.drumtrainer.databinding.ActivityMainBinding

/**
 * Entry point of the app.
 *
 * On first launch the user is sent to [ProfileActivity] to create a student
 * profile.  On subsequent launches the main dashboard is shown, listing
 * available curriculum levels and the student's progress.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var db: DatabaseHelper

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFreePlayListening = false
    private val audioProcessor: AudioProcessor by lazy {
        AudioProcessor(classifier = DrumHitClassifier(calibration = prefs.getAllCalibrations()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        db    = DatabaseHelper(this)

        Toast.makeText(
            this,
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            Toast.LENGTH_SHORT
        ).show()

        if (prefs.isFirstLaunch || prefs.activeStudentId == -1L) {
            startActivity(Intent(this, ProfileActivity::class.java))
            return
        }

        setupDashboard()
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.isFirstLaunch && prefs.activeStudentId != -1L) {
            setupDashboard()
            requestFreePlayMicIfNeeded()
        }
    }

    override fun onPause() {
        super.onPause()
        stopFreePlayListening()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_FREE_PLAY_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startFreePlayListening()
        }
    }

    private fun setupDashboard() {
        val studentId = prefs.activeStudentId
        val students  = db.getAllStudents()
        val student   = students.firstOrNull { it.id == studentId } ?: run {
            startActivity(Intent(this, ProfileActivity::class.java))
            return
        }

        binding.textStudentName.text = getString(R.string.welcome_student, student.name)
        binding.textStudentAge.text  = getString(R.string.student_age, student.ageYears)

        // Profile picture
        val picUri = prefs.profilePictureUri
        if (picUri.isNotEmpty()) {
            try {
                val bmp = BitmapFactory.decodeFile(picUri)
                if (bmp != null) binding.imageProfile.setImageBitmap(bmp)
            } catch (_: Exception) { /* keep default */ }
        }

        val passedIds = db.getPassedLessonIds(studentId)
        binding.textLessonsCompleted.text = getString(R.string.lessons_completed, passedIds.size)

        // Level progress (child-friendly)
        val totalLevels = CurriculumProvider.curriculum.levels.size
        val currentLevel = (student.currentLevel + 1).coerceAtMost(totalLevels)
        binding.textLevelProgress.text = getString(R.string.level_progress, currentLevel, totalLevels)
        binding.progressLevel.max      = totalLevels
        binding.progressLevel.progress = currentLevel

        // Medal counts
        val (bronze, silver, gold) = db.getMedalCounts(studentId)
        binding.textMedalBronze.text = getString(R.string.medal_count_bronze, bronze)
        binding.textMedalSilver.text = getString(R.string.medal_count_silver, silver)
        binding.textMedalGold.text   = getString(R.string.medal_count_gold, gold)

        // Tapping a pad on the drum kit plays its synthesised sound and
        // temporarily suppresses microphone input to avoid acoustic feedback.
        binding.drumKitView.onPadTapped = { part ->
            DrumSoundPlayer.play(part)
            if (isFreePlayListening) {
                audioProcessor.suppressInputFor(DrumSoundPlayer.soundDurationMs(part))
            }
        }

        binding.buttonStartLesson.setOnClickListener {
            startActivity(Intent(this, LessonActivity::class.java))
        }

        binding.buttonProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.buttonCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
    }

    private fun requestFreePlayMicIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startFreePlayListening()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_FREE_PLAY_AUDIO
            )
        }
    }

    private fun startFreePlayListening() {
        if (isFreePlayListening) return
        isFreePlayListening = true
        audioProcessor.startRecording(emptyList(), 120) { _, part, _ ->
            if (part != null) {
                runOnUiThread {
                    binding.drumKitView.freeParts = setOf(part)
                    mainHandler.postDelayed({
                        binding.drumKitView.freeParts = emptySet()
                    }, 600L)
                }
            }
        }
    }

    private fun stopFreePlayListening() {
        if (!isFreePlayListening) return
        isFreePlayListening = false
        mainHandler.removeCallbacksAndMessages(null)
        audioProcessor.stopRecording(emptyList())
        binding.drumKitView.freeParts = emptySet()
    }

    companion object {
        private const val REQUEST_FREE_PLAY_AUDIO = 1001
    }
}
