package com.boazjonker.unbound.ui

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
 * The D-pad, drawn as a real 3D block: a cross-shaped slab of purple metal on thick gold side walls, seen from slightly below so the walls show. The top
 * has bevelled edges, a gold outline, arms that slope down into a sunken pivot and raised gold arrows. When pressed, the whole block rocks toward that arm
 * and dips into its gold-rimmed socket. The corners of the block are turned and projected in 3D, so its walls and top keep their true perspective.
 */
class DpadPainter {

    private companion object {
        // Half the width of an arm and half the length of the cross, in the 150-wide design
        const val ARM = 28f
        const val END = 75f
        const val THICKNESS = 18f
        // Distance of the eye from the pad, in design units
        const val EYE = 520f
        // The pad is seen from slightly below, so its lower walls show
        const val BASE_PITCH = 24f
    }

    /** Width of the cross. */
    var size = 0f
        private set

    private val corners = floatArrayOf(
        -ARM, -END, ARM, -END, ARM, -ARM, END, -ARM, END, ARM, ARM, ARM,
        ARM, END, -ARM, END, -ARM, ARM, -END, ARM, -END, -ARM, -ARM, -ARM,
    )
    private val top = FloatArray(24)
    private val bottom = FloatArray(24)
    private val topZ = FloatArray(12)
    private val bottomZ = FloatArray(12)
    private val square = FloatArray(8)
    private val squareSource = FloatArray(8)
    private val faceMatrix = Matrix()
    private val rotated = FloatArray(3)

    private class Wall(var index: Int = 0, var depth: Float = 0f, var shade: Float = 0f)
    private val walls = Array(12) { Wall() }
    private val visibleWalls = ArrayList<Wall>(12)

    private val cross = Path()
    private val innerLine = Path()
    private val arrows = Path()
    private val scaled = Matrix()
    private val polygon = Path()

    private val socketPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val socketRimDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF8C5E10.toInt() }
    private val socketRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD }
    private val socketInnerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x80F2C14E.toInt() }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x80000000.toInt() }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3E2604.toInt() }
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x593E2604; strokeJoin = Paint.Join.ROUND }
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

    private var unit = 0f
    private var socketRadius = 0f

    fun layout(size: Float) {
        this.size = size
        unit = size / 150f
        socketRadius = 88f * unit

        scaled.setTranslate(-75f, -75f)
        scaled.postScale(unit, unit)
        cross.reset()
        for (i in 0 until 12) {
            val x = corners[i * 2] * unit
            val y = corners[i * 2 + 1] * unit
            if (i == 0) cross.moveTo(x, y) else cross.lineTo(x, y)
        }
        cross.close()
        innerLine.reset()
        innerLine.addPath(Path().apply {
            moveTo(55f, 9f); lineTo(95f, 9f); lineTo(95f, 55f); lineTo(141f, 55f); lineTo(141f, 95f); lineTo(95f, 95f); lineTo(95f, 141f)
            lineTo(55f, 141f); lineTo(55f, 95f); lineTo(9f, 95f); lineTo(9f, 55f); lineTo(55f, 55f); close()
        }, scaled)
        arrows.reset()
        arrows.addPath(Path().apply {
            moveTo(75f, 11f); lineTo(86f, 28f); lineTo(64f, 28f); close()
            moveTo(75f, 139f); lineTo(86f, 122f); lineTo(64f, 122f); close()
            moveTo(11f, 75f); lineTo(28f, 64f); lineTo(28f, 86f); close()
            moveTo(139f, 75f); lineTo(122f, 64f); lineTo(122f, 86f); close()
        }, scaled)

        val half = END * unit
        squareSource[0] = -half; squareSource[1] = -half
        squareSource[2] = half; squareSource[3] = -half
        squareSource[4] = half; squareSource[5] = half
        squareSource[6] = -half; squareSource[7] = half

        socketPaint.shader = RadialGradient(0f, -socketRadius * 0.2f, socketRadius, 0xFF241A44.toInt(), 0xFF07040F.toInt(), Shader.TileMode.CLAMP)
        socketRimDark.strokeWidth = 3f * unit
        socketRim.strokeWidth = 2f * unit
        socketInnerRing.strokeWidth = 1.5f * unit
        wallEdge.strokeWidth = 1f * unit
        facePaint.shader = LinearGradient(0f, -half, 0f, half, intArrayOf(0xFF7461B8.toInt(), 0xFF3A2C72.toInt(), 0xFF1A1140.toInt()), null, Shader.TileMode.CLAMP)
        dishPaint.shader = RadialGradient(0f, 0f, half, intArrayOf(0x80000000.toInt(), 0x24000000, 0x00000000), floatArrayOf(0f, 0.35f, 0.7f), Shader.TileMode.CLAMP)
        bevelLight.shader = LinearGradient(-half, -half, half, half, intArrayOf(0x99FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.45f), Shader.TileMode.CLAMP)
        bevelLight.strokeWidth = 14f * unit
        bevelDark.shader = LinearGradient(-half, -half, half, half, intArrayOf(0x00000000, 0x99000000.toInt()), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        bevelDark.strokeWidth = 14f * unit
        gloss.set(-60f * unit, -75f * unit, 60f * unit, -27f * unit)
        glossPaint.shader = LinearGradient(0f, gloss.top, 0f, gloss.bottom, 0x6BFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        outlinePaint.shader = LinearGradient(-half, -half, half, half, intArrayOf(0xFFFFF6D0.toInt(), Palette.GOLD, 0xFF8C5E10.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        outlinePaint.strokeWidth = 3.5f * unit
        innerLinePaint.strokeWidth = 1f * unit
        pivotPaint.shader = RadialGradient(-3f * unit, -4f * unit, 14f * unit, 0xFF0A0614.toInt(), 0xFF3A2C72.toInt(), Shader.TileMode.CLAMP)
        pivotRing.strokeWidth = 1.5f * unit
        arrowPaint.shader = LinearGradient(0f, -half, 0f, half, 0xFFFFF6D0.toInt(), 0xFFC8901E.toInt(), Shader.TileMode.MIRROR)
        arrowEdge.strokeWidth = 0.8f * unit
    }

    private var sinPitch = 0f
    private var cosPitch = 1f
    private var sinYaw = 0f
    private var cosYaw = 1f

    /** Turns a point of the pad (design units, z toward the eye) into [rotated]. */
    private fun rotate(x: Float, y: Float, z: Float) {
        // Pitch: positive pushes the top arm away
        val y1 = y * cosPitch - z * sinPitch
        val z1 = y * sinPitch + z * cosPitch
        // Yaw: positive pushes the right arm away
        rotated[0] = x * cosYaw + z1 * sinYaw
        rotated[1] = y1
        rotated[2] = -x * sinYaw + z1 * cosYaw
    }

    /** Projects [rotated] onto the screen, into [out] at [index]. */
    private fun project(out: FloatArray, index: Int) {
        val scale = EYE / (EYE - rotated[2]) * unit
        out[index] = rotated[0] * scale
        out[index + 1] = rotated[1] * scale
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

        // Deep socket with a gold rim; it does not move
        canvas.drawCircle(0f, 6f * unit, socketRadius, socketPaint)
        canvas.drawCircle(0f, 6f * unit, socketRadius + 1.5f * unit, socketRimDark)
        canvas.drawCircle(0f, 6f * unit, socketRadius + 3.5f * unit, socketRim)
        canvas.drawCircle(0f, 8f * unit, socketRadius - 6f * unit, socketInnerRing)

        val pitch = Math.toRadians((BASE_PITCH + tiltX).toDouble())
        val yaw = Math.toRadians(tiltY.toDouble())
        sinPitch = kotlin.math.sin(pitch).toFloat()
        cosPitch = kotlin.math.cos(pitch).toFloat()
        sinYaw = kotlin.math.sin(yaw).toFloat()
        cosYaw = kotlin.math.cos(yaw).toFloat()
        val dip = -5f * sink

        for (i in 0 until 12) {
            val x = corners[i * 2]
            val y = corners[i * 2 + 1]
            rotate(x, y, dip)
            topZ[i] = rotated[2]
            project(top, i * 2)
            rotate(x, y, dip - THICKNESS)
            bottomZ[i] = rotated[2]
            project(bottom, i * 2)
        }

        // Shadow in the socket, then the back of the block
        polygonOf(bottom)
        canvas.save()
        canvas.translate(4f * unit, 10f * unit)
        canvas.drawPath(polygon, shadowPaint)
        canvas.restore()
        canvas.drawPath(polygon, backPaint)

        // Side walls that face the eye, farthest first, shaded by how much they face the light (from the top left, in front)
        visibleWalls.clear()
        for (i in 0 until 12) {
            val j = (i + 1) % 12
            val dx = corners[j * 2] - corners[i * 2]
            val dy = corners[j * 2 + 1] - corners[i * 2 + 1]
            val length = kotlin.math.hypot(dx, dy)
            rotate(dy / length, -dx / length, 0f)
            val nx = rotated[0]
            val ny = rotated[1]
            val nz = rotated[2]
            val mx = (corners[i * 2] + corners[j * 2]) / 2f
            val my = (corners[i * 2 + 1] + corners[j * 2 + 1]) / 2f
            rotate(mx, my, dip - THICKNESS / 2f)
            val facing = -rotated[0] * nx - rotated[1] * ny + (EYE - rotated[2]) * nz
            if (facing <= 0f) continue
            val light = (-0.4f * nx - 0.7f * ny + 0.6f * nz).coerceAtLeast(0f)
            visibleWalls += walls[i].apply { index = i; depth = rotated[2]; shade = 0.3f + 0.7f * light }
        }
        visibleWalls.sortBy { it.depth }
        for (wall in visibleWalls) {
            val i = wall.index
            val j = (i + 1) % 12
            polygon.reset()
            polygon.moveTo(top[i * 2], top[i * 2 + 1])
            polygon.lineTo(top[j * 2], top[j * 2 + 1])
            polygon.lineTo(bottom[j * 2], bottom[j * 2 + 1])
            polygon.lineTo(bottom[i * 2], bottom[i * 2 + 1])
            polygon.close()
            wallPaint.color = goldShade(wall.shade)
            canvas.drawPath(polygon, wallPaint)
            canvas.drawPath(polygon, wallEdge)
        }

        // The top face, mapped onto its tilted plane
        for (k in 0 until 4) {
            rotate(squareSource[k * 2] / unit, squareSource[k * 2 + 1] / unit, dip)
            project(square, k * 2)
        }
        faceMatrix.setPolyToPoly(squareSource, 0, square, 0, 4)
        canvas.concat(faceMatrix)

        canvas.drawPath(cross, facePaint)
        canvas.save()
        canvas.clipPath(cross)
        canvas.drawPath(cross, dishPaint)
        val end = END * unit
        val arm = ARM * unit
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

    private fun polygonOf(points: FloatArray) {
        polygon.reset()
        for (i in 0 until 12) {
            if (i == 0) polygon.moveTo(points[0], points[1]) else polygon.lineTo(points[i * 2], points[i * 2 + 1])
        }
        polygon.close()
    }

    /** Gold from dark bronze (0) to bright (1). */
    private fun goldShade(t: Float): Int {
        val dark = intArrayOf(0x5E, 0x3B, 0x08)
        val light = intArrayOf(0xFF, 0xE3, 0x9A)
        val r = (dark[0] + (light[0] - dark[0]) * t).toInt()
        val g = (dark[1] + (light[1] - dark[1]) * t).toInt()
        val b = (dark[2] + (light[2] - dark[2]) * t).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
