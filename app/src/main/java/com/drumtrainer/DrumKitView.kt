package com.drumtrainer

import android.app.AlertDialog
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
 * - **Tap the "+" button** (top-right corner) → shows a dialog to add a drum
 *   that is not currently in the set.
 * - **Drag a pad to the remove zone** (red strip at the bottom) → the drum is
 *   removed from the set and the change is persisted.
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

    /**
     * Drum parts recently detected during free play (outside of training).
     * They are highlighted with the pad's own full-brightness colour instead
     * of the training-mode green/red feedback colours.
     */
    var freeParts: Set<DrumPart> = emptySet()
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
    private val removePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val removeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val addButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.FILL
    }
    private val addLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private data class PadDef(
        val part: DrumPart,
        var relX: Float,
        var relY: Float,
        val relRadius: Float,
        val isCymbal: Boolean
    )

    // Master catalogue of all available pads with their default positions.
    // Positions mirror the approximate real-world layout visible in the product photo:
    //   – two cymbal pads on stands (left and far-right)
    //   – one ride cymbal (right, slightly inward)
    //   – snare and tom pads around the central module
    //   – large bass pad at bottom-right
    private val allDefaultPads = listOf(
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

    // The currently active drum set (mutable, persisted to SharedPreferences).
    private val padDefs = mutableListOf<PadDef>()

    // ── Drag state ────────────────────────────────────────────────────────────

    private var draggedIndex: Int = -1
    private var dragStartX:   Float = 0f
    private var dragStartY:   Float = 0f
    private var dragOrigRelX: Float = 0f
    private var dragOrigRelY: Float = 0f
    private var dragOverRemoveZone: Boolean = false

    // ── Add button layout (computed during onDraw) ────────────────────────────
    private var addButtonCx:     Float = 0f
    private var addButtonCy:     Float = 0f
    private var addButtonRadius: Float = 0f

    private val prefs by lazy {
        context.getSharedPreferences("drum_kit_layout", Context.MODE_PRIVATE)
    }

    init {
        loadPadSet()
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
            val isFreePart = freeParts.any { p ->
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
                null  -> padColor(pad.part, isActive || isFreePart)
            }
            rimPaint.color = when {
                isDragging          -> Color.parseColor("#FFCC00")
                hitCorrect == true  -> Color.parseColor("#4CAF50")
                hitCorrect == false -> Color.parseColor("#F44336")
                isActive || isFreePart -> Color.WHITE
                else                -> Color.parseColor("#555555")
            }
            rimPaint.strokeWidth = dpToPx(if (isActive || isDragging || hitCorrect != null || isFreePart) 4f else 3f)

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
                rimPaint.strokeWidth = dpToPx(if (isActive || isDragging || hitCorrect != null || isFreePart) 4f else 3f)
                if (isDragging) canvas.drawCircle(cx, cy, r * 1.3f, dragHintPaint)
            }

            // Pad label
            labelPaint.textSize = minOf(w, h) * 0.052f
            labelPaint.color = when {
                hitCorrect != null || isActive || isDragging || isFreePart -> Color.WHITE
                else -> Color.parseColor("#999999")
            }
            canvas.drawText(padLabel(pad.part), cx, cy + labelPaint.textSize * 0.38f, labelPaint)
        }

        // ── Remove zone (shown only while dragging) ───────────────────────────
        if (draggedIndex >= 0) {
            val zoneTop = h * (1f - REMOVE_ZONE_RATIO)
            removePaint.color = if (dragOverRemoveZone)
                Color.parseColor("#CCF44336")
            else
                Color.parseColor("#66F44336")
            canvas.drawRect(0f, zoneTop, w, h, removePaint)
            removeTextPaint.textSize = dpToPx(13f)
            canvas.drawText(
                "🗑  Drop here to remove",
                w / 2f,
                zoneTop + (h - zoneTop) / 2f + removeTextPaint.textSize * 0.4f,
                removeTextPaint
            )
        }

        // ── Add button ("+" in top-right corner) ─────────────────────────────
        val btnR = dpToPx(16f)
        addButtonCx     = w - btnR - dpToPx(6f)
        addButtonCy     = btnR + dpToPx(6f)
        addButtonRadius = btnR
        canvas.drawCircle(addButtonCx, addButtonCy, btnR, addButtonPaint)
        addLabelPaint.textSize = btnR * 1.1f
        canvas.drawText("+", addButtonCx, addButtonCy + addLabelPaint.textSize * 0.36f, addLabelPaint)
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val w = width.toFloat()
        val h = height.toFloat()
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Check add button first (before pad hit-test)
                if (isOverAddButton(event.x, event.y)) {
                    showAddDialog()
                    return true
                }
                draggedIndex = findPadAt(event.x, event.y, w, h)
                if (draggedIndex >= 0) {
                    dragStartX   = event.x
                    dragStartY   = event.y
                    dragOrigRelX = padDefs[draggedIndex].relX
                    dragOrigRelY = padDefs[draggedIndex].relY
                    dragOverRemoveZone = false
                    // Prevent parent ScrollView from intercepting touch events
                    // during a drag so that vertical drags are not stolen.
                    parent?.requestDisallowInterceptTouchEvent(true)
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
                    dragOverRemoveZone = event.y > h * (1f - REMOVE_ZONE_RATIO)
                    invalidate()
                    true
                } else false
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (draggedIndex >= 0) {
                    val movedX = abs(event.x - dragStartX)
                    val movedY = abs(event.y - dragStartY)
                    // Re-evaluate remove-zone membership at lift time so that a quick
                    // finger movement that wasn't captured by a MOVE event still
                    // triggers deletion when the finger is released in the zone.
                    dragOverRemoveZone = event.y > h * (1f - REMOVE_ZONE_RATIO)
                    when {
                        movedX < dpToPx(10f) && movedY < dpToPx(10f) -> {
                            // Short tap – restore exact position and invoke callback
                            restoreDraggedPosition()
                            onPadTapped?.invoke(padDefs[draggedIndex].part)
                        }
                        dragOverRemoveZone -> {
                            // Drag to remove zone – delete this pad from the set
                            removePad(draggedIndex)
                        }
                        else -> {
                            // Drag ended – persist new layout
                            savePositions()
                        }
                    }
                    draggedIndex = -1
                    dragOverRemoveZone = false
                    invalidate()
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (draggedIndex >= 0) {
                    // Restore original position on cancel (e.g. system interruption)
                    restoreDraggedPosition()
                    draggedIndex = -1
                    dragOverRemoveZone = false
                    invalidate()
                }
                true
            }
            else -> super.onTouchEvent(event)
        }
    }

    // ── Add / Remove ──────────────────────────────────────────────────────────

    private fun isOverAddButton(x: Float, y: Float): Boolean {
        val dx = x - addButtonCx
        val dy = y - addButtonCy
        val touchR = addButtonRadius * 1.5f
        return dx * dx + dy * dy <= touchR * touchR
    }

    private fun showAddDialog() {
        val existingParts = padDefs.map { it.part }.toSet()
        // Offer all parts not yet in the set; skip HI_HAT_OPEN (always paired with CLOSED)
        val available = allDefaultPads.filter {
            it.part != DrumPart.HI_HAT_OPEN && it.part !in existingParts
        }
        if (available.isEmpty()) return
        val names = available.map { it.part.displayName }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.add_drum))
            .setItems(names) { _, which -> addPad(available[which]) }
            .show()
    }

    private fun addPad(template: PadDef) {
        // Place the new pad in the centre of the view
        padDefs.add(template.copy(relX = 0.50f, relY = 0.45f))
        // HI_HAT_CLOSED and HI_HAT_OPEN are always kept together
        if (template.part == DrumPart.HI_HAT_CLOSED) {
            val openDefault = allDefaultPads.first { it.part == DrumPart.HI_HAT_OPEN }
            padDefs.add(openDefault.copy(relX = 0.50f, relY = 0.45f))
        }
        saveEnabledParts()
        invalidate()
    }

    private fun removePad(index: Int) {
        if (padDefs.count { it.part != DrumPart.HI_HAT_OPEN } <= 1) return // keep at least one visible pad
        val part = padDefs[index].part
        padDefs.removeAt(index)
        // If the closed hi-hat is removed, also remove the open hi-hat entry
        if (part == DrumPart.HI_HAT_CLOSED) {
            padDefs.removeAll { it.part == DrumPart.HI_HAT_OPEN }
        }
        saveEnabledParts()
        invalidate()
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

    private fun saveEnabledParts() {
        val editor = prefs.edit()
        editor.putStringSet("enabled_parts", padDefs.map { it.part.name }.toSet())
        for (pad in padDefs) {
            editor.putFloat("relX_${pad.part.name}", pad.relX)
            editor.putFloat("relY_${pad.part.name}", pad.relY)
        }
        editor.apply()
    }

    private fun loadPadSet() {
        padDefs.clear()
        val savedEnabled = prefs.getStringSet("enabled_parts", null)
        if (savedEnabled == null) {
            // First launch or legacy data – start with all defaults, respecting any saved positions
            for (d in allDefaultPads) {
                val x = prefs.getFloat("relX_${d.part.name}", d.relX)
                val y = prefs.getFloat("relY_${d.part.name}", d.relY)
                padDefs.add(d.copy(relX = x, relY = y))
            }
        } else {
            // Restore saved drum set with saved positions
            for (d in allDefaultPads) {
                if (d.part.name in savedEnabled) {
                    val x = prefs.getFloat("relX_${d.part.name}", d.relX)
                    val y = prefs.getFloat("relY_${d.part.name}", d.relY)
                    padDefs.add(d.copy(relX = x, relY = y))
                }
            }
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

    /** Snaps the dragged pad back to the position recorded at drag-start. */
    private fun restoreDraggedPosition() {
        if (draggedIndex < 0) return
        padDefs[draggedIndex].relX = dragOrigRelX
        padDefs[draggedIndex].relY = dragOrigRelY
        syncHiHat()
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

    companion object {
        /** Fraction of the view height reserved as the "drop to remove" zone. */
        private const val REMOVE_ZONE_RATIO = 0.20f
    }
}
