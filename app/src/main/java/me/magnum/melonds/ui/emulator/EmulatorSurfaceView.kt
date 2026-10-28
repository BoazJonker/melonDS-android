package me.magnum.melonds.ui.emulator

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import me.magnum.melonds.domain.model.render.PresentFrameWrapper
import me.magnum.melonds.ui.emulator.model.RuntimeRendererConfiguration
import me.magnum.melonds.ui.emulator.render.EmulatorRenderer
import me.magnum.melonds.ui.emulator.render.GlContext
import me.magnum.melonds.ui.emulator.render.SurfaceRenderer

class EmulatorSurfaceView(context: Context, attrs: AttributeSet? = null) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private companion object {
        const val GAME_FRAME_RATE = 60f
    }

    private val surfaceLock = Any()
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var surface: Surface? = null
    private var renderer: EmulatorRenderer? = null
    private var surfaceRenderer: SurfaceRenderer? = null

    init {
        holder.addCallback(this)
    }

    fun setRenderer(emulatorRenderer: EmulatorRenderer) {
        renderer = emulatorRenderer
    }

    fun updateRendererConfiguration(newRendererConfiguration: RuntimeRendererConfiguration?) {
        renderer?.updateRendererConfiguration(newRendererConfiguration)
    }

    fun setSurfaceRenderer(newRenderer: SurfaceRenderer, glContext: GlContext) {
        synchronized(surfaceLock) {
            surfaceRenderer?.stop(glContext)
            surfaceRenderer = newRenderer
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        synchronized(surfaceLock) {
            surface = holder.surface
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The game runs at 60 FPS: ask for a refresh rate that's a multiple of 60 (like 60 or 120 Hz), so that every frame is shown for the same amount
            // of time. With other rates, like 90 Hz, some frames stay on screen longer than others, which makes movement look uneven
            runCatching { holder.surface.setFrameRate(GAME_FRAME_RATE, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(surfaceLock) {
            surfaceWidth = width
            surfaceHeight = height
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        synchronized(surfaceLock) {
            surface = null
        }
    }

    fun doFrame(glContext: GlContext, presentFrameWrapper: PresentFrameWrapper) {
        synchronized(surfaceLock) {
            surfaceRenderer?.doFrame(
                glContext = glContext,
                surfaceView = this,
                surface = surface,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                renderer = renderer,
                presentFrameWrapper = presentFrameWrapper,
            )
        }
    }

    fun stop(glContext: GlContext) {
        synchronized(surfaceLock) {
            surfaceRenderer?.stop(glContext)
        }
    }
}