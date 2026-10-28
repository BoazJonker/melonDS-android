package me.magnum.melonds.ui.emulator

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.core.view.isGone
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.Input
import me.magnum.melonds.domain.model.input.SoftInputBehaviour
import me.magnum.melonds.domain.model.layout.LayoutComponent
import me.magnum.melonds.ui.common.LayoutView
import me.magnum.melonds.ui.emulator.input.ActionButtonsInputHandler
import me.magnum.melonds.ui.emulator.input.ButtonsInputHandler
import me.magnum.melonds.ui.emulator.input.DpadInputHandler
import me.magnum.melonds.ui.emulator.input.FrontendInputHandler
import me.magnum.melonds.ui.emulator.input.IInputListener
import me.magnum.melonds.ui.emulator.input.SingleButtonInputHandler
import me.magnum.melonds.ui.emulator.input.StickInputHandler
import me.magnum.melonds.ui.emulator.input.TapInputHandler
import me.magnum.melonds.ui.emulator.input.TouchscreenInputHandler
import me.magnum.melonds.ui.emulator.input.VirtualStylus
import me.magnum.melonds.ui.emulator.input.view.StickView
import me.magnum.melonds.ui.emulator.input.view.ToggleableImageView
import me.magnum.melonds.ui.emulator.model.ConnectedControllersState
import me.magnum.melonds.ui.emulator.model.RuntimeInputLayoutConfiguration
import me.magnum.melonds.ui.layouteditor.model.LayoutTarget
import javax.inject.Inject

@AndroidEntryPoint
class RuntimeLayoutView(context: Context, attrs: AttributeSet? = null) : LayoutView(context, attrs) {

    @Inject
    lateinit var touchVibrator: TouchVibrator

    private var currentRuntimeLayout: RuntimeInputLayoutConfiguration? = null
    private var frontendInputHandler: IInputListener? = null
    private var systemInputHandler: IInputListener? = null
    private var isSoftInputVisible = true
    private var areScreensSwapped = false
    private var connectedControllersState: ConnectedControllersState = ConnectedControllersState.NoControllers
    // Drives the stylus from the stick and action buttons, when the stick control style is used
    private val virtualStylus = VirtualStylus()
    private var areControlsFaded = false

    /**
     * Called when the player taps the small screen window of the "one screen + mini screen" layout.
     */
    var onMiniScreenTapped: (() -> Unit)? = null

    /**
     * Called when the player starts or stops holding the small screen window.
     */
    var onMiniScreenPressedChanged: ((isPressed: Boolean) -> Unit)? = null

    /**
     * Called for every touch on the controls or the main screen (touches on the small screen window are excluded), with whether a finger is still down.
     */
    var onUserInput: ((isTouching: Boolean) -> Unit)? = null
    private var isMiniScreenGesture = false

    /**
     * Whether touches on the main screen are sent to the DS touch screen while it shows the top screen. Used during cutscenes, so that tapping the cutscene
     * advances it as if the touch screen had been tapped.
     */
    var forwardTouchesWhenSwapped = false
        set(value) {
            field = value
            updateScreenInputs()
        }

    private companion object {
        const val FADED_CONTROLS_ALPHA_FACTOR = 0.2f
    }

    fun setFrontendInputHandler(frontendInputHandler: FrontendInputHandler) {
        this.frontendInputHandler = frontendInputHandler
        updateInputs()
    }

    fun setSystemInputHandler(systemInputHandler: IInputListener) {
        this.systemInputHandler = systemInputHandler
        updateInputs()
    }

    fun setConnectedControllersState(state: ConnectedControllersState) {
        connectedControllersState = state
        updateVisibility()
    }

    fun toggleSoftInputVisibility() {
        isSoftInputVisible = !isSoftInputVisible
        setLayoutComponentToggleState(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT, isSoftInputVisible)
        updateVisibility()
    }

    fun swapScreens() {
        areScreensSwapped = !areScreensSwapped
        updateScreenInputs()
    }

    fun areScreensSwapped(): Boolean {
        return areScreensSwapped
    }

    fun setLayoutComponentToggleState(layoutComponent: LayoutComponent, isEnabled: Boolean) {
        val toggleableImageView = getLayoutComponentView(layoutComponent)?.view as? ToggleableImageView ?: return
        toggleableImageView.setToggleState(isEnabled)
    }

    fun instantiateLayout(runtimeLayout: RuntimeInputLayoutConfiguration, layoutTarget: LayoutTarget) {
        currentRuntimeLayout = runtimeLayout
        instantiateLayout(runtimeLayout.layout, layoutTarget)
        updateInputs()
        updateVisibility()
        setLayoutComponentToggleState(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT, isSoftInputVisible)
    }

    private fun updateInputs() {
        val currentRuntimeLayout = currentRuntimeLayout
        if (currentRuntimeLayout == null) {
            isGone = true
            return
        }

        isVisible = true
        val inputAlpha = currentRuntimeLayout.softInputOpacity / 100f

        val enableHapticFeedback = currentRuntimeLayout.isHapticFeedbackEnabled
        virtualStylus.inputListener = systemInputHandler
        systemInputHandler?.let {
            getLayoutComponentView(LayoutComponent.DPAD)?.view?.let { dpadView ->
                val handler = if (dpadView is StickView) {
                    StickInputHandler(virtualStylus, enableHapticFeedback)
                } else {
                    DpadInputHandler(it, enableHapticFeedback, touchVibrator)
                }
                dpadView.setOnTouchListener(handler)
            }
            getLayoutComponentView(LayoutComponent.BUTTONS)?.view?.let { buttonsView ->
                // The stick replaces the D-pad, in which case the face buttons are the action buttons
                val handler = if (getLayoutComponentView(LayoutComponent.DPAD)?.view is StickView) {
                    ActionButtonsInputHandler(virtualStylus, enableHapticFeedback, touchVibrator)
                } else {
                    ButtonsInputHandler(it, enableHapticFeedback, touchVibrator)
                }
                buttonsView.setOnTouchListener(handler)
            }
            getLayoutComponentView(LayoutComponent.BUTTON_L)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.L, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_R)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.R, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_SELECT)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.SELECT, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_START)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.START, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_HINGE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.HINGE, enableHapticFeedback, touchVibrator))
        }
        frontendInputHandler?.let {
            getLayoutComponentView(LayoutComponent.BUTTON_RESET)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.RESET, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_PAUSE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.PAUSE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_FAST_FORWARD_TOGGLE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.FAST_FORWARD, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_MICROPHONE_TOGGLE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.MICROPHONE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.TOGGLE_SOFT_INPUT, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_SWAP_SCREENS)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.SWAP_SCREENS, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_QUICK_SAVE)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.QUICK_SAVE, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_QUICK_LOAD)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.QUICK_LOAD, enableHapticFeedback, touchVibrator))
            getLayoutComponentView(LayoutComponent.BUTTON_REWIND)?.view?.setOnTouchListener(SingleButtonInputHandler(it, Input.REWIND, enableHapticFeedback, touchVibrator))
        }

        getLayoutComponentViews().forEach {
            if (!it.component.isScreen()) {
                it.view.apply {
                    alpha = inputAlpha * if (areControlsFaded) FADED_CONTROLS_ALPHA_FACTOR else 1f
                }
            }
        }

        updateScreenInputs()
    }

    /**
     * Fades the on-screen controls out (during cutscenes) or back in. The controls keep working while faded.
     */
    fun setControlsFaded(faded: Boolean) {
        if (faded == areControlsFaded) {
            return
        }

        areControlsFaded = faded
        val inputAlpha = (currentRuntimeLayout?.softInputOpacity ?: 100) / 100f
        val targetAlpha = inputAlpha * if (faded) FADED_CONTROLS_ALPHA_FACTOR else 1f
        getLayoutComponentViews().forEach {
            if (!it.component.isScreen()) {
                it.view.animate().alpha(targetAlpha).setDuration(250).start()
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val miniScreenView = listOfNotNull(getLayoutComponentView(LayoutComponent.TOP_SCREEN), getLayoutComponentView(LayoutComponent.BOTTOM_SCREEN))
                .firstOrNull { it.onTop }
                ?.view
            isMiniScreenGesture = miniScreenView != null &&
                    event.x >= miniScreenView.x && event.x < miniScreenView.x + miniScreenView.width &&
                    event.y >= miniScreenView.y && event.y < miniScreenView.y + miniScreenView.height
        }
        if (!isMiniScreenGesture) {
            val isTouching = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            onUserInput?.invoke(isTouching)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun updateScreenInputs() {
        val (touchScreenComponent, nonTouchScreenComponent) = if (areScreensSwapped) {
            LayoutComponent.TOP_SCREEN to LayoutComponent.BOTTOM_SCREEN
        } else {
            LayoutComponent.BOTTOM_SCREEN to LayoutComponent.TOP_SCREEN
        }

        val screenViews = listOfNotNull(getLayoutComponentView(LayoutComponent.TOP_SCREEN), getLayoutComponentView(LayoutComponent.BOTTOM_SCREEN))
        val miniScreenView = screenViews.firstOrNull { it.onTop }
        val mainScreenView = screenViews.firstOrNull { !it.onTop }
        if (miniScreenView != null && mainScreenView != null) {
            // "One screen + mini screen" layout: tapping the small window swaps the screens, and the main screen receives the touch input
            miniScreenView.view.setOnTouchListener(
                TapInputHandler(onPressedChanged = { onMiniScreenPressedChanged?.invoke(it) }) { onMiniScreenTapped?.invoke() }
            )
            val mainShowsTouchScreen = mainScreenView.component == touchScreenComponent
            val mainScreenHandler = if (mainShowsTouchScreen || forwardTouchesWhenSwapped) {
                systemInputHandler?.let { TouchscreenInputHandler(it, virtualStylus) }
            } else {
                null
            }
            mainScreenView.view.setOnTouchListener(mainScreenHandler)
            return
        }

        systemInputHandler?.let {
            getLayoutComponentView(touchScreenComponent)?.view?.setOnTouchListener(TouchscreenInputHandler(it, virtualStylus))
        }
        getLayoutComponentView(nonTouchScreenComponent)?.view?.setOnTouchListener(null)
    }

    private fun updateVisibility() {
        val currentConnectedControllersState = connectedControllersState
        var hiddenComponents = when(currentRuntimeLayout?.softInputBehaviour) {
            SoftInputBehaviour.ALWAYS_VISIBLE -> emptyList()
            SoftInputBehaviour.HIDE_SYSTEM_BUTTONS_WHEN_CONTROLLERS_CONNECTED, null -> {
                when(currentConnectedControllersState) {
                    ConnectedControllersState.NoControllers -> emptyList()
                    is ConnectedControllersState.ControllersConnected -> listOf(
                        LayoutComponent.BUTTONS,
                        LayoutComponent.DPAD,
                        LayoutComponent.BUTTON_L,
                        LayoutComponent.BUTTON_R,
                        LayoutComponent.BUTTON_START,
                        LayoutComponent.BUTTON_SELECT
                    )
                }
            }
            SoftInputBehaviour.HIDE_ALL_BUTTONS_ASSIGNED_TO_CONNECTED_CONTROLLERS -> when(currentConnectedControllersState) {
                ConnectedControllersState.NoControllers -> emptyList()
                is ConnectedControllersState.ControllersConnected -> {
                    LayoutComponent.entries.filter {
                        // The component can be hidden if all matching inputs are assigned to connected controllers
                        it.matchingInputs.all {
                            currentConnectedControllersState.assignedInputs.contains(it)
                        }
                    }
                }
            }
            SoftInputBehaviour.ALWAYS_INVISIBLE -> LayoutComponent.entries.toList()
        }

        if (!isSoftInputVisible) {
            // Hide everything except the soft input toggle button if it was not already hidden by the soft input behaviour logic
            hiddenComponents = if (hiddenComponents.contains(LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT)) {
                LayoutComponent.entries.toList()
            } else {
                LayoutComponent.entries.toList().filter { it != LayoutComponent.BUTTON_TOGGLE_SOFT_INPUT}
            }
        }

        val visibleComponents = LayoutComponent.entries.filter { !hiddenComponents.contains(it) }

        hiddenComponents.forEach { component ->
            if (!component.isScreen()) {
                getLayoutComponentView(component)?.view?.isVisible = false
            }
        }
        visibleComponents.forEach { component ->
            if (!component.isScreen()) {
                getLayoutComponentView(component)?.view?.isVisible = true
            }
        }
    }
}