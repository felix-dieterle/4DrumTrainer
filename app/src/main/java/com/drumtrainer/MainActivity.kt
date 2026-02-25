package com.drumtrainer

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)
        db    = DatabaseHelper(this)

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
}
