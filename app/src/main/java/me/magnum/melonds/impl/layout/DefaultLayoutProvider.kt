package me.magnum.melonds.impl.layout

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.content.getSystemService
import me.magnum.melonds.domain.model.Rect
import me.magnum.melonds.domain.model.SCREEN_HEIGHT
import me.magnum.melonds.domain.model.SCREEN_WIDTH
import me.magnum.melonds.domain.model.consoleAspectRatio
import me.magnum.melonds.domain.model.layout.Insets
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.domain.model.layout.LayoutDisplay
import me.magnum.melonds.domain.model.layout.PositionedLayoutComponent
import me.magnum.melonds.domain.model.layout.ScreenFold
import me.magnum.melonds.domain.model.layout.ScreenLayout
import me.magnum.melonds.domain.model.layout.UILayout
import me.magnum.melonds.domain.model.layout.UILayoutVariant
import me.magnum.melonds.domain.model.ui.Orientation
import me.magnum.melonds.impl.ScreenUnitsConverter
import kotlin.math.min
import kotlin.math.roundToInt

class DefaultLayoutProvider(
    private val context: Context,
    private val screenUnitsConverter: ScreenUnitsConverter,
    private val sharedPreferences: SharedPreferences,
) {

    private enum class ScreenFit {
        ORIGINAL,
        FIT_EQUAL,
        FIT_BOTTOM_FOCUS,
        STRETCH,
        SINGLE_SCREEN,
    }

    private fun getScreenFit(): ScreenFit {
        val value = sharedPreferences.getString("video_screen_fit", "single_screen") ?: "single_screen"
        return ScreenFit.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ScreenFit.SINGLE_SCREEN
    }

    fun buildDefaultLayout(variant: UILayoutVariant): UILayout {
        val width = variant.uiSize.x
        val height = variant.uiSize.y
        val orientation = variant.orientation
        val folds = variant.folds
        val mainDisplay = variant.displays.mainScreenDisplay
        val secondaryDisplay = variant.displays.secondaryScreenDisplay
        val mainDisplayInsets = variant.uiInsets

        val (mainScreenLayout, secondaryScreenLayout) = if (secondaryDisplay != null) {
            val secondaryDisplayInsets = getDisplaySafeInsets(secondaryDisplay)

            // Prioritise scenarios where a secondary display is present. In this scenario, one screen is shown in each display, and they should take the maximum available
            // space, independently of orientation or folds.

            // Check if it's a dual-screen device (both displays are built-in) or it's using an external display
            if (mainDisplay.type == LayoutDisplay.Type.BUILT_IN && secondaryDisplay.type == LayoutDisplay.Type.BUILT_IN) {
                // Assume that the default display is always the top one
                if (mainDisplay.isDefaultDisplay) {
                    val secondaryDisplayOrientation = if (secondaryDisplay.width > secondaryDisplay.height) Orientation.LANDSCAPE else Orientation.PORTRAIT
                    val secondaryLayout = when (secondaryDisplayOrientation) {
                        Orientation.PORTRAIT -> buildDefaultPortraitLayout(secondaryDisplay.width, secondaryDisplay.height, secondaryDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                        Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(secondaryDisplay.width, secondaryDisplay.height, secondaryDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    }
                    buildSingleScreenLayout(width, height, LayoutComponent.TOP_SCREEN) to secondaryLayout
                } else {
                    val mainLayout = when (orientation) {
                        Orientation.PORTRAIT -> buildDefaultPortraitLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                        Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    }
                    mainLayout to buildSingleScreenLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.TOP_SCREEN)
                }
            } else {
                // An external display is being used. Display the bottom screen on the main display, together with all soft input components
                val mainLayout = when (orientation) {
                    Orientation.PORTRAIT -> buildDefaultPortraitLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                    Orientation.LANDSCAPE -> buildDefaultLandscapeLayout(width, height, mainDisplayInsets, LayoutComponent.BOTTOM_SCREEN)
                }
                mainLayout to buildSingleScreenLayout(secondaryDisplay.width, secondaryDisplay.height, LayoutComponent.TOP_SCREEN)
            }
        } else {
            val mainScreenLayout = when (orientation) {
                Orientation.PORTRAIT -> {
                    if (folds.any { it.orientation == Orientation.LANDSCAPE }) {
                        // Flip-phone layout
                        buildDefaultFoldingPortraitLayout(width, height, folds, mainDisplayInsets)
                    } else {
                        // Simple portrait layout. Ignore vertical fold since there's no good way to support it
                        buildDefaultPortraitLayout(width, height, mainDisplayInsets)
                    }
                }
                Orientation.LANDSCAPE -> {
                    if (folds.any { it.orientation == Orientation.PORTRAIT }) {
                        // Book layout
                        buildDefaultFoldingLandscapeLayout(width, height, folds, mainDisplayInsets)
                    } else if (folds.any { it.orientation == Orientation.LANDSCAPE }) {
                        // Flip-phone layout
                        buildDefaultFoldingPortraitLayout(width, height, folds, mainDisplayInsets)
                    } else {
                        // No fold
                        buildDefaultLandscapeLayout(width, height, mainDisplayInsets)
                    }
                }
            }
            mainScreenLayout to ScreenLayout()
        }

        return UILayout(mainScreenLayout, secondaryScreenLayout)
    }

    private fun buildDefaultPortraitLayout(width: Int, height: Int, insets: Insets, singleScreenComponent: LayoutComponent? = null): ScreenLayout {
        require(singleScreenComponent?.isScreen() != false) { "When specifying a single screen component, it must be a screen component" }

        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeWidth = width - safeLeft - safeRight
        val safeHeight = height - safeTop - safeBottom

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var screenWidth = safeWidth
        var screenHeight = (safeWidth / consoleAspectRatio).toInt()

        val isSingleScreenFit = singleScreenComponent == null && getScreenFit() == ScreenFit.SINGLE_SCREEN

        val screenComponents = if (isSingleScreenFit) {
            // The bottom (touch) screen takes the full width. The top screen is shown as a small overlay on its top-right corner
            val bottomScreenView = Rect(safeLeft, safeTop, screenWidth, screenHeight)
            val miniScreenWidth = (screenWidth * 0.35f).roundToInt()
            val miniScreenHeight = (miniScreenWidth / consoleAspectRatio).toInt()
            val topScreenView = Rect(safeLeft + screenWidth - miniScreenWidth, safeTop, miniScreenWidth, miniScreenHeight)

            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN, onTop = true),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else if (singleScreenComponent == null) {
            var screenMargin = 0
            if (getScreenFit() == ScreenFit.STRETCH) {
                // Each screen takes the full width and half of the available height, ignoring the console's aspect ratio
                screenHeight = safeHeight / 2
            } else if (screenHeight * 2 > safeHeight) {
                screenWidth = (safeHeight / 2 * consoleAspectRatio).toInt()
                screenHeight = safeHeight / 2
                screenMargin = (safeWidth - screenWidth) / 2
            }

            val topScreenView = Rect(safeLeft + screenMargin, safeTop, screenWidth, screenHeight)
            val bottomScreenView = Rect(safeLeft + screenMargin, safeTop + screenHeight, screenWidth, screenHeight)

            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else {
            // Align screen to the top of the safe area
            val screenArea = Rect(safeLeft, safeTop, screenWidth, screenHeight)
            arrayOf(PositionedLayoutComponent(screenArea, singleScreenComponent))
        }

        val screensBottom = if (singleScreenComponent == null && !isSingleScreenFit) safeTop + screenHeight * 2 else safeTop + screenHeight
        // Check if there's space to put all the buttons below the screens. If not, place L, R, and utility buttons aligned with the top of the bottom screen
        val utilityButtonsTop = if (screensBottom + lrButtonsSize + largeButtonsSize > height) {
            safeTop + screenHeight
        } else if (isSingleScreenFit) {
            // Leave a small gap for the screen frame
            screensBottom + spacing4dp * 2
        } else {
            screensBottom
        }

        // With the one screen + mini screen layout, the controls sit right below the screen, like on a Game Boy, if there's enough room. Otherwise they're
        // anchored to the bottom of the screen
        // In that placement the stick (or D-pad) and the buttons are larger, and the stick is lowered so that its centre lines up with the large Sword button,
        // which sits slightly below the middle of the buttons area
        val stickSize = screenUnitsConverter.dpToPixels(150f).toInt()
        val buttonsSize = screenUnitsConverter.dpToPixels(170f).toInt()
        val stickOffset = (buttonsSize * 0.58f - stickSize / 2f).toInt()
        val gameBoyControlsTop = utilityButtonsTop + lrButtonsSize + spacing4dp * 2
        val startSelectTop = gameBoyControlsTop + maxOf(buttonsSize, stickOffset + stickSize) + spacing4dp * 3
        val useGameBoyPlacement = isSingleScreenFit &&
                startSelectTop + smallButtonsSize <= height - safeBottom &&
                stickSize + buttonsSize <= safeWidth
        val startSelectY = if (useGameBoyPlacement) startSelectTop else height - safeBottom - smallButtonsSize

        val dpadView: Rect
        val buttonsView: Rect
        if (useGameBoyPlacement) {
            dpadView = Rect(safeLeft, gameBoyControlsTop + stickOffset, stickSize, stickSize)
            buttonsView = Rect(width - safeRight - buttonsSize, gameBoyControlsTop, buttonsSize, buttonsSize)
        } else {
            val controlsTop = height - safeBottom - largeButtonsSize
            dpadView = Rect(safeLeft, controlsTop, largeButtonsSize, largeButtonsSize)
            buttonsView = Rect(width - safeRight - largeButtonsSize, controlsTop, largeButtonsSize, largeButtonsSize)
        }

        return ScreenLayout(
            listOf(
                *screenComponents,
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(safeLeft, utilityButtonsTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - safeRight - lrButtonsSize, utilityButtonsTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - spacing4dp / 2, startSelectY, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp / 2, startSelectY, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize / 2, utilityButtonsTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                // Small swap screens button in the bottom-right corner, level with Start/Select
                PositionedLayoutComponent(Rect(width - safeRight - smallButtonsSize, startSelectY, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SWAP_SCREENS),
            )
        )
    }

    private fun buildDefaultLandscapeLayout(width: Int, height: Int, insets: Insets, singleScreenComponent: LayoutComponent? = null): ScreenLayout {
        require(singleScreenComponent?.isScreen() != false) { "When specifying a single screen component, it must be a screen component" }
        if (singleScreenComponent == null && getScreenFit() == ScreenFit.SINGLE_SCREEN) {
            return buildLandscapeHandheldLayout(width, height, insets)
        }

        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeWidth = width - safeLeft - safeRight
        val safeHeight = height - safeTop - safeBottom

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        val isSingleScreenFit = singleScreenComponent == null && getScreenFit() == ScreenFit.SINGLE_SCREEN

        val screenComponents = if (singleScreenComponent == null) {
            val (topScreenView, bottomScreenView) = buildLandscapeScreenRects(safeLeft, safeTop, safeWidth, safeHeight)

            arrayOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN, onTop = isSingleScreenFit),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
            )
        } else {
            val screenArea = centerScreenIn(safeWidth, safeHeight)
            val offsetScreen = Rect(screenArea.x + safeLeft, screenArea.y + safeTop, screenArea.width, screenArea.height)
            arrayOf(PositionedLayoutComponent(offsetScreen, singleScreenComponent))
        }

        val dpadView = Rect(safeLeft, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - safeRight - largeButtonsSize, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                *screenComponents,
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(safeLeft, safeTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - safeRight - lrButtonsSize, safeTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect((width - spacing4dp) / 2 - smallButtonsSize, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect((width + spacing4dp) / 2, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize / 2, safeTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                // Small swap screens button in the top-right corner, below R
                PositionedLayoutComponent(Rect(width - safeRight - smallButtonsSize, safeTop + lrButtonsSize + spacing4dp, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SWAP_SCREENS),
            )
        )
    }

    /**
     * Landscape layout for the "one screen + mini screen" fit, laid out like a handheld console: the main screen fills the height in the middle, with the
     * controls in the columns on either side so that nothing covers the game. The left column holds L, the Blow button, the stick, and Select and Start. The
     * right column holds the small screen window at the top, the swap button and R below it, and the action buttons.
     */
    private fun buildLandscapeHandheldLayout(width: Int, height: Int, insets: Insets): ScreenLayout {
        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeWidth = width - safeLeft - safeRight
        val safeHeight = height - safeTop - safeBottom

        val margin = screenUnitsConverter.dpToPixels(8f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val minColumnWidth = screenUnitsConverter.dpToPixels(150f).toInt()
        val maxMiniScreenWidth = screenUnitsConverter.dpToPixels(200f).toInt()

        // The main screen fills the height, unless that leaves the side columns too narrow for the controls
        var screenHeight = safeHeight
        var screenWidth = (screenHeight * consoleAspectRatio).toInt()
        if (safeWidth - screenWidth < minColumnWidth * 2) {
            screenWidth = (safeWidth - minColumnWidth * 2).coerceAtLeast(safeWidth / 3)
            screenHeight = (screenWidth / consoleAspectRatio).toInt()
        }
        val screenX = safeLeft + (safeWidth - screenWidth) / 2
        val screenY = safeTop + (safeHeight - screenHeight) / 2
        val columnWidth = (safeWidth - screenWidth) / 2 - margin * 2
        val leftColumnX = safeLeft + margin
        val rightColumnX = screenX + screenWidth + margin
        val bottom = height - safeBottom - margin

        // Right column: small screen window, then the swap button and R, then the action buttons
        val miniScreenWidth = minOf(columnWidth, maxMiniScreenWidth)
        val miniScreenHeight = (miniScreenWidth / consoleAspectRatio).toInt()
        val miniScreenView = Rect(rightColumnX + columnWidth - miniScreenWidth, safeTop + margin, miniScreenWidth, miniScreenHeight)
        val rightRowY = miniScreenView.bottom + margin
        val swapView = Rect(rightColumnX, rightRowY + (lrButtonsSize - smallButtonsSize) / 2, smallButtonsSize, smallButtonsSize)
        val rView = Rect(rightColumnX + columnWidth - lrButtonsSize, rightRowY, lrButtonsSize, lrButtonsSize)
        val buttonsTop = rightRowY + lrButtonsSize + margin
        val buttonsSize = minOf(screenUnitsConverter.dpToPixels(170f).toInt(), columnWidth, bottom - buttonsTop).coerceAtLeast(smallButtonsSize)
        val buttonsView = Rect(rightColumnX + columnWidth - buttonsSize, bottom - buttonsSize, buttonsSize, buttonsSize)

        // Left column: L and the Blow button, then the stick, then Select and Start
        val lView = Rect(leftColumnX, safeTop + margin, lrButtonsSize, lrButtonsSize)
        val blowView = Rect(leftColumnX + columnWidth - smallButtonsSize, safeTop + margin + lrButtonsSize / 2, smallButtonsSize, smallButtonsSize)
        val startSelectY = bottom - smallButtonsSize
        val selectView = Rect(leftColumnX, startSelectY, smallButtonsSize, smallButtonsSize)
        val startView = Rect(leftColumnX + smallButtonsSize + margin * 2, startSelectY, smallButtonsSize, smallButtonsSize)
        val stickAreaTop = blowView.bottom + margin
        val stickAreaBottom = startSelectY - margin
        val stickSize = minOf(screenUnitsConverter.dpToPixels(150f).toInt(), columnWidth, stickAreaBottom - stickAreaTop).coerceAtLeast(smallButtonsSize)
        val stickView = Rect(leftColumnX + (columnWidth - stickSize) / 2, stickAreaTop + (stickAreaBottom - stickAreaTop - stickSize) / 2, stickSize, stickSize)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(miniScreenView, LayoutComponent.TOP_SCREEN, onTop = true),
                PositionedLayoutComponent(Rect(screenX, screenY, screenWidth, screenHeight), LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(stickView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(lView, LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(rView, LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(selectView, LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(startView, LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(blowView, LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
                PositionedLayoutComponent(swapView, LayoutComponent.BUTTON_SWAP_SCREENS),
            )
        )
    }

    /**
     * Calculates the positions of the top and bottom screens for a landscape layout with both screens side-by-side, according to the user's screen fit preference.
     */
    private fun buildLandscapeScreenRects(safeLeft: Int, safeTop: Int, safeWidth: Int, safeHeight: Int): Pair<Rect, Rect> {
        return when (getScreenFit()) {
            ScreenFit.ORIGINAL -> {
                var topScreenWidth = (safeWidth * 0.66f).roundToInt()
                var topScreenHeight = (topScreenWidth / consoleAspectRatio).toInt()
                if (topScreenHeight > safeHeight) {
                    topScreenWidth = (safeHeight * consoleAspectRatio).toInt()
                    topScreenHeight = safeHeight
                }

                val bottomScreenWidth = safeWidth - topScreenWidth
                val bottomScreenHeight = (bottomScreenWidth / consoleAspectRatio).toInt()
                Rect(safeLeft, safeTop, topScreenWidth, topScreenHeight) to Rect(safeLeft + topScreenWidth, safeTop, bottomScreenWidth, bottomScreenHeight)
            }
            ScreenFit.FIT_EQUAL -> {
                // Both screens with the same size, as large as possible, centered in the available area
                var screenWidth = safeWidth / 2
                var screenHeight = (screenWidth / consoleAspectRatio).toInt()
                if (screenHeight > safeHeight) {
                    screenHeight = safeHeight
                    screenWidth = (safeHeight * consoleAspectRatio).toInt()
                }

                val marginX = (safeWidth - screenWidth * 2) / 2
                val marginY = (safeHeight - screenHeight) / 2
                val left = safeLeft + marginX
                val top = safeTop + marginY
                Rect(left, top, screenWidth, screenHeight) to Rect(left + screenWidth, top, screenWidth, screenHeight)
            }
            ScreenFit.FIT_BOTTOM_FOCUS -> {
                // The bottom (touch) screen takes the full height, and the top screen uses the remaining width
                var bottomScreenHeight = safeHeight
                var bottomScreenWidth = (safeHeight * consoleAspectRatio).toInt()
                val maxBottomScreenWidth = (safeWidth * 0.7f).roundToInt()
                if (bottomScreenWidth > maxBottomScreenWidth) {
                    bottomScreenWidth = maxBottomScreenWidth
                    bottomScreenHeight = (bottomScreenWidth / consoleAspectRatio).toInt()
                }

                var topScreenWidth = safeWidth - bottomScreenWidth
                var topScreenHeight = (topScreenWidth / consoleAspectRatio).toInt()
                if (topScreenHeight > safeHeight) {
                    topScreenHeight = safeHeight
                    topScreenWidth = (safeHeight * consoleAspectRatio).toInt()
                }

                val marginX = (safeWidth - topScreenWidth - bottomScreenWidth) / 2
                val left = safeLeft + marginX
                val bottomTop = safeTop + (safeHeight - bottomScreenHeight) / 2
                Rect(left, safeTop, topScreenWidth, topScreenHeight) to Rect(left + topScreenWidth, bottomTop, bottomScreenWidth, bottomScreenHeight)
            }
            ScreenFit.STRETCH -> {
                // Each screen takes half of the available area, ignoring the console's aspect ratio
                val screenWidth = safeWidth / 2
                Rect(safeLeft, safeTop, screenWidth, safeHeight) to Rect(safeLeft + screenWidth, safeTop, safeWidth - screenWidth, safeHeight)
            }
            ScreenFit.SINGLE_SCREEN -> {
                // The bottom (touch) screen is centered and as large as possible. The top screen is shown as a small screen on the top-left corner, using the free space
                // to the left of the main screen when there's enough of it, or overlapping the main screen otherwise
                val centeredScreen = centerScreenIn(safeWidth, safeHeight)
                val bottomScreenView = Rect(safeLeft + centeredScreen.x, safeTop + centeredScreen.y, centeredScreen.width, centeredScreen.height)
                val miniScreenWidth = centeredScreen.x.coerceIn((centeredScreen.width * 0.3f).roundToInt(), (centeredScreen.width * 0.45f).roundToInt())
                val miniScreenHeight = (miniScreenWidth / consoleAspectRatio).toInt()
                Rect(safeLeft, safeTop, miniScreenWidth, miniScreenHeight) to bottomScreenView
            }
        }
    }

    /**
     * Creates a portrait layout that supports a horizontal fold. Screens are attached to either sides of the fold and as large as they can be to fit the screen.
     */
    private fun buildDefaultFoldingPortraitLayout(width: Int, height: Int, folds: List<ScreenFold>, insets: Insets): ScreenLayout {
        // Only one fold is supported for now
        val mainFold = folds.first()

        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeWidth = width - safeLeft - safeRight

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing4dp = screenUnitsConverter.dpToPixels(4f).toInt()

        var screenWidth = safeWidth
        var screenHeight = (safeWidth / consoleAspectRatio).toInt()
        val topHalfHeight = mainFold.foldBounds.y - safeTop
        val bottomHalfHeight = height - safeBottom - mainFold.foldBounds.bottom
        var screenMargin = 0
        if (screenHeight > topHalfHeight || screenHeight > bottomHalfHeight) {
            val limitingHeight = min(topHalfHeight, bottomHalfHeight)
            screenWidth = (limitingHeight * consoleAspectRatio).toInt()
            screenHeight = limitingHeight
            screenMargin = (safeWidth - screenWidth) / 2
        }

        val topScreenView = Rect(safeLeft + screenMargin, mainFold.foldBounds.y - screenHeight, screenWidth, screenHeight)
        val bottomScreenView = Rect(safeLeft + screenMargin, mainFold.foldBounds.bottom, screenWidth, screenHeight)
        val dpadView = Rect(safeLeft, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - safeRight - largeButtonsSize, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(safeLeft, mainFold.foldBounds.bottom, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - safeRight - lrButtonsSize, mainFold.foldBounds.bottom, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize - spacing4dp / 2, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(width / 2 + spacing4dp / 2, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(width / 2 - smallButtonsSize / 2, mainFold.foldBounds.bottom, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
            )
        )
    }

    /**
     * Creates a landscape layout that supports a vertical fold. Screens are attached to either sides of the fold and as large as they can be to fit the screen.
     */
    private fun buildDefaultFoldingLandscapeLayout(width: Int, height: Int, folds: List<ScreenFold>, insets: Insets): ScreenLayout {
        // Only one fold is supported for now
        val mainFold = folds.first()

        val safeLeft = insets.left
        val safeTop = insets.top
        val safeRight = insets.right
        val safeBottom = insets.bottom
        val safeHeight = height - safeTop - safeBottom

        val largeButtonsSize = screenUnitsConverter.dpToPixels(140f).toInt()
        val lrButtonsSize = screenUnitsConverter.dpToPixels(50f).toInt()
        val smallButtonsSize = screenUnitsConverter.dpToPixels(40f).toInt()
        val spacing8dp = screenUnitsConverter.dpToPixels(8f).toInt()

        // Position to the left of the fold, attached to the fold. As big as the screen allows
        var topScreenWidth = mainFold.foldBounds.x - safeLeft
        var topScreenHeight = (topScreenWidth / consoleAspectRatio).toInt()
        if (topScreenHeight > safeHeight) {
            topScreenHeight = safeHeight
            topScreenWidth = (safeHeight * consoleAspectRatio).toInt()
        }

        // Position to the right of the fold, attached to the fold. As big as the screen allows
        var bottomScreenWidth = width - safeRight - mainFold.foldBounds.right
        var bottomScreenHeight = (bottomScreenWidth / consoleAspectRatio).toInt()
        if (bottomScreenHeight > safeHeight) {
            bottomScreenHeight = safeHeight
            bottomScreenWidth = (safeHeight * consoleAspectRatio).toInt()
        }

        // Check if the screens are small enough to be positioned under the L/R buttons
        val screenYPos = if (topScreenHeight < (safeHeight - lrButtonsSize - spacing8dp) && bottomScreenHeight < (safeHeight - lrButtonsSize - spacing8dp)) {
            safeTop + lrButtonsSize + spacing8dp
        } else {
            safeTop
        }

        val topScreenView = Rect(mainFold.foldBounds.x - topScreenWidth, screenYPos, topScreenWidth, topScreenHeight)
        val bottomScreenView = Rect(mainFold.foldBounds.right, screenYPos, bottomScreenWidth, bottomScreenHeight)
        val dpadView = Rect(safeLeft, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)
        val buttonsView = Rect(width - safeRight - largeButtonsSize, height - safeBottom - largeButtonsSize, largeButtonsSize, largeButtonsSize)

        return ScreenLayout(
            listOf(
                PositionedLayoutComponent(topScreenView, LayoutComponent.TOP_SCREEN),
                PositionedLayoutComponent(bottomScreenView, LayoutComponent.BOTTOM_SCREEN),
                PositionedLayoutComponent(dpadView, LayoutComponent.DPAD),
                PositionedLayoutComponent(buttonsView, LayoutComponent.BUTTONS),
                PositionedLayoutComponent(Rect(safeLeft, safeTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_L),
                PositionedLayoutComponent(Rect(width - safeRight - lrButtonsSize, safeTop, lrButtonsSize, lrButtonsSize), LayoutComponent.BUTTON_R),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.x - smallButtonsSize - spacing8dp, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_SELECT),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + spacing8dp, height - safeBottom - smallButtonsSize, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_START),
                PositionedLayoutComponent(Rect(mainFold.foldBounds.right + smallButtonsSize + spacing8dp, safeTop, smallButtonsSize, smallButtonsSize), LayoutComponent.BUTTON_MICROPHONE_TOGGLE),
            )
        )
    }

    private fun buildSingleScreenLayout(width: Int, height: Int, screenComponent: LayoutComponent): ScreenLayout {
        val screenView = centerScreenIn(width, height)
        val positionedScreenComponent = PositionedLayoutComponent(screenView, screenComponent)

        return ScreenLayout(listOf(positionedScreenComponent))
    }

    private fun centerScreenIn(width: Int, height: Int): Rect {
        val areaAspectRatio = width.toFloat() / height
        return if (areaAspectRatio > consoleAspectRatio) {
            // Center horizontally
            val scale = height.toFloat() / SCREEN_HEIGHT
            val scaledWidth = (SCREEN_WIDTH * scale).toInt()
            Rect((width - scaledWidth) / 2, 0, scaledWidth, height)
        } else {
            // Center vertically
            val scale = width.toFloat() / SCREEN_WIDTH
            val scaledHeight = (SCREEN_HEIGHT * scale).toInt()
            Rect(0, (height - scaledHeight) / 2, width, scaledHeight)
        }
    }

    private fun getDisplaySafeInsets(display: LayoutDisplay): Insets {
        if (!display.isDefaultDisplay) {
            return Insets.Zero
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return Insets.Zero
        }

        val windowManager = context.getSystemService<WindowManager>() ?: return Insets.Zero

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cutoutInsets = windowManager.currentWindowMetrics.windowInsets.getInsets(WindowInsets.Type.displayCutout())
            Insets(cutoutInsets.left, cutoutInsets.top, cutoutInsets.right, cutoutInsets.bottom)
        } else {
            @Suppress("DEPRECATION")
            val displayCutout = windowManager.defaultDisplay.cutout ?: return Insets.Zero
            Insets(displayCutout.safeInsetLeft, displayCutout.safeInsetTop, displayCutout.safeInsetRight, displayCutout.safeInsetBottom)
        }
    }
}