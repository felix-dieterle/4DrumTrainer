package com.drumtrainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.drumtrainer.model.DrumPart

/**
 * Scrolling note "highway" for the lesson screen.
 *
 * Notes travel from right to left at the pace dictated by the lesson BPM.
 * Each drum part occupies its own horizontal lane. A vertical **hit line**
 * near the left edge marks exactly when the student should strike.
 *
 * Call [startScroll] when the practice session begins and [stopScroll] when
 * it ends.
 */
class NoteScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** How many milliseconds of notes are visible ahead of the hit line. */
    private val lookaheadMs = 2500L

    private var notes: List<Pair<Long, DrumPart>> = emptyList()
    private var parts: List<DrumPart> = emptyList()
    private var isScrolling = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
    }
    private val altLanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22243A")
        style = Paint.Style.FILL
    }
    private val hitLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AAAAAA")
        textAlign = Paint.Align.LEFT
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val noteRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val frameCallback = object : Runnable {
        override fun run() {
            if (isScrolling) {
                invalidate()
                postDelayed(this, 16L) // ~60 fps
            }
        }
    }

    /**
     * Begin scrolling the note highway.
     *
     * @param notes  Pre-computed (wallClockMs, [DrumPart]) list produced by
     *               [com.drumtrainer.audio.AudioProcessor.buildExpectedTimestamps].
     */
    fun startScroll(notes: List<Pair<Long, DrumPart>>) {
        this.notes = notes
        this.parts = notes.map { it.second }.distinct()
        isScrolling = true
        requestLayout() // re-measure lane heights based on part count
        post(frameCallback)
    }

    /** Stop the animation and hide the view content. */
    fun stopScroll() {
        isScrolling = false
        removeCallbacks(frameCallback)
        notes = emptyList()
        parts = emptyList()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val laneCount = parts.size.coerceAtLeast(3)
        val h = (laneCount * dpToPx(44f)).toInt()
        setMeasuredDimension(w, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (parts.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val laneH = h / parts.size
        val hitLineX = w * 0.14f
        val noteRadius = laneH * 0.28f

        // Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Lane backgrounds + labels
        labelPaint.textSize = laneH * 0.40f
        for (i in parts.indices) {
            val top = i * laneH
            if (i % 2 == 1) canvas.drawRect(0f, top, w, top + laneH, altLanePaint)
            canvas.drawText(partShortName(parts[i]), dpToPx(4f), top + laneH * 0.67f, labelPaint)
        }

        // Hit line
        canvas.drawLine(hitLineX, 0f, hitLineX, h, hitLinePaint)

        if (!isScrolling) return

        val nowMs = System.currentTimeMillis()
        val scrollRange = w - hitLineX

        for ((noteMs, part) in notes) {
            val relMs = noteMs - nowMs
            if (relMs < -400L || relMs > lookaheadMs) continue

            val laneIndex = parts.indexOf(part)
            if (laneIndex < 0) continue

            val x  = hitLineX + (relMs.toFloat() / lookaheadMs.toFloat()) * scrollRange
            val cy = laneIndex * laneH + laneH * 0.5f
            val alpha = if (relMs < 0) (255 * (1f + relMs / 400f)).toInt().coerceIn(0, 255) else 255

            notePaint.color = partColor(part)
            notePaint.alpha = alpha
            noteRimPaint.alpha = alpha
            canvas.drawCircle(x, cy, noteRadius, notePaint)
            canvas.drawCircle(x, cy, noteRadius, noteRimPaint)
        }
    }

    private fun partShortName(part: DrumPart): String = when (part) {
        DrumPart.BASS_DRUM     -> "BD"
        DrumPart.SNARE         -> "SN"
        DrumPart.HI_HAT_CLOSED -> "HH"
        DrumPart.HI_HAT_OPEN   -> "OH"
        DrumPart.RIDE          -> "RD"
        DrumPart.CRASH         -> "CR"
        DrumPart.TOM           -> "TM"
    }

    private fun partColor(part: DrumPart): Int = when (part) {
        DrumPart.BASS_DRUM     -> 0xFFE53935.toInt()
        DrumPart.SNARE         -> 0xFF1E88E5.toInt()
        DrumPart.HI_HAT_CLOSED -> 0xFF43A047.toInt()
        DrumPart.HI_HAT_OPEN   -> 0xFF00ACC1.toInt()
        DrumPart.RIDE          -> 0xFFFB8C00.toInt()
        DrumPart.CRASH         -> 0xFF8E24AA.toInt()
        DrumPart.TOM           -> 0xFFF4511E.toInt()
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
