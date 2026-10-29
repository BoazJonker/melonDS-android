package com.boazjonker.unbound.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated night sky over the mountains of Borrius: twinkling stars, shooting stars, aurora ribbons that wave slowly, a glowing moon, layered mountains
 * and little wisps of light drifting upwards. The parts that never move are drawn once into cached images.
 *
 * In the game, the area of the game screen is left out, and the screen gets a gold frame.
 */
class NightSkyView(context: Context) : View(context) {

    private companion object {
        const val TAU = (2 * PI).toFloat()
        const val STAR_COUNT = 140
        const val WISP_COUNT = 22
    }

    private class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)
    private class Wisp(val x: Float, val speed: Float, val sway: Float, val size: Float, val phase: Float, colour: Int) {
        val tint = PorterDuffColorFilter(colour, PorterDuff.Mode.MULTIPLY)
    }

    private val d = resources.displayMetrics.density
    private val startTime = SystemClock.uptimeMillis()

    /**
     * Redraw interval. The home screen animates smoothly; in the game the sky moves slowly, so a lower rate is enough and keeps the cost low.
     */
    var frameIntervalMs = 33L

    var isAnimated = true
        set(value) {
            field = value
            invalidate()
        }

    private val gameRect = RectF()
    private var hasGameRect = false

    private val random = Random(7)
    private val stars = List(STAR_COUNT) {
        Star(random.nextFloat(), random.nextFloat() * 0.75f, 0.6f + random.nextFloat() * 1.6f, 0.6f + random.nextFloat() * 2f, random.nextFloat() * TAU)
    }
    private val wisps = List(WISP_COUNT) {
        val colour = when (it % 3) {
            0 -> Palette.GLOW
            1 -> Palette.AURORA_TEAL
            else -> Palette.GOLD_LIGHT
        }
        Wisp(random.nextFloat(), 0.025f + random.nextFloat() * 0.04f, 0.02f + random.nextFloat() * 0.04f, 3f + random.nextFloat() * 5f, random.nextFloat(), colour)
    }

    private var skyBitmap: Bitmap? = null
    private var mountainsBitmap: Bitmap? = null
    private var glowSprite: Bitmap? = null

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.WHITE }
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val auroraPaints = List(3) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val auroraPath = Path()
    private val shootingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val frameInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val frameShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
    private val frameRect = RectF()

    fun setGameRect(rect: RectF?) {
        hasGameRect = rect != null
        rect?.let { gameRect.set(it) }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        skyBitmap?.recycle()
        mountainsBitmap?.recycle()
        skyBitmap = drawSky(w, h)
        mountainsBitmap = drawMountains(w, h)
        if (glowSprite == null) glowSprite = drawGlowSprite()

        val colours = listOf(Palette.AURORA_TEAL, Palette.VIOLET_LIGHT, Palette.AURORA_PINK)
        auroraPaints.forEachIndexed { i, paint ->
            val base = h * (0.18f + i * 0.07f)
            val colour = colours[i]
            paint.shader = LinearGradient(
                0f, base - h * 0.12f, 0f, base + h * 0.06f,
                intArrayOf(colour and 0x00FFFFFF, (colour and 0x00FFFFFF) or 0x70000000, colour and 0x00FFFFFF),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    private fun drawSky(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Palette.NIGHT, Palette.NIGHT_MID, Palette.NIGHT_HORIZON, Palette.VIOLET_DARK),
            floatArrayOf(0f, 0.45f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // Moon with a soft halo
        val moonX = w * 0.8f
        val moonY = h * 0.13f
        val moonRadius = min(w, h) * 0.07f
        paint.shader = RadialGradient(moonX, moonY, moonRadius * 4f, 0x55E3C6FF, 0x00E3C6FF, Shader.TileMode.CLAMP)
        canvas.drawCircle(moonX, moonY, moonRadius * 4f, paint)
        paint.shader = RadialGradient(moonX - moonRadius * 0.3f, moonY - moonRadius * 0.3f, moonRadius * 1.2f, 0xFFFFFBF0.toInt(), 0xFFD9C8F5.toInt(), Shader.TileMode.CLAMP)
        canvas.drawCircle(moonX, moonY, moonRadius, paint)
        paint.shader = null
        paint.color = 0x22543C8C
        canvas.drawCircle(moonX + moonRadius * 0.3f, moonY + moonRadius * 0.2f, moonRadius * 0.22f, paint)
        canvas.drawCircle(moonX - moonRadius * 0.35f, moonY + moonRadius * 0.35f, moonRadius * 0.14f, paint)
        canvas.drawCircle(moonX - moonRadius * 0.1f, moonY - moonRadius * 0.4f, moonRadius * 0.1f, paint)
        return bitmap
    }

    private fun drawMountains(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val layerRandom = Random(11)
        val layers = listOf(
            Triple(0.7f, 0xFF2C1A5C.toInt(), 0xFF1C1140.toInt()),
            Triple(0.8f, 0xFF1D1240.toInt(), 0xFF120B2A.toInt()),
            Triple(0.9f, 0xFF110A26.toInt(), 0xFF07040F.toInt()),
        )
        layers.forEachIndexed { index, (horizon, top, bottom) ->
            val path = Path()
            val base = h * horizon
            path.moveTo(0f, h.toFloat())
            path.lineTo(0f, base)
            var x = 0f
            val peakWidth = w * (0.22f - index * 0.04f)
            while (x < w) {
                val peakX = x + peakWidth * (0.4f + layerRandom.nextFloat() * 0.3f)
                val peakY = base - h * (0.06f + layerRandom.nextFloat() * 0.1f) / (1 + index * 0.6f)
                val nextX = x + peakWidth
                path.lineTo(peakX, peakY)
                path.lineTo(nextX, base - h * layerRandom.nextFloat() * 0.02f)
                x = nextX
            }
            path.lineTo(w.toFloat(), h.toFloat())
            path.close()
            paint.shader = LinearGradient(0f, base - h * 0.15f, 0f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
            canvas.drawPath(path, paint)

            if (index == 0) {
                // Moonlight on the far peaks
                paint.shader = LinearGradient(0f, base - h * 0.16f, 0f, base, 0x30E3C6FF, 0x00E3C6FF, Shader.TileMode.CLAMP)
                canvas.drawPath(path, paint)
            }
        }

        // Pine trees along the nearest ridge
        paint.shader = null
        paint.color = 0xFF05030B.toInt()
        val treeRandom = Random(5)
        var x = -10f * d
        while (x < w) {
            val treeHeight = (14f + treeRandom.nextFloat() * 22f) * d
            val baseY = h * (0.93f + treeRandom.nextFloat() * 0.04f)
            val tree = Path()
            tree.moveTo(x, baseY)
            tree.lineTo(x + treeHeight * 0.22f, baseY - treeHeight)
            tree.lineTo(x + treeHeight * 0.44f, baseY)
            tree.close()
            canvas.drawPath(tree, paint)
            canvas.drawRect(x, baseY, x + treeHeight * 0.44f, h.toFloat(), paint)
            x += treeHeight * (0.3f + treeRandom.nextFloat() * 0.5f)
        }
        return bitmap
    }

    private fun drawGlowSprite(): Bitmap {
        val size = (32 * d).toInt().coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        paint.shader = RadialGradient(
            center, center, center,
            intArrayOf(0xFFFFFFFF.toInt(), 0x88FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.25f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(center, center, center, paint)
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val sky = skyBitmap ?: return
        val t = (SystemClock.uptimeMillis() - startTime) / 1000f
        val time = if (isAnimated) t else 0f

        canvas.save()
        if (hasGameRect) {
            canvas.clipOutRect(gameRect)
        }

        canvas.drawBitmap(sky, 0f, 0f, bitmapPaint)
        drawStars(canvas, w, h, time)
        drawShootingStar(canvas, w, h, time)
        drawAurora(canvas, w, h, time)
        mountainsBitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        drawWisps(canvas, w, h, time)
        canvas.restore()

        if (hasGameRect) {
            drawFrame(canvas)
        }

        if (isAnimated) {
            postInvalidateDelayed(frameIntervalMs)
        }
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float, t: Float) {
        for (star in stars) {
            val twinkle = 0.55f + 0.45f * sin(t * star.speed + star.phase)
            starPaint.alpha = (twinkle * 255).toInt()
            canvas.drawCircle(star.x * w, star.y * h, star.size * d * (0.8f + twinkle * 0.3f), starPaint)
        }
    }

    private fun drawShootingStar(canvas: Canvas, w: Float, h: Float, t: Float) {
        // One shooting star every 9 seconds, crossing the sky in under a second
        val period = 9f
        val cycle = (t / period).toInt()
        val progress = (t % period) / 0.9f
        if (progress > 1f) return
        val starRandom = Random(cycle)
        val startX = w * (0.15f + starRandom.nextFloat() * 0.5f)
        val startY = h * (0.04f + starRandom.nextFloat() * 0.2f)
        val length = w * 0.25f
        val headX = startX + length * progress
        val headY = startY + length * 0.35f * progress
        val alpha = sin(progress * PI.toFloat())
        shootingPaint.shader = LinearGradient(headX - length * 0.3f, headY - length * 0.105f, headX, headY, 0x00FFFFFF, Palette.WHITE, Shader.TileMode.CLAMP)
        shootingPaint.alpha = (alpha * 255).toInt()
        shootingPaint.strokeWidth = 2f * d
        canvas.drawLine(headX - length * 0.3f, headY - length * 0.105f, headX, headY, shootingPaint)
    }

    private fun drawAurora(canvas: Canvas, w: Float, h: Float, t: Float) {
        val step = max(w / 40f, 4f)
        auroraPaints.forEachIndexed { i, paint ->
            val base = h * (0.18f + i * 0.07f)
            val amplitude = h * (0.03f + i * 0.01f)
            val speed = 0.25f + i * 0.1f
            auroraPath.reset()
            var x = -step
            auroraPath.moveTo(x, base)
            while (x <= w + step) {
                val y = base + amplitude * sin(x / w * TAU * (1.2f + i * 0.3f) + t * speed + i) - h * 0.1f
                auroraPath.lineTo(x, y)
                x += step
            }
            while (x >= -step) {
                val y = base + amplitude * 0.6f * sin(x / w * TAU * (1.5f + i * 0.2f) + t * speed * 1.3f + i * 2) + h * 0.04f
                auroraPath.lineTo(x, y)
                x -= step
            }
            auroraPath.close()
            canvas.drawPath(auroraPath, paint)
        }
    }

    private fun drawWisps(canvas: Canvas, w: Float, h: Float, t: Float) {
        val sprite = glowSprite ?: return
        for (wisp in wisps) {
            // Rise from the bottom, swaying from side to side, and fade near the top
            val progress = (wisp.phase + t * wisp.speed) % 1f
            val y = h * (1.05f - progress * 0.8f)
            val x = (wisp.x + wisp.sway * sin(t * 0.8f + wisp.phase * TAU)) * w
            val size = wisp.size * d * 3f
            val fade = sin(progress * PI.toFloat())
            spritePaint.alpha = (fade * 200).toInt()
            spritePaint.colorFilter = wisp.tint
            frameRect.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f)
            canvas.drawBitmap(sprite, null, frameRect, spritePaint)
        }
    }

    private fun drawFrame(canvas: Canvas) {
        val border = 4f * d
        frameRect.set(gameRect)
        frameRect.inset(-border / 2f, -border / 2f)
        // Shadow under the screen, so that it seems to float
        canvas.save()
        canvas.clipOutRect(gameRect)
        canvas.drawRoundRect(frameRect.left + border, frameRect.top + border * 2f, frameRect.right + border, frameRect.bottom + border * 2.5f, border * 2, border * 2, frameShadowPaint)
        canvas.restore()

        framePaint.strokeWidth = border
        framePaint.shader = LinearGradient(0f, frameRect.top, 0f, frameRect.bottom, intArrayOf(Palette.GOLD_LIGHT, Palette.GOLD, Palette.GOLD_DARK), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(frameRect, border, border, framePaint)
        frameRect.inset(-border * 1.1f, -border * 1.1f)
        frameInnerPaint.strokeWidth = border * 0.5f
        frameInnerPaint.color = Palette.VIOLET_DARK
        canvas.drawRoundRect(frameRect, border * 2, border * 2, frameInnerPaint)
    }
}
