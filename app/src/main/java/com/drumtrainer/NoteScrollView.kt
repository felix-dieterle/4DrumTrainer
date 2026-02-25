package com.drumtrainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.drumtrainer.model.DrumPart

/**
 * Scrolling note "highway" for the lesson screen.
 *
 * Notes travel from right to left at the pace dictated by the lesson BPM.
 * Each drum part occupies its own horizontal lane. A vertical **hit line**
 * near the left edge marks exactly when the student should strike.
 *
 * Notes are drawn as colour-coded rounded rectangles (one per beat event)
 * so they are visually distinct and easy to read at a glance.
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
    private val laneSeparatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333355")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val hitLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val hitZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22FFFFFF")
        style = Paint.Style.FILL
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

    // Use Choreographer instead of postDelayed so frames are aligned with vsync,
    // preventing timing drift relative to the metronome and drum kit updates.
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isScrolling) {
                invalidate()
                Choreographer.getInstance().postFrameCallback(this)
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
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    /** Stop the animation and hide the view content. */
    fun stopScroll() {
        isScrolling = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        notes = emptyList()
        parts = emptyList()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val laneCount = parts.size.coerceAtLeast(3)
        val h = (laneCount * dpToPx(48f)).toInt()
        setMeasuredDimension(w, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (parts.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val laneH = h / parts.size
        val hitLineX = w * 0.14f
        // Note size: wider rectangles, taller than the circle equivalent
        val noteW = laneH * 0.55f
        val noteH = laneH * 0.52f
        val noteCorner = noteH * 0.35f

        // Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Lane backgrounds + separators + labels
        labelPaint.textSize = laneH * 0.38f
        for (i in parts.indices) {
            val top = i * laneH
            if (i % 2 == 1) canvas.drawRect(0f, top, w, top + laneH, altLanePaint)
            // Lane separator
            if (i > 0) canvas.drawLine(0f, top, w, top, laneSeparatorPaint)
            canvas.drawText(partShortName(parts[i]), dpToPx(4f), top + laneH * 0.65f, labelPaint)
        }

        // Hit zone (subtle highlight behind the hit line)
        canvas.drawRect(hitLineX - noteW * 0.6f, 0f, hitLineX + noteW * 0.6f, h, hitZonePaint)

        // Hit line
        canvas.drawLine(hitLineX, 0f, hitLineX, h, hitLinePaint)

        if (!isScrolling) return

        val nowMs = System.currentTimeMillis()
        val scrollRange = w - hitLineX

        for ((noteMs, part) in notes) {
            val relMs = noteMs - nowMs
            if (relMs < -500L || relMs > lookaheadMs) continue

            val laneIndex = parts.indexOf(part)
            if (laneIndex < 0) continue

            val x  = hitLineX + (relMs.toFloat() / lookaheadMs.toFloat()) * scrollRange
            val cy = laneIndex * laneH + laneH * 0.5f
            // Fade out notes that have passed the hit line
            val alpha = if (relMs < 0) (255 * (1f + relMs / 500f)).toInt().coerceIn(0, 255) else 255

            // Dim notes far away; full brightness near the hit line
            val proximity = 1f - (relMs.toFloat() / lookaheadMs.toFloat()).coerceIn(0f, 1f)
            val brightnessAlpha = (80 + (175 * proximity)).toInt().coerceIn(0, 255)
            val finalAlpha = minOf(alpha, brightnessAlpha)

            notePaint.color = partColor(part)
            notePaint.alpha = finalAlpha
            noteRimPaint.alpha = finalAlpha

            val rect = RectF(x - noteW * 0.5f, cy - noteH * 0.5f, x + noteW * 0.5f, cy + noteH * 0.5f)
            canvas.drawRoundRect(rect, noteCorner, noteCorner, notePaint)
            canvas.drawRoundRect(rect, noteCorner, noteCorner, noteRimPaint)
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
