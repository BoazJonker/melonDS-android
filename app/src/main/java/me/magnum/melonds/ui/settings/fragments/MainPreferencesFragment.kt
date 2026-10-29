package me.magnum.melonds.ui.settings.fragments

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreference
import com.smp.masterswitchpreference.MasterSwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.R
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.contracts.DirectoryPickerContract
import me.magnum.melonds.common.vibration.TouchVibrator
import me.magnum.melonds.domain.model.MicSource
import me.magnum.melonds.domain.model.VideoFiltering
import me.magnum.melonds.domain.model.VideoRenderer
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.isMicrophonePermissionGranted
import me.magnum.melonds.extensions.isSustainedPerformanceModeAvailable
import me.magnum.melonds.impl.SettingsBackupManager
import me.magnum.melonds.ui.inputsetup.InputSetupActivity
import me.magnum.melonds.ui.romlist.SpiritHomeViewModel
import me.magnum.melonds.ui.settings.PreferenceFragmentTitleProvider
import me.magnum.melonds.utils.RendererSupport
import me.magnum.melonds.utils.enumValueOfIgnoreCase
import javax.inject.Inject

/**
 * All settings on one page, in four sections (Graphics, Controls, Sound and Saves), with the advanced settings folded away under "More options".
 */
@AndroidEntryPoint
class MainPreferencesFragment : BasePreferenceFragment(), PreferenceFragmentTitleProvider {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var vibrator: TouchVibrator
    @Inject lateinit var settingsBackupManager: SettingsBackupManager

    // Checks that a newly chosen game folder holds Spirit Tracks, like the start screen does
    private val gameFolderViewModel: SpiritHomeViewModel by viewModels()

    private lateinit var micSourcePreference: ListPreference
    private lateinit var rewindPreference: MasterSwitchPreference

    private val microphonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            micSourcePreference.value = MicSource.DEVICE.name.lowercase()
        }
    }

    private val gameFolderLauncher = registerForActivityResult(DirectoryPickerContract(Permission.READ_WRITE)) { uri ->
        if (uri != null) {
            gameFolderViewModel.onFolderSelected(uri)
        }
    }

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runSettingsBackupTask(R.string.settings_backup_success, R.string.settings_backup_error) { settingsBackupManager.backup(uri) }
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runSettingsBackupTask(R.string.settings_restore_success, R.string.settings_restore_error) { settingsBackupManager.restore(uri) }
        }
    }

    override fun getTitle() = getString(R.string.settings)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_main, rootKey)

        setupGraphicsPreferences()
        setupControlsPreferences()
        setupSoundPreferences()
        setupSavesPreferences()
        setupMoreOptionsPreferences()
    }

    private fun setupGraphicsPreferences() {
        findPreference<ListPreference>("video_internal_resolution")?.apply {
            summaryProvider = Preference.SummaryProvider<ListPreference> { preference ->
                if (preference.value == "auto") {
                    getString(R.string.settings_auto_resolution_summary, settingsRepository.getAutoInternalResolutionScale())
                } else {
                    preference.entry
                }
            }
        }

        findPreference<SwitchPreference>("cel_shading")?.setOnPreferenceChangeListener { _, newValue ->
            val filter = if (newValue as Boolean) VideoFiltering.TOON else VideoFiltering.LINEAR
            preferenceManager.sharedPreferences?.edit { putString("video_filtering", filter.name.lowercase()) }
            true
        }
    }

    private fun setupControlsPreferences() {
        if (!vibrator.supportsVibration()) {
            findPreference<SwitchPreference>("input_touch_haptic_feedback_enabled")?.isVisible = false
        }
        findPreference<Preference>("input_key_mapping")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), InputSetupActivity::class.java))
            true
        }
    }

    private fun setupSoundPreferences() {
        findPreference<SeekBarPreference>("volume")?.let { volumePreference ->
            updateVolumePreferenceSummary(volumePreference, volumePreference.value)
            volumePreference.setOnPreferenceChangeListener { _, newValue ->
                updateVolumePreferenceSummary(volumePreference, newValue as Int)
                true
            }
        }

        micSourcePreference = findPreference("mic_source")!!
        micSourcePreference.setOnPreferenceChangeListener { _, newValue ->
            val newMicSource = enumValueOfIgnoreCase<MicSource>(newValue as String)
            if (newMicSource == MicSource.DEVICE && !requireContext().isMicrophonePermissionGranted()) {
                requestMicrophonePermission(false)
                false
            } else {
                true
            }
        }
    }

    private fun setupSavesPreferences() {
        val gameFolderPreference = findPreference<Preference>("game_folder")!!
        gameFolderPreference.setOnPreferenceClickListener {
            gameFolderLauncher.launch(null)
            true
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val gameFolderPreference = findPreference<Preference>("game_folder") ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    settingsRepository.observeRomSearchDirectories().collect { directories ->
                        gameFolderPreference.summary = directories.firstOrNull()?.let { getFolderName(it) } ?: getString(R.string.settings_game_folder_none)
                    }
                }
                launch {
                    gameFolderViewModel.problemEvent.collect { problem ->
                        val (title, message) = when (problem) {
                            SpiritHomeViewModel.Problem.NOT_SPIRIT_TRACKS -> R.string.home_not_spirit_tracks_title to R.string.home_not_spirit_tracks_message
                            SpiritHomeViewModel.Problem.NO_ACCESS -> R.string.home_no_access_title to R.string.home_no_access_message
                        }
                        AlertDialog.Builder(requireContext())
                            .setTitle(title)
                            .setMessage(message)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
            }
        }
    }

    private fun setupMoreOptionsPreferences() {
        findPreference<ListPreference>("video_renderer")?.let { rendererPreference ->
            if (!RendererSupport.isGpuRenderingSupported(requireContext())) {
                rendererPreference.isVisible = false
            } else if (!RendererSupport.isComputeRendererSupported(requireContext())) {
                val computeIndex = rendererPreference.entryValues.indexOfFirst { it == VideoRenderer.COMPUTE.name.lowercase() }
                rendererPreference.entries = rendererPreference.entries.filterIndexed { index, _ -> index != computeIndex }.toTypedArray()
                rendererPreference.entryValues = rendererPreference.entryValues.filterIndexed { index, _ -> index != computeIndex }.toTypedArray()
            }
        }

        findPreference<SwitchPreference>("enable_sustained_performance")?.isVisible = requireContext().isSustainedPerformanceModeAvailable()
        rewindPreference = findPreference("enable_rewind")!!

        findPreference<Preference>("backup_settings")?.setOnPreferenceClickListener {
            backupLauncher.launch(null)
            true
        }
        findPreference<Preference>("restore_settings")?.setOnPreferenceClickListener {
            restoreLauncher.launch(null)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh values that may have changed in other screens
        rewindPreference.onPreferenceChangeListener?.onPreferenceChange(rewindPreference, rewindPreference.sharedPreferences?.getBoolean(rewindPreference.key, false))
        findPreference<ListPreference>("video_internal_resolution")?.let { it.summaryProvider = it.summaryProvider }
    }

    private fun getFolderName(folderUri: Uri): String {
        return runCatching { DocumentFile.fromTreeUri(requireContext(), folderUri)?.name }.getOrNull() ?: folderUri.lastPathSegment.orEmpty()
    }

    private fun updateVolumePreferenceSummary(volumePreference: SeekBarPreference, volume: Int) {
        val volumePercentage = (volume / volumePreference.max.toFloat() * 100f).toInt()
        volumePreference.summary = getString(R.string.volume_percentage, volumePercentage)
    }

    private fun requestMicrophonePermission(overrideRationaleRequest: Boolean) {
        if (!overrideRationaleRequest && shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.microphone_permission_required)
                .setMessage(R.string.microphone_permission_required_info)
                .setPositiveButton(R.string.ok) { _, _ -> requestMicrophonePermission(true) }
                .show()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun runSettingsBackupTask(successMessage: Int, errorMessage: Int, task: suspend () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { task() }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    AlertDialog.Builder(requireContext())
                        .setMessage(successMessage)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
