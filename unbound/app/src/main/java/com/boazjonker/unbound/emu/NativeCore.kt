package com.boazjonker.unbound.emu

import java.nio.ByteBuffer

/**
 * The mGBA core. Only one game runs at a time. [runFrame] and the save state functions must be called from the emulation thread, [copyFrame] can be called
 * from any thread.
 */
object NativeCore {

    init {
        System.loadLibrary("unbound")
    }

    @JvmStatic
    external fun load(romPath: String, patchPath: String?, savePath: String, sampleRate: Int): Boolean

    @JvmStatic
    external fun unload()

    @JvmStatic
    external fun reset()

    /**
     * Runs one frame with the given keys held (see [GbaKeys]) and writes its audio, as interleaved stereo samples, into [audioOut]. Returns the number of
     * stereo samples written.
     */
    @JvmStatic
    external fun runFrame(keys: Int, audioOut: ShortArray): Int

    /**
     * Copies the latest frame into a direct buffer of [getVideoWidth] x [getVideoHeight] RGBA pixels.
     */
    @JvmStatic
    external fun copyFrame(buffer: ByteBuffer): Boolean

    @JvmStatic
    external fun saveState(path: String): Boolean

    @JvmStatic
    external fun loadState(path: String): Boolean

    @JvmStatic
    external fun getGameCode(): String

    @JvmStatic
    external fun getVideoWidth(): Int

    @JvmStatic
    external fun getVideoHeight(): Int
}

object GbaKeys {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val SELECT = 1 shl 2
    const val START = 1 shl 3
    const val RIGHT = 1 shl 4
    const val LEFT = 1 shl 5
    const val UP = 1 shl 6
    const val DOWN = 1 shl 7
    const val R = 1 shl 8
    const val L = 1 shl 9
}
