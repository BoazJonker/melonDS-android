package com.boazjonker.unbound.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
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
 * The on-screen GBA controls. A and B are purple caps inside a 3D gold ring that grows outward when pressed; the D-pad is a thick 3D cross that tips
 * toward the pressed direction like a real rocker pad. L, R, Start and Select are 3D buttons that sink when pressed. Several fingers can press buttons at
 * once, and a finger can slide from one button to another. The fast-forward button only shows when it is turned on in the options.
 */
class ControlsView(context: Context) : View(context) {

    interface Listener {
        fun onKeysChanged(keys: Int)
        fun onFastForwardToggled()
    }

    private class Control(val button: Button3D?, val key: Int, view: View) {
        var cx = 0f
        var cy = 0f
        val press = PressState { view.invalidate() }

        fun contains(x: Float, y: Float) = button?.contains(x - cx, y - cy) ?: false
    }

    private class RoundKey(val painter: AbButtonPainter, val key: Int, view: View) {
        var cx = 0f
        var cy = 0f
        val press = PressState { view.invalidate() }

        fun contains(x: Float, y: Float): Boolean {
            val r = painter.size / 2f * 1.15f
            return hypot(x - cx, y - cy) <= r
        }
    }

    var listener: Listener? = null

    var isHapticsEnabled = true

    var controlsAlpha = 1f
        set(value) {
            field = value
            alpha = value
        }

    var isFastForwardShown = false
        set(value) {
            field = value
            if (width > 0) layoutControls(width.toFloat(), height.toFloat())
            invalidate()
        }

    var isFastForwardOn = false
        set(value) {
            field = value
            fastForward.button?.isLit = value
            invalidate()
        }

    private val dpad = DpadPainter()
    private var dpadX = 0f
    private var dpadY = 0f
    private val a = RoundKey(AbButtonPainter("A"), GbaKeys.A, this)
    private val b = RoundKey(AbButtonPainter("B"), GbaKeys.B, this)
    private val l = Control(Button3D(Button3D.Kind.SHOULDER, "L"), GbaKeys.L, this)
    private val r = Control(Button3D(Button3D.Kind.SHOULDER, "R"), GbaKeys.R, this)
    private val start = Control(Button3D(Button3D.Kind.PILL, "START"), GbaKeys.START, this)
    private val select = Control(Button3D(Button3D.Kind.PILL, "SELECT"), GbaKeys.SELECT, this)
    private val fastForward = Control(Button3D(Button3D.Kind.ROUND).apply { icon = Button3D.Icon.FAST_FORWARD }, 0, this)

    private val pillControls = listOf(l, r, start, select)
    private val roundKeys = listOf(a, b)

    // What each finger is pressing: keys (buttons and D-pad directions), or the fast-forward button
    private val pointerKeys = HashMap<Int, Int>()
    private val pointerActions = HashMap<Int, Control>()
    private var currentKeys = 0

    // D-pad tilt, in degrees, and how far it dips into its socket
    private var tiltX = 0f
    private var tiltY = 0f
    private var sink = 0f
    private var tiltAnimator: ValueAnimator? = null
    private var dpadDirections = 0

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
        // Sizes follow the width, but shrink when the space under the screen is short; everything is spread over that space
        val s = min(w, areaHeight * 0.67f)
        val left = (w - s) / 2f
        fun x(fraction: Float) = left + s * fraction
        fun y(fraction: Float) = top + areaHeight * fraction

        place(l, s * 0.31f, s * 0.1f, x(0.215f), y(0.1f))
        place(r, s * 0.31f, s * 0.1f, x(0.785f), y(0.1f))
        place(fastForward, s * 0.1f, 0f, x(0.5f), y(0.1f))

        dpad.layout(s * 0.385f)
        dpadX = x(0.26f)
        dpadY = y(0.43f)
        placeRound(a, s * 0.21f, x(0.84f), y(0.37f))
        placeRound(b, s * 0.21f, x(0.626f), y(0.53f))

        place(select, s * 0.21f, s * 0.082f, x(0.372f), y(0.76f))
        place(start, s * 0.21f, s * 0.082f, x(0.628f), y(0.76f))
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

        dpad.layout(s * 0.7f)
        dpadX = leftX
        dpadY = h * 0.52f
        placeRound(a, s * 0.38f, rightX + s * 0.22f, h * 0.46f)
        placeRound(b, s * 0.38f, rightX - s * 0.2f, h * 0.62f)

        val bottomY = h * 0.88f
        place(select, s * 0.4f, s * 0.15f, leftX, bottomY)
        place(start, s * 0.4f, s * 0.15f, rightX + s * 0.1f, bottomY)
        place(fastForward, s * 0.2f, 0f, rightX - s * 0.3f, bottomY)
    }

    private fun place(control: Control, width: Float, height: Float, x: Float, y: Float) {
        control.button?.layout(width, height)
        control.cx = x
        control.cy = y
    }

    private fun placeRound(key: RoundKey, size: Float, x: Float, y: Float) {
        key.painter.layout(size)
        key.cx = x
        key.cy = y
    }

    override fun onDraw(canvas: Canvas) {
        for (control in listOf(l, r)) {
            control.button?.draw(canvas, control.cx, control.cy, control.press.press, control.press.ripple)
        }
        if (isFastForwardShown) {
            fastForward.button?.draw(canvas, fastForward.cx, fastForward.cy, fastForward.press.press, fastForward.press.ripple)
        }
        dpad.draw(canvas, dpadX, dpadY, tiltX, tiltY, sink, dpadDirections)
        for (key in roundKeys) {
            key.painter.draw(canvas, key.cx, key.cy, key.press.press, key.press.ripple)
        }
        for (control in listOf(select, start)) {
            control.button?.draw(canvas, control.cx, control.cy, control.press.press, control.press.ripple)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                if (isFastForwardShown && fastForward.contains(x, y)) {
                    pointerActions[id] = fastForward
                    fastForward.press.setPressed(true)
                    haptic()
                    listener?.onFastForwardToggled()
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
                val id = event.getPointerId(event.actionIndex)
                pointerKeys.remove(id)
                pointerActions.remove(id)?.press?.setPressed(false)
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
        val dx = x - dpadX
        val dy = y - dpadY
        val dpadRadius = dpad.size / 2f
        if (hypot(dx, dy) <= dpadRadius * 1.3f) {
            return dpadKeys(dx, dy, dpadRadius)
        }
        val round = roundKeys.filter { it.contains(x, y) }.minByOrNull { hypot(x - it.cx, y - it.cy) }
        if (round != null) return round.key
        return pillControls
            .filter { it.contains(x, y) }
            .minByOrNull { hypot(x - it.cx, y - it.cy) }
            ?.key ?: 0
    }

    private fun dpadKeys(dx: Float, dy: Float, radius: Float): Int {
        if (hypot(dx, dy) < radius * 0.15f) return 0
        // 8 directions: each diagonal covers 45 degrees around it
        val angle = Math.toDegrees(atan2(-dy, dx).toDouble()).let { if (it < 0) it + 360 else it }
        return when (((angle + 22.5) / 45).toInt() % 8) {
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

        pillControls.forEach { it.press.setPressed(keys and it.key != 0) }
        roundKeys.forEach { it.press.setPressed(keys and it.key != 0) }
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
        dpadDirections = directions

        var targetX = 0f
        var targetY = 0f
        if (directions and 1 != 0) targetX += DPAD_TILT
        if (directions and 2 != 0) targetX -= DPAD_TILT
        if (directions and 4 != 0) targetY -= DPAD_TILT
        if (directions and 8 != 0) targetY += DPAD_TILT

        val startX = tiltX
        val startY = tiltY
        val startSink = sink
        val pressing = directions != 0
        val targetSink = if (pressing) 1f else 0f
        tiltAnimator?.cancel()
        tiltAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (pressing) PressState.PRESS_DURATION_MS else PressState.RELEASE_DURATION_MS
            interpolator = if (pressing) DecelerateInterpolator() else OvershootInterpolator(3f)
            addUpdateListener {
                val t = it.animatedValue as Float
                tiltX = startX + (targetX - startX) * t
                tiltY = startY + (targetY - startY) * t
                sink = startSink + (targetSink - startSink) * t
                invalidate()
            }
            start()
        }
    }

    /**
     * The controls pop in with a small bounce, when the game appears.
     */
    fun popIn() {
        scaleX = 0.85f
        scaleY = 0.85f
        alpha = 0f
        animate().scaleX(1f).scaleY(1f).alpha(controlsAlpha).setInterpolator(OvershootInterpolator(2.2f)).setDuration(420).start()
    }

    private fun haptic() {
        if (isHapticsEnabled) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        tiltAnimator?.cancel()
        (pillControls + listOf(fastForward)).forEach { it.press.cancel() }
        roundKeys.forEach { it.press.cancel() }
    }

    private companion object {
        const val DPAD_TILT = 16f
    }
}
