package me.magnum.melonds.ui.emulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Region
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import me.magnum.melonds.domain.model.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated countryside scene drawn behind the on-screen controls, inspired by The Legend of Zelda: Spirit Tracks: a sky with drifting clouds, rolling hills
 * with parallax and a railway on which a small steam train regularly passes by, puffing smoke. Everything is drawn with simple shapes.
 *
 * The areas covered by the DS screens are never drawn, so the scene only shows around them. The screens get a dark wood and gold frame, which is drawn even
 * when the scene itself is disabled.
 */
class SpiritTracksBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private companion object {
        // Redraw at roughly 30 FPS. The scene moves slowly, so this is smooth enough and saves battery
        const val FRAME_INTERVAL_MS = 33L

        const val TRAIN_SPEED_DP = 70f
        const val TRAIN_PAUSE_SECONDS = 6f
        const val SMOKE_INTERVAL_SECONDS = 0.28f
        const val SMOKE_LIFETIME_SECONDS = 2.4f
    }

    private val density = resources.displayMetrics.density

    private class ScreenArea(val rect: RectF, val isMiniScreen: Boolean)

    private val screenAreas = mutableListOf<ScreenArea>()
    private var isAnimating = false

    /**
     * Whether to draw the countryside scene. When disabled, only the screen frames are drawn.
     */
    var isSceneEnabled = true
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Opacity of the small screen window, so that its frame fades together with it.
     */
    var miniScreenAlpha = 1f
        set(value) {
            field = value
            invalidate()
        }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val startTime = SystemClock.uptimeMillis()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val skyPaint = Paint()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillPath = Path()
    private val ridgePath = Path()
    private val farHillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nearHillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val groundPaint = Paint()
    private val vignettePaint = Paint()
    private val carriagePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cabPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boilerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ridgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x59FFFFE6
    }
    private val rect = RectF()

    private val clouds = listOf(
        Cloud(0.10f, 0.12f, 1.0f, 6f),
        Cloud(0.45f, 0.22f, 0.7f, 4f),
        Cloud(0.75f, 0.08f, 1.2f, 7f),
        Cloud(0.30f, 0.34f, 0.55f, 3f),
    )

    private class Cloud(val startX: Float, val y: Float, val scale: Float, val speedDp: Float)

    private class Puff(var x: Float, var y: Float, val bornAt: Float, val drift: Float)

    private val puffs = ArrayDeque<Puff>()
    private var lastPuffTime = 0f

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isAnimating) {
                return
            }
            invalidate()
            postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    /**
     * Sets the areas covered by the DS screens, each with whether it's the small screen window. These areas are left undrawn, apart from their frames.
     */
    fun setScreenRects(rects: List<Pair<Rect, Boolean>>) {
        screenAreas.clear()
        rects.forEach { (rect, isMiniScreen) ->
            val rectF = RectF(rect.x.toFloat(), rect.y.toFloat(), (rect.x + rect.width).toFloat(), (rect.y + rect.height).toFloat())
            screenAreas.add(ScreenArea(rectF, isMiniScreen))
        }
        invalidate()
    }

    fun startAnimation() {
        if (isAnimating) {
            return
        }
        isAnimating = true
        removeCallbacks(frameRunnable)
        post(frameRunnable)
    }

    fun stopAnimation() {
        isAnimating = false
        removeCallbacks(frameRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.75f,
            intArrayOf(0xFF6FB7DA.toInt(), 0xFFB9E0EE.toInt(), 0xFFF3E8C8.toInt()),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        val sunRadius = 46 * density
        sunPaint.shader = RadialGradient(
            w * 0.82f, h * 0.14f, sunRadius,
            intArrayOf(0xFFFFF4C4.toInt(), 0xCCFFE48A.toInt(), 0x00FFE48A),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )

        // Vertical shading for the hills, ground and train. The train only moves horizontally, so its gradients can be created once
        val trackY = h * 0.86f
        val wheelY = trackY - 7 * density
        farHillPaint.shader = verticalGradient(h * 0.60f - 0.045f * h, trackY, 0xFF9CC77A.toInt(), 0xFF7FB062.toInt())
        nearHillPaint.shader = verticalGradient(h * 0.70f - 0.035f * h, trackY, 0xFF6FAE52.toInt(), 0xFF4F8E3A.toInt())
        groundPaint.shader = verticalGradient(h * 0.78f, h.toFloat(), 0xFF5E9A44.toInt(), 0xFF467A32.toInt())
        carriagePaint.shader = verticalGradient(wheelY - 26 * density, wheelY - 3 * density, 0xFFA06C40.toInt(), 0xFF6E4626.toInt())
        cabPaint.shader = verticalGradient(wheelY - 34 * density, wheelY - 4 * density, 0xFF7E5230.toInt(), 0xFF553418.toInt())
        boilerPaint.shader = verticalGradient(wheelY - 24 * density, wheelY - 6 * density, 0xFF3F7A52.toInt(), 0xFF21452D.toInt())
        ridgePaint.strokeWidth = 2 * density
        // Soft darkening towards the edges
        vignettePaint.shader = RadialGradient(
            w / 2f, h / 2f, maxOf(w, h) * 0.75f,
            intArrayOf(0x00281405, 0x00281405, 0x47281405),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun verticalGradient(top: Float, bottom: Float, topColor: Int, bottomColor: Int): Shader {
        return LinearGradient(0f, top, 0f, bottom, topColor, bottomColor, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            return
        }

        if (isSceneEnabled) {
            drawScene(canvas, w, h)
        }
        drawScreenFrames(canvas)
    }

    private fun drawScene(canvas: Canvas, w: Float, h: Float) {
        val time = (SystemClock.uptimeMillis() - startTime) / 1000f

        canvas.save()
        screenAreas.forEach { clipOutRect(canvas, it.rect) }

        // Sky, slowly turning sun rays and the sun
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        drawSunRays(canvas, w * 0.82f, h * 0.14f, maxOf(w, h), time)
        canvas.drawCircle(w * 0.82f, h * 0.14f, 46 * density, sunPaint)

        drawClouds(canvas, w, h, time)

        // Hills, from far to near, scrolling at different speeds for a parallax effect
        val trackY = h * 0.86f
        drawHills(canvas, w, h * 0.60f, 0.045f * h, 220 * density, 6 * density * time, farHillPaint, trackY)
        drawHills(canvas, w, h * 0.70f, 0.035f * h, 150 * density, 14 * density * time, nearHillPaint, trackY)

        // Ground below the hills
        canvas.drawRect(0f, h * 0.78f, w, h, groundPaint)

        drawTrack(canvas, w, h, trackY)
        drawTrain(canvas, w, trackY, time)

        canvas.drawRect(0f, 0f, w, h, vignettePaint)
        canvas.restore()
    }

    /**
     * Draws a carved frame around each screen: a gold inlay between two dark lines next to the picture, then wood and a dark outer edge. The frame is drawn
     * outside the screen so that it never covers the game's HUD.
     */
    private fun drawScreenFrames(canvas: Canvas) {
        val d = density
        screenAreas.forEach { area ->
            val alpha = if (area.isMiniScreen) miniScreenAlpha else 1f
            val corner = if (area.isMiniScreen) 3 * d else 0f

            // (colour, stroke width, distance of the stroke's centre from the edge; negative is outside the screen)
            drawFrameLine(canvas, area.rect, 0xFF2A180B.toInt(), 0.6f * d, -0.3f * d, corner, alpha)
            drawFrameLine(canvas, area.rect, 0xFFE2B54A.toInt(), 1.3f * d, -1.25f * d, corner, alpha)
            drawFrameLine(canvas, area.rect, 0xFF2A180B.toInt(), 0.6f * d, -2.2f * d, corner, alpha)
            drawFrameLine(canvas, area.rect, 0xFF5C3A1E.toInt(), 2.2f * d, -3.6f * d, corner, alpha)
            drawFrameLine(canvas, area.rect, 0xFF2A180B.toInt(), 1f * d, -5.2f * d, corner, alpha)
        }
    }

    private fun drawFrameLine(canvas: Canvas, area: RectF, color: Int, width: Float, inset: Float, corner: Float, alpha: Float) {
        framePaint.color = color
        framePaint.alpha = (((color ushr 24) and 0xFF) * alpha).toInt()
        framePaint.strokeWidth = width
        rect.set(area)
        rect.inset(inset, inset)
        canvas.drawRoundRect(rect, corner, corner, framePaint)
    }

    @Suppress("DEPRECATION")
    private fun clipOutRect(canvas: Canvas, rect: RectF) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutRect(rect)
        } else {
            canvas.clipRect(rect, Region.Op.DIFFERENCE)
        }
    }

    private fun drawSunRays(canvas: Canvas, cx: Float, cy: Float, length: Float, time: Float) {
        fillPaint.color = 0x2EFFF4C4
        val rayCount = 10
        for (i in 0 until rayCount) {
            val angle = (i.toFloat() / rayCount * 360f) + time * 3f
            rect.set(cx - length, cy - length, cx + length, cy + length)
            canvas.drawArc(rect, angle, 7f, true, fillPaint)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, time: Float) {
        clouds.forEach { cloud ->
            val cloudWidth = 90 * density * cloud.scale
            val span = w + cloudWidth * 2
            val x = ((cloud.startX * span - cloud.speedDp * density * time) % span + span) % span - cloudWidth
            val y = cloud.y * h
            val r = 16 * density * cloud.scale
            // Bluish shadow underneath, the white cloud, then bright highlights on top
            fillPaint.color = 0x8C96AAC3.toInt()
            drawCloudShape(canvas, x, y + r * 0.25f, r, 1f)
            fillPaint.color = 0xF5FFFFFF.toInt()
            drawCloudShape(canvas, x, y, r, 1f)
            fillPaint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x + r * 0.9f, y - r * 0.85f, r * 0.7f, fillPaint)
            canvas.drawCircle(x + r * 2.1f, y - r * 0.45f, r * 0.55f, fillPaint)
        }
    }

    private fun drawCloudShape(canvas: Canvas, x: Float, y: Float, r: Float, scale: Float) {
        canvas.drawCircle(x, y, r * scale, fillPaint)
        canvas.drawCircle(x + r * 1.1f, y - r * 0.6f, r * 1.25f * scale, fillPaint)
        canvas.drawCircle(x + r * 2.3f, y - r * 0.2f, r * 1.05f * scale, fillPaint)
        canvas.drawCircle(x + r * 3.2f, y + r * 0.1f, r * 0.8f * scale, fillPaint)
        rect.set(x, y - r * 0.2f, x + r * 3.4f, y + r * 0.85f)
        canvas.drawRoundRect(rect, r * 0.5f, r * 0.5f, fillPaint)
    }

    private fun drawHills(canvas: Canvas, w: Float, baseY: Float, amplitude: Float, wavelength: Float, offset: Float, paint: Paint, bottom: Float) {
        hillPath.reset()
        ridgePath.reset()
        hillPath.moveTo(0f, bottom)
        val step = 8 * density
        var x = 0f
        while (x <= w + step) {
            val phase = (x + offset) / wavelength * 2 * PI
            val y = baseY - amplitude * (sin(phase) * 0.7 + sin(phase * 0.37 + 1.3) * 0.3).toFloat()
            hillPath.lineTo(x, y)
            if (x == 0f) ridgePath.moveTo(x, y + density) else ridgePath.lineTo(x, y + density)
            x += step
        }
        hillPath.lineTo(w, bottom)
        hillPath.close()
        canvas.drawPath(hillPath, paint)
        // Sunlit ridge along the top of the hills
        canvas.drawPath(ridgePath, ridgePaint)
    }

    private fun drawTrack(canvas: Canvas, w: Float, h: Float, trackY: Float) {
        // Gravel bed
        fillPaint.color = 0xFFB59A6A.toInt()
        canvas.drawRect(0f, trackY - 7 * density, w, trackY + 9 * density, fillPaint)

        // Sleepers
        fillPaint.color = 0xFF5C3A1E.toInt()
        val sleeperSpacing = 22 * density
        val sleeperWidth = 8 * density
        var x = 0f
        while (x < w) {
            canvas.drawRect(x, trackY - 4 * density, x + sleeperWidth, trackY + 7 * density, fillPaint)
            x += sleeperSpacing
        }

        // Rails
        strokePaint.strokeWidth = 2.5f * density
        strokePaint.color = 0xFF55555E.toInt()
        canvas.drawLine(0f, trackY - 2 * density, w, trackY - 2 * density, strokePaint)
        canvas.drawLine(0f, trackY + 4 * density, w, trackY + 4 * density, strokePaint)
        strokePaint.strokeWidth = 1f * density
        strokePaint.color = 0xFFB8BCC6.toInt()
        canvas.drawLine(0f, trackY - 2.8f * density, w, trackY - 2.8f * density, strokePaint)
    }

    private fun drawTrain(canvas: Canvas, w: Float, trackY: Float, time: Float) {
        val d = density
        val trainLength = 150 * d
        val travelDistance = w + trainLength * 1.5f
        val travelTime = travelDistance / (TRAIN_SPEED_DP * d)
        val cycleTime = travelTime + TRAIN_PAUSE_SECONDS
        val cycleProgress = time % cycleTime

        // Front of the locomotive. The train travels from left to right
        val front = -trainLength * 0.2f + cycleProgress * TRAIN_SPEED_DP * d
        val isVisible = cycleProgress <= travelTime
        val wheelY = trackY - 7 * d

        if (isVisible) {
            // Shadow of the train on the ground
            fillPaint.color = 0x40000000
            rect.set(front - 150 * d, trackY - 1 * d, front + 6 * d, trackY + 7 * d)
            canvas.drawOval(rect, fillPaint)

            // Carriages
            for (i in 0..1) {
                val right = front - 58 * d - i * 46 * d
                drawCarriage(canvas, right - 42 * d, right, wheelY, time)
            }
            drawLocomotive(canvas, front, wheelY, time)

            // Emit smoke from the chimney
            if (time - lastPuffTime > SMOKE_INTERVAL_SECONDS) {
                lastPuffTime = time
                puffs.addLast(Puff(front - 14 * d, wheelY - 36 * d, time, (puffs.size % 3 - 1) * 4 * d))
            }
        }

        // Smoke keeps drifting even after the train has left
        while (puffs.isNotEmpty() && time - puffs.first().bornAt > SMOKE_LIFETIME_SECONDS) {
            puffs.removeFirst()
        }
        puffs.forEach { puff ->
            val age = (time - puff.bornAt) / SMOKE_LIFETIME_SECONDS
            val alpha = ((1f - age) * 200).toInt().coerceIn(0, 255)
            val radius = (5 + age * 14) * d
            val puffX = puff.x - age * 18 * d + puff.drift
            val puffY = puff.y - age * 46 * d
            // Grey shading on the lower side of each puff
            fillPaint.color = ((alpha * 0.6f).toInt() shl 24) or 0xAAA5A0
            canvas.drawCircle(puffX + radius * 0.2f, puffY + radius * 0.25f, radius, fillPaint)
            fillPaint.color = (alpha shl 24) or 0xF4F1EA
            canvas.drawCircle(puffX, puffY, radius, fillPaint)
        }
    }

    private fun drawLocomotive(canvas: Canvas, front: Float, wheelY: Float, time: Float) {
        val d = density
        // Cab
        rect.set(front - 56 * d, wheelY - 34 * d, front - 34 * d, wheelY - 4 * d)
        canvas.drawRoundRect(rect, 2 * d, 2 * d, cabPaint)
        fillPaint.color = 0xFF3B2412.toInt()
        rect.set(front - 59 * d, wheelY - 38 * d, front - 31 * d, wheelY - 33 * d)
        canvas.drawRoundRect(rect, 2 * d, 2 * d, fillPaint)
        fillPaint.color = 0xFFF6EDD3.toInt()
        rect.set(front - 51 * d, wheelY - 29 * d, front - 40 * d, wheelY - 19 * d)
        canvas.drawRect(rect, fillPaint)

        // Boiler with gold bands
        rect.set(front - 36 * d, wheelY - 24 * d, front - 2 * d, wheelY - 6 * d)
        canvas.drawRoundRect(rect, 8 * d, 8 * d, boilerPaint)
        // Shine along the top of the boiler
        fillPaint.color = 0x47FFFFFF
        rect.set(front - 33 * d, wheelY - 22 * d, front - 5 * d, wheelY - 18 * d)
        canvas.drawRoundRect(rect, 2 * d, 2 * d, fillPaint)
        fillPaint.color = 0xFFE2B54A.toInt()
        canvas.drawRect(front - 26 * d, wheelY - 24 * d, front - 23 * d, wheelY - 6 * d, fillPaint)
        canvas.drawRect(front - 13 * d, wheelY - 24 * d, front - 10 * d, wheelY - 6 * d, fillPaint)

        // Chimney
        fillPaint.color = 0xFF232128.toInt()
        canvas.drawRect(front - 17 * d, wheelY - 34 * d, front - 11 * d, wheelY - 23 * d, fillPaint)
        rect.set(front - 20 * d, wheelY - 37 * d, front - 8 * d, wheelY - 33 * d)
        canvas.drawRoundRect(rect, 1.5f * d, 1.5f * d, fillPaint)

        // Lamp and cowcatcher
        fillPaint.color = 0xFFFFE48A.toInt()
        canvas.drawCircle(front - 2 * d, wheelY - 19 * d, 2.5f * d, fillPaint)
        // Glow around the lamp
        fillPaint.color = 0x2EFFE48A
        canvas.drawCircle(front - 2 * d, wheelY - 19 * d, 13 * d, fillPaint)
        canvas.drawCircle(front - 2 * d, wheelY - 19 * d, 8 * d, fillPaint)
        fillPaint.color = 0xFF232128.toInt()
        hillPath.reset()
        hillPath.moveTo(front - 4 * d, wheelY - 6 * d)
        hillPath.lineTo(front + 5 * d, wheelY + 2 * d)
        hillPath.lineTo(front - 4 * d, wheelY + 2 * d)
        hillPath.close()
        canvas.drawPath(hillPath, fillPaint)

        // Wheels
        drawWheel(canvas, front - 46 * d, wheelY, 7 * d, time)
        drawWheel(canvas, front - 28 * d, wheelY + 2 * d, 5 * d, time)
        drawWheel(canvas, front - 14 * d, wheelY + 2 * d, 5 * d, time)
    }

    private fun drawCarriage(canvas: Canvas, left: Float, right: Float, wheelY: Float, time: Float) {
        val d = density
        rect.set(left, wheelY - 26 * d, right, wheelY - 3 * d)
        canvas.drawRoundRect(rect, 3 * d, 3 * d, carriagePaint)
        fillPaint.color = 0xFF3B2412.toInt()
        rect.set(left - 2 * d, wheelY - 29 * d, right + 2 * d, wheelY - 25 * d)
        canvas.drawRoundRect(rect, 2 * d, 2 * d, fillPaint)
        fillPaint.color = 0xFFE2B54A.toInt()
        canvas.drawRect(left, wheelY - 8 * d, right, wheelY - 6 * d, fillPaint)
        fillPaint.color = 0xFFF6EDD3.toInt()
        val windowWidth = (right - left - 4 * 3 * d) / 3
        for (i in 0..2) {
            val wx = left + 3 * d + i * (windowWidth + 3 * d)
            canvas.drawRect(wx, wheelY - 22 * d, wx + windowWidth, wheelY - 13 * d, fillPaint)
        }
        // Coupling to the vehicle in front
        fillPaint.color = 0xFF232128.toInt()
        canvas.drawRect(right, wheelY - 7 * d, right + 4 * d, wheelY - 5 * d, fillPaint)

        drawWheel(canvas, left + 9 * d, wheelY + 2 * d, 5 * d, time)
        drawWheel(canvas, right - 9 * d, wheelY + 2 * d, 5 * d, time)
    }

    private fun drawWheel(canvas: Canvas, cx: Float, cy: Float, radius: Float, time: Float) {
        fillPaint.color = 0xFF232128.toInt()
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.color = 0xFFB33A2A.toInt()
        canvas.drawCircle(cx, cy, radius * 0.72f, fillPaint)

        // Spokes rotate with the distance travelled
        val angle = time * TRAIN_SPEED_DP * density / radius
        strokePaint.color = 0xFF232128.toInt()
        strokePaint.strokeWidth = max(1f, radius * 0.18f)
        for (i in 0..1) {
            val a = angle + i * PI.toFloat() / 2
            val dx = cos(a) * radius * 0.72f
            val dy = sin(a) * radius * 0.72f
            canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, strokePaint)
        }
        fillPaint.color = 0xFFE2B54A.toInt()
        canvas.drawCircle(cx, cy, min(radius * 0.25f, 2.5f * density), fillPaint)
    }
}
