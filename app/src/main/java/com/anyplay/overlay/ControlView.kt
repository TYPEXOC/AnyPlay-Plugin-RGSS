package com.anyplay.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import com.anyplay.engine.GamepadButton
import com.anyplay.engine.InputMapper
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a control sends its key events (JsBridge in the real overlay, null in previews). */
internal interface KeySink {
    fun keyDown(code: Int)
    fun keyUp(code: Int)
}

/**
 * ControlView
 * -------------
 * One control on the gamepad: either the 4-arm D-pad or a single button.
 * Look and proportions are measured from the reference screenshots:
 *
 *  BUTTON  - radial-gradient fill (color at ~55% alpha for the default 70%
 *            opacity), a crisp 2 dp ring in the full color, white label
 *            whose size is 25% of the diameter and shrinks to fit 77% of
 *            the width (that's why ESCAPE is smaller than ENTER).
 *  D-PAD   - four separate "home plate" arms, flat fill, no ring, small
 *            white triangle glyphs. Radius R = half the view; gap to the
 *            center 0.325R, arm width 0.49R, tip 0.185R.
 *
 *  JOYSTICK - round base (same color/opacity logic as a button) with four
 *            faint arrows and a lighter knob that follows the finger. The knob
 *            position is turned into the same arrow keys the D-pad sends.
 *
 * The same class draws the live preview inside the edit dialog (sink = null,
 * so it never sends keys) — that is what guarantees preview == real thing.
 */
internal class ControlView(
    context: Context,
    initial: ControlSpec,
    private val sink: KeySink?
) : View(context) {

    var spec: ControlSpec = initial
        set(value) {
            if (field.keyCode != value.keyCode || field.type != value.type || field.diagonal != value.diagonal) releaseIfHeld()
            field = value
            requestLayout()
            invalidate()
        }

    private val density = resources.displayMetrics.density

    private var isHeld = false
    private var heldKey = KeyEventUnset
    private var heldDirection: GamepadButton? = null

    // Joystick state: directions currently pressed + knob offset (px from center).
    private val joyHeld = LinkedHashSet<GamepadButton>()
    private var knobDx = 0f
    private var knobDy = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFAFAFA")
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    init {
        // Touching a control must NEVER pull Android focus away from the
        // WebView — a focus-less WebView can silently drop dispatched keys.
        isFocusable = false
        isFocusableInTouchMode = false
    }

    // ---- Measuring --------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            (spec.widthDp * density).roundToInt().coerceAtLeast(1),
            (spec.heightDp * density).roundToInt().coerceAtLeast(1)
        )
    }

    // ---- Drawing ----------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        when (spec.type) {
            ControlType.DPAD -> drawDpad(canvas)
            ControlType.JOYSTICK -> drawJoystick(canvas)
            ControlType.BUTTON -> drawButton(canvas)
        }
    }

    // ---- Joystick drawing -------------------------------------------------

    /** Radius of the round base (ring included inside the view). */
    private fun joyBaseRadius(): Float = min(width, height) / 2f - 1f * density

    private fun joyKnobRadius(): Float = joyBaseRadius() * 0.40f

    /** How far the knob center may travel from the middle. */
    private fun joyTravel(): Float = joyBaseRadius() - joyKnobRadius()

    private fun drawJoystick(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val ringW = 2f * density
        val baseR = joyBaseRadius() - ringW / 2f
        if (baseR <= 1f) return
        val rgb = spec.color and 0xFFFFFF
        val op = spec.opacity

        // Base disc.
        val fillAlpha = (op * 0.60f).coerceIn(0f, 1f)
        fillPaint.shader = RadialGradient(
            cx, cy, baseR,
            intArrayOf(withAlpha(rgb, fillAlpha * 0.85f), withAlpha(rgb, (fillAlpha * 1.15f).coerceAtMost(1f))),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseR, fillPaint)
        ringPaint.strokeWidth = ringW
        ringPaint.color = withAlpha(lighten(rgb, 0.25f), (op / 0.7f).coerceIn(0f, 1f))
        canvas.drawCircle(cx, cy, baseR, ringPaint)

        // Four faint direction arrows near the rim.
        val oldAlpha = arrowPaint.alpha
        arrowPaint.alpha = 130
        val d = baseR * 0.80f
        val a = baseR * 0.13f
        drawArrow(canvas, GamepadButton.UP, cx, cy - d, a)
        drawArrow(canvas, GamepadButton.DOWN, cx, cy + d, a)
        drawArrow(canvas, GamepadButton.LEFT, cx - d, cy, a)
        drawArrow(canvas, GamepadButton.RIGHT, cx + d, cy, a)
        arrowPaint.alpha = oldAlpha

        // Knob.
        val kr = joyKnobRadius()
        val kx = cx + knobDx
        val ky = cy + knobDy
        val knobBase = lighten(rgb, if (joyHeld.isNotEmpty()) 0.60f else 0.40f)
        val knobAlpha = (op + 0.15f).coerceIn(0f, 1f)
        fillPaint.shader = RadialGradient(
            kx, ky, kr.coerceAtLeast(1f),
            intArrayOf(withAlpha(lighten(knobBase, 0.12f), knobAlpha), withAlpha(knobBase, knobAlpha)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(kx, ky, kr, fillPaint)
        ringPaint.strokeWidth = 1.5f * density
        ringPaint.color = withAlpha(Color.WHITE, 0.35f)
        canvas.drawCircle(kx, ky, kr, ringPaint)
        fillPaint.shader = null
    }

    private fun drawButton(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rgb = spec.color and 0xFFFFFF
        val shownRgb = if (isHeld) lighten(rgb, 0.30f) else rgb
        val fillAlpha = (spec.opacity * 0.78f).coerceIn(0f, 1f)
        val ringAlpha = (spec.opacity / 0.7f).coerceIn(0f, 1f)
        val ringW = 2f * density

        // Shape bounds, inset by half the ring so the ring stays inside the view.
        val inset = ringW / 2f
        when (spec.shape) {
            ControlShape.CIRCLE -> {
                val d = min(w, h) - ringW
                rect.set((w - d) / 2f, (h - d) / 2f, (w + d) / 2f, (h + d) / 2f)
            }
            else -> rect.set(inset, inset, w - inset, h - inset)
        }

        // Subtle radial gradient: slightly darker center, slightly lighter edge
        // (matches the reference's soft vignette).
        val radius = min(rect.width(), rect.height()) / 2f
        val centerColor = withAlpha(shownRgb, fillAlpha * 0.85f)
        val edgeColor = withAlpha(shownRgb, (fillAlpha * 1.15f).coerceAtMost(1f))
        fillPaint.shader = RadialGradient(
            rect.centerX(), rect.centerY(), radius.coerceAtLeast(1f),
            intArrayOf(centerColor, edgeColor), null, Shader.TileMode.CLAMP
        )
        ringPaint.strokeWidth = ringW
        ringPaint.color = withAlpha(shownRgb, ringAlpha)

        when (spec.shape) {
            ControlShape.CIRCLE, ControlShape.OVAL -> {
                canvas.drawOval(rect, fillPaint)
                canvas.drawOval(rect, ringPaint)
            }
            ControlShape.SQUARE -> {
                val r = 4f * density
                canvas.drawRoundRect(rect, r, r, fillPaint)
                canvas.drawRoundRect(rect, r, r, ringPaint)
            }
            ControlShape.ROUNDED -> {
                val r = min(rect.width(), rect.height()) * 0.28f
                canvas.drawRoundRect(rect, r, r, fillPaint)
                canvas.drawRoundRect(rect, r, r, ringPaint)
            }
        }

        if (spec.label.isNotEmpty()) drawLabel(canvas, w, h)
    }

    private fun drawLabel(canvas: Canvas, w: Float, h: Float) {
        var size = spec.textDp * density
        textPaint.textSize = size
        val maxWidth = w * 0.77f
        while (textPaint.measureText(spec.label) > maxWidth && size > 6f * density * 0.6f) {
            size -= 0.5f
            textPaint.textSize = size
        }
        val fm = textPaint.fontMetrics
        val baseline = h / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(spec.label, w / 2f, baseline, textPaint)
    }

    private fun drawDpad(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f
        val g = r * 0.325f   // center -> flat inner edge of an arm
        val s = r * 0.49f    // arm width AND straight-body length
        val p = r * 0.185f   // pointed tip

        val rgb = spec.color and 0xFFFFFF
        for (button in DPAD_BUTTONS) {
            val shown = if (heldDirection == button) lighten(rgb, 0.25f) else rgb
            armPaint.color = withAlpha(shown, spec.opacity)
            drawArm(canvas, button, cx, cy, g, s, p, r)
        }
    }

    private fun drawArm(
        canvas: Canvas, button: GamepadButton,
        cx: Float, cy: Float, g: Float, s: Float, p: Float, r: Float
    ) {
        val ax: Float
        val ay: Float
        when (button) {
            GamepadButton.UP -> { ax = 0f; ay = -1f }
            GamepadButton.DOWN -> { ax = 0f; ay = 1f }
            GamepadButton.LEFT -> { ax = -1f; ay = 0f }
            GamepadButton.RIGHT -> { ax = 1f; ay = 0f }
            else -> return
        }
        val px = -ay
        val py = ax
        val halfW = s / 2f
        fun pt(along: Float, perp: Float) =
            floatArrayOf(cx + ax * along + px * perp, cy + ay * along + py * perp)

        val a = pt(g, -halfW)
        val b = pt(g + s, -halfW)
        val tip = pt(g + s + p, 0f)
        val c = pt(g + s, halfW)
        val d = pt(g, halfW)
        val path = Path().apply {
            moveTo(a[0], a[1]); lineTo(b[0], b[1]); lineTo(tip[0], tip[1])
            lineTo(c[0], c[1]); lineTo(d[0], d[1]); close()
        }
        canvas.drawPath(path, armPaint)

        // Arrow glyph: 12 ref-px wide on a 117 ref-px radius = 0.1026 R,
        // centered 0.623 R from the center of the pad.
        val gc = pt(0.623f * r, 0f)
        drawArrow(canvas, button, gc[0], gc[1], 0.1026f * r)
    }

    private fun drawArrow(canvas: Canvas, button: GamepadButton, cx: Float, cy: Float, size: Float) {
        val half = size / 2f
        val path = Path()
        when (button) {
            GamepadButton.UP -> {
                path.moveTo(cx, cy - half); path.lineTo(cx - half, cy + half); path.lineTo(cx + half, cy + half)
            }
            GamepadButton.DOWN -> {
                path.moveTo(cx, cy + half); path.lineTo(cx - half, cy - half); path.lineTo(cx + half, cy - half)
            }
            GamepadButton.LEFT -> {
                path.moveTo(cx - half, cy); path.lineTo(cx + half, cy - half); path.lineTo(cx + half, cy + half)
            }
            GamepadButton.RIGHT -> {
                path.moveTo(cx + half, cy); path.lineTo(cx - half, cy - half); path.lineTo(cx - half, cy + half)
            }
            else -> return
        }
        path.close()
        canvas.drawPath(path, arrowPaint)
    }

    // ---- Touch (play mode only; edit mode is handled by the overlay) -----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val sink = sink ?: return false
        return when (spec.type) {
            ControlType.DPAD -> dpadTouch(sink, event)
            ControlType.JOYSTICK -> joystickTouch(sink, event)
            ControlType.BUTTON -> buttonTouch(sink, event)
        }
    }

    // ---- Joystick touch ---------------------------------------------------

    private fun joystickTouch(sink: KeySink, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> moveJoystick(sink, event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                applyJoyDirections(sink, emptySet())
                knobDx = 0f
                knobDy = 0f
                invalidate()
            }
        }
        return true
    }

    private fun moveJoystick(sink: KeySink, x: Float, y: Float) {
        val dx = x - width / 2f
        val dy = y - height / 2f
        val travel = joyTravel()
        val dist = hypot(dx, dy)
        if (dist > travel && dist > 0f) {
            val k = travel / dist
            knobDx = dx * k
            knobDy = dy * k
        } else {
            knobDx = dx
            knobDy = dy
        }
        applyJoyDirections(sink, joyDirections(dx, dy, travel))
        invalidate()
    }

    /** Finger offset -> which arrow keys should be down. */
    private fun joyDirections(dx: Float, dy: Float, travel: Float): Set<GamepadButton> {
        if (hypot(dx, dy) < travel * 0.30f) return emptySet()   // dead zone
        val ax = abs(dx)
        val ay = abs(dy)
        val horiz = if (dx > 0f) GamepadButton.RIGHT else GamepadButton.LEFT
        val vert = if (dy > 0f) GamepadButton.DOWN else GamepadButton.UP

        if (spec.diagonal) {
            // 8 sectors of 45 degrees (tan 22.5 deg = 0.414).
            val out = LinkedHashSet<GamepadButton>()
            if (ax >= ay * 0.414f) out += horiz
            if (ay >= ax * 0.414f) out += vert
            return out
        }

        // 4-way like the D-pad, with hysteresis so it doesn't flicker on the 45 deg line.
        val horizontal = when (joyHeld.firstOrNull()) {
            GamepadButton.LEFT, GamepadButton.RIGHT -> ay <= ax * 1.25f
            GamepadButton.UP, GamepadButton.DOWN -> ax > ay * 1.25f
            else -> ax > ay
        }
        return setOf(if (horizontal) horiz else vert)
    }

    /** Releases keys no longer wanted FIRST, then presses the new ones. */
    private fun applyJoyDirections(sink: KeySink, wanted: Set<GamepadButton>) {
        for (b in joyHeld.toList()) {
            if (b !in wanted) {
                sink.keyUp(InputMapper.keyCodeFor(b))
                joyHeld.remove(b)
            }
        }
        for (b in wanted) {
            if (joyHeld.add(b)) sink.keyDown(InputMapper.keyCodeFor(b))
        }
    }

    private fun buttonTouch(sink: KeySink, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (spec.latch) {
                    if (isHeld) releaseIfHeld() else press(sink)
                } else {
                    press(sink)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!spec.latch) releaseIfHeld()
            }
        }
        return true
    }

    private fun press(sink: KeySink) {
        if (isHeld) return
        isHeld = true
        heldKey = spec.keyCode
        sink.keyDown(heldKey)
        invalidate()
    }

    private fun dpadTouch(sink: KeySink, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val zone = zoneFor(event.x, event.y)
                if (zone != heldDirection) switchDirection(sink, zone)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> switchDirection(sink, null)
        }
        return true
    }

    private fun switchDirection(sink: KeySink, newDirection: GamepadButton?) {
        heldDirection?.let { sink.keyUp(InputMapper.keyCodeFor(it)) }
        heldDirection = newDirection
        newDirection?.let { sink.keyDown(InputMapper.keyCodeFor(it)) }
        invalidate()
    }

    /** Which arm a touch falls under — null inside a small dead zone at the center. */
    private fun zoneFor(x: Float, y: Float): GamepadButton? {
        val dx = x - width / 2f
        val dy = y - height / 2f
        val deadZone = min(width, height) * 0.15f
        if (hypot(dx, dy) < deadZone) return null
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) GamepadButton.RIGHT else GamepadButton.LEFT
        } else {
            if (dy > 0) GamepadButton.DOWN else GamepadButton.UP
        }
    }

    /** Releases whatever this control is holding (button key, D-pad or joystick direction). */
    fun releaseIfHeld() {
        val sink = sink
        if (sink != null) {
            if (isHeld) sink.keyUp(heldKey)
            heldDirection?.let { sink.keyUp(InputMapper.keyCodeFor(it)) }
            for (b in joyHeld) sink.keyUp(InputMapper.keyCodeFor(b))
        }
        isHeld = false
        heldDirection = null
        joyHeld.clear()
        knobDx = 0f
        knobDy = 0f
        invalidate()
    }

    // ---- Color helpers ----------------------------------------------------

    private fun withAlpha(rgb: Int, alpha: Float): Int =
        Color.argb((alpha.coerceIn(0f, 1f) * 255f).roundToInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))

    private fun lighten(rgb: Int, amount: Float): Int {
        fun mix(c: Int) = (c + (255 - c) * amount).roundToInt().coerceIn(0, 255)
        return Color.rgb(mix(Color.red(rgb)), mix(Color.green(rgb)), mix(Color.blue(rgb)))
    }

    private companion object {
        const val KeyEventUnset = 0
        val DPAD_BUTTONS = listOf(GamepadButton.UP, GamepadButton.DOWN, GamepadButton.LEFT, GamepadButton.RIGHT)
    }
}
