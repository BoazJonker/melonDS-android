package com.boazjonker.unbound.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.boazjonker.unbound.emu.GbaKeys
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * The on-screen GBA controls. Every button is a 3D button that sinks when pressed, sends out a gold ring and springs back when released; the D-pad tilts
 * towards the pressed direction like a rocker. Several fingers can press buttons at once, and a finger can slide from one button to another.
 */
class ControlsView(context: Context) : View(context) {

    interface Listener {
        fun onKeysChanged(keys: Int)
        fun onMenu()
        fun onFastForwardToggled()
    }

    private class Control(val button: Button3D, val key: Int, val view: View) {
        var cx = 0f
        var cy = 0f
        val press = PressState { view.invalidate() }

        fun contains(x: Float, y: Float) = button.contains(x - cx, y - cy)
    }

    var listener: Listener? = null

    var isHapticsEnabled = true

    var controlsAlpha = 1f
        set(value) {
            field = value
            alpha = value
        }

    var isFastForwardOn = false
        set(value) {
            field = value
            fastForward.button.isLit = value
            invalidate()
        }

    private val dpad = Control(Button3D(Button3D.Kind.DPAD), 0, this)
    private val a = Control(Button3D(Button3D.Kind.POKE_BALL, "A"), GbaKeys.A, this)
    private val b = Control(Button3D(Button3D.Kind.GREAT_BALL, "B"), GbaKeys.B, this)
    private val l = Control(Button3D(Button3D.Kind.SHOULDER, "L"), GbaKeys.L, this)
    private val r = Control(Button3D(Button3D.Kind.SHOULDER, "R"), GbaKeys.R, this)
    private val start = Control(Button3D(Button3D.Kind.PILL, "START"), GbaKeys.START, this)
    private val select = Control(Button3D(Button3D.Kind.PILL, "SELECT"), GbaKeys.SELECT, this)
    private val menu = Control(Button3D(Button3D.Kind.ROUND).apply { icon = Button3D.Icon.MENU }, 0, this)
    private val fastForward = Control(Button3D(Button3D.Kind.ROUND).apply { icon = Button3D.Icon.FAST_FORWARD }, 0, this)

    private val keyControls = listOf(a, b, l, r, start, select)
    private val drawOrder = listOf(l, r, menu, fastForward, dpad, b, a, select, start)

    // What each finger is pressing: a key control, the D-pad (with its directions) or an action button
    private val pointerKeys = HashMap<Int, Int>()
    private val pointerActions = HashMap<Int, Control>()
    private var currentKeys = 0

    // D-pad tilt, in degrees
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltAnimator: ValueAnimator? = null
    private var dpadDirections = 0
    private val camera = Camera()
    private val tiltMatrix = Matrix()

    private val gameRect = RectF()

    /**
     * Places the controls around the game screen.
     */
    fun setGameRect(rect: RectF) {
        gameRect.set(rect)
        if (width > 0) layoutControls(width.toFloat(), height.toFloat())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutControls(w.toFloat(), h.toFloat())
    }

    private fun layoutControls(w: Float, h: Float) {
        if (h >= w) layoutPortrait(w, h) else layoutLandscape(w, h)
    }

    private fun layoutPortrait(w: Float, h: Float) {
        val top = gameRect.bottom
        val areaHeight = h - top
        // Scale everything to the space below the screen
        val s = min(w, areaHeight * 1.45f)
        val cx = w / 2f
        val margin = s * 0.05f

        place(l, s * 0.3f, s * 0.1f, margin + s * 0.15f, top + margin + s * 0.06f)
        place(r, s * 0.3f, s * 0.1f, w - margin - s * 0.15f, top + margin + s * 0.06f)
        place(menu, s * 0.1f, 0f, cx - s * 0.08f, top + margin + s * 0.06f)
        place(fastForward, s * 0.1f, 0f, cx + s * 0.08f, top + margin + s * 0.06f)

        val middle = top + areaHeight * 0.42f
        place(dpad, s * 0.36f, 0f, margin + s * 0.22f, middle)
        place(a, s * 0.19f, 0f, w - margin - s * 0.12f, middle - s * 0.07f)
        place(b, s * 0.19f, 0f, w - margin - s * 0.34f, middle + s * 0.08f)

        val bottomY = min(h - margin - s * 0.05f, middle + maxOf(s * 0.28f, areaHeight * 0.3f))
        place(select, s * 0.19f, s * 0.075f, cx - s * 0.12f, bottomY)
        place(start, s * 0.19f, s * 0.075f, cx + s * 0.12f, bottomY)
    }

    private fun layoutLandscape(w: Float, h: Float) {
        val leftWidth = gameRect.left
        val rightWidth = w - gameRect.right
        val column = min(leftWidth, rightWidth)
        val s = min(column * 1.05f, h * 0.55f)
        val leftX = leftWidth / 2f
        val rightX = w - rightWidth / 2f

        place(l, s * 0.62f, s * 0.2f, leftX, h * 0.12f)
        place(r, s * 0.62f, s * 0.2f, rightX, h * 0.12f)

        place(dpad, s * 0.72f, 0f, leftX, h * 0.52f)
        place(a, s * 0.38f, 0f, rightX + s * 0.22f, h * 0.47f)
        place(b, s * 0.38f, 0f, rightX - s * 0.2f, h * 0.6f)

        val bottomY = h * 0.88f
        place(select, s * 0.4f, s * 0.15f, leftX - s * 0.1f, bottomY)
        place(menu, s * 0.2f, 0f, leftX + s * 0.3f, bottomY)
        place(start, s * 0.4f, s * 0.15f, rightX + s * 0.1f, bottomY)
        place(fastForward, s * 0.2f, 0f, rightX - s * 0.3f, bottomY)
    }

    private fun place(control: Control, width: Float, height: Float, x: Float, y: Float) {
        control.button.layout(width, height)
        control.cx = x
        control.cy = y
    }

    override fun onDraw(canvas: Canvas) {
        for (control in drawOrder) {
            if (control === dpad) {
                drawDpad(canvas)
            } else {
                control.button.draw(canvas, control.cx, control.cy, control.press.press, control.press.ripple)
            }
        }
    }

    private fun drawDpad(canvas: Canvas) {
        canvas.save()
        camera.save()
        // Keep the perspective subtle: the camera sits a few D-pad widths away (its location is in inches of 72 pixels)
        camera.setLocation(0f, 0f, -dpad.button.width * 4f / 72f)
        // Positive X rotation pushes the top edge away, negative Y rotation pushes the right edge away
        camera.rotateX(tiltX)
        camera.rotateY(tiltY)
        camera.getMatrix(tiltMatrix)
        camera.restore()
        tiltMatrix.preTranslate(-dpad.cx, -dpad.cy)
        tiltMatrix.postTranslate(dpad.cx, dpad.cy)
        canvas.concat(tiltMatrix)
        dpad.button.draw(canvas, dpad.cx, dpad.cy, if (dpadDirections != 0) 0.5f else 0f, dpad.press.ripple, dpadDirections)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                val action = listOf(menu, fastForward).firstOrNull { it.contains(x, y) }
                if (action != null) {
                    pointerActions[id] = action
                    action.press.setPressed(true)
                    haptic()
                    if (action === fastForward) listener?.onFastForwardToggled()
                } else {
                    pointerKeys[id] = keysAt(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    if (id in pointerKeys) {
                        pointerKeys[id] = keysAt(event.getX(i), event.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                pointerKeys.remove(id)
                pointerActions.remove(id)?.let { action ->
                    action.press.setPressed(false)
                    if (action === menu && action.contains(event.getX(index), event.getY(index))) {
                        listener?.onMenu()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerKeys.clear()
                pointerActions.values.forEach { it.press.setPressed(false) }
                pointerActions.clear()
            }
        }
        updateKeys()
        return true
    }

    /**
     * Releases every button, for example when the game is paused.
     */
    fun releaseAll() {
        pointerKeys.clear()
        pointerActions.values.forEach { it.press.setPressed(false) }
        pointerActions.clear()
        updateKeys()
    }

    private fun keysAt(x: Float, y: Float): Int {
        val dx = x - dpad.cx
        val dy = y - dpad.cy
        val dpadRadius = dpad.button.width / 2f
        if (hypot(dx, dy) <= dpadRadius * 1.3f) {
            return dpadKeys(dx, dy, dpadRadius)
        }
        // The nearest button that is under the finger
        return keyControls
            .filter { it.contains(x, y) }
            .minByOrNull { hypot(x - it.cx, y - it.cy) }
            ?.key ?: 0
    }

    private fun dpadKeys(dx: Float, dy: Float, radius: Float): Int {
        if (hypot(dx, dy) < radius * 0.15f) return 0
        // 8 directions: each diagonal covers 45 degrees around it
        val angle = Math.toDegrees(atan2(-dy, dx).toDouble()).let { if (it < 0) it + 360 else it }
        val sector = ((angle + 22.5) / 45).toInt() % 8
        return when (sector) {
            0 -> GbaKeys.RIGHT
            1 -> GbaKeys.RIGHT or GbaKeys.UP
            2 -> GbaKeys.UP
            3 -> GbaKeys.UP or GbaKeys.LEFT
            4 -> GbaKeys.LEFT
            5 -> GbaKeys.LEFT or GbaKeys.DOWN
            6 -> GbaKeys.DOWN
            else -> GbaKeys.DOWN or GbaKeys.RIGHT
        }
    }

    private fun updateKeys() {
        var keys = 0
        pointerKeys.values.forEach { keys = keys or it }
        if (keys == currentKeys) return

        val newlyPressed = keys and currentKeys.inv()
        currentKeys = keys
        if (newlyPressed != 0) haptic()

        keyControls.forEach { it.press.setPressed(keys and it.key != 0) }
        updateDpad(keys)
        listener?.onKeysChanged(keys)
        invalidate()
    }

    private fun updateDpad(keys: Int) {
        var directions = 0
        if (keys and GbaKeys.UP != 0) directions = directions or 1
        if (keys and GbaKeys.DOWN != 0) directions = directions or 2
        if (keys and GbaKeys.LEFT != 0) directions = directions or 4
        if (keys and GbaKeys.RIGHT != 0) directions = directions or 8
        if (directions == dpadDirections) return
        val wasPressed = dpadDirections != 0
        dpadDirections = directions
        dpad.press.setPressed(directions != 0)
        if (directions != 0 && wasPressed) {
            // Sliding between directions: a new ring for the new direction
            dpad.press.setPressed(false)
            dpad.press.setPressed(true)
        }

        var targetX = 0f
        var targetY = 0f
        if (directions and 1 != 0) targetX += DPAD_TILT
        if (directions and 2 != 0) targetX -= DPAD_TILT
        if (directions and 4 != 0) targetY += DPAD_TILT
        if (directions and 8 != 0) targetY -= DPAD_TILT

        val startX = tiltX
        val startY = tiltY
        val pressing = directions != 0
        tiltAnimator?.cancel()
        tiltAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (pressing) PressState.PRESS_DURATION_MS else PressState.RELEASE_DURATION_MS
            interpolator = if (pressing) DecelerateInterpolator() else OvershootInterpolator(3f)
            addUpdateListener {
                val t = it.animatedValue as Float
                tiltX = startX + (targetX - startX) * t
                tiltY = startY + (targetY - startY) * t
                invalidate()
            }
            start()
        }
    }

    private fun haptic() {
        if (isHapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tiltAnimator?.cancel()
        (keyControls + listOf(dpad, menu, fastForward)).forEach { it.press.cancel() }
    }

    private companion object {
        const val DPAD_TILT = 12f
    }
}
