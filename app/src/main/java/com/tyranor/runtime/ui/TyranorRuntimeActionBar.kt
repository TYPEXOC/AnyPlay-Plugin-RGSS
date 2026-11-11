package com.tyranor.runtime.ui

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.tyranor.runtime.input.PadOverlayPrefs
import com.tyranor.runtime.input.TyranorMouseController
import com.tyranor.runtime.input.TyranorPadController
import com.tyranor.runtime.rgss.R
import kotlin.math.abs
import kotlin.math.roundToInt

/** The same three compact runtime actions shown by TYN's MV/MZ host. */
class TyranorRuntimeActionBar(
    activity: Activity,
    parent: FrameLayout,
    private val pad: TyranorPadController,
    private val mouse: TyranorMouseController,
    primaryColor: Int,
    onPrimaryColor: Int,
    showCheats: Boolean,
    onCheats: () -> Unit,
) {
    private val padButton = ActionIcon(activity, Icon.PAD, primaryColor, onPrimaryColor)
    private val mouseButton = ActionIcon(activity, Icon.MOUSE, primaryColor, onPrimaryColor)
    private val bar: LinearLayout

    init {
        val density = activity.resources.displayMetrics.density
        bar = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        // Keep the pad control visible even when the pad starts hidden, so the player can
        // enable it without leaving the game.
        bar.addView(padButton, LinearLayout.LayoutParams(dp(48, density), dp(48, density)))
        bar.addView(mouseButton, LinearLayout.LayoutParams(dp(48, density), dp(48, density)).apply {
            topMargin = dp(10, density)
        })
        if (showCheats) {
            bar.addView(ActionIcon(activity, Icon.CHEAT, primaryColor, onPrimaryColor).apply {
                contentDescription = activity.getString(R.string.tyn_runtime_cheat_button)
                setOnClickListener { onCheats() }
            }, LinearLayout.LayoutParams(dp(48, density), dp(48, density)).apply {
                topMargin = dp(10, density)
            })
        }
        padButton.contentDescription = activity.getString(R.string.tyn_runtime_pad_button)
        padButton.setOnClickListener {
            pad.setPadVisible(!pad.pad.isPadVisible())
            refresh()
        }
        mouseButton.contentDescription = activity.getString(R.string.tyn_runtime_mouse_button)
        mouseButton.setOnClickListener {
            mouse.setEnabled(!mouse.isEnabled)
            refresh()
        }
        parent.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            topMargin = dp(16, density)
            marginStart = dp(16, density)
        })
        for (index in 0 until bar.childCount) {
            bindMoveGesture(activity, parent, bar, bar.getChildAt(index), density)
        }
        val savedPosition = PadOverlayPrefs.actionBarPosition(activity)
        bar.post {
            val params = bar.layoutParams as? FrameLayout.LayoutParams ?: return@post
            val maxLeft = (parent.width - bar.width).coerceAtLeast(0)
            val maxTop = (parent.height - bar.height).coerceAtLeast(0)
            params.gravity = Gravity.TOP or Gravity.LEFT
            if (savedPosition != null) {
                params.leftMargin = (savedPosition.first * maxLeft).roundToInt()
                params.topMargin = (savedPosition.second * maxTop).roundToInt()
            } else {
                params.leftMargin = dp(16, density).coerceIn(0, maxLeft)
                params.topMargin = dp(16, density).coerceIn(0, maxTop)
            }
            bar.layoutParams = params
        }
        refresh()
    }

    fun refresh() {
        padButton.isActive = pad.pad.isPadVisible()
        mouseButton.isActive = mouse.isEnabled
    }

    fun bringToFront() {
        bar.bringToFront()
    }

    private fun dp(value: Int, density: Float) = (value * density).toInt()

    /** Match MVMZ's launcher gesture: taps activate, an 8dp drag moves and persists the whole group. */
    private fun bindMoveGesture(
        context: Activity,
        parent: FrameLayout,
        bar: LinearLayout,
        target: View,
        density: Float,
    ) {
        var pointerId = -1
        var startRawX = 0f
        var startRawY = 0f
        var startLeft = 0
        var startTop = 0
        var moved = false
        val dragThreshold = dp(8, density)

        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val params = bar.layoutParams as? FrameLayout.LayoutParams ?: return@setOnTouchListener false
                    pointerId = event.getPointerId(event.actionIndex)
                    startRawX = event.rawX
                    startRawY = event.rawY
                    startLeft = params.leftMargin
                    startTop = params.topMargin
                    moved = false
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> true
                MotionEvent.ACTION_MOVE -> {
                    if (pointerId == -1) return@setOnTouchListener true
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (!moved && abs(dx) + abs(dy) < dragThreshold) return@setOnTouchListener true
                    moved = true
                    val params = bar.layoutParams as? FrameLayout.LayoutParams ?: return@setOnTouchListener true
                    val maxLeft = (parent.width - bar.width).coerceAtLeast(0)
                    val maxTop = (parent.height - bar.height).coerceAtLeast(0)
                    params.gravity = Gravity.TOP or Gravity.LEFT
                    params.leftMargin = (startLeft + dx).roundToInt().coerceIn(0, maxLeft)
                    params.topMargin = (startTop + dy).roundToInt().coerceIn(0, maxTop)
                    bar.layoutParams = params
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (pointerId != -1 && moved) {
                        val params = bar.layoutParams as? FrameLayout.LayoutParams
                        val maxLeft = (parent.width - bar.width).coerceAtLeast(0)
                        val maxTop = (parent.height - bar.height).coerceAtLeast(0)
                        PadOverlayPrefs.saveActionBarPosition(
                            context,
                            if (maxLeft == 0) 0f else (params?.leftMargin ?: 0).toFloat() / maxLeft,
                            if (maxTop == 0) 0f else (params?.topMargin ?: 0).toFloat() / maxTop,
                        )
                    } else if (pointerId != -1) {
                        view.performClick()
                    }
                    pointerId = -1
                    moved = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    pointerId = -1
                    moved = false
                    true
                }
                else -> pointerId != -1
            }
        }
    }

    private enum class Icon { PAD, MOUSE, CHEAT }

    private class ActionIcon(
        activity: Activity,
        private val icon: Icon,
        private val primaryColor: Int,
        private val onPrimaryColor: Int,
    ) : View(activity) {
        private val density = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(onPrimaryColor, PorterDuff.Mode.SRC_IN)
        }
        private val wandBitmap = if (icon == Icon.CHEAT) {
            BitmapFactory.decodeResource(resources, R.drawable.ic_tyranor_cheat_wand)
        } else {
            null
        }
        private val rect = RectF()

        var isActive: Boolean = true
            set(value) {
                field = value
                invalidate()
            }

        init {
            setWillNotDraw(false)
            isClickable = true
            isFocusable = true
            elevation = 6f * density
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(width, height) / 2f - 1f * density
            fill.color = when (icon) {
                Icon.PAD -> if (isActive) primaryColor else 0xFF30343B.toInt()
                Icon.MOUSE -> if (isActive) primaryColor else 0xFF333333.toInt()
                Icon.CHEAT -> primaryColor
            }
            canvas.drawCircle(cx, cy, radius, fill)
            if (icon != Icon.CHEAT) {
                stroke.color = if (icon == Icon.PAD) 0xFFBFC7D2.toInt() else 0xFFD6D6D6.toInt()
                canvas.drawCircle(cx, cy, radius, stroke)
            }
            stroke.color = onPrimaryColor
            fill.color = onPrimaryColor
            when (icon) {
                Icon.PAD -> drawGrid(canvas, cx, cy, radius)
                Icon.MOUSE -> drawMouse(canvas, cx, cy, radius)
                Icon.CHEAT -> drawWand(canvas, cx, cy, radius)
            }
        }

        private fun drawGrid(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
            val cell = radius * 0.31f
            val gap = radius * 0.12f
            for (row in 0..1) for (column in 0..1) {
                val left = cx - cell - gap / 2f + column * (cell + gap)
                val top = cy - cell - gap / 2f + row * (cell + gap)
                rect.set(left, top, left + cell, top + cell)
                canvas.drawRoundRect(rect, cell * 0.25f, cell * 0.25f, fill)
            }
        }

        private fun drawMouse(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
            val bodyWidth = radius * 0.78f
            val bodyHeight = radius * 1.18f
            rect.set(cx - bodyWidth / 2f, cy - bodyHeight / 2f, cx + bodyWidth / 2f, cy + bodyHeight / 2f)
            canvas.drawRoundRect(rect, bodyWidth / 2.6f, bodyWidth / 2.6f, stroke)
            canvas.drawLine(cx, rect.top + stroke.strokeWidth, cx, cy - bodyHeight * 0.08f, stroke)
            canvas.drawLine(rect.left, cy - bodyHeight * 0.08f, rect.right, cy - bodyHeight * 0.08f, stroke)
        }

        private fun drawWand(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
            val bitmap = wandBitmap ?: return
            val size = radius * 1.45f
            rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
            canvas.drawBitmap(bitmap, null, rect, iconPaint)
        }
    }
}
