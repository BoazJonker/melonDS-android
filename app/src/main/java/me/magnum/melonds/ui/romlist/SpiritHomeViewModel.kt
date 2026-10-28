package me.magnum.melonds.ui.romlist

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.magnum.melonds.common.DirectoryAccessValidator
import me.magnum.melonds.common.Permission
import me.magnum.melonds.common.UriPermissionManager
import me.magnum.melonds.common.romprocessors.RomFileProcessorFactory
import me.magnum.melonds.domain.model.RomScanningStatus
import me.magnum.melonds.domain.model.rom.Rom
import me.magnum.melonds.domain.repositories.RomsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.utils.EventSharedFlow
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Start screen of the app. Only The Legend of Zelda: Spirit Tracks can be played: the player picks the folder that holds the game, and any other game in it
 * is ignored. The folder is used (rather than a single file) so that the game's save file is kept next to it, where it survives reinstalling the app.
 */
@HiltViewModel
class SpiritHomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val romsRepository: RomsRepository,
    private val settingsRepository: SettingsRepository,
    private val romFileProcessorFactory: RomFileProcessorFactory,
    private val uriPermissionManager: UriPermissionManager,
    private val directoryAccessValidator: DirectoryAccessValidator,
) : ViewModel() {

    sealed class State {
        data object Loading : State()
        data object NoGame : State()
        data class Ready(val rom: Rom) : State()
    }

    enum class Problem {
        // The chosen folder doesn't hold Spirit Tracks
        NOT_SPIRIT_TRACKS,
        // The app can't read or write the chosen folder
        NO_ACCESS,
    }

    private companion object {
        // Game codes of Spirit Tracks start with these letters, followed by a region letter (E: America, P: Europe, J: Japan, K: Korea...)
        const val SPIRIT_TRACKS_GAME_CODE = "BKI"
        val ROM_EXTENSIONS = setOf("nds", "zip", "7z")
        const val MAX_FOLDER_DEPTH = 3
    }

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _isCheckingFolder = MutableStateFlow(false)
    val isCheckingFolder: StateFlow<Boolean> = _isCheckingFolder.asStateFlow()

    private val _problemEvent = EventSharedFlow<Problem>()
    val problemEvent: Flow<Problem> = _problemEvent

    // Whether each ROM file is Spirit Tracks, so that each file is only read once
    private val spiritTracksCache = ConcurrentHashMap<Uri, Boolean>()

    init {
        viewModelScope.launch {
            combine(settingsRepository.observeRomSearchDirectories(), romsRepository.getRoms(), romsRepository.getRomScanningStatus()) { directories, roms, scanningStatus ->
                Triple(directories, roms, scanningStatus)
            }.collect { (directories, roms, scanningStatus) ->
                val spiritTracksRom = if (directories.isEmpty()) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        roms.filter { isSpiritTracks(it) }.maxByOrNull { it.lastPlayed?.time ?: 0L }
                    }
                }
                _state.value = when {
                    directories.isEmpty() -> State.NoGame
                    spiritTracksRom != null -> State.Ready(spiritTracksRom)
                    scanningStatus == RomScanningStatus.SCANNING -> State.Loading
                    else -> State.NoGame
                }
            }
        }
    }

    /**
     * Uses the chosen folder if it holds Spirit Tracks. Otherwise, the previous folder is kept and the player is told what went wrong.
     */
    fun onFolderSelected(folderUri: Uri) {
        viewModelScope.launch {
            val access = directoryAccessValidator.getDirectoryAccessForPermission(folderUri, Permission.READ_WRITE)
            if (access != DirectoryAccessValidator.DirectoryAccessResult.OK) {
                _problemEvent.tryEmit(Problem.NO_ACCESS)
                return@launch
            }

            _isCheckingFolder.value = true
            val hasSpiritTracks = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(context, folderUri)?.let { folderHasSpiritTracks(it, folderUri, 0) } ?: false
            }
            _isCheckingFolder.value = false

            if (hasSpiritTracks) {
                uriPermissionManager.persistDirectoryPermissions(folderUri, Permission.READ_WRITE)
                settingsRepository.addRomSearchDirectory(folderUri)
            } else {
                _problemEvent.tryEmit(Problem.NOT_SPIRIT_TRACKS)
            }
        }
    }

    fun setRomLastPlayedNow(rom: Rom) {
        romsRepository.setRomLastPlayed(rom, Calendar.getInstance().time)
    }

    private fun folderHasSpiritTracks(folder: DocumentFile, treeUri: Uri, depth: Int): Boolean {
        val files = runCatching { folder.listFiles() }.getOrNull() ?: return false
        return files.any { file ->
            if (file.isDirectory) {
                depth < MAX_FOLDER_DEPTH && folderHasSpiritTracks(file, treeUri, depth + 1)
            } else {
                val extension = file.name?.substringAfterLast('.', "")?.lowercase()
                extension in ROM_EXTENSIONS && isSpiritTracksFile(file, treeUri)
            }
        }
    }

    private fun isSpiritTracksFile(file: DocumentFile, treeUri: Uri): Boolean {
        return runCatching {
            val processor = romFileProcessorFactory.getFileRomProcessorForDocument(file) ?: return false
            val rom = processor.getRomFromUri(file.uri, treeUri) ?: return false
            processor.getRomInfo(rom)?.gameCode?.startsWith(SPIRIT_TRACKS_GAME_CODE) == true
        }.getOrDefault(false)
    }

    private fun isSpiritTracks(rom: Rom): Boolean {
        return spiritTracksCache.getOrPut(rom.uri) {
            runCatching {
                val processor = romFileProcessorFactory.getFileRomProcessorForDocument(rom.uri) ?: return@runCatching false
                processor.getRomInfo(rom)?.gameCode?.startsWith(SPIRIT_TRACKS_GAME_CODE) == true
            }.getOrDefault(false)
        }
    }
}
