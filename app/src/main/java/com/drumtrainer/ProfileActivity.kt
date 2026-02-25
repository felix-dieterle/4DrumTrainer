package com.drumtrainer

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.drumtrainer.data.DatabaseHelper
import com.drumtrainer.data.PreferencesManager
import com.drumtrainer.databinding.ActivityProfileBinding
import com.drumtrainer.model.AgeGroup
import com.drumtrainer.model.Student
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Allows the user to create or view a student profile.
 *
 * The form collects:
 *  - Student name
 *  - Date of birth (must be ≥ [AgeGroup.MINIMUM_AGE] years ago)
 *  - Profile picture (optional, picked from the device gallery)
 *
 * On save the student is persisted via [DatabaseHelper] and the active student
 * preference is updated.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var db: DatabaseHelper

    private var selectedBirthDate: LocalDate? = null

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            try {
                // Copy to internal storage so the URI remains valid across reboots
                val destFile = File(filesDir, PROFILE_PIC_FILENAME)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                prefs.profilePictureUri = destFile.absolutePath
                val bmp = BitmapFactory.decodeFile(destFile.absolutePath)
                if (bmp != null) binding.imageProfilePicture.setImageBitmap(bmp)
            } catch (_: Exception) {
                Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = PreferencesManager(this)
        db    = DatabaseHelper(this)

        binding.buttonPickDate.setOnClickListener { showDatePicker() }
        binding.buttonSaveProfile.setOnClickListener { saveProfile() }
        binding.buttonUploadPicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Load existing profile picture if available
        val picPath = prefs.profilePictureUri
        if (picPath.isNotEmpty()) {
            val bmp = BitmapFactory.decodeFile(picPath)
            if (bmp != null) binding.imageProfilePicture.setImageBitmap(bmp)
        }

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

    companion object {
        private const val PROFILE_PIC_FILENAME = "profile_picture.jpg"
    }
}
