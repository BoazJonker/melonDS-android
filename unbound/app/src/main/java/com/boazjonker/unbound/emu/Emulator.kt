package com.boazjonker.unbound.emu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs the game on its own thread. Writing the sound to the speaker paces the game at its normal speed; while fast-forwarding, the sound is skipped and
 * frames are timed with the clock instead.
 */
class Emulator(private val onFrame: () -> Unit) {

    companion object {
        const val SAMPLE_RATE = 48000
        private const val GBA_FPS = 59.7275
    }

    @Volatile
    var keys = 0

    /**
     * Speed multiplier while fast-forwarding, or 1 for normal speed.
     */
    @Volatile
    var speed = 1

    @Volatile
    var volume = 1f
        set(value) {
            field = value
            audioTrack?.setVolume(value)
        }

    @Volatile
    private var paused = false

    @Volatile
    private var running = false

    private val lock = Object()
    private val tasks = ConcurrentLinkedQueue<() -> Unit>()
    private var thread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "Emulation").apply { start() }
    }

    fun pause() {
        paused = true
    }

    fun resume() {
        synchronized(lock) {
            paused = false
            lock.notifyAll()
        }
    }

    val isPaused get() = paused

    /**
     * Runs [task] on the emulation thread between two frames, and waits for it to finish (at most [timeoutMs]). Returns false if it did not finish in time.
     */
    fun runAndWait(timeoutMs: Long = 2000, task: () -> Unit): Boolean {
        if (!running) {
            task()
            return true
        }
        val latch = CountDownLatch(1)
        tasks.add {
            try {
                task()
            } finally {
                latch.countDown()
            }
        }
        synchronized(lock) { lock.notifyAll() }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Runs [task] on the emulation thread between two frames, without waiting for it.
     */
    fun post(task: () -> Unit) {
        tasks.add(task)
        synchronized(lock) { lock.notifyAll() }
    }

    fun stop() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        thread?.join(2000)
        thread = null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val track = createAudioTrack()
        audioTrack = track
        track?.setVolume(volume)
        track?.play()

        val audio = ShortArray(4096)
        var nextFrameTime = SystemClock.elapsedRealtimeNanos()
        var wasPlayingAudio = false

        while (running) {
            runTasks()
            if (paused) {
                track?.pause()
                synchronized(lock) {
                    while (paused && running && tasks.isEmpty()) {
                        lock.wait()
                    }
                }
                if (!paused) {
                    track?.play()
                }
                nextFrameTime = SystemClock.elapsedRealtimeNanos()
                continue
            }

            val currentSpeed = speed
            val samples = NativeCore.runFrame(keys, audio)
            onFrame()

            if (currentSpeed <= 1 && track != null) {
                if (!wasPlayingAudio) {
                    track.flush()
                    track.play()
                }
                // Blocks while the speaker's buffer is full, which keeps the game at its normal speed
                track.write(audio, 0, samples * 2, AudioTrack.WRITE_BLOCKING)
                wasPlayingAudio = true
                nextFrameTime = SystemClock.elapsedRealtimeNanos()
            } else {
                if (wasPlayingAudio) {
                    track?.pause()
                    wasPlayingAudio = false
                }
                val frameNanos = (1_000_000_000.0 / (GBA_FPS * currentSpeed.coerceAtLeast(1))).toLong()
                nextFrameTime += frameNanos
                val wait = nextFrameTime - SystemClock.elapsedRealtimeNanos()
                if (wait > 0) {
                    Thread.sleep(wait / 1_000_000, (wait % 1_000_000).toInt())
                } else if (wait < -frameNanos * 4) {
                    // Too far behind: don't try to catch up
                    nextFrameTime = SystemClock.elapsedRealtimeNanos()
                }
            }
        }

        runTasks()
        track?.stop()
        track?.release()
        audioTrack = null
    }

    private fun runTasks() {
        while (true) {
            val task = tasks.poll() ?: break
            task()
        }
    }

    private fun createAudioTrack(): AudioTrack? {
        return try {
            val minSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            // Around 3 frames of sound: enough to avoid crackles, small enough to keep the delay low
            val size = maxOf(minSize, 800 * 4 * 3)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(size)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            null
        }
    }
}
