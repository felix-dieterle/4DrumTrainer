package com.drumtrainer

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.drumtrainer.data.DatabaseHelper
import com.drumtrainer.data.PreferencesManager
import com.drumtrainer.databinding.ActivityProfileBinding
import com.drumtrainer.model.AgeGroup
import com.drumtrainer.model.Student
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Allows the user to create or view a student profile.
 *
 * The form collects:
 *  - Student name
 *  - Date of birth (must be ≥ [AgeGroup.MINIMUM_AGE] years ago)
 *
 * On save the student is persisted via [DatabaseHelper] and the active student
 * preference is updated.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var db: DatabaseHelper

    private var selectedBirthDate: LocalDate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = PreferencesManager(this)
        db    = DatabaseHelper(this)

        binding.buttonPickDate.setOnClickListener { showDatePicker() }
        binding.buttonSaveProfile.setOnClickListener { saveProfile() }

        // Pre-fill if an existing student is active
        val existingId = prefs.activeStudentId
        if (existingId != -1L) {
            db.getAllStudents().firstOrNull { it.id == existingId }?.let { prefillForm(it) }
        }
    }

    private fun showDatePicker() {
        val today = LocalDate.now()
        val initialDate = selectedBirthDate ?: today.minusYears(AgeGroup.MINIMUM_AGE.toLong())

        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedBirthDate = LocalDate.of(year, month + 1, day)
                binding.textSelectedDate.text = selectedBirthDate
                    ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    ?: ""
            },
            initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth
        ).show()
    }

    private fun prefillForm(student: Student) {
        binding.editStudentName.setText(student.name)
        selectedBirthDate = student.birthDate
        binding.textSelectedDate.text =
            student.birthDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

    private fun saveProfile() {
        val name = binding.editStudentName.text.toString().trim()
        if (name.isEmpty()) {
            binding.editStudentName.error = getString(R.string.error_name_required)
            return
        }

        val birthDate = selectedBirthDate
        if (birthDate == null) {
            Toast.makeText(this, R.string.error_birth_date_required, Toast.LENGTH_SHORT).show()
            return
        }

        // Validate minimum age
        val ageYears = java.time.Period.between(birthDate, LocalDate.now()).years
        if (ageYears < AgeGroup.MINIMUM_AGE) {
            Toast.makeText(
                this,
                getString(R.string.error_too_young, AgeGroup.MINIMUM_AGE),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val student = Student(name = name, birthDate = birthDate)
        val rowId   = db.insertStudent(student)
        if (rowId > 0) {
            prefs.activeStudentId = rowId
            prefs.isFirstLaunch   = false
            Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
