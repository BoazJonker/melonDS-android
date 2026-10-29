package me.magnum.melonds.ui.emulator

import android.os.SystemClock

/**
 * Guesses when the game is showing a cutscene, based on how much each DS screen changes over time. A cutscene is assumed when the touch screen stays still
 * while the top screen keeps animating, and to have ended when the touch screen keeps changing again.
 *
 * Menus can look similar (an animated top screen above a still menu, like the save file menu), so the detector is careful not to swap back and forth:
 * - The touch screen must stay completely still for a while before a cutscene is assumed, so a blinking cursor in a menu prevents it.
 * - After a cutscene ends, a new one isn't detected for a while.
 * - When the player touches the touch screen, they're using a menu or playing, so no cutscene is detected for a while.
 * - If cutscenes keep being detected and ended in a short time, detection pauses for a few minutes.
 *
 * Must be used from a single thread.
 */
class CutsceneDetector(private val onCutsceneChanged: (isCutscene: Boolean) -> Unit) {

    private companion object {
        // Average per channel change (0-255) below which a screen is considered still, and above which it is considered animating
        const val STILL_THRESHOLD = 0.6f
        const val MOVING_THRESHOLD = 2.5f

        // How long the touch screen must stay still before a cutscene is assumed
        const val ENTER_DELAY_MS = 2000L
        // Fraction of samples in that time in which the top screen must be animating
        const val TOP_MOVING_FRACTION = 0.4f

        // A cutscene ends when the touch screen animates in a few of the recent samples (about 8 samples per second). Real cutscenes keep it completely
        // still, so any repeated change means gameplay or a menu
        const val EXIT_WINDOW_SAMPLES = 8
        const val EXIT_MOVING_SAMPLES = 3

        // No new cutscene is detected for this long after one ends, or after the player touches the touch screen
        const val COOLDOWN_AFTER_CUTSCENE_MS = 10_000L
        const val COOLDOWN_AFTER_TOUCH_MS = 8_000L

        // Detecting this many cutscenes within the time window means the detector is being fooled (for example by a menu), so it pauses for a while
        const val FLIP_LIMIT = 2
        const val FLIP_WINDOW_MS = 60_000L
        const val FLIP_PAUSE_MS = 180_000L
    }

    var isCutscene = false
        private set

    private var stillSince = 0L
    private var stillSamples = 0
    private var topMovingSamples = 0
    private val recentBottomMoving = ArrayDeque<Boolean>()
    // Set when the player manually swaps screens. Detection resumes once the touch screen is animating again
    private var isSuppressed = false
    private var blockedUntil = 0L
    private val recentCutsceneStarts = ArrayDeque<Long>()

    fun onScreenActivity(topScreenChange: Float, bottomScreenChange: Float) {
        val now = SystemClock.uptimeMillis()

        if (!isCutscene) {
            if (bottomScreenChange < STILL_THRESHOLD) {
                if (stillSamples == 0) {
                    stillSince = now
                }
                stillSamples++
                if (topScreenChange > MOVING_THRESHOLD) {
                    topMovingSamples++
                }
            } else {
                resetStillTracking()
                if (bottomScreenChange > MOVING_THRESHOLD) {
                    isSuppressed = false
                }
            }

            val hasBeenStillLongEnough = stillSamples > 0 && now - stillSince >= ENTER_DELAY_MS
            val isBlocked = isSuppressed || now < blockedUntil
            if (!isBlocked && hasBeenStillLongEnough && topMovingSamples >= stillSamples * TOP_MOVING_FRACTION) {
                startCutscene(now)
            }
        } else {
            recentBottomMoving.addLast(bottomScreenChange > MOVING_THRESHOLD)
            if (recentBottomMoving.size > EXIT_WINDOW_SAMPLES) {
                recentBottomMoving.removeFirst()
            }
            if (recentBottomMoving.count { it } >= EXIT_MOVING_SAMPLES) {
                endCutscene(now)
            }
        }
    }

    /**
     * Called when the player touches the touch screen while it isn't showing a cutscene. They're using a menu or playing, so no cutscene is detected for a
     * while.
     */
    fun onPlayerTouchedScreen() {
        if (!isCutscene) {
            blockedUntil = maxOf(blockedUntil, SystemClock.uptimeMillis() + COOLDOWN_AFTER_TOUCH_MS)
            resetStillTracking()
        }
    }

    /**
     * Stops treating the current scene as a cutscene, and doesn't detect a new one until the touch screen has animated again. Used when the player swaps the
     * screens manually.
     */
    fun suppressUntilGameplay() {
        isCutscene = false
        isSuppressed = true
        recentBottomMoving.clear()
        resetStillTracking()
    }

    private fun startCutscene(now: Long) {
        recentCutsceneStarts.addLast(now)
        while (recentCutsceneStarts.isNotEmpty() && now - recentCutsceneStarts.first() > FLIP_WINDOW_MS) {
            recentCutsceneStarts.removeFirst()
        }
        if (recentCutsceneStarts.size >= FLIP_LIMIT) {
            // Being fooled: pause instead of swapping again
            recentCutsceneStarts.clear()
            blockedUntil = now + FLIP_PAUSE_MS
            resetStillTracking()
            return
        }

        isCutscene = true
        recentBottomMoving.clear()
        onCutsceneChanged(true)
    }

    private fun endCutscene(now: Long) {
        isCutscene = false
        recentBottomMoving.clear()
        resetStillTracking()
        blockedUntil = maxOf(blockedUntil, now + COOLDOWN_AFTER_CUTSCENE_MS)
        onCutsceneChanged(false)
    }

    private fun resetStillTracking() {
        stillSamples = 0
        topMovingSamples = 0
        stillSince = 0L
    }
}
