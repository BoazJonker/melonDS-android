package com.boazjonker.unbound

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.boazjonker.unbound.emu.GameFiles
import com.boazjonker.unbound.ui.LogoView
import com.boazjonker.unbound.ui.NightSkyView
import com.boazjonker.unbound.ui.Palette
import com.boazjonker.unbound.ui.PlankButton
import com.boazjonker.unbound.ui.PortalOverlayView
import com.boazjonker.unbound.ui.TunnelOverlayView
import com.boazjonker.unbound.ui.Themed
import kotlin.math.min

/**
 * Start screen: the title over an animated night sky, with buttons to continue, start the game and change settings. On first launch, it asks for the game
 * file.
 */
class HomeActivity : ComponentActivity() {

    private lateinit var settings: Settings
    private lateinit var files: GameFiles
    private lateinit var content: LinearLayout
    private lateinit var banner: TextView
    private lateinit var stage: FrameLayout
    private lateinit var logo: LogoView
    private lateinit var portal: PortalOverlayView
    private lateinit var tunnel: TunnelOverlayView
    private var showingSettings = false

    private val pickRom = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importRom(it) } }
    private val pickPatch = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importPatch(it) } }
    private val pickSave = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { importSave(it) } }
    private val createSave = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let { exportSave(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        files = GameFiles(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // The stage holds the whole start screen; the portal transition shakes it and pulls it into the portal. The tunnel and banner stay on top of it
        val outer = FrameLayout(this)
        val root = FrameLayout(this)
        stage = root
        outer.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val sky = NightSkyView(this).apply { isAnimated = settings.isBackgroundAnimated }
        root.addView(sky, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        logo = LogoView(this)
        column.addView(logo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(18f)
        })

        val scroll = Themed.scroll(this, column).apply { isFillViewport = true }
        val maxWidth = dp(460f)
        root.addView(scroll, FrameLayout.LayoutParams(
            min(maxWidth, resources.displayMetrics.widthPixels),
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER_HORIZONTAL,
        ))
        column.setPadding(dp(20f), dp(32f), dp(20f), dp(28f))

        portal = PortalOverlayView(this)
        root.addView(portal, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tunnel = TunnelOverlayView(this)
        outer.addView(tunnel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        banner = Themed.banner(this)
        outer.addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP))
        setContentView(outer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (portal.isPlaying) {
                    return
                }
                if (showingSettings) {
                    showingSettings = false
                    refresh()
                } else {
                    finish()
                }
            }
        })

        // The title drops in, then the buttons follow
        logo.alpha = 0f
        logo.translationY = -dp(30f).toFloat()
        logo.animate().alpha(1f).translationY(0f).setDuration(700).setInterpolator(DecelerateInterpolator()).start()
    }

    override fun onResume() {
        super.onResume()
        // Back from the game: put the start screen back together
        portal.reset()
        tunnel.darkness = 0f
        logo.isMedallionVisible = true
        logo.titleAlpha = 1f
        content.alpha = 1f
        refresh()
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    private fun refresh() {
        content.removeAllViews()
        when {
            showingSettings -> buildSettings()
            !files.hasRom -> buildFirstLaunch()
            else -> buildMain()
        }
        animateIn()
    }

    private fun animateIn() {
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            child.alpha = 0f
            child.translationY = dp(24f).toFloat()
            child.animate().alpha(1f).translationY(0f).setStartDelay(120L + i * 70L).setDuration(380).setInterpolator(DecelerateInterpolator(1.6f)).start()
        }
    }

    private fun addButton(button: PlankButton, topMargin: Float = 4f) {
        content.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            this.topMargin = dp(topMargin)
        })
    }

    private fun addText(text: String) {
        val panel = Themed.panel(this)
        panel.addView(Themed.body(this, text).apply { setPadding(0, 0, 0, 0) })
        content.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12f)
        })
    }

    private fun addHeading(text: String) {
        content.addView(TextView(this).apply {
            this.text = text
            typeface = Palette.bold
            setTextColor(Palette.GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            letterSpacing = 0.12f
            setPadding(dp(12f), dp(14f), 0, dp(2f))
        })
    }

    private fun buildFirstLaunch() {
        addText(getString(R.string.home_welcome))
        addButton(Themed.plank(this, getString(R.string.home_choose_rom), getString(R.string.home_choose_rom_hint), primary = true) {
            pickRom.launch(arrayOf("*/*"))
        })
    }

    private fun buildMain() {
        // One Play button: it continues from the quick save when there is one, and otherwise starts the game
        val canResume = files.resumeState.isFile
        val hint = if (canResume) {
            val savedAt = files.resumeState.lastModified()
            val savedAgo = if (System.currentTimeMillis() - savedAt < DateUtils.MINUTE_IN_MILLIS) {
                getString(R.string.just_now)
            } else {
                DateUtils.getRelativeTimeSpanString(savedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            }
            getString(R.string.home_play_resume_hint, savedAgo)
        } else {
            getString(R.string.home_play_hint)
        }
        addButton(Themed.plank(this, getString(R.string.home_play), hint, primary = true) {
            startGame(resume = canResume)
        }.apply { medal = PlankButton.Medal.PLAY }, topMargin = 8f)
        addButton(Themed.plank(this, getString(R.string.home_settings)) {
            showingSettings = true
            refresh()
        }.apply { medal = PlankButton.Medal.SETTINGS }, topMargin = 12f)
        if (files.patch.isFile) {
            addFooter(getString(R.string.home_patch_active))
        }
    }

    /**
     * A small line of text between two little gold rings.
     */
    private fun addFooter(text: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(22f), 0, 0)
        }
        fun ring() = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1.5f), Palette.GOLD)
            }
        }
        row.addView(ring(), LinearLayout.LayoutParams(dp(8f), dp(8f)))
        row.addView(TextView(this).apply {
            this.text = text
            setTextColor(0xFFD8D0F2.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(10f), 0, dp(10f), 0)
        })
        row.addView(ring(), LinearLayout.LayoutParams(dp(8f), dp(8f)))
        content.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildSettings() {
        addHeading(getString(R.string.settings_heading_screen))
        cycle(
            { getString(R.string.settings_filter, settings.screenFilter.label) },
            getString(R.string.settings_filter_hint),
        ) {
            settings.screenFilter = Settings.ScreenFilter.entries[(settings.screenFilter.ordinal + 1) % Settings.ScreenFilter.entries.size]
        }
        cycle({ getString(R.string.settings_background, onOff(settings.isBackgroundAnimated)) }) {
            settings.isBackgroundAnimated = !settings.isBackgroundAnimated
        }

        addHeading(getString(R.string.settings_heading_controls))
        cycle({ getString(R.string.settings_opacity, settings.controlsOpacity) }) {
            settings.controlsOpacity = next(listOf(100, 80, 60, 40), settings.controlsOpacity)
        }
        cycle({ getString(R.string.settings_haptics, onOff(settings.isHapticsEnabled)) }) {
            settings.isHapticsEnabled = !settings.isHapticsEnabled
        }
        cycle({ getString(R.string.settings_fast_forward_button, onOff(settings.isFastForwardButtonShown)) }, getString(R.string.settings_fast_forward_button_hint)) {
            settings.isFastForwardButtonShown = !settings.isFastForwardButtonShown
        }

        addHeading(getString(R.string.settings_heading_game))
        cycle({ getString(R.string.settings_speed, settings.fastForwardSpeed) }) {
            settings.fastForwardSpeed = next(listOf(2, 3, 4, 8), settings.fastForwardSpeed)
        }
        cycle({ getString(R.string.settings_volume, settings.volume) }) {
            settings.volume = next(listOf(100, 75, 50, 25, 0), settings.volume)
        }
        cycle({
            if (settings.autosaveMinutes == 0) getString(R.string.settings_autosave_off) else getString(R.string.settings_autosave, settings.autosaveMinutes)
        }, getString(R.string.settings_autosave_hint)) {
            settings.autosaveMinutes = next(listOf(2, 5, 10, 0), settings.autosaveMinutes)
        }

        addHeading(getString(R.string.settings_heading_files))
        addButton(Themed.plank(this, getString(R.string.settings_change_rom)) { pickRom.launch(arrayOf("*/*")) })
        if (files.patch.isFile) {
            addButton(Themed.plank(this, getString(R.string.settings_remove_patch)) {
                files.removePatch()
                showBanner(getString(R.string.banner_patch_removed))
                refresh()
            })
        } else {
            addButton(Themed.plank(this, getString(R.string.settings_add_patch), getString(R.string.settings_add_patch_hint)) {
                pickPatch.launch(arrayOf("*/*"))
            })
        }
        addButton(Themed.plank(this, getString(R.string.settings_export_save)) {
            if (files.save.isFile) createSave.launch("Pokemon Unbound.sav") else showBanner(getString(R.string.banner_no_save))
        })
        addButton(Themed.plank(this, getString(R.string.settings_import_save), getString(R.string.settings_import_save_hint)) {
            pickSave.launch(arrayOf("*/*"))
        })

        content.addView(View(this), LinearLayout.LayoutParams(1, dp(12f)))
        addButton(Themed.plank(this, getString(R.string.settings_done), primary = true) {
            showingSettings = false
            refresh()
        })
    }

    /**
     * A button that shows a setting's current value and changes it on every tap.
     */
    private fun cycle(label: () -> String, hint: String? = null, change: () -> Unit) {
        val button = Themed.plank(this, label(), hint) {}
        button.setOnClickListener {
            change()
            button.title = label()
        }
        addButton(button)
    }

    private fun next(options: List<Int>, current: Int): Int {
        val index = options.indexOf(current)
        return options[(index + 1) % options.size]
    }

    private fun onOff(value: Boolean) = getString(if (value) R.string.on else R.string.off)

    /**
     * The portal transition: the ring spins and opens into a portal, an arm reaches out and pulls you in, and the game opens at the end of the tunnel.
     */
    private fun startGame(resume: Boolean) {
        if (portal.isPlaying) return
        val logoPosition = IntArray(2)
        val stagePosition = IntArray(2)
        logo.getLocationInWindow(logoPosition)
        stage.getLocationInWindow(stagePosition)
        val centerX = logoPosition[0] - stagePosition[0] + logo.medallionCenterX
        val centerY = logoPosition[1] - stagePosition[1] + logo.medallionCenterY
        tunnel.centerX = centerX
        tunnel.centerY = centerY

        logo.isMedallionVisible = false
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 600
            addUpdateListener { logo.titleAlpha = it.animatedValue as Float }
            start()
        }
        content.animate().alpha(0f).setDuration(500).start()

        portal.play(centerX, centerY, logo.medallionRadius, stage,
            onDarken = { tunnel.darkness = it },
            onPulled = {
                startActivity(
                    Intent(this, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_RESUME, resume)
                        .putExtra(GameActivity.EXTRA_PORTAL, true)
                )
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            },
        )
    }

    private fun showBanner(text: String) {
        Themed.showBanner(banner, text, dp(48f).toFloat())
    }

    private fun inBackground(work: () -> Unit) {
        Thread(work).start()
    }

    private fun importRom(uri: Uri) {
        showBanner(getString(R.string.banner_importing))
        inBackground {
            val result = files.importRom(uri)
            runOnUiThread {
                when (result) {
                    GameFiles.ImportResult.OK -> {
                        showBanner(getString(R.string.banner_rom_ready))
                        Themed.showDialog(
                            this,
                            getString(R.string.patch_question_title),
                            getString(R.string.patch_question),
                            listOf(
                                getString(R.string.patch_question_ready) to {},
                                getString(R.string.patch_question_add) to { pickPatch.launch(arrayOf("*/*")) },
                            ),
                        )
                    }
                    GameFiles.ImportResult.NOT_A_GBA_ROM -> Themed.showMessage(this, getString(R.string.error_title), getString(R.string.error_not_rom))
                    else -> Themed.showMessage(this, getString(R.string.error_title), getString(R.string.error_import))
                }
                refresh()
            }
        }
    }

    private fun importPatch(uri: Uri) {
        inBackground {
            val result = files.importPatch(uri)
            runOnUiThread {
                when (result) {
                    GameFiles.ImportResult.OK -> showBanner(getString(R.string.banner_patch_added))
                    GameFiles.ImportResult.NOT_A_PATCH -> Themed.showMessage(this, getString(R.string.error_title), getString(R.string.error_not_patch))
                    else -> Themed.showMessage(this, getString(R.string.error_title), getString(R.string.error_import))
                }
                refresh()
            }
        }
    }

    private fun importSave(uri: Uri) {
        inBackground {
            val success = files.importSave(uri)
            runOnUiThread {
                if (success) showBanner(getString(R.string.banner_save_imported))
                else Themed.showMessage(this, getString(R.string.error_title), getString(R.string.error_import_save))
                refresh()
            }
        }
    }

    private fun exportSave(uri: Uri) {
        inBackground {
            val success = files.exportSave(uri)
            runOnUiThread {
                showBanner(getString(if (success) R.string.banner_save_exported else R.string.error_export_save))
            }
        }
    }
}
