package com.tyranor.runtime.input

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import org.libsdl.app.SDLActivity
import kotlin.math.hypot

/** Optional SDL mouse mode for games that need a movable pointer in addition to touch input. */
class TyranorMouseController(activity: Activity, parent: FrameLayout) {
    val view = MouseView(activity)

    init {
        parent.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    val isEnabled: Boolean get() = view.mouseModeEnabled

    fun setEnabled(enabled: Boolean) = view.setMouseModeEnabled(enabled)

    fun onPause() = view.cancelGesture()

    fun shutdown() {
        view.cancelGesture()
        (view.parent as? ViewGroup)?.removeView(view)
    }

    class MouseView(activity: Activity) : View(activity) {
        private val slop = ViewConfiguration.get(activity).scaledTouchSlop
        private val density = resources.displayMetrics.density
        private val pointer = Path()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            strokeJoin = Paint.Join.ROUND
            color = 0xE6141414.toInt()
        }

        var mouseModeEnabled: Boolean = false
            private set
        private var downX = 0f
        private var downY = 0f
        private var pointerX = 0f
        private var pointerY = 0f
        private var moved = false
        private var longPressed = false

        private val longPress = Runnable {
            if (mouseModeEnabled && !moved) {
                longPressed = true
                sendClick(2, downX, downY)
            }
        }

        init {
            setWillNotDraw(false)
            isClickable = true
        }

        fun setMouseModeEnabled(enabled: Boolean) {
            if (mouseModeEnabled == enabled) return
            cancelGesture()
            mouseModeEnabled = enabled
            invalidate()
        }

        fun cancelGesture() {
            removeCallbacks(longPress)
            moved = false
            longPressed = false
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!mouseModeEnabled) return
            val x = if (pointerX > 0f) pointerX else width * 0.5f
            val y = if (pointerY > 0f) pointerY else height * 0.45f
            val scale = density
            pointer.rewind()
            pointer.moveTo(x, y)
            pointer.lineTo(x, y + 13.6f * scale)
            pointer.lineTo(x + 3.3f * scale, y + 10.6f * scale)
            pointer.lineTo(x + 5.6f * scale, y + 15.8f * scale)
            pointer.lineTo(x + 7.7f * scale, y + 14.8f * scale)
            pointer.lineTo(x + 5.6f * scale, y + 9.9f * scale)
            pointer.lineTo(x + 10.3f * scale, y + 9.6f * scale)
            pointer.close()
            canvas.save()
            canvas.translate(1f * density, 1.5f * density)
            fill.color = 0x59000000
            canvas.drawPath(pointer, fill)
            canvas.restore()
            fill.color = Color.WHITE
            canvas.drawPath(pointer, fill)
            canvas.drawPath(pointer, stroke)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!mouseModeEnabled) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    pointerX = downX
                    pointerY = downY
                    moved = false
                    longPressed = false
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val x = event.x
                    val y = event.y
                    if (!moved && hypot((x - downX).toDouble(), (y - downY).toDouble()) > slop) {
                        moved = true
                        removeCallbacks(longPress)
                    }
                    pointerX = x
                    pointerY = y
                    if (moved) sendMove(x, y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPress)
                    if (!moved && !longPressed) sendClick(1, event.x, event.y)
                    else if (moved) sendMove(event.x, event.y)
                    cancelGesture()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    cancelGesture()
                    return true
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                    removeCallbacks(longPress)
                    moved = true
                    return true
                }
            }
            return true
        }

        private fun sendMove(x: Float, y: Float) {
            if (width <= 0 || height <= 0) return
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_HOVER_MOVE, x, y, false)
        }

        private fun sendClick(button: Int, x: Float, y: Float) {
            SDLActivity.onNativeMouse(button, MotionEvent.ACTION_DOWN, x, y, false)
            SDLActivity.onNativeMouse(button, MotionEvent.ACTION_UP, x, y, false)
        }
    }
}
