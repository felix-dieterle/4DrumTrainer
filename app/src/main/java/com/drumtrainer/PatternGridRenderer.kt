package com.drumtrainer

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.drumtrainer.model.BeatEvent
import com.drumtrainer.model.DrumPart

/**
 * Renders a simplified beat grid into a [LinearLayout] container.
 *
 * Each drum part gets one row; each beat position in the bar gets one column.
 * Cells that have a hit are filled with a coloured indicator.
 */
object PatternGridRenderer {

    /**
     * Clears [container] and populates it with one row per [DrumPart] that
     * appears in [pattern].
     */
    fun render(container: LinearLayout, pattern: List<BeatEvent>, context: Context) {
        container.removeAllViews()
        container.orientation = LinearLayout.VERTICAL

        val parts     = pattern.map { it.part }.distinct()
        val maxBeats  = pattern.maxOfOrNull { it.beatIndex + 1 } ?: 0
        val hitSet    = pattern.map { it.beatIndex to it.part }.toSet()

        parts.forEach { part ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dpToPx(4, context) }
            }

            // Label
            val label = TextView(context).apply {
                text = part.displayName
                textSize = 12f
                minWidth  = dpToPx(100, context)
                gravity   = Gravity.CENTER_VERTICAL
            }
            row.addView(label)

            // Beat cells
            for (beat in 0 until maxBeats) {
                val isHit = hitSet.contains(beat to part)
                val cell  = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(28, context), dpToPx(28, context)
                    ).also {
                        it.marginEnd   = dpToPx(4, context)
                        it.bottomMargin = dpToPx(2, context)
                    }
                    setBackgroundColor(if (isHit) partColor(part) else Color.parseColor("#DDDDDD"))
                    text = if (isHit) "●" else ""
                    gravity = Gravity.CENTER
                    textSize = 10f
                }
                row.addView(cell)
            }
            container.addView(row)
        }
    }

    private fun partColor(part: DrumPart): Int = when (part) {
        DrumPart.BASS_DRUM     -> Color.parseColor("#E53935") // red
        DrumPart.SNARE         -> Color.parseColor("#1E88E5") // blue
        DrumPart.HI_HAT_CLOSED -> Color.parseColor("#43A047") // green
        DrumPart.HI_HAT_OPEN   -> Color.parseColor("#00ACC1") // cyan
        DrumPart.RIDE          -> Color.parseColor("#FB8C00") // orange
        DrumPart.CRASH         -> Color.parseColor("#8E24AA") // purple
        DrumPart.TOM           -> Color.parseColor("#F4511E") // deep orange
    }

    private fun dpToPx(dp: Int, context: Context): Int =
        (dp * context.resources.displayMetrics.density).toInt()
}
