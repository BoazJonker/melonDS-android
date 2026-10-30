package com.boazjonker.unbound.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.PathParser
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun clamp01(t: Float) = t.coerceIn(0f, 1f)
private fun progress(ms: Float, start: Float, end: Float) = clamp01((ms - start) / (end - start))
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
private fun easeIn(t: Float) = t * t * t
private fun easeInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

/**
 * The swirling portal that opens inside the gold ring: an energy rim racing around it, a whirlpool of galaxy colours with light streaks spiralling inward,
 * and a bright pulsing core. Drawn around a centre, with the radius of the gold ring.
 */
class PortalPainter {

    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sparkRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF07031A.toInt() }
    private val swirlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spiralPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private var builtFor = -1f

    private fun build(r: Float) {
        if (r == builtFor) return
        builtFor = r
        rimPaint.shader = SweepGradient(0f, 0f,
            intArrayOf(0x0046F0C8, 0xFF46F0C8.toInt(), 0x00B26BFF, 0xFFB26BFF.toInt(), 0x00FF6FD8, 0xFFFF6FD8.toInt(), 0x0046F0C8, 0xFF46F0C8.toInt()),
            floatArrayOf(0f, 0.11f, 0.25f, 0.42f, 0.55f, 0.7f, 0.83f, 1f))
        sparkRimPaint.shader = SweepGradient(0f, 0f,
            intArrayOf(0x00FFEDB0, 0xE6FFEDB0.toInt(), 0x00FFEDB0, 0x00FFEDB0, 0xE6FFEDB0.toInt(), 0x00FFEDB0, 0x00FFEDB0),
            floatArrayOf(0f, 0.06f, 0.17f, 0.5f, 0.56f, 0.67f, 1f))
        swirlPaint.shader = SweepGradient(0f, 0f,
            intArrayOf(0xFF120A35.toInt(), Palette.VIOLET, 0xFF120A35.toInt(), Palette.AURORA_TEAL, 0xFF120A35.toInt(), Palette.AURORA_PINK, 0xFF120A35.toInt(), Palette.VIOLET_LIGHT, 0xFF120A35.toInt()),
            floatArrayOf(0f, 0.1f, 0.18f, 0.3f, 0.42f, 0.55f, 0.68f, 0.8f, 1f))
        // Twelve thin light streaks
        val stripes = 12
        val colours = IntArray(stripes * 4)
        val positions = FloatArray(stripes * 4)
        for (i in 0 until stripes) {
            val base = i.toFloat() / stripes
            val step = 1f / stripes
            colours[i * 4] = 0x00CFFFF2; positions[i * 4] = base
            colours[i * 4 + 1] = 0x00CFFFF2; positions[i * 4 + 1] = base + step * 0.45f
            colours[i * 4 + 2] = 0x73CFFFF2; positions[i * 4 + 2] = base + step * 0.5f
            colours[i * 4 + 3] = 0x00CFFFF2; positions[i * 4 + 3] = base + step * 0.62f
        }
        spiralPaint.shader = SweepGradient(0f, 0f, colours, positions)
        val disc = r * 0.92f
        depthPaint.shader = RadialGradient(0f, 0f, disc, intArrayOf(0x00000000, 0x2607031A, 0xBF07031A.toInt()), floatArrayOf(0f, 0.4f, 0.85f), Shader.TileMode.CLAMP)
        corePaint.shader = RadialGradient(0f, 0f, disc * 0.22f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFCFFFF2.toInt(), 0x999C7BFF.toInt(), 0x009C7BFF), floatArrayOf(0f, 0.3f, 0.65f, 1f), Shader.TileMode.CLAMP)
    }

    /**
     * @param r Radius of the gold ring
     * @param scale How far the portal has opened, 0 to about 1.1
     * @param t Time in seconds, for the turning parts
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float, scale: Float, t: Float) {
        if (scale <= 0f) return
        build(r)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        canvas.save()
        canvas.rotate(t * 327f % 360f)
        rimPaint.strokeWidth = r * 0.09f
        canvas.drawCircle(0f, 0f, r * 1.045f, rimPaint)
        canvas.restore()
        canvas.save()
        canvas.rotate(-t * 212f % 360f)
        sparkRimPaint.strokeWidth = r * 0.035f
        canvas.drawCircle(0f, 0f, r * 1.1f, sparkRimPaint)
        canvas.restore()

        val disc = r * 0.92f
        clip.reset()
        clip.addCircle(0f, 0f, disc, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawCircle(0f, 0f, disc, discPaint)
        canvas.save()
        canvas.rotate(-t * 225f % 360f)
        canvas.drawCircle(0f, 0f, disc * 1.2f, swirlPaint)
        canvas.restore()
        canvas.save()
        canvas.rotate(t * 450f % 360f)
        canvas.drawCircle(0f, 0f, disc * 0.72f, spiralPaint)
        canvas.restore()
        canvas.drawCircle(0f, 0f, disc, depthPaint)
        ringPaint.strokeWidth = disc * 0.018f
        ringPaint.color = 0x739C7BFF
        canvas.drawCircle(0f, 0f, disc * 0.56f, ringPaint)
        ringPaint.color = 0x6646F0C8
        canvas.drawCircle(0f, 0f, disc * 0.32f, ringPaint)
        val pulse = 0.8f + 0.35f * (0.5f + 0.5f * sin(t * PI.toFloat() / 0.9f))
        canvas.save()
        canvas.scale(pulse, pulse)
        canvas.drawCircle(0f, 0f, disc * 0.22f, corePaint)
        canvas.restore()
        canvas.restore()

        canvas.restore()
    }
}

/**
 * A giant hand seen palm-first, as if reaching straight at you: a galaxy-purple palm with a glowing gem, and fingers made of two gold-trimmed galaxy
 * plates each, joined by a glowing teal joint and ending in a long crystal claw. Drawn in a 400 x 420 design space; the centre of the palm is at
 * ([PALM_X], [PALM_Y]).
 */
class HandPainter {

    companion object {
        const val PALM_X = 200f
        const val PALM_Y = 254f
        const val WIDTH = 400f
    }

    // Where each finger joins the palm: x, y, angle and size. The last one is the thumb
    private val fingers = arrayOf(
        floatArrayOf(158f, 186f, -26f, 1f),
        floatArrayOf(196f, 174f, -6f, 1.06f),
        floatArrayOf(234f, 182f, 14f, 1f),
        floatArrayOf(264f, 204f, 34f, 0.85f),
        floatArrayOf(142f, 264f, -76f, 0.9f),
    )
    private val lowerPlate = RectF(-19f, -74f, 19f, 6f)
    private val upperPlate = RectF(-16f, -142f, 16f, -70f)
    private val claw = PathParser.createPathFromPathData("M-13 -136 Q-15 -180 0 -212 Q15 -180 13 -136 Z")
    private val palm = PathParser.createPathFromPathData("M140 190 Q200 160 262 192 Q296 250 266 312 Q200 344 136 312 Q106 252 140 190 Z")
    private val crease = PathParser.createPathFromPathData("M150 214 Q200 196 252 216")

    private val galaxyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goldTrim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        shader = LinearGradient(0f, -150f, 0f, 0f, intArrayOf(0xFFFFF6D0.toInt(), Palette.GOLD, 0xFF8C5E10.toInt()), null, Shader.TileMode.MIRROR)
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeJoin = Paint.Join.ROUND
        color = 0x4D46F0C8
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xD946F0C8.toInt() }
    private val clawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -136f, 0f, -212f, intArrayOf(0xFF1F6E66.toInt(), Palette.AURORA_TEAL, 0xFFF0FFFB.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private val clawEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = 0xFF0F3F3A.toInt() }
    private val palmShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(PALM_X, 240f, 110f, intArrayOf(0x00000000, 0x00000000, 0x73000000), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val palmTrim = Paint(goldTrim).apply { strokeWidth = 5f }
    private val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x59CFFFF2; strokeCap = Paint.Cap.ROUND }
    private val gemSocket = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.INK }
    private val gemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(193f, 248f, 20f, intArrayOf(0xFFFFFFFF.toInt(), 0xFF9FFFE6.toInt(), 0xFF1F8C7A.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
    }
    private val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(PALM_X, 350f, 80f, 0xB39C7BFF.toInt(), 0x009C7BFF, Shader.TileMode.CLAMP)
    }

    init {
        val bitmap = Bitmap.createBitmap(400, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFF3B2390.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(140f, 126f, 250f, 0xFFC79BFF.toInt(), 0x00C79BFF, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 400f, 420f, paint)
        paint.shader = RadialGradient(280f, 273f, 200f, 0xB346F0C8.toInt(), 0x0046F0C8, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 400f, 420f, paint)
        paint.shader = null
        paint.color = Palette.WHITE
        val random = Random(9)
        repeat(90) {
            paint.alpha = 120 + random.nextInt(136)
            canvas.drawCircle(random.nextFloat() * 400f, random.nextFloat() * 420f, 0.6f + random.nextFloat() * 1.3f, paint)
        }
        galaxyPaint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    /**
     * Draws the hand in its design space; the caller sets up the transform.
     *
     * @param fold How far the fingers have folded over the palm: 0 spread, 1 closed
     * @param stretch Extra length of the fingers, for flexing
     * @param gemGlow 0 to 1
     */
    fun draw(canvas: Canvas, fold: Float, stretch: Float, gemGlow: Float) {
        canvas.drawOval(PALM_X - 70f, 300f, PALM_X + 70f, 400f, mistPaint)
        val foldScale = lerp(1f + stretch * 0.06f, -0.38f, fold)
        for (finger in fingers) {
            canvas.save()
            canvas.translate(finger[0], finger[1])
            canvas.rotate(finger[2])
            canvas.scale(finger[3], finger[3])
            canvas.scale(1f, foldScale, 0f, 6f)
            // The galaxy fill stays put while the finger moves, like looking through it into space
            canvas.drawRoundRect(lowerPlate, 15f, 15f, glowPaint)
            canvas.drawRoundRect(upperPlate, 13f, 13f, glowPaint)
            canvas.drawRoundRect(lowerPlate, 15f, 15f, galaxyPaint)
            canvas.drawRoundRect(lowerPlate, 15f, 15f, goldTrim)
            canvas.drawCircle(0f, -74f, 11f, jointPaint)
            canvas.drawRoundRect(upperPlate, 13f, 13f, galaxyPaint)
            canvas.drawRoundRect(upperPlate, 13f, 13f, goldTrim)
            canvas.drawPath(claw, clawPaint)
            canvas.drawPath(claw, clawEdge)
            canvas.restore()
        }
        canvas.drawPath(palm, glowPaint)
        canvas.drawPath(palm, galaxyPaint)
        canvas.drawPath(palm, palmShade)
        canvas.drawPath(palm, palmTrim)
        canvas.drawPath(crease, creasePaint)
        canvas.drawCircle(PALM_X, PALM_Y, 27f, gemSocket)
        canvas.drawCircle(PALM_X, PALM_Y, 27f, goldTrim)
        gemPaint.alpha = (165 + 90 * gemGlow).toInt()
        canvas.drawCircle(PALM_X, PALM_Y, 18f, gemPaint)
    }
}

/**
 * The light-speed tunnel: darkness with streaks of light rushing outward and coloured rings flying past.
 */
class TunnelPainter {

    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringColours = intArrayOf(Palette.GOLD, Palette.AURORA_TEAL, Palette.GOLD, Palette.VIOLET_LIGHT, Palette.AURORA_PINK)
    private val streakAngles = FloatArray(56)
    private var builtFor = -1f

    init {
        val random = Random(4)
        for (i in streakAngles.indices) streakAngles[i] = random.nextFloat() * 360f
    }

    fun draw(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, t: Float, alpha: Float, density: Float) {
        if (alpha <= 0f) return
        val far = hypot(max(cx, w - cx), max(cy, h - cy))
        if (far != builtFor) {
            builtFor = far
            darkPaint.shader = RadialGradient(cx, cy, far, intArrayOf(0xFF2A1A6E.toInt(), 0xFF0B0624.toInt(), 0xFF050310.toInt()), floatArrayOf(0f, 0.35f, 0.7f), Shader.TileMode.CLAMP)
        }
        val a = (alpha * 255).toInt()
        darkPaint.alpha = a
        canvas.drawRect(0f, 0f, w, h, darkPaint)

        // Streaks: each rushes outward from near the centre, every 0.6 seconds
        streakPaint.strokeWidth = 2f * density
        for ((i, angle) in streakAngles.withIndex()) {
            val p = ((t / 0.6f) + i * 0.137f) % 1f
            val rad = Math.toRadians(angle.toDouble())
            val inner = far * (0.08f + easeIn(p) * 0.9f)
            val outer = inner + far * (0.05f + p * 0.25f)
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            streakPaint.color = if (i % 3 == 0) Palette.GOLD else 0xFFCFFFF2.toInt()
            streakPaint.alpha = ((1f - p) * 0.8f * a).toInt()
            canvas.drawLine(cx + c * inner, cy + s * inner, cx + c * outer, cy + s * outer, streakPaint)
        }

        // Rings fly past, one every 0.22 seconds
        val base = 50f * density
        for (i in ringColours.indices) {
            val p = ((t / 1.1f) + i / ringColours.size.toFloat()) % 1f
            val radius = base * (0.12f + easeIn(p) * 7f)
            val ringAlpha = (sin(p * PI.toFloat()) * a).toInt()
            ringGlow.color = ringColours[i]
            ringGlow.alpha = ringAlpha / 3
            ringGlow.strokeWidth = 10f * density
            canvas.drawCircle(cx, cy, radius, ringGlow)
            ringPaint.color = ringColours[i]
            ringPaint.alpha = ringAlpha
            ringPaint.strokeWidth = 3.5f * density
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }
    }
}

/**
 * The start of the game, played over the start screen after PLAY: the gold ring spins fast, slows and clunks to a stop with a flash, then bursts open into
 * a swirling portal that pulls in sparks of light. The screen rumbles while energy cracks spread from the portal, then a tiny hand appears deep inside
 * it and comes straight at you, growing until it fills the screen, flexes its claws and closes on you.
 *
 * The view draws the ring, portal, cracks and hand itself. The shaking moves [stage], the view that holds the whole start screen (this view included).
 * Tapping skips ahead.
 */
class PortalOverlayView(context: Context) : View(context) {

    companion object {
        /** When the screen has been pulled into the portal and the game should open. */
        const val PULLED_MS = 9_300f
        /** When the darkness of the tunnel starts covering everything: the moment the hand closes over the screen. */
        const val DARK_START_MS = 8_650f
    }

    private val d = resources.displayMetrics.density
    private val coin = CoinPainter()
    private val portal = PortalPainter()
    private val hand = HandPainter()
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var cx = 0f
    private var cy = 0f
    private var r = 0f
    private var stage: View? = null
    private var onDarken: ((Float) -> Unit)? = null
    private var onPulled: (() -> Unit)? = null
    private var animator: ValueAnimator? = null
    private var ms = 0f
    private var finished = false

    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val impactPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crackGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0x66B26BFF }
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFFE3C6FF.toInt() }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cracks = mutableListOf<Pair<Path, Float>>()
    private val sparkColours = intArrayOf(Palette.GOLD_LIGHT, 0xFFCFFFF2.toInt(), Palette.GLOW, Palette.GOLD_LIGHT, 0xFFCFFFF2.toInt(), 0xFFFFD6F4.toInt())

    private val crackData = listOf(
        "M195 160 L188 120 L200 96 L190 60 L198 20",
        "M318 250 L350 236 L362 214 L388 204",
        "M312 372 L340 392 L338 420 L370 446 L390 452",
        "M78 368 L52 388 L58 414 L24 436 L0 440",
        "M74 236 L44 222 L36 196 L6 186",
        "M262 182 L290 150 L284 124 L310 100",
        "M128 182 L100 150 L106 120 L80 96",
    )

    init {
        visibility = GONE
    }

    /**
     * Starts the transition around the gold ring at ([centerX], [centerY]) with radius [radius], in this view's coordinates.
     *
     * @param onDarken Called with the darkness of the tunnel (0 to 1) as it covers the screen
     * @param onPulled Called once the screen has been pulled into the portal
     */
    fun play(centerX: Float, centerY: Float, radius: Float, stage: View, onDarken: (Float) -> Unit, onPulled: () -> Unit) {
        cx = centerX
        cy = centerY
        r = radius
        this.stage = stage
        this.onDarken = onDarken
        this.onPulled = onPulled
        finished = false
        buildCracks()
        stage.pivotX = cx + left
        stage.pivotY = cy + top

        visibility = VISIBLE
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, PULLED_MS).apply {
            duration = PULLED_MS.toLong()
            interpolator = LinearInterpolator()
            addUpdateListener { update(it.animatedValue as Float) }
            start()
        }
    }

    /**
     * Puts everything back as it was, for when the player comes back to the start screen.
     */
    fun reset() {
        animator?.cancel()
        animator = null
        visibility = GONE
        stage?.apply {
            translationX = 0f
            translationY = 0f
            rotation = 0f
            scaleX = 1f
            scaleY = 1f
        }
        ms = 0f
    }

    val isPlaying get() = animator?.isRunning == true

    private fun buildCracks() {
        cracks.clear()
        val matrix = Matrix().apply {
            setTranslate(-195f, -300f)
            postScale(r / 120f, r / 120f)
            postTranslate(cx, cy)
        }
        for (data in crackData) {
            val path = Path()
            path.addPath(PathParser.createPathFromPathData(data), matrix)
            cracks += path to PathMeasure(path, false).length
        }
    }

    private fun update(time: Float) {
        ms = time
        val stage = stage ?: return
        val (dx, dy, rotation) = shake(time)
        stage.translationX = dx
        stage.translationY = dy
        stage.rotation = rotation
        onDarken?.invoke(progress(time, DARK_START_MS, 8_950f))
        invalidate()
        if (time >= PULLED_MS && !finished) {
            finished = true
            onPulled?.invoke()
        }
    }

    /**
     * Shaking of the screen over time: a strong rumble while the portal opens, tremors while the hand hovers close, and a jolt when it grabs.
     */
    private fun shake(time: Float): Triple<Float, Float, Float> {
        val amplitude = when {
            time in 3_000f..5_000f -> 9f * (1f - 0.5f * progress(time, 4_400f, 5_000f))
            time in 7_600f..8_300f -> 4f
            time in 8_450f..8_700f -> 12f * (1f - progress(time, 8_450f, 8_700f))
            else -> 0f
        } * d
        if (amplitude == 0f) return Triple(0f, 0f, 0f)
        val n = time / 40f
        return Triple(
            amplitude * sin(n * 2.3f) * cos(n * 1.7f),
            amplitude * cos(n * 2.9f) * sin(n * 1.3f),
            amplitude / d * 0.09f * sin(n * 3.1f),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (r == 0f) return
        val time = ms
        val t = SystemClock.uptimeMillis() / 1000f

        // Portal behind the ring
        val portalScale = when {
            time < 2_400f -> 0f
            time < 2_800f -> lerp(0.1f, 1.12f, easeOut(progress(time, 2_400f, 2_800f)))
            else -> lerp(1.12f, 1f, progress(time, 2_800f, 3_000f))
        }
        portal.draw(canvas, cx, cy, r, portalScale, t)

        // The ring: six fast turns that slow down, a small swing past the stop and back, then still
        val rotation = when {
            time < 2_000f -> easeOut(progress(time, 0f, 2_000f)) * 2_175f
            time < 2_200f -> lerp(2_175f, 2_150f, progress(time, 2_000f, 2_200f))
            time < 2_400f -> lerp(2_150f, 2_160f, progress(time, 2_200f, 2_400f))
            else -> 2_160f
        }
        val growth = lerp(1f, 1.1f, easeOut(progress(time, 0f, 2_000f)))
        val flash = if (time in 2_050f..2_800f) 1f - progress(time, 2_150f, 2_800f) else 0f
        coin.draw(canvas, cx, cy, r * growth, rotation, faceAlpha = 1f - progress(time, 0f, 1_200f), glow = flash + 0.3f * progress(time, 2_400f, 3_000f))
        if (flash > 0f) {
            flashPaint.shader = RadialGradient(cx, cy, r * 1.6f, intArrayOf(0x00FFEDB0, 0xD9FFEDB0.toInt(), 0x00F2C14E), floatArrayOf(0.5f, 0.62f, 0.8f), Shader.TileMode.CLAMP)
            flashPaint.alpha = (flash * 255).toInt()
            canvas.drawCircle(cx, cy, r * 1.6f * (1f + (1f - flash) * 0.3f), flashPaint)
        }

        drawSparks(canvas, time, t)
        drawCracks(canvas, time)

        // Darkened edges while the portal rumbles
        val vignette = min(progress(time, 3_000f, 4_000f), 1f - progress(time, 9_000f, 9_600f))
        if (vignette > 0f) {
            if (vignettePaint.shader == null) {
                val far = hypot(max(cx, width - cx), max(cy, height - cy))
                vignettePaint.shader = RadialGradient(cx, cy, far, intArrayOf(0x00050310, 0x00050310, 0xBF050310.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            }
            vignettePaint.alpha = (vignette * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)
        }

        // The edges darken as the hand blocks out the light
        val dim = progress(time, 6_200f, 8_300f) * 0.7f
        if (dim > 0f && time < 8_900f) {
            if (dimPaint.shader == null) {
                val far = hypot(max(cx, width - cx), max(cy, height - cy))
                dimPaint.shader = RadialGradient(cx, cy + r * 0.3f, far, intArrayOf(0x00050310, 0x00050310, 0xE6050310.toInt()), floatArrayOf(0f, 0.3f, 0.8f), Shader.TileMode.CLAMP)
            }
            dimPaint.alpha = (dim * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        }
        drawHand(canvas, time, t)

        // Flash when the arm grabs
        val impact = if (time >= 8_550f) 1f - progress(time, 8_600f, 9_000f) else 0f
        if (impact > 0f) {
            if (impactPaint.shader == null) {
                impactPaint.shader = RadialGradient(width / 2f, height * 0.6f, max(width, height).toFloat(), intArrayOf(0xFFFFFFFF.toInt(), 0xD9CFFFF2.toInt(), 0x809C7BFF.toInt()), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
            }
            impactPaint.alpha = (impact * 0.85f * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), impactPaint)
        }
    }

    private fun drawSparks(canvas: Canvas, time: Float, t: Float) {
        val visible = min(progress(time, 2_600f, 3_100f), 1f - progress(time, 8_000f, 8_400f))
        if (visible <= 0f) return
        for (i in 0 until 14) {
            val angle = i * 360f / 14f + i * 7f
            val p = ((t / 1.1f) + i * 0.29f) % 1f
            val distance = lerp(r * 1.55f, r * 0.07f, easeIn(p))
            val rad = Math.toRadians(angle.toDouble())
            val x = cx + cos(rad).toFloat() * distance
            val y = cy + sin(rad).toFloat() * distance
            val alpha = visible * min(p / 0.15f, 1f) * (1f - p * 0.6f)
            val size = (if (i % 3 == 0) 3f else 2f) * d * (1f - p * 0.8f)
            sparkPaint.color = sparkColours[i % sparkColours.size]
            sparkPaint.alpha = (alpha * 90).toInt()
            canvas.drawCircle(x, y, size * 2.6f, sparkPaint)
            sparkPaint.alpha = (alpha * 255).toInt()
            canvas.drawCircle(x, y, size, sparkPaint)
        }
    }

    private fun drawCracks(canvas: Canvas, time: Float) {
        val grow = progress(time, 3_100f, 4_400f)
        val visible = 1f - progress(time, 8_300f, 8_800f)
        if (grow <= 0f || visible <= 0f) return
        crackGlow.strokeWidth = 7f * d
        crackPaint.strokeWidth = 2.2f * d
        crackGlow.alpha = (visible * 102).toInt()
        crackPaint.alpha = (visible * 255).toInt()
        for ((path, length) in cracks) {
            val effect = DashPathEffect(floatArrayOf(length, length), length * (1f - grow))
            crackGlow.pathEffect = effect
            crackPaint.pathEffect = effect
            canvas.drawPath(path, crackGlow)
            canvas.drawPath(path, crackPaint)
        }
    }

    /**
     * The hand: it appears tiny deep inside the portal, wobbles, then comes straight at you faster and faster, hovers close flexing its fingers, and
     * lunges, closing over the screen.
     */
    private fun drawHand(canvas: Canvas, time: Float, t: Float) {
        if (time < 5_000f || time > 8_900f) return
        // At scale 1 the hand is as wide as the screen
        val k = width / HandPainter.WIDTH
        val (scale, rotation, drop) = when {
            time < 5_300f -> progress(time, 5_000f, 5_300f).let { Triple(lerp(0.03f, 0.07f, it), lerp(0f, -4f, it), 0f) }
            time < 5_700f -> progress(time, 5_300f, 5_700f).let { Triple(lerp(0.07f, 0.1f, it), lerp(-4f, 3f, it), 0f) }
            time < 5_900f -> progress(time, 5_700f, 5_900f).let { Triple(lerp(0.1f, 0.09f, it), lerp(3f, 0f, it), 0f) }
            // Coming closer: it grows slowly at first, then faster and faster
            time < 7_600f -> easeIn(progress(time, 5_900f, 7_600f)).let { Triple(lerp(0.09f, 1f, it), lerp(0f, -3f, it), lerp(0f, 40f, it)) }
            time < 8_000f -> progress(time, 7_600f, 8_000f).let { Triple(lerp(1f, 1.1f, it), lerp(-3f, 2f, it), lerp(40f, 46f, it)) }
            time < 8_300f -> progress(time, 8_000f, 8_300f).let { Triple(lerp(1.1f, 1.18f, it), lerp(2f, 0f, it), lerp(46f, 52f, it)) }
            time < 8_650f -> easeIn(progress(time, 8_300f, 8_650f)).let { Triple(lerp(1.18f, 5.5f, it), 0f, lerp(52f, 90f, it)) }
            else -> progress(time, 8_650f, 8_900f).let { Triple(lerp(5.5f, 7f, it), 0f, lerp(90f, 100f, it)) }
        }
        val alpha = progress(time, 5_000f, 5_250f)
        val fold = easeInOut(progress(time, 8_350f, 8_650f))
        val stretch = if (time in 7_600f..8_300f) sin((time - 7_600f) / 700f * PI.toFloat()) else 0f

        canvas.save()
        if (alpha < 1f) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (alpha * 255).toInt())
        canvas.translate(cx, cy + drop * k)
        canvas.rotate(rotation)
        canvas.scale(scale * k, scale * k)
        canvas.translate(-HandPainter.PALM_X, -HandPainter.PALM_Y)
        hand.draw(canvas, fold = fold, stretch = stretch, gemGlow = 0.5f + 0.5f * sin(t * PI.toFloat()))
        if (alpha < 1f) canvas.restore()
        canvas.restore()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        // Tapping skips straight into the game
        if (event.actionMasked == MotionEvent.ACTION_UP && !finished && ms < DARK_START_MS) {
            animator?.cancel()
            update(DARK_START_MS)
            animator = ValueAnimator.ofFloat(DARK_START_MS, PULLED_MS).apply {
                duration = (PULLED_MS - DARK_START_MS).toLong()
                interpolator = LinearInterpolator()
                addUpdateListener { update(it.animatedValue as Float) }
                start()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}

/**
 * The tunnel, drawn over everything: on the start screen it covers the screen as you are pulled in, and in the game it keeps rushing until the game is
 * ready, then opens from the middle with a burst of light and a ring of gold, revealing the game.
 */
class TunnelOverlayView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val tunnel = TunnelPainter()
    private val startTime = SystemClock.uptimeMillis()
    private val hole = Path()
    private val burstPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD_LIGHT }
    private val edgeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Palette.GOLD }

    /** Coverage of the darkness, 0 to 1. */
    var darkness = 0f
        set(value) {
            field = value
            visibility = if (value > 0f || revealing) VISIBLE else GONE
            invalidate()
        }

    /** Where the tunnel and the reveal are centred, in this view's coordinates; the middle of the view when not set. */
    var centerX = Float.NaN
    var centerY = Float.NaN

    private var revealing = false
    private var revealStart = 0L
    private var onRevealed: (() -> Unit)? = null

    /**
     * Opens the tunnel from the middle, revealing what is behind it.
     */
    fun reveal(onRevealed: () -> Unit) {
        this.onRevealed = onRevealed
        revealing = true
        revealStart = SystemClock.uptimeMillis()
        visibility = VISIBLE
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = visibility == VISIBLE && darkness >= 1f && !revealing

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = if (centerX.isNaN()) w / 2f else centerX
        val cy = if (centerY.isNaN()) h / 2f else centerY
        val t = (SystemClock.uptimeMillis() - startTime) / 1000f

        var p = 0f
        if (revealing) {
            p = clamp01((SystemClock.uptimeMillis() - revealStart) / REVEAL_MS)
        }
        val far = hypot(max(cx, w - cx), max(cy, h - cy))
        val holeRadius = far * easeInOut(p) * 1.05f

        canvas.save()
        if (holeRadius > 0f) {
            hole.reset()
            hole.addCircle(cx, cy, holeRadius, Path.Direction.CW)
            canvas.clipOutPath(hole)
        }
        tunnel.draw(canvas, w, h, cx, cy, t, darkness, d)
        canvas.restore()

        if (revealing) {
            val burst = 1f - p
            burstPaint.shader = RadialGradient(cx, cy, max(1f, far * 0.35f * (0.4f + p * 3f)),
                intArrayOf(0xF2FFFFFF.toInt(), 0xB3FFEDB0.toInt(), 0x00F2C14E), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            burstPaint.alpha = (burst * 255).toInt()
            canvas.drawCircle(cx, cy, far * 0.35f * (0.4f + p * 3f), burstPaint)
            if (holeRadius > 0f) {
                edgeGlow.strokeWidth = 14f * d
                edgeGlow.alpha = (burst * 110).toInt()
                canvas.drawCircle(cx, cy, holeRadius, edgeGlow)
                edgePaint.strokeWidth = 4f * d
                edgePaint.alpha = (burst * 255).toInt()
                canvas.drawCircle(cx, cy, holeRadius, edgePaint)
            }
            if (p >= 1f) {
                revealing = false
                darkness = 0f
                onRevealed?.invoke()
                onRevealed = null
                return
            }
        }
        if (visibility == VISIBLE) postInvalidateOnAnimation()
    }

    private companion object {
        const val REVEAL_MS = 900f
    }
}
