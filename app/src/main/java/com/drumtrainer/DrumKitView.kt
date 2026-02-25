package com.drumtrainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.drumtrainer.model.DrumPart
import kotlin.math.abs

/**
 * Displays a top-down schematic of the compact e-drum kit.
 *
 * Each pad is drawn as a circle or oval at a fixed position that approximates
 * the real layout shown in the product photo (two cymbal pads on stands, three
 * drum pads around a central control module, and one large bass pad).
 *
 * Interactions:
 * - **Tap** a pad → [onPadTapped] callback is invoked (e.g. to play a sound).
 * - **Drag** a pad → the pad moves to the new position; layout is saved to
 *   SharedPreferences and restored on next view creation.
 *
 * Set [activeParts] to highlight the pads the student should hit right now.
 */
class DrumKitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** Called when the user taps a pad (short touch without drag). */
    var onPadTapped: ((DrumPart) -> Unit)? = null

    /** Drum parts that should be visually highlighted at this moment. */
    var activeParts: Set<DrumPart> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Drum parts that were recently detected by the microphone, mapped to whether
     * the hit was correct (true = green, false = red).
     */
    var hitResultParts: Map<DrumPart, Boolean> = emptyMap()
        set(value) {
            field = value
            invalidate()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A2E")
    }
    private val modulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C2C3E")
        style = Paint.Style.FILL
    }
    private val padFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val standPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#666666")
        strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val dragHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private data class PadDef(
        val part: DrumPart,
        var relX: Float,
        var relY: Float,
        val relRadius: Float,
        val isCymbal: Boolean
    )

    // Positions mirror the approximate real-world layout visible in the product photo:
    //   – two cymbal pads on stands (left and far-right)
    //   – one ride cymbal (right, slightly inward)
    //   – snare and tom pads around the central module
    //   – large bass pad at bottom-right
    private val padDefs = mutableListOf(
        PadDef(DrumPart.HI_HAT_CLOSED, 0.14f, 0.20f, 0.09f, isCymbal = true),
        PadDef(DrumPart.HI_HAT_OPEN,   0.14f, 0.20f, 0.09f, isCymbal = true), // same physical pad
        PadDef(DrumPart.CRASH,         0.88f, 0.24f, 0.08f, isCymbal = true),
        PadDef(DrumPart.RIDE,          0.72f, 0.14f, 0.08f, isCymbal = true),
        PadDef(DrumPart.SNARE,         0.38f, 0.55f, 0.11f, isCymbal = false),
        PadDef(DrumPart.TOM_HIGH,      0.50f, 0.40f, 0.09f, isCymbal = false),
        PadDef(DrumPart.TOM_MID,       0.62f, 0.46f, 0.09f, isCymbal = false),
        PadDef(DrumPart.TOM_FLOOR,     0.74f, 0.56f, 0.10f, isCymbal = false),
        PadDef(DrumPart.BASS_DRUM,     0.80f, 0.68f, 0.14f, isCymbal = false)
    )

    // ── Drag state ────────────────────────────────────────────────────────────

    private var draggedIndex: Int = -1
    private var dragStartX:   Float = 0f
    private var dragStartY:   Float = 0f
    private var dragOrigRelX: Float = 0f
    private var dragOrigRelY: Float = 0f

    private val prefs by lazy {
        context.getSharedPreferences("drum_kit_layout", Context.MODE_PRIVATE)
    }

    init {
        loadPositions()
    }

    // ── Measure / Draw ────────────────────────────────────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (w * 0.50f).toInt().coerceAtLeast(120)
        setMeasuredDimension(w, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Central control module
        canvas.drawRoundRect(
            RectF(w * 0.28f, h * 0.38f, w * 0.56f, h * 0.62f),
            dpToPx(8f), dpToPx(8f), modulePaint
        )

        // Cymbal stands (vertical lines from pad base to floor)
        canvas.drawLine(w * 0.14f, h * 0.29f, w * 0.14f, h * 0.80f, standPaint)
        canvas.drawLine(w * 0.72f, h * 0.22f, w * 0.72f, h * 0.80f, standPaint)
        canvas.drawLine(w * 0.88f, h * 0.32f, w * 0.88f, h * 0.80f, standPaint)

        // Floor / base bar
        rimPaint.color = Color.parseColor("#555555")
        rimPaint.strokeWidth = dpToPx(5f)
        canvas.drawLine(0f, h * 0.80f, w, h * 0.80f, rimPaint)
        rimPaint.strokeWidth = dpToPx(3f)

        // Draw pads – deduplicate pads that share a position (HI_HAT_OPEN/CLOSED)
        val drawn = mutableSetOf<DrumPart>()
        for ((index, pad) in padDefs.withIndex()) {
            // Skip HI_HAT_OPEN since it shares position with HI_HAT_CLOSED
            if (pad.part == DrumPart.HI_HAT_OPEN) continue
            if (pad.part in drawn) continue
            drawn.add(pad.part)

            val isActive = activeParts.any { p ->
                p == pad.part ||
                    (pad.part == DrumPart.HI_HAT_CLOSED && p == DrumPart.HI_HAT_OPEN) ||
                    (pad.part == DrumPart.HI_HAT_OPEN   && p == DrumPart.HI_HAT_CLOSED)
            }
            val isDragging = index == draggedIndex

            // Check if this pad was recently detected (null = not detected)
            val hitCorrect = hitResultParts[pad.part]
                ?: if (pad.part == DrumPart.HI_HAT_CLOSED) hitResultParts[DrumPart.HI_HAT_OPEN]
                   else if (pad.part == DrumPart.HI_HAT_OPEN) hitResultParts[DrumPart.HI_HAT_CLOSED]
                   else null

            val cx = w * pad.relX
            val cy = h * pad.relY
            val r  = minOf(w, h) * pad.relRadius

            padFillPaint.color = when (hitCorrect) {
                true  -> Color.parseColor("#4CAF50") // green – correct instrument
                false -> Color.parseColor("#F44336") // red   – wrong instrument
                null  -> padColor(pad.part, isActive)
            }
            rimPaint.color = when {
                isDragging        -> Color.parseColor("#FFCC00")
                hitCorrect == true  -> Color.parseColor("#4CAF50")
                hitCorrect == false -> Color.parseColor("#F44336")
                isActive          -> Color.WHITE
                else              -> Color.parseColor("#555555")
            }
            rimPaint.strokeWidth = dpToPx(if (isActive || isDragging || hitCorrect != null) 4f else 3f)

            if (pad.isCymbal) {
                val oval = RectF(cx - r, cy - r * 0.32f, cx + r, cy + r * 0.32f)
                canvas.drawOval(oval, padFillPaint)
                canvas.drawOval(oval, rimPaint)
                if (isDragging) canvas.drawOval(RectF(cx - r * 1.3f, cy - r * 0.5f, cx + r * 1.3f, cy + r * 0.5f), dragHintPaint)
            } else {
                canvas.drawCircle(cx, cy, r, padFillPaint)
                canvas.drawCircle(cx, cy, r, rimPaint)
                // Inner ring for drum-head texture
                rimPaint.strokeWidth = dpToPx(1.5f)
                canvas.drawCircle(cx, cy, r * 0.55f, rimPaint)
                rimPaint.strokeWidth = dpToPx(if (isActive || isDragging || hitCorrect != null) 4f else 3f)
                if (isDragging) canvas.drawCircle(cx, cy, r * 1.3f, dragHintPaint)
            }

            // Pad label
            labelPaint.textSize = minOf(w, h) * 0.052f
            labelPaint.color = when {
                hitCorrect != null || isActive || isDragging -> Color.WHITE
                else -> Color.parseColor("#999999")
            }
            canvas.drawText(padLabel(pad.part), cx, cy + labelPaint.textSize * 0.38f, labelPaint)
        }
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                draggedIndex = findPadAt(event.x, event.y, w, h)
                if (draggedIndex >= 0) {
                    dragStartX   = event.x
                    dragStartY   = event.y
                    dragOrigRelX = padDefs[draggedIndex].relX
                    dragOrigRelY = padDefs[draggedIndex].relY
                    invalidate()
                    true
                } else false
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggedIndex >= 0) {
                    val dx = (event.x - dragStartX) / w
                    val dy = (event.y - dragStartY) / h
                    padDefs[draggedIndex].relX = (dragOrigRelX + dx).coerceIn(0.05f, 0.95f)
                    padDefs[draggedIndex].relY = (dragOrigRelY + dy).coerceIn(0.05f, 0.95f)
                    // Keep HI_HAT_OPEN in sync with HI_HAT_CLOSED (same physical pad)
                    syncHiHat()
                    invalidate()
                    true
                } else false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggedIndex >= 0) {
                    val movedX = abs(event.x - dragStartX)
                    val movedY = abs(event.y - dragStartY)
                    if (movedX < dpToPx(10f) && movedY < dpToPx(10f)) {
                        // Short tap – invoke callback
                        onPadTapped?.invoke(padDefs[draggedIndex].part)
                    } else {
                        // Drag ended – persist new layout
                        savePositions()
                    }
                    draggedIndex = -1
                    invalidate()
                }
                true
            }
            else -> super.onTouchEvent(event)
        }
    }

    // ── Position persistence ──────────────────────────────────────────────────

    private fun savePositions() {
        val editor = prefs.edit()
        for (pad in padDefs) {
            editor.putFloat("relX_${pad.part.name}", pad.relX)
            editor.putFloat("relY_${pad.part.name}", pad.relY)
        }
        editor.apply()
    }

    private fun loadPositions() {
        for (pad in padDefs) {
            val savedX = prefs.getFloat("relX_${pad.part.name}", pad.relX)
            val savedY = prefs.getFloat("relY_${pad.part.name}", pad.relY)
            pad.relX = savedX
            pad.relY = savedY
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the index into [padDefs] of the pad closest to ([x], [y]) within
     * its touch radius, or -1 if no pad was hit.
     */
    private fun findPadAt(x: Float, y: Float, w: Float, h: Float): Int {
        val touchRadius = dpToPx(24f) // generous touch target
        for ((i, pad) in padDefs.withIndex()) {
            if (pad.part == DrumPart.HI_HAT_OPEN) continue // skip duplicate hi-hat entry
            val cx = w * pad.relX
            val cy = h * pad.relY
            val r  = minOf(w, h) * pad.relRadius + touchRadius
            val dx = x - cx
            val dy = y - cy
            if (dx * dx + dy * dy <= r * r) return i
        }
        return -1
    }

    /** Keeps HI_HAT_OPEN position in sync with HI_HAT_CLOSED (same physical pad). */
    private fun syncHiHat() {
        val closed = padDefs.firstOrNull { it.part == DrumPart.HI_HAT_CLOSED } ?: return
        val open   = padDefs.firstOrNull { it.part == DrumPart.HI_HAT_OPEN   } ?: return
        open.relX = closed.relX
        open.relY = closed.relY
    }

    private fun padColor(part: DrumPart, active: Boolean): Int {
        val hex = when (part) {
            DrumPart.BASS_DRUM     -> 0xE53935
            DrumPart.SNARE         -> 0x1E88E5
            DrumPart.HI_HAT_CLOSED -> 0x43A047
            DrumPart.HI_HAT_OPEN   -> 0x00ACC1
            DrumPart.RIDE          -> 0xFB8C00
            DrumPart.CRASH         -> 0x8E24AA
            DrumPart.TOM_HIGH      -> 0xF4511E
            DrumPart.TOM_MID       -> 0xFF7043
            DrumPart.TOM_FLOOR     -> 0xBF360C
        }
        val r = (hex shr 16) and 0xFF
        val g = (hex shr 8)  and 0xFF
        val b = hex          and 0xFF
        val scale = if (active) 1f else 0.45f
        return Color.rgb(
            (r * scale + 24).toInt().coerceIn(0, 255),
            (g * scale + 24).toInt().coerceIn(0, 255),
            (b * scale + 24).toInt().coerceIn(0, 255)
        )
    }

    private fun padLabel(part: DrumPart): String = when (part) {
        DrumPart.BASS_DRUM              -> "Bass"
        DrumPart.SNARE                  -> "Snare"
        DrumPart.HI_HAT_CLOSED,
        DrumPart.HI_HAT_OPEN            -> "Hi-Hat"
        DrumPart.RIDE                   -> "Ride"
        DrumPart.CRASH                  -> "Crash"
        DrumPart.TOM_HIGH               -> "Hi Tom"
        DrumPart.TOM_MID                -> "Mid Tom"
        DrumPart.TOM_FLOOR              -> "Fl Tom"
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
