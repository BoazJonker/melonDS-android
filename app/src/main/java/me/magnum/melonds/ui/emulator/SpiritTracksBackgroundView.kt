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
 * The areas covered by the DS screens are never drawn, so the scene only shows around them.
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

    private val screenRects = mutableListOf<RectF>()
    private var isAnimating = false
    private val startTime = SystemClock.uptimeMillis()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val skyPaint = Paint()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillPath = Path()
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
     * Sets the areas covered by the DS screens. These areas are left undrawn.
     */
    fun setScreenRects(rects: List<Rect?>) {
        screenRects.clear()
        rects.filterNotNull().forEach {
            screenRects.add(RectF(it.x.toFloat(), it.y.toFloat(), (it.x + it.width).toFloat(), (it.y + it.height).toFloat()))
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
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            return
        }

        val time = (SystemClock.uptimeMillis() - startTime) / 1000f

        canvas.save()
        screenRects.forEach { clipOutRect(canvas, it) }

        // Sky and sun
        canvas.drawRect(0f, 0f, w, h, skyPaint)
        canvas.drawCircle(w * 0.82f, h * 0.14f, 46 * density, sunPaint)

        drawClouds(canvas, w, h, time)

        // Hills, from far to near, scrolling at different speeds for a parallax effect
        val trackY = h * 0.86f
        drawHills(canvas, w, h * 0.60f, 0.045f * h, 220 * density, 6 * density * time, 0xFF9CC77A.toInt(), trackY)
        drawHills(canvas, w, h * 0.70f, 0.035f * h, 150 * density, 14 * density * time, 0xFF6FAE52.toInt(), trackY)

        // Ground below the hills
        fillPaint.color = 0xFF5E9A44.toInt()
        canvas.drawRect(0f, h * 0.78f, w, h, fillPaint)

        drawTrack(canvas, w, h, trackY)
        drawTrain(canvas, w, trackY, time)

        canvas.restore()
    }

    @Suppress("DEPRECATION")
    private fun clipOutRect(canvas: Canvas, rect: RectF) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canvas.clipOutRect(rect)
        } else {
            canvas.clipRect(rect, Region.Op.DIFFERENCE)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, time: Float) {
        fillPaint.color = 0xF2FFFFFF.toInt()
        clouds.forEach { cloud ->
            val cloudWidth = 90 * density * cloud.scale
            val span = w + cloudWidth * 2
            val x = ((cloud.startX * span - cloud.speedDp * density * time) % span + span) % span - cloudWidth
            val y = cloud.y * h
            val r = 16 * density * cloud.scale
            canvas.drawCircle(x, y, r, fillPaint)
            canvas.drawCircle(x + r * 1.1f, y - r * 0.6f, r * 1.25f, fillPaint)
            canvas.drawCircle(x + r * 2.3f, y - r * 0.2f, r * 1.05f, fillPaint)
            canvas.drawCircle(x + r * 3.2f, y + r * 0.1f, r * 0.8f, fillPaint)
            rect.set(x, y - r * 0.2f, x + r * 3.4f, y + r * 0.85f)
            canvas.drawRoundRect(rect, r * 0.5f, r * 0.5f, fillPaint)
        }
    }

    private fun drawHills(canvas: Canvas, w: Float, baseY: Float, amplitude: Float, wavelength: Float, offset: Float, color: Int, bottom: Float) {
        hillPath.reset()
        hillPath.moveTo(0f, bottom)
        val step = 8 * density
        var x = 0f
        while (x <= w + step) {
            val phase = (x + offset) / wavelength * 2 * PI
            val y = baseY - amplitude * (sin(phase) * 0.7 + sin(phase * 0.37 + 1.3) * 0.3).toFloat()
            hillPath.lineTo(x, y)
            x += step
        }
        hillPath.lineTo(w, bottom)
        hillPath.close()
        fillPaint.color = color
        canvas.drawPath(hillPath, fillPaint)
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
            fillPaint.color = (alpha shl 24) or 0xF4F1EA
            val radius = (5 + age * 14) * d
            canvas.drawCircle(puff.x - age * 18 * d + puff.drift, puff.y - age * 46 * d, radius, fillPaint)
        }
    }

    private fun drawLocomotive(canvas: Canvas, front: Float, wheelY: Float, time: Float) {
        val d = density
        // Cab
        fillPaint.color = 0xFF6B4423.toInt()
        rect.set(front - 56 * d, wheelY - 34 * d, front - 34 * d, wheelY - 4 * d)
        canvas.drawRoundRect(rect, 2 * d, 2 * d, fillPaint)
        fillPaint.color = 0xFF3B2412.toInt()
        rect.set(front - 59 * d, wheelY - 38 * d, front - 31 * d, wheelY - 33 * d)
        canvas.drawRoundRect(rect, 2 * d, 2 * d, fillPaint)
        fillPaint.color = 0xFFF6EDD3.toInt()
        rect.set(front - 51 * d, wheelY - 29 * d, front - 40 * d, wheelY - 19 * d)
        canvas.drawRect(rect, fillPaint)

        // Boiler with gold bands
        fillPaint.color = 0xFF2F5D3E.toInt()
        rect.set(front - 36 * d, wheelY - 24 * d, front - 2 * d, wheelY - 6 * d)
        canvas.drawRoundRect(rect, 8 * d, 8 * d, fillPaint)
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
        fillPaint.color = 0xFF8A5A32.toInt()
        rect.set(left, wheelY - 26 * d, right, wheelY - 3 * d)
        canvas.drawRoundRect(rect, 3 * d, 3 * d, fillPaint)
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
