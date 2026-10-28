package me.magnum.melonds.ui.emulator.input

import android.os.Handler
import android.os.Looper
import androidx.core.os.HandlerCompat
import me.magnum.melonds.MelonEmulator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.Point
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Translates the stick and action buttons into stylus input on the DS touch screen, for games like The Legend of Zelda: Spirit Tracks that are controlled
 * entirely with the stylus. Link is assumed to be near the centre of the touch screen, since the camera follows him.
 *
 * - The stick holds the stylus at a distance from Link in the pushed direction, which makes him walk (or run, when pushed further).
 * - The action buttons perform the gestures for a sword slash, a spin attack, a roll and using an item, towards the direction Link last moved in.
 *
 * Real touches on the touch screen always take priority: while the player touches the screen, the stick and gestures are ignored.
 */
class VirtualStylus {

    private companion object {
        const val SCREEN_WIDTH = 256
        const val SCREEN_HEIGHT = 192
        // Where Link usually stands on the touch screen
        const val LINK_X = 128f
        const val LINK_Y = 100f

        const val STICK_DEAD_ZONE = 0.2f
        const val WALK_DISTANCE = 24f
        const val RUN_EXTRA_DISTANCE = 56f

        const val GESTURE_STEP_MS = 16L

        // Each tap of the roll's double tap is held for a few frames, with a short gap in between, so the game sees two separate quick taps
        const val ROLL_TAP_STEPS = 3
        const val ROLL_TAP_GAP_STEPS = 3
    }

    var inputListener: IInputListener? = null

    private val handler = Handler(Looper.getMainLooper())
    private val gestureToken = Any()
    private val point = Point()

    private var isTouching = false
    private var isRealTouchActive = false
    private var isGestureRunning = false

    private var isStickHeld = false
    private var stickX = 0f
    private var stickY = 0f
    // Direction Link last moved in. Facing down (towards the player) by default
    private var facingX = 0f
    private var facingY = 1f

    fun onRealTouchStarted() {
        isRealTouchActive = true
        cancelGesture()
        // The real touch takes over the touch screen input, so it will be released by the real touch handler
        isTouching = false
    }

    fun onRealTouchEnded() {
        isRealTouchActive = false
        isTouching = false
        if (isStickHeld) {
            applyStick()
        }
    }

    /**
     * Updates the stick position. [x] and [y] are in the range -1..1, with y pointing down.
     */
    fun setStick(x: Float, y: Float) {
        val magnitude = hypot(x, y)
        if (magnitude < STICK_DEAD_ZONE) {
            releaseStick()
            return
        }

        isStickHeld = true
        stickX = x
        stickY = y
        facingX = x / magnitude
        facingY = y / magnitude
        applyStick()
    }

    fun releaseStick() {
        isStickHeld = false
        if (!isRealTouchActive && !isGestureRunning) {
            releaseTouch()
        }
    }

    /**
     * Slashes the sword by swiping across the area in front of Link.
     */
    fun swordSlash() {
        val frontX = LINK_X + facingX * 30f
        val frontY = LINK_Y + facingY * 30f
        // Perpendicular to the facing direction
        val perpX = -facingY
        val perpY = facingX
        val points = (0..6).map {
            val t = it / 6f * 2f - 1f
            frontX + perpX * 24f * t to frontY + perpY * 24f * t
        }
        performGesture(points)
    }

    /**
     * Performs a spin attack by drawing a circle around Link.
     */
    fun spinAttack() {
        val startAngle = atan2(facingY, facingX)
        val points = (0..20).map {
            val angle = startAngle + it / 20f * 2f * PI.toFloat()
            LINK_X + cos(angle) * 38f to LINK_Y + sin(angle) * 38f
        }
        performGesture(points)
    }

    /**
     * Rolls by quickly tapping twice in the facing direction, which makes Link roll that way. Both taps are performed with one press of the button.
     */
    fun roll() {
        val targetX = (LINK_X + facingX * 70f).coerceIn(8f, SCREEN_WIDTH - 8f)
        val targetY = (LINK_Y + facingY * 70f).coerceIn(8f, SCREEN_HEIGHT - 8f)
        val tap = List(ROLL_TAP_STEPS) { targetX to targetY }
        performStrokes(listOf(tap, tap), gapSteps = ROLL_TAP_GAP_STEPS)
    }

    /**
     * Uses the equipped item by tapping in front of Link.
     */
    fun useItem() {
        val targetX = LINK_X + facingX * 64f
        val targetY = LINK_Y + facingY * 64f
        performGesture(List(3) { targetX to targetY })
    }

    private fun applyStick() {
        if (isRealTouchActive || isGestureRunning || !isStickHeld) {
            return
        }

        val magnitude = hypot(stickX, stickY).coerceAtMost(1f)
        val distance = WALK_DISTANCE + RUN_EXTRA_DISTANCE * ((magnitude - STICK_DEAD_ZONE) / (1f - STICK_DEAD_ZONE))
        touchAt(LINK_X + facingX * distance, LINK_Y + facingY * distance)
    }

    private fun performGesture(points: List<Pair<Float, Float>>) {
        performStrokes(listOf(points), gapSteps = 0)
    }

    /**
     * Performs one or more stylus strokes, one point per frame, lifting the stylus for [gapSteps] frames between strokes.
     */
    private fun performStrokes(strokes: List<List<Pair<Float, Float>>>, gapSteps: Int) {
        if (isRealTouchActive) {
            return
        }

        cancelGesture()
        isGestureRunning = true
        // Lift the stylus first so that the game sees a new touch
        releaseTouch()

        var step = 1L
        strokes.forEachIndexed { strokeIndex, points ->
            if (strokeIndex > 0) {
                HandlerCompat.postDelayed(handler, { releaseTouch() }, gestureToken, step * GESTURE_STEP_MS)
                step += gapSteps
            }
            points.forEach { (x, y) ->
                HandlerCompat.postDelayed(handler, { touchAt(x, y) }, gestureToken, step * GESTURE_STEP_MS)
                step++
            }
        }
        HandlerCompat.postDelayed(handler, {
            releaseTouch()
            isGestureRunning = false
            // Continue walking if the stick is still held
            HandlerCompat.postDelayed(handler, { applyStick() }, gestureToken, GESTURE_STEP_MS)
        }, gestureToken, (step + 1) * GESTURE_STEP_MS)
    }

    private fun cancelGesture() {
        handler.removeCallbacksAndMessages(gestureToken)
        isGestureRunning = false
    }

    private fun touchAt(x: Float, y: Float) {
        val listener = inputListener ?: return
        point.x = x.toInt().coerceIn(0, SCREEN_WIDTH - 1)
        point.y = y.toInt().coerceIn(0, SCREEN_HEIGHT - 1)
        if (!isTouching) {
            listener.onKeyPress(Input.TOUCHSCREEN)
            isTouching = true
        }
        listener.onTouch(point)
    }

    private fun releaseTouch() {
        if (!isTouching) {
            return
        }
        inputListener?.onKeyReleased(Input.TOUCHSCREEN)
        MelonEmulator.onScreenRelease()
        isTouching = false
    }
}
