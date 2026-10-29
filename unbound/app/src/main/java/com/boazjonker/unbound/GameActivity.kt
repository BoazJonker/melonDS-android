package com.boazjonker.unbound

import android.graphics.RectF
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.boazjonker.unbound.emu.Emulator
import com.boazjonker.unbound.emu.GameFiles
import com.boazjonker.unbound.emu.GbaKeys
import com.boazjonker.unbound.emu.NativeCore
import com.boazjonker.unbound.ui.ControlsView
import com.boazjonker.unbound.ui.GameRenderer
import com.boazjonker.unbound.ui.NightSkyView
import com.boazjonker.unbound.ui.PlankButton
import com.boazjonker.unbound.ui.Themed
import kotlin.math.abs
import kotlin.math.min

class GameActivity : ComponentActivity(), ControlsView.Listener {

    companion object {
        const val EXTRA_RESUME = "resume"
    }

    private lateinit var settings: Settings
    private lateinit var files: GameFiles
    private lateinit var root: FrameLayout
    private lateinit var sky: NightSkyView
    private lateinit var gameView: GLSurfaceView
    private lateinit var controls: ControlsView
    private lateinit var menuOverlay: FrameLayout
    private lateinit var menuScroll: View
    private lateinit var banner: TextView
    private val renderer = GameRenderer()
    private val emulator = Emulator { gameView.requestRender() }
    private val handler = Handler(Looper.getMainLooper())

    private var isLoaded = false
    private var touchKeys = 0
    private var padKeys = 0
    private var stickKeys = 0
    private var safeInsets = Insets.NONE

    private val autosave = object : Runnable {
        override fun run() {
            val minutes = settings.autosaveMinutes
            if (isLoaded && !emulator.isPaused && minutes > 0) {
                emulator.post { NativeCore.saveState(files.resumeState.path) }
            }
            handler.postDelayed(this, minutes.coerceAtLeast(1) * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        files = GameFiles(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        buildViews()
        applySettings()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (menuOverlay.visibility == View.VISIBLE) closeMenu() else onMenu()
            }
        })

        val resume = intent.getBooleanExtra(EXTRA_RESUME, false)
        Thread {
            val loaded = NativeCore.load(files.rom.path, files.patch.takeIf { it.isFile }?.path, files.save.path, Emulator.SAMPLE_RATE)
            if (loaded && resume && files.resumeState.isFile) {
                NativeCore.loadState(files.resumeState.path)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!loaded) {
                    Themed.showMessage(this, getString(R.string.error_title), getString(R.string.error_load_rom)) { finish() }
                    return@runOnUiThread
                }
                isLoaded = true
                if (!isResumedState) emulator.pause()
                emulator.start()
                handler.postDelayed(autosave, settings.autosaveMinutes.coerceAtLeast(1) * 60_000L)
            }
        }.start()
    }

    private var isResumedState = false

    private fun buildViews() {
        root = FrameLayout(this)
        sky = NightSkyView(this).apply { frameIntervalMs = 66L }
        gameView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        controls = ControlsView(this).also { it.listener = this }
        banner = Themed.banner(this)
        menuOverlay = buildMenu()

        root.addView(sky, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(gameView, FrameLayout.LayoutParams(1, 1))
        root.addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP))
        root.addView(menuOverlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            safeInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            layoutGame()
            insets
        }
        root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                root.post { layoutGame() }
            }
        }
    }

    /**
     * Places the game screen: across the top in portrait, in the middle in landscape. The controls go around it.
     */
    private fun layoutGame() {
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        if (w == 0f || h == 0f) return
        val density = resources.displayMetrics.density
        val margin = 14f * density
        val rect = RectF()
        if (h >= w) {
            val top = safeInsets.top + margin
            var gameWidth = w - margin * 2
            var gameHeight = gameWidth / 1.5f
            // Leave at least 55% of the screen for the controls
            val maxHeight = h * 0.45f - top
            if (gameHeight > maxHeight) {
                gameHeight = maxHeight
                gameWidth = gameHeight * 1.5f
            }
            rect.set((w - gameWidth) / 2f, top, (w + gameWidth) / 2f, top + gameHeight)
        } else {
            val left = safeInsets.left.toFloat()
            val right = w - safeInsets.right
            var gameHeight = h - margin * 2
            var gameWidth = gameHeight * 1.5f
            val maxWidth = (right - left) * 0.6f
            if (gameWidth > maxWidth) {
                gameWidth = maxWidth
                gameHeight = gameWidth / 1.5f
            }
            val cx = (left + right) / 2f
            rect.set(cx - gameWidth / 2f, (h - gameHeight) / 2f, cx + gameWidth / 2f, (h + gameHeight) / 2f)
        }

        gameView.layoutParams = (gameView.layoutParams as FrameLayout.LayoutParams).apply {
            width = rect.width().toInt()
            height = rect.height().toInt()
            leftMargin = rect.left.toInt()
            topMargin = rect.top.toInt()
        }
        val snapped = RectF(rect.left.toInt().toFloat(), rect.top.toInt().toFloat(), (rect.left.toInt() + rect.width().toInt()).toFloat(), (rect.top.toInt() + rect.height().toInt()).toFloat())
        sky.setGameRect(snapped)
        controls.setGameRect(snapped)
        menuScroll.layoutParams = (menuScroll.layoutParams as FrameLayout.LayoutParams).apply {
            width = min(420f * density, w - 32f * density).toInt()
        }
    }

    private fun buildMenu(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xB30B0820.toInt())
            visibility = View.GONE
            isClickable = true
        }
        val panel = Themed.panel(this)
        val title = Themed.title(this, getString(R.string.menu_title))
        panel.addView(title)

        fun add(button: PlankButton) {
            panel.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        add(Themed.plank(this, getString(R.string.menu_resume), primary = true) { closeMenu() })
        add(Themed.plank(this, getString(R.string.menu_save_state)) {
            emulator.runAndWait { NativeCore.saveState(files.slotState.path) }
            closeMenu()
            showBanner(getString(R.string.banner_state_saved))
        })
        add(Themed.plank(this, getString(R.string.menu_load_state)) {
            if (!files.slotState.isFile) {
                showBanner(getString(R.string.banner_no_state))
                return@plank
            }
            var loaded = false
            emulator.runAndWait { loaded = NativeCore.loadState(files.slotState.path) }
            closeMenu()
            showBanner(getString(if (loaded) R.string.banner_state_loaded else R.string.banner_state_failed))
        })
        val speed = Themed.plank(this, "") {}
        speed.title = getString(R.string.menu_speed, settings.fastForwardSpeed)
        speed.setOnClickListener {
            val next = when (settings.fastForwardSpeed) {
                2 -> 3
                3 -> 4
                4 -> 8
                else -> 2
            }
            settings.fastForwardSpeed = next
            speed.title = getString(R.string.menu_speed, next)
            if (emulator.speed > 1) emulator.speed = next
        }
        add(speed)
        val filter = Themed.plank(this, "") {}
        filter.title = getString(R.string.menu_filter, settings.screenFilter.label)
        filter.setOnClickListener {
            val next = Settings.ScreenFilter.entries[(settings.screenFilter.ordinal + 1) % Settings.ScreenFilter.entries.size]
            settings.screenFilter = next
            renderer.filter = next
            filter.title = getString(R.string.menu_filter, next.label)
            gameView.requestRender()
        }
        add(filter)
        add(Themed.plank(this, getString(R.string.menu_quit)) { finish() })

        menuScroll = Themed.scroll(this, panel)
        overlay.addView(menuScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        return overlay
    }

    private fun applySettings() {
        renderer.filter = settings.screenFilter
        sky.isAnimated = settings.isBackgroundAnimated
        controls.isHapticsEnabled = settings.isHapticsEnabled
        controls.controlsAlpha = settings.controlsOpacity / 100f
        emulator.volume = settings.volume / 100f
    }

    override fun onMenu() {
        if (!isLoaded) return
        controls.releaseAll()
        emulator.pause()
        menuOverlay.alpha = 0f
        menuOverlay.visibility = View.VISIBLE
        menuOverlay.animate().alpha(1f).setDuration(180).start()
    }

    private fun closeMenu() {
        menuOverlay.animate().alpha(0f).setDuration(150).withEndAction { menuOverlay.visibility = View.GONE }.start()
        if (isLoaded) emulator.resume()
    }

    override fun onKeysChanged(keys: Int) {
        touchKeys = keys
        updateKeys()
    }

    override fun onFastForwardToggled() {
        val on = emulator.speed <= 1
        emulator.speed = if (on) settings.fastForwardSpeed else 1
        controls.isFastForwardOn = on
        showBanner(if (on) getString(R.string.banner_fast_forward, settings.fastForwardSpeed) else getString(R.string.banner_normal_speed))
    }

    private fun updateKeys() {
        emulator.keys = touchKeys or padKeys or stickKeys
    }

    private fun showBanner(text: String) {
        Themed.showBanner(banner, text, (safeInsets.top + 24 * resources.displayMetrics.density))
    }

    // Game controllers

    private fun keyForCode(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> GbaKeys.A
        KeyEvent.KEYCODE_BUTTON_B -> GbaKeys.B
        KeyEvent.KEYCODE_BUTTON_X -> GbaKeys.B
        KeyEvent.KEYCODE_BUTTON_Y -> GbaKeys.A
        KeyEvent.KEYCODE_BUTTON_L1 -> GbaKeys.L
        KeyEvent.KEYCODE_BUTTON_R1 -> GbaKeys.R
        KeyEvent.KEYCODE_BUTTON_START -> GbaKeys.START
        KeyEvent.KEYCODE_BUTTON_SELECT -> GbaKeys.SELECT
        KeyEvent.KEYCODE_DPAD_UP -> GbaKeys.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> GbaKeys.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> GbaKeys.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> GbaKeys.RIGHT
        else -> 0
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fromController = event.source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0
        if (menuOverlay.visibility == View.VISIBLE || !fromController) {
            return super.dispatchKeyEvent(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_R2 || event.keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onFastForwardToggled()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE || event.keyCode == KeyEvent.KEYCODE_BUTTON_L2) {
            if (event.action == KeyEvent.ACTION_UP) onMenu()
            return true
        }
        val key = keyForCode(event.keyCode)
        if (key == 0) return super.dispatchKeyEvent(event)
        padKeys = if (event.action == KeyEvent.ACTION_DOWN) padKeys or key else padKeys and key.inv()
        updateKeys()
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == 0 || event.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event)
        }
        // Left stick and hat switch both act as the D-pad
        val x = event.getAxisValue(MotionEvent.AXIS_X).let { if (abs(it) > 0.4f) it else event.getAxisValue(MotionEvent.AXIS_HAT_X) }
        val y = event.getAxisValue(MotionEvent.AXIS_Y).let { if (abs(it) > 0.4f) it else event.getAxisValue(MotionEvent.AXIS_HAT_Y) }
        var keys = 0
        if (x < -0.4f) keys = keys or GbaKeys.LEFT
        if (x > 0.4f) keys = keys or GbaKeys.RIGHT
        if (y < -0.4f) keys = keys or GbaKeys.UP
        if (y > 0.4f) keys = keys or GbaKeys.DOWN
        stickKeys = keys
        updateKeys()
        return true
    }

    // Lifecycle

    override fun onResume() {
        super.onResume()
        isResumedState = true
        gameView.onResume()
        if (isLoaded && menuOverlay.visibility != View.VISIBLE) emulator.resume()
    }

    override fun onPause() {
        super.onPause()
        isResumedState = false
        if (isLoaded) {
            // Quick resume: the game continues from here next time
            emulator.runAndWait { NativeCore.saveState(files.resumeState.path) }
            emulator.pause()
        }
        controls.releaseAll()
        gameView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        emulator.stop()
        if (isLoaded) {
            NativeCore.unload()
        }
    }
}
