package com.boazjonker.unbound.emu

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * The game's files, all kept in the app's private storage: a copy of the ROM, an optional patch applied when the game starts, the in-game save and the
 * save states.
 */
class GameFiles(context: Context) {

    enum class ImportResult { OK, NOT_A_GBA_ROM, NOT_A_PATCH, FAILED }

    private val dir = File(context.filesDir, "game").apply { mkdirs() }
    private val contentResolver = context.contentResolver

    val rom = File(dir, "game.gba")
    val patch = File(dir, "patch.bin")
    val save = File(dir, "unbound.sav")
    val resumeState = File(dir, "resume.state")
    val slotState = File(dir, "slot1.state")

    val hasRom get() = rom.isFile

    /**
     * Copies a ROM into the app. Zip files are accepted too: the first .gba file inside is used.
     */
    fun importRom(uri: Uri): ImportResult {
        val temp = File(dir, "import.tmp")
        try {
            val copied = contentResolver.openInputStream(uri)?.use { input ->
                val buffered = input.buffered()
                buffered.mark(4)
                val magic = ByteArray(4)
                val read = buffered.read(magic)
                buffered.reset()
                if (read == 4 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) {
                    copyFirstGbaFromZip(buffered, temp)
                } else {
                    temp.outputStream().use { buffered.copyTo(it) }
                    true
                }
            } ?: false
            if (!copied) {
                return ImportResult.NOT_A_GBA_ROM
            }
            if (!isGbaRom(temp)) {
                return ImportResult.NOT_A_GBA_ROM
            }
            rom.delete()
            if (!temp.renameTo(rom)) {
                return ImportResult.FAILED
            }
            // The quick resume state belongs to the previous ROM
            resumeState.delete()
            return ImportResult.OK
        } catch (e: Exception) {
            return ImportResult.FAILED
        } finally {
            temp.delete()
        }
    }

    fun importPatch(uri: Uri): ImportResult {
        val temp = File(dir, "patch.tmp")
        try {
            contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return ImportResult.FAILED
            val magic = temp.inputStream().use { input -> ByteArray(5).also { input.read(it) } }
            val text = String(magic, Charsets.US_ASCII)
            if (!text.startsWith("BPS1") && !text.startsWith("UPS1") && text != "PATCH") {
                return ImportResult.NOT_A_PATCH
            }
            patch.delete()
            if (!temp.renameTo(patch)) {
                return ImportResult.FAILED
            }
            resumeState.delete()
            return ImportResult.OK
        } catch (e: Exception) {
            return ImportResult.FAILED
        } finally {
            temp.delete()
        }
    }

    fun removePatch() {
        patch.delete()
        resumeState.delete()
    }

    fun exportSave(uri: Uri): Boolean {
        return try {
            contentResolver.openOutputStream(uri, "wt")?.use { output -> save.inputStream().use { it.copyTo(output) } } != null
        } catch (e: Exception) {
            false
        }
    }

    fun importSave(uri: Uri): Boolean {
        val temp = File(dir, "save.tmp")
        return try {
            contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } ?: return false
            // Game saves are at most 128 KB (plus a small footer some emulators add)
            if (temp.length() == 0L || temp.length() > 256 * 1024) {
                return false
            }
            save.delete()
            val success = temp.renameTo(save)
            // The game in the quick resume state would overwrite the imported save
            resumeState.delete()
            success
        } catch (e: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    private fun copyFirstGbaFromZip(input: InputStream, target: File): Boolean {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return false
                if (!entry.isDirectory && entry.name.endsWith(".gba", ignoreCase = true)) {
                    target.outputStream().use { zip.copyTo(it) }
                    return true
                }
            }
        }
    }

    private fun isGbaRom(file: File): Boolean {
        if (file.length() < 0xC0) {
            return false
        }
        val header = file.inputStream().use { input -> ByteArray(0xC0).also { input.read(it) } }
        // Fixed value in every GBA cartridge header
        return header[0xB2] == 0x96.toByte()
    }
}
