package me.magnum.melonds.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object RendererSupport {
    private const val GLES_3_2 = 0x30002

    /**
     * Whether the device supports the OpenGL and compute renderers, which need OpenGL ES 3.2.
     */
    fun isGpuRenderingSupported(context: Context): Boolean {
        val glesVersion = context.getSystemService<ActivityManager>()?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        return glesVersion >= GLES_3_2
    }

    /**
     * Whether the device supports the compute renderer. Only Adreno GPUs (Qualcomm chipsets) are supported for now.
     */
    fun isComputeRendererSupported(context: Context): Boolean {
        return isGpuRenderingSupported(context) && Build.HARDWARE.lowercase() == "qcom"
    }
}
