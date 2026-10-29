package com.boazjonker.unbound.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min

/**
 * Draws a 3D on-screen button: a dark metal socket, and a raised cap with a gold rim and a glossy face. Pressing sinks the cap into the socket. Everything
 * is drawn from shapes, centered on (0, 0); call [layout] whenever the size changes.
 */
class Button3D(val kind: Kind, var label: String? = null) {

    enum class Kind {
        // A: red and white, like a Poké Ball
        POKE_BALL,
        // B: blue with red fins, like a Great Ball
        GREAT_BALL,
        // Small round button with a symbol (menu, fast-forward)
        ROUND,
        // Start and Select
        PILL,
        // L and R
        SHOULDER,
        DPAD,
    }

    enum class Icon { NONE, MENU, FAST_FORWARD }

    var icon = Icon.NONE

    /**
     * Lit buttons (such as fast-forward while it is on) get a glowing face.
     */
    var isLit = false

    var width = 0f
        private set
    var height = 0f
        private set
    private var depth = 0f

    private val socketPath = Path()
    private val shadowPath = Path()
    private val sidePath = Path()
    private val rimPath = Path()
    private val facePath = Path()
    private val glossRect = RectF()
    private val rippleRect = RectF()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.SHADOW }
    private val socketPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val socketEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val litFacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD_LIGHT }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.INK }
    private val ballTopPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballBottomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val finPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.RED }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GOLD }
    private val pressedArmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.WHITE; strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.WHITE
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
    }
    private val ballTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.INK
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()

    /**
     * @param width Width of the cap. For round buttons and the D-pad, this is also the height
     */
    fun layout(width: Float, height: Float) {
        this.width = width
        this.height = if (kind == Kind.PILL || kind == Kind.SHOULDER) height else width
        val w = this.width
        val h = this.height
        val size = min(w, h)
        depth = if (kind == Kind.DPAD) size * 0.05f else size * 0.09f

        val socketGrow = if (kind == Kind.DPAD) size * 0.05f else size * 0.07f
        shape(socketPath, w + socketGrow * 2, h + socketGrow * 2, depth * 0.6f)
        shape(shadowPath, w + socketGrow * 2, h + socketGrow * 2, depth * 1.8f)
        shape(sidePath, w, h, depth)
        shape(rimPath, w, h, 0f)
        val rim = if (kind == Kind.DPAD) size * 0.035f else size * 0.07f
        shape(facePath, w - rim * 2, h - rim * 2, 0f)

        socketPaint.shader = RadialGradient(-w * 0.2f, -h * 0.3f, maxOf(w, h) * 0.9f, Palette.METAL_LIGHT, Palette.METAL_DARK, Shader.TileMode.CLAMP)
        socketEdgePaint.strokeWidth = size * 0.02f
        socketEdgePaint.color = 0x55FFFFFF
        sidePaint.shader = LinearGradient(0f, -h / 2f, 0f, h / 2f + depth, Palette.GOLD, Palette.GOLD_DARK, Shader.TileMode.CLAMP)
        rimPaint.shader = LinearGradient(
            0f, -h / 2f, 0f, h / 2f,
            intArrayOf(Palette.GOLD_LIGHT, Palette.GOLD, Palette.GOLD_DARK),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        val faceTop = if (kind == Kind.DPAD) Palette.METAL_LIGHT else Palette.VIOLET_LIGHT
        val faceBottom = if (kind == Kind.DPAD) Palette.METAL else Palette.VIOLET_DARK
        facePaint.shader = LinearGradient(0f, -h / 2f, 0f, h / 2f, faceTop, faceBottom, Shader.TileMode.CLAMP)
        litFacePaint.shader = RadialGradient(0f, 0f, maxOf(w, h) * 0.6f, Palette.GLOW, Palette.VIOLET, Shader.TileMode.CLAMP)

        val faceRadius = (w - rim * 2) / 2f
        ballTopPaint.shader = if (kind == Kind.GREAT_BALL) {
            RadialGradient(-faceRadius * 0.3f, -faceRadius * 0.6f, faceRadius * 1.3f, Palette.BLUE_LIGHT, Palette.BLUE_DARK, Shader.TileMode.CLAMP)
        } else {
            RadialGradient(-faceRadius * 0.3f, -faceRadius * 0.6f, faceRadius * 1.3f, Palette.RED_LIGHT, Palette.RED_DARK, Shader.TileMode.CLAMP)
        }
        ballBottomPaint.shader = RadialGradient(-faceRadius * 0.2f, faceRadius * 0.1f, faceRadius * 1.2f, Palette.WHITE, Palette.WHITE_SHADE, Shader.TileMode.CLAMP)
        centerPaint.shader = RadialGradient(-faceRadius * 0.08f, -faceRadius * 0.1f, faceRadius * 0.36f, Palette.WHITE, Palette.WHITE_SHADE, Shader.TileMode.CLAMP)

        val faceHeight = h - rim * 2
        glossRect.set(-(w - rim * 2) * 0.36f, -faceHeight / 2f + faceHeight * 0.04f, (w - rim * 2) * 0.36f, -faceHeight / 2f + faceHeight * 0.42f)
        glossPaint.shader = LinearGradient(0f, glossRect.top, 0f, glossRect.bottom, 0x8CFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)

        textPaint.textSize = when (kind) {
            Kind.PILL -> h * 0.42f
            Kind.SHOULDER -> h * 0.5f
            else -> size * 0.36f
        }
        textPaint.setShadowLayer(size * 0.04f, 0f, size * 0.02f, 0xAA000000.toInt())
        ballTextPaint.textSize = faceRadius * 0.42f
        iconPaint.strokeWidth = size * 0.07f
        ripplePaint.strokeWidth = size * 0.05f
    }

    private fun shape(out: Path, w: Float, h: Float, offsetY: Float) {
        out.reset()
        when (kind) {
            Kind.POKE_BALL, Kind.GREAT_BALL, Kind.ROUND -> out.addCircle(0f, offsetY, w / 2f, Path.Direction.CW)
            Kind.PILL -> out.addRoundRect(-w / 2f, -h / 2f + offsetY, w / 2f, h / 2f + offsetY, h / 2f, h / 2f, Path.Direction.CW)
            Kind.SHOULDER -> out.addRoundRect(-w / 2f, -h / 2f + offsetY, w / 2f, h / 2f + offsetY, h * 0.45f, h * 0.45f, Path.Direction.CW)
            Kind.DPAD -> {
                val arm = w * 0.36f / 2f
                val end = w / 2f
                val corner = arm * 0.35f
                out.addRoundRect(-arm, -end + offsetY, arm, end + offsetY, corner, corner, Path.Direction.CW)
                val horizontal = Path().apply { addRoundRect(-end, -arm + offsetY, end, arm + offsetY, corner, corner, Path.Direction.CW) }
                out.op(horizontal, Path.Op.UNION)
            }
        }
    }

    /**
     * Draws the button centered on ([cx], [cy]).
     *
     * @param press How far the cap is pressed, 0 to 1
     * @param ripple Progress of the press ring, or a negative value for none
     * @param pressedDirections For the D-pad, the pressed arms: bit 0 up, bit 1 down, bit 2 left, bit 3 right
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, press: Float, ripple: Float, pressedDirections: Int = 0) {
        canvas.save()
        canvas.translate(cx, cy)

        canvas.drawPath(shadowPath, shadowPaint)
        canvas.drawPath(socketPath, socketPaint)
        canvas.drawPath(socketPath, socketEdgePaint)
        canvas.drawPath(sidePath, sidePaint)

        if (ripple in 0f..1f) {
            drawRipple(canvas, ripple)
        }

        canvas.save()
        canvas.translate(0f, depth * 0.9f * press.coerceIn(-0.3f, 1f))
        canvas.drawPath(rimPath, rimPaint)
        drawFace(canvas, pressedDirections)
        if (press > 0f) {
            darkenPaint.alpha = (press.coerceAtMost(1f) * 50).toInt()
            canvas.drawPath(rimPath, darkenPaint)
        }
        canvas.restore()

        canvas.restore()
    }

    private fun drawRipple(canvas: Canvas, progress: Float) {
        ripplePaint.alpha = ((1f - progress) * 230).toInt()
        val grow = min(width, height) * 0.3f * progress
        when (kind) {
            Kind.POKE_BALL, Kind.GREAT_BALL, Kind.ROUND, Kind.DPAD -> canvas.drawCircle(0f, 0f, width / 2f + grow, ripplePaint)
            else -> {
                rippleRect.set(-width / 2f - grow, -height / 2f - grow, width / 2f + grow, height / 2f + grow)
                val corner = rippleRect.height() / 2f
                canvas.drawRoundRect(rippleRect, corner, corner, ripplePaint)
            }
        }
    }

    private fun drawFace(canvas: Canvas, pressedDirections: Int) {
        when (kind) {
            Kind.POKE_BALL, Kind.GREAT_BALL -> drawBall(canvas)
            Kind.DPAD -> drawDpad(canvas, pressedDirections)
            else -> {
                canvas.drawPath(facePath, if (isLit) litFacePaint else facePaint)
                drawGloss(canvas)
                when (icon) {
                    Icon.MENU -> drawMenuIcon(canvas)
                    Icon.FAST_FORWARD -> drawFastForwardIcon(canvas)
                    Icon.NONE -> label?.let { drawCenteredText(canvas, it, textPaint) }
                }
            }
        }
    }

    private fun drawBall(canvas: Canvas) {
        val r = width / 2f * 0.86f
        canvas.save()
        canvas.clipPath(facePath)
        canvas.drawRect(-r, -r, r, 0f, ballTopPaint)
        canvas.drawRect(-r, 0f, r, r, ballBottomPaint)
        if (kind == Kind.GREAT_BALL) {
            // The red fins of a Great Ball
            for (side in intArrayOf(-1, 1)) {
                path.reset()
                path.moveTo(side * r * 0.95f, -r * 0.42f)
                path.quadTo(side * r * 0.62f, -r * 0.78f, side * r * 0.2f, -r * 0.98f)
                path.lineTo(side * r * 0.36f, -r * 1.1f)
                path.lineTo(side * r * 1.1f, -r * 0.6f)
                path.close()
                canvas.drawPath(path, finPaint)
            }
        }
        canvas.drawRect(-r, -r * 0.08f, r, r * 0.08f, inkPaint)
        drawGloss(canvas)
        canvas.restore()

        canvas.drawCircle(0f, 0f, r * 0.36f, inkPaint)
        canvas.drawCircle(0f, 0f, r * 0.27f, centerPaint)
        label?.let { drawCenteredText(canvas, it, ballTextPaint) }
    }

    private fun drawDpad(canvas: Canvas, pressedDirections: Int) {
        canvas.drawPath(facePath, facePaint)
        val end = width / 2f
        val arm = width * 0.36f / 2f
        if (pressedDirections != 0) {
            if (pressedDirections and 1 != 0) canvas.drawRect(-arm, -end, arm, -arm, pressedArmPaint)
            if (pressedDirections and 2 != 0) canvas.drawRect(-arm, arm, arm, end, pressedArmPaint)
            if (pressedDirections and 4 != 0) canvas.drawRect(-end, -arm, -arm, arm, pressedArmPaint)
            if (pressedDirections and 8 != 0) canvas.drawRect(arm, -arm, end, arm, pressedArmPaint)
        }
        canvas.drawCircle(0f, 0f, arm * 0.55f, pressedArmPaint)

        // Gold arrows near the end of each arm
        val tip = end * 0.82f
        val base = end * 0.56f
        val half = arm * 0.48f
        for (rotation in floatArrayOf(0f, 90f, 180f, 270f)) {
            canvas.save()
            canvas.rotate(rotation)
            path.reset()
            path.moveTo(0f, -tip)
            path.lineTo(half, -base)
            path.lineTo(-half, -base)
            path.close()
            canvas.drawPath(path, arrowPaint)
            canvas.restore()
        }
        canvas.save()
        canvas.clipPath(facePath)
        drawGloss(canvas)
        canvas.restore()
    }

    private fun drawGloss(canvas: Canvas) {
        canvas.drawOval(glossRect, glossPaint)
    }

    private fun drawMenuIcon(canvas: Canvas) {
        val w = width * 0.2f
        val gap = width * 0.12f
        for (i in -1..1) {
            canvas.drawLine(-w, i * gap, w, i * gap, iconPaint)
        }
    }

    private fun drawFastForwardIcon(canvas: Canvas) {
        val s = width * 0.16f
        for (offset in floatArrayOf(-s * 0.95f, s * 0.15f)) {
            path.reset()
            path.moveTo(offset, -s)
            path.lineTo(offset + s * 1.1f, 0f)
            path.lineTo(offset, s)
            path.close()
            canvas.drawPath(path, iconPaint)
        }
    }

    private fun drawCenteredText(canvas: Canvas, text: String, paint: Paint) {
        val baseline = -(paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, 0f, baseline, paint)
    }

    /**
     * Whether a point, relative to the button's center, is on the button (or close to it, by [slop] times its size).
     */
    fun contains(x: Float, y: Float, slop: Float = 1.2f): Boolean {
        return when (kind) {
            Kind.PILL, Kind.SHOULDER -> {
                val extra = height * (slop - 1f)
                x in (-width / 2f - extra)..(width / 2f + extra) && y in (-height / 2f - extra)..(height / 2f + extra)
            }
            else -> x * x + y * y <= (width / 2f * slop) * (width / 2f * slop)
        }
    }
}
