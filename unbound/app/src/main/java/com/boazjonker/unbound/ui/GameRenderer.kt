package com.boazjonker.unbound.ui

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.boazjonker.unbound.Settings
import com.boazjonker.unbound.emu.NativeCore
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the game's frames, scaled to fill the view. The Sharp filter keeps the pixels crisp while smoothing the edges between them, so the picture looks
 * even at any size.
 */
class GameRenderer : GLSurfaceView.Renderer {

    private companion object {
        const val WIDTH = 240
        const val HEIGHT = 160

        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val FRAGMENT_PLAIN = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """

        // Sharp bilinear: nearest-neighbour inside each game pixel, linear blending only across the edges between pixels
        const val FRAGMENT_SHARP = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D uTexture;
            uniform vec2 uTextureSize;
            uniform vec2 uScale;
            varying vec2 vTexCoord;
            void main() {
                vec2 texel = vTexCoord * uTextureSize;
                vec2 texelFloor = floor(texel);
                vec2 fraction = texel - texelFloor;
                vec2 regionRange = 0.5 - 0.5 / uScale;
                vec2 centerDistance = fraction - 0.5;
                vec2 adjusted = (centerDistance - clamp(centerDistance, -regionRange, regionRange)) * uScale + 0.5;
                gl_FragColor = texture2D(uTexture, (texelFloor + adjusted) / uTextureSize);
            }
        """
    }

    @Volatile
    var filter = Settings.ScreenFilter.SHARP

    private val frame: ByteBuffer = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4).order(ByteOrder.nativeOrder())
    private val quad: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        // x, y, u, v
        put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f))
        position(0)
    }

    private var texture = 0
    private var plainProgram = 0
    private var sharpProgram = 0
    private var viewWidth = 1
    private var viewHeight = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        plainProgram = createProgram(VERTEX_SHADER, FRAGMENT_PLAIN)
        sharpProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHARP)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        texture = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, WIDTH, HEIGHT, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frame)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        if (NativeCore.copyFrame(frame)) {
            frame.position(0)
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, WIDTH, HEIGHT, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frame)
        }

        val currentFilter = filter
        val sampling = if (currentFilter == Settings.ScreenFilter.PIXELS) GLES20.GL_NEAREST else GLES20.GL_LINEAR
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, sampling)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, sampling)

        val program = if (currentFilter == Settings.ScreenFilter.SHARP) sharpProgram else plainProgram
        GLES20.glUseProgram(program)
        if (program == sharpProgram) {
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uTextureSize"), WIDTH.toFloat(), HEIGHT.toFloat())
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uScale"), viewWidth.toFloat() / WIDTH, viewHeight.toFloat() / HEIGHT)
        }
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
        quad.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(position)
        quad.position(2)
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(texCoord)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compileShader(GLES20.GL_VERTEX_SHADER, vertexSource))
        GLES20.glAttachShader(program, compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource))
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source.trimIndent())
        GLES20.glCompileShader(shader)
        return shader
    }
}
