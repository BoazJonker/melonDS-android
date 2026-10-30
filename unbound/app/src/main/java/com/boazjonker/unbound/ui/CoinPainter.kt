package com.boazjonker.unbound.ui

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Draws the gold medallion as a 3D coin: a thick gold ring (darker layers behind it give it depth), with a violet inside and the dark band of a Poké
 * Ball. It can be turned around its vertical axis, like a coin spinning on a table.
 */
class CoinPainter {

    private val camera = Camera()
    private val matrix = Matrix()

    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val insidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thinRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x99F2C14E.toInt() }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.INK }
    private val bandEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCF2C14E.toInt() }
    private val arc = RectF()

    private var shadersFor = -1f

    private val layerColours = intArrayOf(0xFF5E3B08.toInt(), 0xFF7A5210.toInt(), 0xFFA86F12.toInt())

    private fun updateShaders(r: Float) {
        if (r == shadersFor) return
        shadersFor = r
        // Shaders are built around (0, 0), the coin is drawn translated
        ringPaint.shader = LinearGradient(0f, -r, 0f, r, intArrayOf(Palette.GOLD_LIGHT, Palette.GOLD, Palette.GOLD_DARK), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        insidePaint.shader = RadialGradient(0f, -r * 0.2f, r * 1.2f, 0xE63A2690.toInt(), 0xE6120A35.toInt(), Shader.TileMode.CLAMP)
    }

    /**
     * @param rotationY Turn around the vertical axis, in degrees
     * @param faceAlpha Opacity of the inside and band (0 to 1); the ring alone stays when it is 0
     * @param glow Extra gold glow around the ring, 0 to 1
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float, rotationY: Float, faceAlpha: Float = 1f, glow: Float = 0f) {
        updateShaders(r)
        val ringWidth = r * 0.117f
        val ringRadius = r - ringWidth / 2f
        val depthUnit = r / 120f

        canvas.save()
        canvas.translate(cx, cy)

        // Darker gold layers behind the front ring give the coin its thickness
        for ((index, z) in floatArrayOf(8f, 5f, 2f).withIndex()) {
            withCamera(canvas, rotationY, z * depthUnit) {
                layerPaint.color = layerColours[index]
                layerPaint.strokeWidth = ringWidth
                canvas.drawCircle(0f, 0f, ringRadius, layerPaint)
            }
        }

        if (faceAlpha > 0f) {
            withCamera(canvas, rotationY, 5f * depthUnit) {
                val alpha = (faceAlpha * 255).toInt()
                insidePaint.alpha = alpha
                canvas.drawCircle(0f, 0f, r - ringWidth * 0.6f, insidePaint)
                thinRingPaint.alpha = (faceAlpha * 153).toInt()
                thinRingPaint.strokeWidth = r * 0.012f
                canvas.drawCircle(0f, 0f, r * 0.8f, thinRingPaint)
                val bandHalf = r * 0.065f
                bandPaint.alpha = alpha
                bandEdgePaint.alpha = (faceAlpha * 204).toInt()
                canvas.drawRect(-r + ringWidth * 0.5f, -bandHalf, r - ringWidth * 0.5f, bandHalf, bandPaint)
                canvas.drawRect(-r + ringWidth * 0.5f, -bandHalf - r * 0.008f, r - ringWidth * 0.5f, -bandHalf + r * 0.008f, bandEdgePaint)
                canvas.drawRect(-r + ringWidth * 0.5f, bandHalf - r * 0.008f, r - ringWidth * 0.5f, bandHalf + r * 0.008f, bandEdgePaint)
            }
        }

        withCamera(canvas, rotationY, 0f) {
            if (glow > 0f) {
                glowPaint.color = Palette.GOLD
                glowPaint.alpha = (glow * 150).toInt()
                glowPaint.strokeWidth = ringWidth * 2.2f
                canvas.drawCircle(0f, 0f, ringRadius, glowPaint)
            }
            ringPaint.strokeWidth = ringWidth
            canvas.drawCircle(0f, 0f, ringRadius, ringPaint)
            // Bevel: a bright outer edge on top and a shaded inner edge
            edgePaint.strokeWidth = r * 0.018f
            edgePaint.color = 0xFFFFF6D0.toInt()
            arc.set(-r, -r, r, r)
            canvas.drawArc(arc, 190f, 160f, false, edgePaint)
            edgePaint.color = 0xFF6B4510.toInt()
            canvas.drawArc(arc, 10f, 160f, false, edgePaint)
            val inner = r - ringWidth
            arc.set(-inner, -inner, inner, inner)
            edgePaint.color = 0xFF8C5E10.toInt()
            canvas.drawArc(arc, 190f, 160f, false, edgePaint)
            edgePaint.color = 0xCCFFF6D0.toInt()
            canvas.drawArc(arc, 10f, 160f, false, edgePaint)
            // Shine across the top left
            shinePaint.color = 0x8CFFFAE1.toInt()
            shinePaint.strokeWidth = ringWidth * 0.55f
            arc.set(-ringRadius, -ringRadius, ringRadius, ringRadius)
            canvas.drawArc(arc, 200f, 50f, false, shinePaint)
        }

        canvas.restore()
    }

    private inline fun withCamera(canvas: Canvas, rotationY: Float, z: Float, block: () -> Unit) {
        camera.save()
        // Keep the perspective gentle: the camera sits far away compared to the coin
        camera.setLocation(0f, 0f, -24f)
        camera.rotateY(rotationY)
        camera.translate(0f, 0f, z)
        camera.getMatrix(matrix)
        camera.restore()
        canvas.save()
        canvas.concat(matrix)
        block()
        canvas.restore()
    }
}
