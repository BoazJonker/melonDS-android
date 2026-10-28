package me.magnum.melonds.ui.romlist

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.ConsoleType
import me.magnum.melonds.domain.model.DownloadProgress
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.model.appupdate.AppUpdate
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.parcelables.RomParcelable
import me.magnum.melonds.ui.common.melonTextButtonColors
import me.magnum.melonds.ui.common.rom.EmulatorLaunchValidatorDelegate
import me.magnum.melonds.ui.dsiwaremanager.DSiWareManagerActivity
import me.magnum.melonds.ui.emulator.EmulatorActivity
import me.magnum.melonds.ui.romdetails.RomDetailsActivity
import me.magnum.melonds.ui.romlist.ui.DownloadProgressDialog
import me.magnum.melonds.ui.romlist.ui.NightlyUpdateDialog
import me.magnum.melonds.ui.romlist.ui.ProdUpdateAvailableDialog
import me.magnum.melonds.ui.romlist.ui.RomListScreen
import me.magnum.melonds.ui.settings.SettingsActivity
import me.magnum.melonds.ui.theme.MelonTheme
import javax.inject.Inject

@AndroidEntryPoint
class RomListActivity : AppCompatActivity() {

    companion object {
        private const val KEY_LAUNCH_LAST_GAME_ON_STARTUP = "launch_last_game_on_startup"
        private const val KEY_LAST_LAUNCHED_ROM_URI = "last_launched_rom_uri"
    }

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    private val viewModel: RomListViewModel by viewModels()
    private val updatesViewModel: UpdatesViewModel by viewModels()

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
            val roms by viewModel.roms.collectAsStateWithLifecycle()
            val romScanningStatus by viewModel.romScanningStatus.collectAsStateWithLifecycle(initialValue = RomScanningStatus.NOT_SCANNING)
            val hasSearchDirectories by viewModel.hasSearchDirectories.collectAsStateWithLifecycle(initialValue = true)

            var currentUpdate by remember { mutableStateOf<AppUpdate?>(null) }
            var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }
            var showInvalidDirectoryDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                updatesViewModel.appUpdate.collectLatest {
                    currentUpdate = it
                }
            }

            LaunchedEffect(Unit) {
                updatesViewModel.updateDownloadProgressEvent.collectLatest { progress ->
                    when (progress) {
                        is DownloadProgress.DownloadUpdate -> {
                            downloadProgress = progress
                        }
                        is DownloadProgress.DownloadComplete -> {
                            downloadProgress = null
                        }
                        is DownloadProgress.DownloadFailed -> {
                            downloadProgress = null
                            Toast.makeText(this@RomListActivity, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                viewModel.invalidDirectoryAccessEvent.collectLatest {
                    showInvalidDirectoryDialog = true
                }
            }

            MelonTheme {
                RomListScreen(
                    roms = roms,
                    isRefreshing = romScanningStatus == RomScanningStatus.SCANNING,
                    hasSearchDirectories = hasSearchDirectories,
                    onSearchQueryChange = viewModel::setRomSearchQuery,
                    onSortChange = viewModel::setRomSorting,
                    onFirmwareBoot = { consoleType ->
                        emulatorLauncherValidatorDelegate.validateFirmware(consoleType)
                    },
                    onRomSelected = { rom ->
                        viewModel.setRomLastPlayedNow(rom)
                        emulatorLauncherValidatorDelegate.validateRom(rom)
                    },
                    onRomConfigClick = { rom ->
                        val intent = Intent(this@RomListActivity, RomDetailsActivity::class.java).apply {
                            putExtra(RomDetailsActivity.KEY_ROM, RomParcelable(rom))
                        }
                        startActivity(intent)
                    },
                    onRefresh = viewModel::refreshRoms,
                    onDirectorySelected = viewModel::addRomSearchDirectory,
                    onNavigateToSettings = {
                        val intent = Intent(this@RomListActivity, SettingsActivity::class.java)
                        startActivity(intent)
                    },
                    onNavigateToDsiWareManager = {
                        val intent = Intent(this@RomListActivity, DSiWareManagerActivity::class.java)
                        startActivity(intent)
                    },
                    retrieveRomIcon = { rom ->
                        viewModel.getRomIcon(rom)
                    },
                )

                currentUpdate?.let { update ->
                    when (update.type) {
                        AppUpdate.Type.PRODUCTION -> {
                            ProdUpdateAvailableDialog(
                                update = update,
                                onUpdate = {
                                    currentUpdate = null
                                    downloadProgress = DownloadProgress.DownloadUpdate(0, 0)
                                    updatesViewModel.downloadUpdate(update)
                                },
                                onSkip = {
                                    updatesViewModel.skipUpdate(update)
                                    currentUpdate = null
                                },
                                onDismiss = {
                                    currentUpdate = null
                                },
                            )
                        }
                        AppUpdate.Type.NIGHTLY -> {
                            NightlyUpdateDialog(
                                onUpdate = {
                                    currentUpdate = null
                                    downloadProgress = DownloadProgress.DownloadUpdate(0, 0)
                                    updatesViewModel.downloadUpdate(update)
                                },
                                onDismiss = {
                                    updatesViewModel.skipUpdate(update)
                                    currentUpdate = null
                                },
                            )
                        }
                    }
                }

                (downloadProgress as? DownloadProgress.DownloadUpdate)?.let { progress ->
                    DownloadProgressDialog(
                        downloadProgress = progress,
                        onMoveToBackground = {
                            downloadProgress = null
                        },
                    )
                }

                if (showInvalidDirectoryDialog) {
                    AlertDialog(
                        onDismissRequest = { showInvalidDirectoryDialog = false },
                        title = { Text(stringResource(R.string.error_invalid_directory)) },
                        text = { Text(stringResource(R.string.error_invalid_directory_description)) },
                        confirmButton = {
                            TextButton(
                                onClick = { showInvalidDirectoryDialog = false },
                                colors = melonTextButtonColors(),
                            ) {
                                Text(stringResource(R.string.ok).uppercase())
                            }
                        },
                    )
                }
            }
        }
    }

    /**
     * Launches the last played game straight away so that the app behaves like a standalone game. This activity stays below the emulator so that exiting the game
     * (or failing to load it) shows the game list.
     */
    private fun launchLastGameIfEnabled() {
        if (!sharedPreferences.getBoolean(KEY_LAUNCH_LAST_GAME_ON_STARTUP, true)) {
            return
        }

        val romUri = sharedPreferences.getString(KEY_LAST_LAUNCHED_ROM_URI, null) ?: return
        val intent = Intent(this, EmulatorActivity::class.java).apply {
            putExtra(EmulatorActivity.KEY_URI, romUri)
        }
        startActivity(intent)
    }
}
