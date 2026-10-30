package com.boazjonker.unbound.ui

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * A and B: a glossy purple cap with a gold letter, sitting in a dark socket, with a raised 3D gold ring as the outermost layer. Pressing sinks the cap and
 * makes the gold ring grow outward and catch more light. Drawn around (0, 0); [layout] sets the size.
 */
class AbButtonPainter(private val letter: String) {

    /** Diameter of the gold ring, the outermost part of the button. */
    var size = 0f
        private set

    private val socketPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringSidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5E3B08.toInt() }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringShadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringLightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capSidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E0F48.toInt() }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capShadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD_LIGHT }
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        color = Palette.GOLD_LIGHT
    }
    private val ringPath = Path()
    private val gloss = RectF()

    private var outer = 0f
    private var inner = 0f
    private var capRadius = 0f
    private var depth = 0f

    fun layout(size: Float) {
        this.size = size
        outer = size / 2f
        inner = outer * 0.55f
        capRadius = outer * 0.5f
        depth = size * 0.06f

        ringPath.reset()
        ringPath.fillType = Path.FillType.EVEN_ODD
        ringPath.addCircle(0f, 0f, outer, Path.Direction.CW)
        ringPath.addCircle(0f, 0f, inner, Path.Direction.CW)

        socketPaint.shader = RadialGradient(-outer * 0.1f, -outer * 0.3f, outer, 0xFF3A2F64.toInt(), 0xFF120C24.toInt(), Shader.TileMode.CLAMP)
        ringPaint.shader = LinearGradient(-outer * 0.4f, -outer, outer * 0.4f, outer,
            intArrayOf(0xFFFFF6D0.toInt(), Palette.GOLD, 0xFFA86F12.toInt(), 0xFFFFE39A.toInt()), floatArrayOf(0f, 0.35f, 0.72f, 1f), Shader.TileMode.CLAMP)
        // Rounded like a torus: darker at the inner and outer edges, bright across the middle of the band
        ringShadePaint.shader = RadialGradient(0f, 0f, outer,
            intArrayOf(0x61000000, 0x61000000, 0x00000000, 0x00000000, 0x66000000), floatArrayOf(0f, 0.55f, 0.68f, 0.84f, 1f), Shader.TileMode.CLAMP)
        capPaint.shader = RadialGradient(-capRadius * 0.24f, -capRadius * 0.4f, capRadius * 1.5f,
            intArrayOf(0xFFD2BEFF.toInt(), 0xFF8A5CF0.toInt(), Palette.VIOLET, 0xFF2A1A6E.toInt()), floatArrayOf(0f, 0.32f, 0.62f, 1f), Shader.TileMode.CLAMP)
        capShadePaint.shader = LinearGradient(0f, 0f, 0f, capRadius, 0x00000000, 0x4D000000, Shader.TileMode.CLAMP)
        gloss.set(-capRadius * 0.6f, -capRadius * 0.82f, capRadius * 0.6f, -capRadius * 0.3f)
        glossPaint.shader = LinearGradient(0f, gloss.top, 0f, gloss.bottom, 0x8CFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        letterPaint.textSize = capRadius * 0.95f
        letterPaint.setShadowLayer(capRadius * 0.05f, 0f, capRadius * 0.08f, 0xFF2A1406.toInt())
        glowPaint.strokeWidth = size * 0.035f
    }

    /**
     * @param press 0 (up) to 1 (pressed); goes a little below 0 while springing back
     * @param ripple Progress of the glow ring sent out by a press, or negative for none
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, press: Float, ripple: Float) {
        val p = press.coerceIn(-0.3f, 1f)
        canvas.save()
        canvas.translate(cx, cy)

        canvas.drawCircle(0f, depth * 0.4f, outer * 0.92f, socketPaint)

        if (ripple in 0f..1f) {
            glowPaint.alpha = ((1f - ripple) * 200).toInt()
            canvas.drawCircle(0f, 0f, outer * (1.08f + ripple * 0.25f), glowPaint)
        }

        // Purple cap: sinks into the socket and darkens
        canvas.drawCircle(0f, depth, capRadius, capSidePaint)
        canvas.save()
        canvas.translate(0f, depth * 1.15f * p)
        canvas.drawCircle(0f, 0f, capRadius, capPaint)
        canvas.drawCircle(0f, 0f, capRadius, capShadePaint)
        canvas.drawOval(gloss, glossPaint)
        canvas.drawText(letter, 0f, -(letterPaint.descent() + letterPaint.ascent()) / 2f, letterPaint)
        if (p > 0f) {
            darkenPaint.alpha = (p * 46).toInt()
            canvas.drawCircle(0f, 0f, capRadius, darkenPaint)
        }
        canvas.restore()

        // Gold ring: grows outward when pressed
        val ringScale = 1f + 0.12f * p
        canvas.save()
        canvas.scale(ringScale, ringScale)
        canvas.save()
        canvas.translate(0f, depth)
        canvas.drawPath(ringPath, ringSidePaint)
        canvas.restore()
        canvas.drawPath(ringPath, ringPaint)
        canvas.drawPath(ringPath, ringShadePaint)
        if (p > 0f) {
            ringLightPaint.color = 0xFFFFF6D0.toInt()
            ringLightPaint.alpha = (p * 60).toInt()
            canvas.drawPath(ringPath, ringLightPaint)
        }
        canvas.restore()

        canvas.restore()
    }
}

/**
 * The D-pad: a purple metal cross on a thick, stepped gold body, with bevelled edges, a gold outline, arms that slope down into a sunken centre pivot and
 * raised gold arrows. It sits in a deep socket with a gold rim and tips toward the pressed direction like a real rocker pad. Drawn around (0, 0).
 */
class DpadPainter {

    /** Width of the cross. */
    var size = 0f
        private set

    private val camera = Camera()
    private val matrix = Matrix()
    private val cross = Path()
    private val innerLine = Path()
    private val arrows = Path()
    private val scaled = Matrix()

    private val socketPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val socketRimDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF8C5E10.toInt() }
    private val socketRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD }
    private val socketInnerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x80F2C14E.toInt() }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73000000 }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dishPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val bevelDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val innerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x4DF2C14E }
    private val pivotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pivotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xB3F2C14E.toInt() }
    private val arrowShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x990A0614.toInt() }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF8C5E10.toInt() }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }
    private val gloss = RectF()

    private val bodyColours = intArrayOf(
        0xFF3E2604.toInt(), 0xFF4F3107.toInt(), 0xFF6B4510.toInt(), 0xFF845612.toInt(), 0xFFA06A14.toInt(), 0xFFBE8420.toInt(), 0xFFD9A030.toInt(),
    )

    private var unit = 0f
    private var socketRadius = 0f

    /**
     * The cross path and its parts come from the 150x150 design, centred on (75, 75), scaled to [size].
     */
    fun layout(size: Float) {
        this.size = size
        unit = size / 142f
        socketRadius = 84f * unit

        scaled.setTranslate(-75f, -75f)
        scaled.postScale(unit, unit)
        cross.reset()
        cross.addPath(roundedCross(), scaled)
        innerLine.reset()
        innerLine.addPath(Path().apply {
            moveTo(56f, 12f); lineTo(94f, 12f); lineTo(94f, 54f); lineTo(136f, 54f); lineTo(136f, 96f); lineTo(94f, 96f); lineTo(94f, 138f)
            lineTo(56f, 138f); lineTo(56f, 96f); lineTo(14f, 96f); lineTo(14f, 54f); lineTo(56f, 54f); close()
        }, scaled)
        arrows.reset()
        arrows.addPath(Path().apply {
            moveTo(75f, 13f); lineTo(86f, 30f); lineTo(64f, 30f); close()
            moveTo(75f, 137f); lineTo(86f, 120f); lineTo(64f, 120f); close()
            moveTo(13f, 75f); lineTo(30f, 64f); lineTo(30f, 86f); close()
            moveTo(137f, 75f); lineTo(120f, 64f); lineTo(120f, 86f); close()
        }, scaled)

        val half = 71f * unit
        socketPaint.shader = RadialGradient(0f, -socketRadius * 0.2f, socketRadius, 0xFF241A44.toInt(), 0xFF07040F.toInt(), Shader.TileMode.CLAMP)
        socketRimDark.strokeWidth = 3f * unit
        socketRim.strokeWidth = 2f * unit
        socketInnerRing.strokeWidth = 1.5f * unit
        facePaint.shader = LinearGradient(0f, -half, 0f, half, intArrayOf(0xFF7461B8.toInt(), 0xFF3A2C72.toInt(), 0xFF1A1140.toInt()), null, Shader.TileMode.CLAMP)
        dishPaint.shader = RadialGradient(0f, 0f, half, intArrayOf(0x80000000.toInt(), 0x24000000, 0x00000000), floatArrayOf(0f, 0.35f, 0.7f), Shader.TileMode.CLAMP)
        bevelLight.shader = LinearGradient(-half, -half, half, half, intArrayOf(0x99FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.45f), Shader.TileMode.CLAMP)
        bevelLight.strokeWidth = 14f * unit
        bevelDark.shader = LinearGradient(-half, -half, half, half, intArrayOf(0x00000000, 0x99000000.toInt()), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        bevelDark.strokeWidth = 14f * unit
        gloss.set(-58f * unit, -75f * unit, 58f * unit, -27f * unit)
        glossPaint.shader = LinearGradient(0f, gloss.top, 0f, gloss.bottom, 0x6BFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        outlinePaint.shader = LinearGradient(-half, -half, half, half, intArrayOf(0xFFFFF6D0.toInt(), Palette.GOLD, 0xFF8C5E10.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        outlinePaint.strokeWidth = 3.5f * unit
        innerLinePaint.strokeWidth = 1f * unit
        pivotPaint.shader = RadialGradient(-3f * unit, -4f * unit, 14f * unit, 0xFF0A0614.toInt(), 0xFF3A2C72.toInt(), Shader.TileMode.CLAMP)
        pivotRing.strokeWidth = 1.5f * unit
        arrowPaint.shader = LinearGradient(0f, -half, 0f, half, 0xFFFFF6D0.toInt(), 0xFFC8901E.toInt(), Shader.TileMode.MIRROR)
        arrowEdge.strokeWidth = 0.8f * unit
    }

    private fun roundedCross(): Path {
        val vertical = Path().apply { addRoundRect(46f, 4f, 104f, 146f, 4f, 4f, Path.Direction.CW) }
        val horizontal = Path().apply { addRoundRect(4f, 46f, 146f, 104f, 4f, 4f, Path.Direction.CW) }
        vertical.op(horizontal, Path.Op.UNION)
        return vertical
    }

    /**
     * @param tiltX Tilt in degrees; positive pushes the top arm down (up is pressed)
     * @param tiltY Tilt in degrees; positive pushes the right arm down (right is pressed)
     * @param sink How far the pad dips into its socket, 0 to 1
     * @param pressedDirections Bit 0 up, bit 1 down, bit 2 left, bit 3 right
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, tiltX: Float, tiltY: Float, sink: Float, pressedDirections: Int) {
        canvas.save()
        canvas.translate(cx, cy)

        // Deep socket with a gold rim; it does not tilt
        canvas.drawCircle(0f, 4f * unit, socketRadius, socketPaint)
        canvas.drawCircle(0f, 4f * unit, socketRadius + 1.5f * unit, socketRimDark)
        canvas.drawCircle(0f, 4f * unit, socketRadius + 3.5f * unit, socketRim)
        canvas.drawCircle(0f, 6f * unit, socketRadius - 6f * unit, socketInnerRing)

        camera.save()
        camera.setLocation(0f, 0f, -size * 2.6f / 72f)
        camera.rotateX(tiltX)
        camera.rotateY(-tiltY)
        camera.getMatrix(matrix)
        camera.restore()
        canvas.translate(0f, 2f * unit * sink)
        canvas.concat(matrix)

        // Soft shadow, then the gold body in steps from dark to light
        canvas.save()
        canvas.translate(5f * unit, 18f * unit)
        canvas.drawPath(cross, shadowPaint)
        canvas.restore()
        for ((index, colour) in bodyColours.withIndex()) {
            canvas.save()
            canvas.translate(0f, (14f - index * 2f) * unit)
            bodyPaint.color = colour
            canvas.drawPath(cross, bodyPaint)
            canvas.restore()
        }

        canvas.drawPath(cross, facePaint)
        canvas.save()
        canvas.clipPath(cross)
        canvas.drawPath(cross, dishPaint)
        val end = 71f * unit
        val arm = 29f * unit
        if (pressedDirections and 1 != 0) canvas.drawRect(-arm, -end, arm, -arm, shadePaint)
        if (pressedDirections and 2 != 0) canvas.drawRect(-arm, arm, arm, end, shadePaint)
        if (pressedDirections and 4 != 0) canvas.drawRect(-end, -arm, -arm, arm, shadePaint)
        if (pressedDirections and 8 != 0) canvas.drawRect(arm, -arm, end, arm, shadePaint)
        canvas.drawPath(cross, bevelLight)
        canvas.drawPath(cross, bevelDark)
        canvas.drawOval(gloss, glossPaint)
        canvas.restore()
        canvas.drawPath(cross, outlinePaint)
        canvas.drawPath(innerLine, innerLinePaint)

        canvas.drawCircle(0f, 0f, 14f * unit, pivotPaint)
        canvas.drawCircle(0f, 0f, 14f * unit, pivotRing)

        canvas.save()
        canvas.translate(1f * unit, 2.5f * unit)
        canvas.drawPath(arrows, arrowShadow)
        canvas.restore()
        canvas.drawPath(arrows, arrowPaint)
        canvas.drawPath(arrows, arrowEdge)

        canvas.restore()
    }
}
