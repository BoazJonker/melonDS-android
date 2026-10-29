package com.boazjonker.unbound.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * A wide 3D menu button, in the same style as the on-screen controls: a dark metal socket and a raised violet cap with a gold rim. The cap sinks when
 * pressed, sends out a gold ring, and springs back up when released. It can show a second, smaller line of text under its title.
 */
class PlankButton(context: Context) : View(context) {

    private val d = resources.displayMetrics.density

    var title: String = ""
        set(value) {
            field = value
            invalidate()
        }

    var subtitle: String? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    /**
     * Highlighted buttons (the main action of a screen) get a gold face instead of a violet one.
     */
    var isPrimary = false
        set(value) {
            field = value
            updateShaders()
            requestLayout()
            invalidate()
        }

    enum class Medal { NONE, PLAY, SETTINGS }

    /**
     * A gold-ringed medal on the left of the button, with a symbol in it. The text then lines up to the right of it.
     */
    var medal = Medal.NONE
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val press = PressState { invalidate() }

    private val socket = RectF()
    private val side = RectF()
    private val cap = RectF()
    private val face = RectF()
    private val gloss = RectF()
    private val ripple = RectF()

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.SHADOW }
    private val socketPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD_LIGHT }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.bold
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.06f
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Palette.condensed
        textAlign = Paint.Align.CENTER
    }

    private val medalDisc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val medalOuterRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD_LIGHT }
    private val medalInnerRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD }
    private val medalSymbol = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.GOLD_LIGHT; strokeCap = Paint.Cap.ROUND }
    private val symbolPath = Path()

    private val depth get() = 5f * d

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = when {
            medal != Medal.NONE && isPrimary -> 86f * d
            medal != Medal.NONE -> 78f * d
            subtitle != null -> 72f * d
            else -> 60f * d
        }.toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inset = 4f * d
        socket.set(inset, inset, w - inset, h - inset)
        cap.set(socket.left + 4f * d, socket.top + 3f * d, socket.right - 4f * d, socket.bottom - depth - 3f * d)
        side.set(cap.left, cap.top + depth, cap.right, cap.bottom + depth)
        face.set(cap.left + 3f * d, cap.top + 3f * d, cap.right - 3f * d, cap.bottom - 3f * d)
        gloss.set(face.left + face.width() * 0.04f, face.top + 2f * d, face.right - face.width() * 0.04f, face.top + face.height() * 0.48f)
        titlePaint.textSize = min(20f * d, cap.height() * 0.4f)
        subtitlePaint.textSize = titlePaint.textSize * 0.62f
        ripplePaint.strokeWidth = 2.5f * d
        updateShaders()
    }

    private fun updateShaders() {
        if (width == 0) return
        socketPaint.shader = RadialGradient(socket.centerX(), socket.top, socket.width() * 0.7f, Palette.METAL_LIGHT, Palette.METAL_DARK, Shader.TileMode.CLAMP)
        sidePaint.shader = LinearGradient(0f, side.top, 0f, side.bottom, Palette.GOLD, Palette.GOLD_DARK, Shader.TileMode.CLAMP)
        rimPaint.shader = LinearGradient(
            0f, cap.top, 0f, cap.bottom,
            intArrayOf(Palette.GOLD_LIGHT, Palette.GOLD, Palette.GOLD_DARK),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        facePaint.shader = if (isPrimary) {
            LinearGradient(0f, face.top, 0f, face.bottom, intArrayOf(Palette.GOLD_LIGHT, Palette.GOLD, 0xFFC8901E.toInt()), null, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, face.top, 0f, face.bottom, Palette.VIOLET, Palette.VIOLET_DARK, Shader.TileMode.CLAMP)
        }
        glossPaint.shader = LinearGradient(0f, gloss.top, 0f, gloss.bottom, 0x70FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        titlePaint.color = if (isPrimary) Palette.INK else Palette.WHITE
        subtitlePaint.color = if (isPrimary) 0xCC140F22.toInt() else 0xCCF8F6FF.toInt()
        if (isPrimary) {
            titlePaint.clearShadowLayer()
        } else {
            titlePaint.setShadowLayer(2f * d, 0f, 1f * d, 0xAA000000.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        val corner = socket.height() / 2f
        canvas.drawRoundRect(socket.left, socket.top + depth, socket.right, socket.bottom + depth * 0.6f, corner, corner, shadowPaint)
        canvas.drawRoundRect(socket, corner, corner, socketPaint)

        val capCorner = cap.height() / 2f
        canvas.drawRoundRect(side, capCorner, capCorner, sidePaint)

        val p = press.ripple
        if (p in 0f..1f) {
            val grow = 8f * d * p
            ripple.set(cap.left - grow, cap.top - grow, cap.right + grow, cap.bottom + grow)
            ripplePaint.alpha = ((1f - p) * 230).toInt()
            canvas.drawRoundRect(ripple, ripple.height() / 2f, ripple.height() / 2f, ripplePaint)
        }

        canvas.save()
        canvas.translate(0f, depth * 0.9f * press.press.coerceIn(-0.3f, 1f))
        canvas.drawRoundRect(cap, capCorner, capCorner, rimPaint)
        val faceCorner = face.height() / 2f
        canvas.drawRoundRect(face, faceCorner, faceCorner, facePaint)
        canvas.drawRoundRect(gloss, gloss.height() / 2f, gloss.height() / 2f, glossPaint)

        var textX = face.centerX()
        val align = if (medal == Medal.NONE) Paint.Align.CENTER else Paint.Align.LEFT
        titlePaint.textAlign = align
        subtitlePaint.textAlign = align
        if (medal != Medal.NONE) {
            val medalRadius = face.height() / 2f - 3f * d
            val medalX = face.left + face.height() / 2f
            drawMedal(canvas, medalX, face.centerY(), medalRadius)
            textX = medalX + medalRadius + 14f * d
        }

        val sub = subtitle
        if (sub == null) {
            canvas.drawText(title, textX, face.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2f, titlePaint)
        } else {
            val total = titlePaint.textSize + subtitlePaint.textSize * 1.15f
            val titleBaseline = face.centerY() - total / 2f + titlePaint.textSize * 0.85f
            canvas.drawText(title, textX, titleBaseline, titlePaint)
            canvas.drawText(sub, textX, titleBaseline + subtitlePaint.textSize * 1.3f, subtitlePaint)
        }

        if (press.press > 0f) {
            darkenPaint.alpha = (press.press.coerceAtMost(1f) * 45).toInt()
            canvas.drawRoundRect(cap, capCorner, capCorner, darkenPaint)
        }
        canvas.restore()
    }

    private fun drawMedal(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        medalDisc.color = if (isPrimary) Palette.INK else Palette.NIGHT_MID
        canvas.drawCircle(cx, cy, r, medalDisc)
        medalOuterRing.strokeWidth = 2f * d
        canvas.drawCircle(cx, cy, r - 1.5f * d, medalOuterRing)
        medalInnerRing.strokeWidth = 1.5f * d
        canvas.drawCircle(cx, cy, r * 0.74f, medalInnerRing)

        val s = r * 0.34f
        when (medal) {
            Medal.PLAY -> {
                medalSymbol.style = Paint.Style.FILL
                symbolPath.reset()
                symbolPath.moveTo(cx - s * 0.7f, cy - s)
                symbolPath.lineTo(cx + s * 1.05f, cy)
                symbolPath.lineTo(cx - s * 0.7f, cy + s)
                symbolPath.close()
                canvas.drawPath(symbolPath, medalSymbol)
            }
            Medal.SETTINGS -> {
                medalSymbol.style = Paint.Style.STROKE
                medalSymbol.strokeWidth = 2f * d
                canvas.drawCircle(cx, cy, s * 0.45f, medalSymbol)
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    canvas.drawLine(cx + cos * s * 0.72f, cy + sin * s * 0.72f, cx + cos * s * 1.05f, cy + sin * s * 1.05f, medalSymbol)
                }
            }
            Medal.NONE -> {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                press.setPressed(true)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            MotionEvent.ACTION_MOVE -> {
                val inside = event.x in 0f..width.toFloat() && event.y in 0f..height.toFloat()
                if (!inside) press.setPressed(false)
            }
            MotionEvent.ACTION_UP -> {
                val wasPressed = press.isPressed
                press.setPressed(false)
                if (wasPressed) performClick()
            }
            MotionEvent.ACTION_CANCEL -> press.setPressed(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        press.cancel()
    }
}
