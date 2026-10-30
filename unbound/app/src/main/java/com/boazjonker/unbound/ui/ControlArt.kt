package com.boazjonker.unbound.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import androidx.core.graphics.PathParser
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
 * The D-pad: a chunky cross with rounded, pillow-domed purple arms, a dished centre ringed in gold and raised gold arrows, on a solid gold body. The body is
 * drawn as stacked layers of the cross, each projected onto its own plane in perspective, so the pad looks solid from any angle. It sits in a deep well
 * inside a bevelled gold bezel, is seen from slightly below, and rocks toward the pressed arm.
 */
class DpadPainter {

    private companion object {
        const val HALF = 75f
        // Distance of the eye from the pad, in design units (the cross is 150 wide)
        const val EYE = 480f
        // The pad is seen from slightly below, so its gold body shows
        const val BASE_PITCH = 28f
        const val LAYERS = 12
        const val LAYER_STEP = 2f
    }

    /** Width of the cross. */
    var size = 0f
        private set

    private val cross = Path()
    private val arrows = Path()
    private val grooves = Path()
    private val scaled = Matrix()
    private val planeMatrix = Matrix()
    private val source = FloatArray(8)
    private val target = FloatArray(8)
    private val rotated = FloatArray(3)

    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wellRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x40FFEDB0 }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val domePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x61000000 }
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x5905030C }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val dishPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dishRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dishInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x8CF2C14E.toInt() }
    private val arrowShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x8C05030C.toInt() }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFF6D0.toInt(); strokeJoin = Paint.Join.ROUND }

    private val layerColours = intArrayOf(
        0xFF2E1C03.toInt(), 0xFF3E2604.toInt(), 0xFF4F3107.toInt(), 0xFF5E3B08.toInt(), 0xFF6B4510.toInt(), 0xFF7A5210.toInt(),
        0xFF8C5E10.toInt(), 0xFFA06A14.toInt(), 0xFFB87D1C.toInt(), 0xFFD39A2C.toInt(), Palette.GOLD, Palette.GOLD_LIGHT,
    )

    private var unit = 0f

    fun layout(size: Float) {
        this.size = size
        unit = size / 150f
        scaled.setTranslate(-HALF, -HALF)
        scaled.postScale(unit, unit)

        cross.reset()
        cross.addPath(PathParser.createPathFromPathData(
            "M61 0H89Q103 0 103 14V47H136Q150 47 150 61V89Q150 103 136 103H103V136Q103 150 89 150H61Q47 150 47 136V103H14Q0 103 0 89V61Q0 47 14 47H47V14Q47 0 61 0Z"
        ), scaled)
        arrows.reset()
        arrows.addPath(PathParser.createPathFromPathData(
            "M75 12L87 31H63Z M75 138L87 119H63Z M12 75L31 63V87Z M138 75L119 63V87Z"
        ), scaled)
        grooves.reset()
        grooves.addPath(PathParser.createPathFromPathData("M54 54L96 96M96 54L54 96"), scaled)

        val half = HALF * unit
        source[0] = -half; source[1] = -half
        source[2] = half; source[3] = -half
        source[4] = half; source[5] = half
        source[6] = -half; source[7] = half

        val bezel = 88f * unit
        bezelPaint.shader = SweepGradient(0f, 0f,
            intArrayOf(0xFFFFF6D0.toInt(), Palette.GOLD, 0xFF8C5E10.toInt(), 0xFFC8901E.toInt(), 0xFFFFF6D0.toInt()), floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f))
        wellPaint.shader = RadialGradient(0f, -bezel * 0.25f, bezel, 0xFF2A1E52.toInt(), 0xFF07040F.toInt(), Shader.TileMode.CLAMP)
        wellRim.strokeWidth = 3f * unit
        facePaint.shader = LinearGradient(0f, -half, 0f, half, intArrayOf(0xFF6E58B8.toInt(), 0xFF3A2C72.toInt(), 0xFF1A1140.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        // A pillow dome of light for an arm, drawn with its centre moved onto each arm
        domePaint.shader = RadialGradient(-6f * unit, -9f * unit, 30f * unit * 1.4f,
            intArrayOf(0x8CFFFFFF.toInt(), 0x1FFFFFFF, 0x4D000000), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        groovePaint.strokeWidth = 2f * unit
        outlinePaint.shader = LinearGradient(-half, -half, half, half, intArrayOf(0xFFFFF6D0.toInt(), Palette.GOLD, 0xFF8C5E10.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        outlinePaint.strokeWidth = 3.5f * unit
        dishPaint.shader = RadialGradient(0f, 0f, 20f * unit, intArrayOf(0xFF05030C.toInt(), 0xFF2A1E52.toInt(), 0xFF5A47A0.toInt()), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        dishRing.shader = outlinePaint.shader
        dishRing.strokeWidth = 3f * unit
        dishInner.strokeWidth = 1.5f * unit
        arrowPaint.shader = LinearGradient(0f, -half, 0f, half, 0xFFFFF6D0.toInt(), 0xFFB8801A.toInt(), Shader.TileMode.MIRROR)
        arrowEdge.strokeWidth = 1f * unit
    }

    private var sinPitch = 0f
    private var cosPitch = 1f
    private var sinYaw = 0f
    private var cosYaw = 1f

    /** Turns a point of the pad (design units, z toward the eye) into [rotated]. */
    private fun rotate(x: Float, y: Float, z: Float) {
        // Pitch: positive pushes the top arm away. Yaw: positive pushes the right arm away
        val y1 = y * cosPitch - z * sinPitch
        val z1 = y * sinPitch + z * cosPitch
        rotated[0] = x * cosYaw + z1 * sinYaw
        rotated[1] = y1
        rotated[2] = -x * sinYaw + z1 * cosYaw
    }

    /** Maps the flat pad onto its plane at depth [z], turned and in perspective. */
    private fun planeAt(z: Float) {
        for (k in 0 until 4) {
            rotate(source[k * 2] / unit, source[k * 2 + 1] / unit, z)
            val scale = EYE / (EYE - rotated[2]) * unit
            target[k * 2] = rotated[0] * scale
            target[k * 2 + 1] = rotated[1] * scale
        }
        planeMatrix.setPolyToPoly(source, 0, target, 0, 4)
    }

    /**
     * @param tiltX Tilt in degrees; positive pushes the top arm down (up is pressed)
     * @param tiltY Tilt in degrees; positive pushes the right arm down (right is pressed)
     * @param sink How far the pad dips into its well, 0 to 1
     * @param pressedDirections Bit 0 up, bit 1 down, bit 2 left, bit 3 right
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, tiltX: Float, tiltY: Float, sink: Float, pressedDirections: Int) {
        canvas.save()
        canvas.translate(cx, cy)

        // Bevelled gold bezel and the deep well; they do not move
        val bezel = 88f * unit
        canvas.save()
        canvas.translate(0f, 6f * unit)
        canvas.drawCircle(0f, 0f, bezel, bezelPaint)
        canvas.drawCircle(0f, 0f, bezel - 8f * unit, wellPaint)
        canvas.drawCircle(0f, 0f, bezel - 9.5f * unit, wellRim)
        canvas.restore()

        val pitch = Math.toRadians((BASE_PITCH + tiltX).toDouble())
        val yaw = Math.toRadians(tiltY.toDouble())
        sinPitch = kotlin.math.sin(pitch).toFloat()
        cosPitch = kotlin.math.cos(pitch).toFloat()
        sinYaw = kotlin.math.sin(yaw).toFloat()
        cosYaw = kotlin.math.cos(yaw).toFloat()
        val dip = -6f * sink

        // Shadow in the well
        planeAt(dip - LAYERS * LAYER_STEP)
        canvas.save()
        canvas.translate(4f * unit, 12f * unit)
        canvas.concat(planeMatrix)
        canvas.drawPath(cross, shadowPaint)
        canvas.restore()

        // The solid gold body: layers from the deepest, darkest one up to the bright edge under the face
        for (layer in 0 until LAYERS) {
            planeAt(dip - (LAYERS - layer) * LAYER_STEP + if (layer == LAYERS - 1) 1f else 0f)
            canvas.save()
            canvas.concat(planeMatrix)
            layerPaint.color = layerColours[layer]
            canvas.drawPath(cross, layerPaint)
            canvas.restore()
        }

        // The face
        planeAt(dip)
        canvas.concat(planeMatrix)
        canvas.drawPath(cross, facePaint)
        canvas.save()
        canvas.clipPath(cross)
        val armCentre = 49f * unit
        for ((dx, dy) in arrayOf(0f to -armCentre, 0f to armCentre, -armCentre to 0f, armCentre to 0f)) {
            canvas.save()
            canvas.translate(dx, dy)
            canvas.drawCircle(0f, 0f, 30f * unit * 1.4f, domePaint)
            canvas.restore()
        }
        val end = HALF * unit
        val arm = 28f * unit
        val inner = 25f * unit
        if (pressedDirections and 1 != 0) canvas.drawRect(-arm, -end, arm, -inner, shadePaint)
        if (pressedDirections and 2 != 0) canvas.drawRect(-arm, inner, arm, end, shadePaint)
        if (pressedDirections and 4 != 0) canvas.drawRect(-end, -arm, -inner, arm, shadePaint)
        if (pressedDirections and 8 != 0) canvas.drawRect(inner, -arm, end, arm, shadePaint)
        canvas.drawPath(grooves, groovePaint)
        canvas.restore()
        canvas.drawPath(cross, outlinePaint)

        canvas.drawCircle(0f, 0f, 20f * unit, dishPaint)
        canvas.drawCircle(0f, 0f, 20f * unit, dishRing)
        canvas.drawCircle(0f, 0f, 9f * unit, dishInner)

        canvas.save()
        canvas.translate(1f * unit, 3f * unit)
        canvas.drawPath(arrows, arrowShadow)
        canvas.restore()
        canvas.drawPath(arrows, arrowPaint)
        canvas.drawPath(arrows, arrowEdge)

        canvas.restore()
    }
}
