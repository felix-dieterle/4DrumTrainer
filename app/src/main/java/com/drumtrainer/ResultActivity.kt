package com.drumtrainer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.drumtrainer.databinding.ActivityResultBinding

/**
 * Displays the outcome of a completed lesson session:
 *  - Rhythm score (% of hits in time)
 *  - Pitch/instrument score (% of correct drum parts)
 *  - Overall score
 *  - Pass/fail badge
 *
 * Provides "Try Again" and "Next Lesson" navigation buttons.
 */
class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val rhythmScore  = intent.getIntExtra(EXTRA_RHYTHM_SCORE, 0)
        val pitchScore   = intent.getIntExtra(EXTRA_PITCH_SCORE, 0)
        val overallScore = intent.getIntExtra(EXTRA_OVERALL_SCORE, 0)
        val passed       = intent.getBooleanExtra(EXTRA_PASSED, false)

        binding.textRhythmScore.text  = getString(R.string.score_rhythm, rhythmScore)
        binding.textPitchScore.text   = getString(R.string.score_pitch, pitchScore)
        binding.textOverallScore.text = getString(R.string.score_overall, overallScore)

        binding.textPassFail.text = if (passed) getString(R.string.result_pass)
                                    else        getString(R.string.result_fail)
        binding.textPassFail.setTextColor(
            if (passed) getColor(R.color.pass_green) else getColor(R.color.fail_red)
        )

        binding.textEncouragement.text = encouragementText(overallScore, passed)

        binding.buttonTryAgain.setOnClickListener {
            startActivity(Intent(this, LessonActivity::class.java))
            finish()
        }

        binding.buttonNextLesson.setOnClickListener {
            startActivity(Intent(this, LessonActivity::class.java))
            finish()
        }
        binding.buttonNextLesson.isEnabled = passed

        binding.buttonHome.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun encouragementText(score: Int, passed: Boolean): String = when {
        passed && score >= 90 -> getString(R.string.encouragement_excellent)
        passed && score >= 75 -> getString(R.string.encouragement_good)
        passed                -> getString(R.string.encouragement_pass)
        score >= 50           -> getString(R.string.encouragement_almost)
        else                  -> getString(R.string.encouragement_keep_trying)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_RHYTHM_SCORE  = "extra_rhythm_score"
        const val EXTRA_PITCH_SCORE   = "extra_pitch_score"
        const val EXTRA_OVERALL_SCORE = "extra_overall_score"
        const val EXTRA_PASSED        = "extra_passed"
        const val EXTRA_LESSON_ID     = "extra_lesson_id"
    }
}
