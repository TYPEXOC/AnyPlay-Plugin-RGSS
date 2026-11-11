package com.tyranor.runtime.input

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import org.libsdl.app.SDLActivity
import kotlin.math.hypot

/** SDL equivalent of MVMZ's virtual cursor gestures for native RGSS games. */
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
        private companion object {
            const val LONG_PRESS_MS = 350L
            const val TWO_FINGER_TAP_MS = 300L
            const val LEFT_CLICK_HOLD_MS = 50L
            const val CURSOR_SENSITIVITY = 1.5f
            const val WHEEL_SENSITIVITY = 4f
            const val FLUSH_INTERVAL_MS = 16L

            const val IDLE = 0
            const val PENDING = 1
            const val DRAG = 2
            const val WHEEL = 3
            const val COOLDOWN = 4
            const val CONSUMED = 5
        }

        private val density = resources.displayMetrics.density
        private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
        private val pointer = Path()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            strokeJoin = Paint.Join.ROUND
            color = 0xE6141414.toInt()
        }

        var mouseModeEnabled: Boolean = false
            private set
        private var cursorX = 0.5f
        private var cursorY = 0.4f
        private var previousWidth = 0
        private var previousHeight = 0

        private var state = IDLE
        private var firstPointerId = -1
        private var secondPointerId = -1
        private var firstX = 0f
        private var firstY = 0f
        private var secondX = 0f
        private var secondY = 0f
        private var startX = 0f
        private var startY = 0f
        private var lastCenterX = 0f
        private var lastCenterY = 0f
        private var moved = false
        private var longPressFired = false
        private var downAt = 0L
        private var twoFingerAt = 0L
        private var pendingWheelX = 0f
        private var pendingWheelY = 0f
        private var pendingCursorMove = false
        private var pendingCursorX = 0f
        private var pendingCursorY = 0f
        private var flushPosted = false
        private var leftButtonDown = false

        private val longPress = Runnable {
            if (mouseModeEnabled && state == PENDING && !moved) {
                longPressFired = true
                state = CONSUMED
                sendClick(2)
            }
        }
        private val releaseLeftButton = Runnable {
            if (leftButtonDown) {
                leftButtonDown = false
                sendButton(1, MotionEvent.ACTION_UP)
            }
        }
        private val flushPending = Runnable {
            flushPosted = false
            flushNow()
        }

        init {
            setWillNotDraw(false)
            isClickable = true
        }

        fun setMouseModeEnabled(enabled: Boolean) {
            if (mouseModeEnabled == enabled) return
            cancelGesture()
            mouseModeEnabled = enabled
            if (enabled && width > 0 && height > 0) sendMove(cursorX * width, cursorY * height)
            invalidate()
        }

        fun cancelGesture() {
            removeCallbacks(longPress)
            removeCallbacks(flushPending)
            removeCallbacks(releaseLeftButton)
            flushPosted = false
            pendingWheelX = 0f
            pendingWheelY = 0f
            pendingCursorMove = false
            state = IDLE
            firstPointerId = -1
            secondPointerId = -1
            moved = false
            longPressFired = false
            if (leftButtonDown) {
                leftButtonDown = false
                sendButton(1, MotionEvent.ACTION_UP)
            }
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return
            if (previousWidth > 0 && previousHeight > 0 && (w != previousWidth || h != previousHeight)) {
                // Match MVMZ: keep the pointer at the center after a viewport resize/rotation.
                cursorX = 0.5f
                cursorY = 0.5f
                cancelGesture()
                if (mouseModeEnabled) sendMove(cursorX * w, cursorY * h)
            }
            previousWidth = w
            previousHeight = h
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!mouseModeEnabled) return
            val x = cursorX * width
            val y = cursorY * height
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
                    if (firstPointerId != -1) return false
                    firstPointerId = event.getPointerId(event.actionIndex)
                    firstX = event.getX(event.actionIndex)
                    firstY = event.getY(event.actionIndex)
                    startX = firstX
                    startY = firstY
                    lastCenterX = firstX
                    lastCenterY = firstY
                    moved = false
                    longPressFired = false
                    downAt = System.currentTimeMillis()
                    state = PENDING
                    parent?.requestDisallowInterceptTouchEvent(true)
                    postDelayed(longPress, LONG_PRESS_MS)
                    return true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if ((state == PENDING || state == DRAG) && secondPointerId == -1 && event.pointerCount >= 2) {
                        removeCallbacks(longPress)
                        secondPointerId = event.getPointerId(event.actionIndex)
                        updatePointers(event)
                        lastCenterX = (firstX + secondX) / 2f
                        lastCenterY = (firstY + secondY) / 2f
                        twoFingerAt = System.currentTimeMillis()
                        state = WHEEL
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    updatePointers(event)
                    when (state) {
                        PENDING -> {
                            if (distance(firstX, firstY, startX, startY) > touchSlop) {
                                moved = true
                                removeCallbacks(longPress)
                                state = DRAG
                            }
                        }
                        DRAG -> {
                            cursorX = (cursorX + (firstX - lastCenterX) * CURSOR_SENSITIVITY / width.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                            cursorY = (cursorY + (firstY - lastCenterY) * CURSOR_SENSITIVITY / height.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                            queueMove()
                            invalidate()
                        }
                        WHEEL -> {
                            val centerX = (firstX + secondX) / 2f
                            val centerY = (firstY + secondY) / 2f
                            val dy = centerY - lastCenterY
                            if (distance(centerX, centerY, lastCenterX, lastCenterY) > touchSlop) moved = true
                            pendingWheelX += 0f
                            pendingWheelY += -dy * WHEEL_SENSITIVITY / density
                            scheduleFlush()
                        }
                    }
                    if (state == WHEEL) {
                        lastCenterX = (firstX + secondX) / 2f
                        lastCenterY = (firstY + secondY) / 2f
                    } else {
                        lastCenterX = firstX
                        lastCenterY = firstY
                    }
                    return true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (state == WHEEL) {
                        flushNow()
                        if (!moved && System.currentTimeMillis() - twoFingerAt < TWO_FINGER_TAP_MS) sendClick(2)
                        state = COOLDOWN
                        removeCallbacks(longPress)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPress)
                    if (state == PENDING && !longPressFired) {
                        val heldFor = System.currentTimeMillis() - downAt
                        if (heldFor < LONG_PRESS_MS) sendClick(1)
                        else sendClick(2)
                    } else if (state == DRAG) {
                        flushNow()
                    }
                    clearPointerState()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    cancelGesture()
                    return true
                }
            }
            return true
        }

        private fun updatePointers(event: MotionEvent) {
            val firstIndex = event.findPointerIndex(firstPointerId)
            if (firstIndex >= 0) {
                firstX = event.getX(firstIndex)
                firstY = event.getY(firstIndex)
            }
            val secondIndex = event.findPointerIndex(secondPointerId)
            if (secondIndex >= 0) {
                secondX = event.getX(secondIndex)
                secondY = event.getY(secondIndex)
            }
        }

        private fun clearPointerState() {
            removeCallbacks(longPress)
            state = IDLE
            firstPointerId = -1
            secondPointerId = -1
            moved = false
            longPressFired = false
        }

        private fun queueMove() {
            if (width <= 0 || height <= 0) return
            pendingCursorX = cursorX * width
            pendingCursorY = cursorY * height
            pendingCursorMove = true
            scheduleFlush()
        }

        private fun sendMove(x: Float, y: Float) {
            SDLActivity.onNativeMouse(0, MotionEvent.ACTION_HOVER_MOVE, x, y, false)
        }

        private fun sendClick(button: Int) {
            if (width <= 0 || height <= 0) return
            val x = cursorX * width
            val y = cursorY * height
            SDLActivity.onNativeMouse(button, MotionEvent.ACTION_DOWN, x, y, false)
            if (button == 1) {
                removeCallbacks(releaseLeftButton)
                leftButtonDown = true
                postDelayed(releaseLeftButton, LEFT_CLICK_HOLD_MS)
            } else {
                SDLActivity.onNativeMouse(button, MotionEvent.ACTION_UP, x, y, false)
            }
        }

        private fun sendButton(button: Int, action: Int) {
            if (width <= 0 || height <= 0) return
            SDLActivity.onNativeMouse(button, action, cursorX * width, cursorY * height, false)
        }

        private fun scheduleFlush() {
            if (flushPosted) return
            flushPosted = true
            postDelayed(flushPending, FLUSH_INTERVAL_MS)
        }

        private fun flushNow() {
            if (flushPosted) {
                removeCallbacks(flushPending)
                flushPosted = false
            }
            if (pendingCursorMove) {
                sendMove(pendingCursorX, pendingCursorY)
                pendingCursorMove = false
            }
            if (pendingWheelX != 0f || pendingWheelY != 0f) {
                SDLActivity.onNativeMouse(
                    0,
                    MotionEvent.ACTION_SCROLL,
                    pendingWheelX,
                    pendingWheelY,
                    false,
                )
                pendingWheelX = 0f
                pendingWheelY = 0f
            }
        }

        private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
            hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()

        override fun onDetachedFromWindow() {
            cancelGesture()
            super.onDetachedFromWindow()
        }
    }
}
