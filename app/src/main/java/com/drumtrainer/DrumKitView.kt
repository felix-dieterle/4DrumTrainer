package com.drumtrainer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.drumtrainer.model.DrumPart

/**
 * Displays a top-down schematic of the compact e-drum kit.
 *
 * Each pad is drawn as a circle or oval at a fixed position that approximates
 * the real layout shown in the product photo (two cymbal pads on stands, three
 * drum pads around a central control module, and one large bass pad).
 *
 * Set [activeParts] to highlight the pads the student should hit right now.
 */
class DrumKitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** Drum parts that should be visually highlighted at this moment. */
    var activeParts: Set<DrumPart> = emptySet()
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

    private data class PadDef(
        val part: DrumPart,
        val relX: Float,
        val relY: Float,
        val relRadius: Float,
        val isCymbal: Boolean
    )

    // Positions mirror the approximate real-world layout visible in the product photo:
    //   – two cymbal pads on stands (left and far-right)
    //   – one ride cymbal (right, slightly inward)
    //   – snare and tom pads around the central module
    //   – large bass pad at bottom-right
    private val padDefs = listOf(
        PadDef(DrumPart.HI_HAT_CLOSED, 0.14f, 0.20f, 0.09f, isCymbal = true),
        PadDef(DrumPart.HI_HAT_OPEN,   0.14f, 0.20f, 0.09f, isCymbal = true), // same physical pad
        PadDef(DrumPart.CRASH,         0.88f, 0.24f, 0.08f, isCymbal = true),
        PadDef(DrumPart.RIDE,          0.72f, 0.14f, 0.08f, isCymbal = true),
        PadDef(DrumPart.SNARE,         0.38f, 0.55f, 0.11f, isCymbal = false),
        PadDef(DrumPart.TOM,           0.58f, 0.50f, 0.10f, isCymbal = false),
        PadDef(DrumPart.BASS_DRUM,     0.80f, 0.68f, 0.14f, isCymbal = false)
    )

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
        val drawn = mutableSetOf<Pair<Float, Float>>()
        for (pad in padDefs) {
            val key = pad.relX to pad.relY
            if (key in drawn) continue
            drawn.add(key)

            val isActive = activeParts.any { p ->
                p == pad.part ||
                    (pad.part == DrumPart.HI_HAT_CLOSED && p == DrumPart.HI_HAT_OPEN) ||
                    (pad.part == DrumPart.HI_HAT_OPEN   && p == DrumPart.HI_HAT_CLOSED)
            }

            val cx = w * pad.relX
            val cy = h * pad.relY
            val r  = minOf(w, h) * pad.relRadius

            padFillPaint.color = padColor(pad.part, isActive)
            rimPaint.color = if (isActive) Color.WHITE else Color.parseColor("#555555")
            rimPaint.strokeWidth = dpToPx(if (isActive) 4f else 3f)

            if (pad.isCymbal) {
                val oval = RectF(cx - r, cy - r * 0.32f, cx + r, cy + r * 0.32f)
                canvas.drawOval(oval, padFillPaint)
                canvas.drawOval(oval, rimPaint)
            } else {
                canvas.drawCircle(cx, cy, r, padFillPaint)
                canvas.drawCircle(cx, cy, r, rimPaint)
                // Inner ring for drum-head texture
                rimPaint.strokeWidth = dpToPx(1.5f)
                canvas.drawCircle(cx, cy, r * 0.55f, rimPaint)
                rimPaint.strokeWidth = dpToPx(if (isActive) 4f else 3f)
            }

            // Pad label
            labelPaint.textSize = minOf(w, h) * 0.052f
            labelPaint.color = if (isActive) Color.WHITE else Color.parseColor("#999999")
            canvas.drawText(padLabel(pad.part), cx, cy + labelPaint.textSize * 0.38f, labelPaint)
        }
    }

    private fun padColor(part: DrumPart, active: Boolean): Int {
        val hex = when (part) {
            DrumPart.BASS_DRUM     -> 0xE53935
            DrumPart.SNARE         -> 0x1E88E5
            DrumPart.HI_HAT_CLOSED -> 0x43A047
            DrumPart.HI_HAT_OPEN   -> 0x00ACC1
            DrumPart.RIDE          -> 0xFB8C00
            DrumPart.CRASH         -> 0x8E24AA
            DrumPart.TOM           -> 0xF4511E
        }
        val r = (hex shr 16) and 0xFF
        val g = (hex shr 8)  and 0xFF
        val b = hex          and 0xFF
        val scale = if (active) 1f else 0.22f
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
        DrumPart.TOM                    -> "Tom"
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
