package com.drumtrainer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.drumtrainer.audio.DiagnosticsExporter
import com.drumtrainer.audio.InstrumentCalibrator
import com.drumtrainer.data.PreferencesManager
import com.drumtrainer.databinding.ActivityCalibrationBinding
import com.drumtrainer.model.DrumPart
import java.io.File

/**
 * Guides the user through calibrating each drum instrument by recording
 * [com.drumtrainer.audio.InstrumentCalibrator.CALIBRATION_HITS] hits and
 * analysing the dominant frequency band.
 *
 * For each [DrumPart] the user can:
 *  1. Select the instrument from the spinner.
 *  2. Press **Record** and hit the instrument the required number of times;
 *     recording stops automatically once all hits are captured.
 *  3. The detected frequency range is automatically saved to [PreferencesManager].
 *  4. Press **Reset to Default** to remove the saved calibration.
 *
 * Calibration data is consumed by [com.drumtrainer.audio.DrumHitClassifier] via
 * [PreferencesManager.getAllCalibrations] whenever a new lesson session starts.
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var prefs: PreferencesManager

    private val calibrator = InstrumentCalibrator()
    private val parts = DrumPart.values()
    private var selectedPart: DrumPart = parts[0]
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_calibration)

        prefs = PreferencesManager(this)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            parts.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDrumPart.adapter = adapter

        binding.spinnerDrumPart.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                selectedPart = parts[pos]
                updateCalibrationDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.buttonRecord.setOnClickListener { requestMicAndRecord() }
        binding.buttonReset.setOnClickListener  { resetCalibration() }
        binding.buttonExportDiagnostics.setOnClickListener { exportDiagnostics() }
        binding.buttonRefineProfiles.setOnClickListener {
            startActivity(Intent(this, ProfileRefinementActivity::class.java))
        }

        binding.seekBarSensitivity.progress = prefs.micSensitivity
        binding.seekBarSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) prefs.micSensitivity = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        updateCalibrationDisplay()
        updateRefineButtonVisibility()
    }

    private fun updateCalibrationDisplay() {
        val cal   = prefs.getCalibration(selectedPart)
        val stats = prefs.getCalibrationStats(selectedPart)
        binding.textCalibrationStatus.text = when {
            cal != null && stats != null -> getString(
                R.string.calibration_current_with_stats,
                cal.first, cal.second, stats.first, stats.second
            )
            cal != null -> getString(R.string.calibration_current, cal.first, cal.second)
            else -> getString(
                R.string.calibration_default,
                selectedPart.freqRangeLowHz,
                selectedPart.freqRangeHighHz
            )
        }
    }

    /**
     * Builds a localised warning string listing every other [DrumPart] whose
     * effective band overlaps [resultLowHz, resultHighHz].  Returns an empty
     * string when there are no overlaps so the caller can skip the warning.
     */
    private fun buildOverlapWarning(resultLowHz: Int, resultHighHz: Int): String {
        val allCalibrations = prefs.getAllCalibrations()
        val overlapping = DrumPart.values().filter { other ->
            if (other == selectedPart) return@filter false
            val (oLow, oHigh) = allCalibrations[other]
                ?: (other.freqRangeLowHz to other.freqRangeHighHz)
            DiagnosticsExporter.bandsOverlap(resultLowHz, resultHighHz, oLow to oHigh)
        }
        return if (overlapping.isEmpty()) ""
        else getString(
            R.string.calibration_overlap_warning,
            overlapping.joinToString(", ") { it.displayName }
        )
    }

    /**
     * Shows the "Refine Profiles" button whenever at least two instruments have
     * been calibrated, enabling multi-instrument profile comparison.
     * Also updates the calibration completeness counter.
     */
    private fun updateRefineButtonVisibility() {
        val calibratedCount = prefs.getAllCalibrations().size
        val totalCount = DrumPart.values().size
        binding.buttonRefineProfiles.visibility =
            if (calibratedCount >= 2) View.VISIBLE else View.GONE
        binding.textCalibrationCompleteness.text =
            getString(R.string.calibration_completeness, calibratedCount, totalCount)
    }

    private fun requestMicAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCalibration()
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
            startCalibration()
        } else {
            Toast.makeText(this, R.string.error_mic_permission, Toast.LENGTH_LONG).show()
        }
    }

    private fun startCalibration() {
        if (isRecording) return
        isRecording = true
        binding.buttonRecord.isEnabled = false
        binding.buttonReset.isEnabled  = false
        binding.progressCalibration.visibility = View.VISIBLE
        binding.progressCalibration.progress   = 0
        binding.textCalibrationStatus.text = getString(R.string.calibration_background_noise)

        val recordingFile = File(filesDir, "cal_${selectedPart.name}.wav")

        Thread {
            calibrator.record(
                sensitivityFactor = sensitivityToThreshold(binding.seekBarSensitivity.progress),
                audioOutputFile   = recordingFile,
                onProgress = { pct ->
                    runOnUiThread { binding.progressCalibration.progress = pct }
                },
                onHitPhaseStart = {
                    runOnUiThread {
                        binding.textCalibrationStatus.text = getString(
                            R.string.calibration_hit_phase_start,
                            InstrumentCalibrator.CALIBRATION_HITS
                        )
                    }
                },
                onHitDetected = { count ->
                    runOnUiThread {
                        binding.textCalibrationStatus.text = getString(
                            R.string.calibration_hit_progress,
                            count,
                            InstrumentCalibrator.CALIBRATION_HITS
                        )
                    }
                }
            ) { result ->
                runOnUiThread {
                    isRecording = false
                    binding.buttonRecord.isEnabled = true
                    binding.buttonReset.isEnabled  = true
                    binding.progressCalibration.visibility = View.GONE

                    if (result != null) {
                        prefs.setCalibration(selectedPart, result.lowHz, result.highHz)
                        prefs.setCalibrationStats(selectedPart, result.meanHz, result.stddevHz)
                        prefs.setPeakFrequencies(selectedPart, result.peakFrequencies)
                        if (result.audioFilePath != null) {
                            prefs.setRecordingPath(selectedPart, result.audioFilePath)
                        }
                        updateRefineButtonVisibility()
                        val overlapWarning = buildOverlapWarning(result.lowHz, result.highHz)
                        val detail = getString(
                            R.string.calibration_result_detail,
                            result.lowHz,
                            result.highHz,
                            result.meanHz,
                            result.stddevHz,
                            result.peakFrequencies.joinToString(", ")
                        )
                        binding.textCalibrationStatus.text =
                            if (overlapWarning.isEmpty()) detail else "$detail\n\n$overlapWarning"
                        Toast.makeText(
                            this,
                            getString(R.string.calibration_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        updateCalibrationDisplay()
                        Toast.makeText(
                            this,
                            getString(R.string.calibration_no_hits),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }.start()
    }

    private fun resetCalibration() {
        prefs.clearCalibration(selectedPart)
        updateCalibrationDisplay()
        updateRefineButtonVisibility()
        Toast.makeText(this, getString(R.string.calibration_reset), Toast.LENGTH_SHORT).show()
    }

    /**
     * Maps SeekBar progress (0–10) to an onset threshold factor passed to
     * [InstrumentCalibrator.record].
     *
     * - progress 0  → factor 6.0 (Low sensitivity – ignores ambient noise, requires a loud hit)
     * - progress 5  → factor 3.5 (Default – balanced)
     * - progress 10 → factor 1.0 (High sensitivity – detects quiet hits, but also ambient noise)
     */
    private fun sensitivityToThreshold(progress: Int): Float = 6.0f - progress * 0.5f

    private fun exportDiagnostics() {
        val csv = DiagnosticsExporter.buildReport(
            calibrations    = prefs.getAllCalibrations(),
            stats           = prefs.getAllCalibrationStats(),
            peakFrequencies = prefs.getAllPeakFrequencies()
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostics_export_subject))
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.diagnostics_export_chooser)))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1002
    }
}
