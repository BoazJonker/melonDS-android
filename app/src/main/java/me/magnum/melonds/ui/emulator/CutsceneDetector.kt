package me.magnum.melonds.ui.emulator

import android.os.SystemClock

/**
 * Guesses when the game is showing a cutscene, based on how much each DS screen changes over time. A cutscene is assumed when the touch screen stays still
 * while the top screen keeps animating, and to have ended as soon as the touch screen starts changing again.
 *
 * Must be used from a single thread.
 */
class CutsceneDetector(private val onCutsceneChanged: (isCutscene: Boolean) -> Unit) {

    private companion object {
        // Average per channel change (0-255) below which a screen is considered still, and above which it is considered animating
        const val STILL_THRESHOLD = 0.6f
        const val MOVING_THRESHOLD = 2.5f

        // How long the touch screen must stay still before a cutscene is assumed
        const val ENTER_DELAY_MS = 1000L
        // Fraction of samples in that time in which the top screen must be animating
        const val TOP_MOVING_FRACTION = 0.4f
        // Consecutive samples with the touch screen animating needed to end a cutscene
        const val EXIT_SAMPLES = 2
    }

    var isCutscene = false
        private set

    private var stillSince = 0L
    private var stillSamples = 0
    private var topMovingSamples = 0
    private var bottomMovingSamples = 0
    // Set when the player manually swaps screens. Detection resumes once the touch screen is animating again
    private var isSuppressed = false

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
            if (!isSuppressed && hasBeenStillLongEnough && topMovingSamples >= stillSamples * TOP_MOVING_FRACTION) {
                isCutscene = true
                bottomMovingSamples = 0
                onCutsceneChanged(true)
            }
        } else {
            if (bottomScreenChange > MOVING_THRESHOLD) {
                bottomMovingSamples++
                if (bottomMovingSamples >= EXIT_SAMPLES) {
                    isCutscene = false
                    resetStillTracking()
                    onCutsceneChanged(false)
                }
            } else {
                bottomMovingSamples = 0
            }
        }
    }

    /**
     * Stops treating the current scene as a cutscene, and doesn't detect a new one until the touch screen has animated again. Used when the player swaps the
     * screens manually.
     */
    fun suppressUntilGameplay() {
        isCutscene = false
        isSuppressed = true
        resetStillTracking()
    }

    private fun resetStillTracking() {
        stillSamples = 0
        topMovingSamples = 0
        stillSince = 0L
    }
}
