package me.magnum.melonds.ui.romlist

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.DirectoryPickerContract
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.ui.common.rom.EmulatorLaunchValidatorDelegate
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.romlist.ui.SpiritHomeScreen
import me.magnum.melonds.ui.settings.SettingsActivity
import me.magnum.melonds.ui.theme.MelonTheme
import javax.inject.Inject

@AndroidEntryPoint
class RomListActivity : AppCompatActivity() {

    companion object {
        // Renamed so that the setting starts off for everyone: it skips the start screen
        private const val KEY_LAUNCH_LAST_GAME_ON_STARTUP = "skip_start_screen"
        private const val KEY_LAST_LAUNCHED_ROM_URI = "last_launched_rom_uri"
    }

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private val homeViewModel: SpiritHomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null && intent?.action == Intent.ACTION_MAIN) {
            launchLastGameIfEnabled()
        }

        val emulatorLauncherValidatorDelegate = EmulatorLaunchValidatorDelegate(this, object : EmulatorLaunchValidatorDelegate.Callback {
            override fun onRomValidated(rom: Rom) {
                sharedPreferences.edit { putString(KEY_LAST_LAUNCHED_ROM_URI, rom.uri.toString()) }
                val intent = EmulatorActivity.getRomEmulatorActivityIntent(this@RomListActivity, rom)
                startActivity(intent)
            }

            override fun onFirmwareValidated(consoleType: ConsoleType) {
                val intent = EmulatorActivity.getFirmwareEmulatorActivityIntent(this@RomListActivity, consoleType)
                startActivity(intent)
            }

            override fun onValidationAborted() {
                // Do nothing
            }
        })

        setContent {
            val state by homeViewModel.state.collectAsStateWithLifecycle()
            val isCheckingFolder by homeViewModel.isCheckingFolder.collectAsStateWithLifecycle()
            var problem by remember { mutableStateOf<SpiritHomeViewModel.Problem?>(null) }
            val folderPickerLauncher = rememberLauncherForActivityResult(DirectoryPickerContract(Permission.READ_WRITE)) { uri ->
                if (uri != null) {
                    homeViewModel.onFolderSelected(uri)
                }
            }

            LaunchedEffect(Unit) {
                homeViewModel.problemEvent.collectLatest { problem = it }
            }

            MelonTheme {
                SpiritHomeScreen(
                    state = state,
                    isCheckingFolder = isCheckingFolder,
                    problem = problem,
                    onChooseFolder = { folderPickerLauncher.launch(null) },
                    onPlay = { rom ->
                        homeViewModel.setRomLastPlayedNow(rom)
                        emulatorLauncherValidatorDelegate.validateRom(rom)
                    },
                    onOpenSettings = {
                        startActivity(Intent(this@RomListActivity, SettingsActivity::class.java))
                    },
                    onDismissProblem = { problem = null },
                )
            }
        }
    }

    /**
     * Launches the last played game straight away so that the app behaves like a standalone game. This activity stays below the emulator so that exiting the game
     * (or failing to load it) shows the start screen.
     */
    private fun launchLastGameIfEnabled() {
        // Off by default: the start screen is shown, and the game starts with one tap on Play
        if (!sharedPreferences.getBoolean(KEY_LAUNCH_LAST_GAME_ON_STARTUP, false)) {
            return
        }

        val romUri = sharedPreferences.getString(KEY_LAST_LAUNCHED_ROM_URI, null) ?: return
        val intent = Intent(this, EmulatorActivity::class.java).apply {
            putExtra(EmulatorActivity.KEY_URI, romUri)
        }
        startActivity(intent)
    }
}
