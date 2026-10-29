package me.magnum.melonds.ui.emulator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Animated countryside scene drawn behind the on-screen controls, inspired by The Legend of Zelda: Spirit Tracks. A train travels through a layered landscape
 * that scrolls past at different speeds: a sunrise sky with three layers of clouds (the nearest ones casting shadows on the land), two mountain ranges, hills
 * with windmills, trees and grass, a meadow with flowers, telegraph poles, a stream, butterflies and swaying grass in the foreground. Everything is drawn with
 * shapes; the parts that never move are drawn once into a cached image.
 *
 * The areas covered by the DS screens are never drawn, so the scene only shows around them. The screens get a dark wood and gold frame, which is drawn even
 * when the scene itself is disabled.
 */
class SpiritTracksBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class SceneStyle {
        // Countryside with a passing train
        COUNTRYSIDE,
        // Plain parchment with swirls and little spirits drifting by
        SPIRIT_SWIRLS,
    }

    enum class TrainStyle {
        // Toy-like train in the style of Spirit Tracks
        SPIRIT,
        // Classic steam train
        CLASSIC,
    }

    private companion object {
        // Redraw at roughly 15 FPS. The scene moves slowly, so this is smooth enough and keeps the cost low while the game runs
        const val FRAME_INTERVAL_MS = 66L
        const val TAU = (2 * PI).toFloat()

        // Speed at which the world scrolls past the train, at the track's depth
        const val WORLD_SPEED_DP = 60f
        const val SMOKE_INTERVAL_SECONDS = 0.28f
        const val SMOKE_LIFETIME_SECONDS = 2.2f

        const val WOOD_DARK = 0xFF3B2412.toInt()
        const val WOOD = 0xFF6B4423.toInt()
        const val GOLD = 0xFFE2B54A.toInt()
        const val INK = 0xFF232128.toInt()

        // Spirit swirls background: parchment like the button faces, with the pattern in a slightly darker tone
        const val PARCHMENT = 0xFFEADBB4.toInt()
        const val PATTERN_SPIRIT = 0x4D96703A
        const val PATTERN_SWIRL = 0x3896703A
        const val PATTERN_CELL_DP = 75f
        const val PATTERN_SPEED_DP = 10f
        // Shadow under the screens, so that they seem to float above the parchment
        const val SCREEN_SHADOW = 0x94462A0C.toInt()
        const val SCREEN_SHADOW_NEAR = 0x52462A0C
    }

    private class ScreenArea(val rect: RectF, val isMiniScreen: Boolean)
    private class Puff(val x: Float, val y: Float, val bornAt: Float)
    private class Cloud(val x: Float, val y: Float, val scale: Float)
    private class Tree(val x: Float, val scale: Float, val colour: Int)
    private class Blade(val x: Float, val height: Float, val hasFlower: Boolean, val colour: Int)

    private val d = resources.displayMetrics.density
    private val screenAreas = mutableListOf<ScreenArea>()
    private var isAnimating = false
    private val startTime = SystemClock.uptimeMillis()

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

    var sceneStyle = SceneStyle.COUNTRYSIDE
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /**
     * Battery saver: the scene moves at half speed and is redrawn half as often.
     */
    var isLowPower = false

    var trainStyle = TrainStyle.SPIRIT
        set(value) {
            field = value
            puffs.clear()
            invalidate()
        }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xBF1E1208.toInt()
    }
    private val shaded = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val path2 = Path()
    // Gradients used by the trains, reused between frames instead of being created on every frame
    private val gradientCache = HashMap<Long, Shader>()
    private val shaderMatrix = Matrix()
    private val rect = RectF()

    // Static layers (sky, sun, rays, vignette)
    private var skyCache: Bitmap? = null
    // Parchment with its soft light, for the spirit swirls background. Created when first needed
    private var parchmentCache: Bitmap? = null
    // Blurred shadows under the screens, by screen size
    private val screenShadowCache = HashMap<Long, Bitmap>()
    private val spiritPath = Path()
    private val swirlPath = Path()
    private val patternPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Vertical gradients, created when the size changes
    private var backMountainShader: Shader? = null
    private var frontMountainShader: Shader? = null
    private var hazeShader: Shader? = null
    private var farHillShader: Shader? = null
    private var midHillShader: Shader? = null
    private var groundShader: Shader? = null

    private val farClouds = List(6) { Cloud(it * 190f + (it * 71 % 80), 30f + (it * 29 % 40), 0.6f + (it * 13 % 5) / 10f) }
    private val midClouds = List(5) { Cloud(it * 260f + (it * 47 % 90), 55f + (it * 31 % 35), 0.8f + (it * 17 % 5) / 10f) }
    private val nearClouds = List(3) { Cloud(it * 420f + (it * 83 % 120), 28f + (it * 41 % 30), 1.3f + (it * 11 % 4) / 10f) }
    private val trees = List(18) { Tree(it * 95f + (it * 37 % 40), 0.7f + (it * 53 % 10) / 20f, it % 3) }
    private val farTrees = List(26) { Tree(it * 70f + (it * 29 % 30), 0.5f + (it * 17 % 10) / 30f, 0) }
    private val hillTufts = List(70) { Blade(it * 25f + (it * 11 % 17), 3f + (it * 5 % 4), false, 0) }
    private val meadowGrass = List(140) { Blade(it * 12f + (it * 7 % 9), 5f + (it * 13 % 7), it % 9 == 0, it % 3) }
    private val foregroundGrass = List(60) { Blade(it * 28f + (it * 13 % 14), 8f + (it * 7 % 9), it % 7 == 0, 0) }
    private val treeColours = arrayOf(
        intArrayOf(0xFF4E9A3A.toInt(), 0xFF3E7F2D.toInt()),
        intArrayOf(0xFF5DAA44.toInt(), 0xFF478A34.toInt()),
        intArrayOf(0xFF3F8C3A.toInt(), 0xFF2F6E2B.toInt()),
    )
    private val grassColours = intArrayOf(0xFF4E8E38.toInt(), 0xFF5DA043.toInt(), 0xFF3F7A2C.toInt())
    private val flowerColours = intArrayOf(0xFFF6EDD3.toInt(), 0xFFF3D27A.toInt(), 0xFFE9A0B8.toInt())

    private val puffs = ArrayDeque<Puff>()
    private var lastPuffTime = -1f

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!isAnimating) {
                return
            }
            invalidate()
            postDelayed(this, if (isLowPower) FRAME_INTERVAL_MS * 2 else FRAME_INTERVAL_MS)
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
        skyCache?.recycle()
        skyCache = null
        parchmentCache?.recycle()
        parchmentCache = null
        screenShadowCache.values.forEach { it.recycle() }
        screenShadowCache.clear()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val hf = h.toFloat()
        backMountainShader = verticalGradient(hf * 0.25f, hf * 0.46f, 0xFF8796BC.toInt(), 0xFFA3B1CE.toInt())
        frontMountainShader = verticalGradient(hf * 0.34f, hf * 0.55f, 0xFF8EA9A0.toInt(), 0xFFA9C3A0.toInt())
        hazeShader = verticalGradient(hf * 0.36f, hf * 0.54f, 0x00F9E7C4, 0x80F9E7C4.toInt())
        farHillShader = verticalGradient(hf * 0.5f, hf * 0.8f, 0xFFA9D08A.toInt(), 0xFF86B86A.toInt())
        midHillShader = verticalGradient(hf * 0.6f, hf * 0.86f, 0xFF7DBE5C.toInt(), 0xFF559A40.toInt())
        groundShader = verticalGradient(hf * 0.79f, hf, 0xFF62A048.toInt(), 0xFF467A32.toInt())

        gradientCache.clear()
        parchmentCache?.recycle()
        parchmentCache = null
        skyCache?.recycle()
        skyCache = null
        if (w > 0 && h > 0) {
            skyCache = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565).also { drawSky(Canvas(it), w.toFloat(), hf) }
        }
    }

    private fun verticalGradient(top: Float, bottom: Float, vararg colours: Int): Shader {
        return LinearGradient(0f, top, 0f, bottom, colours, null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) {
            return
        }

        if (isSceneEnabled) {
            when (sceneStyle) {
                SceneStyle.COUNTRYSIDE -> drawScene(canvas, w, h)
                SceneStyle.SPIRIT_SWIRLS -> drawSpiritSwirls(canvas, w, h)
            }
        }
        drawScreenFrames(canvas)
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------
    // Scene
    // ---------------------------------------------------------------------------------------------------------------------------------------------------

    private fun drawScene(canvas: Canvas, w: Float, h: Float) {
        val t = sceneTime()
        val camera = t * WORLD_SPEED_DP * d
        val trackY = h * 0.86f

        canvas.save()
        screenAreas.forEach { clipOutRect(canvas, it.rect) }

        skyCache?.let { canvas.drawBitmap(it, 0f, 0f, null) } ?: drawSky(canvas, w, h)

        drawFarClouds(canvas, w, t)
        drawMountains(canvas, w, h, camera)
        midClouds.forEach { drawCloud(canvas, wrapX(it.x * d, 14 * d * t, w + 400 * d), it.y * d, it.scale * 0.6f, 0.85f) }
        shaded.shader = hazeShader
        canvas.drawRect(0f, h * 0.36f, w, h * 0.54f, shaded)

        drawFarHills(canvas, w, h, camera, t)
        drawMidHills(canvas, w, h, camera, t)

        // Shadows of the near clouds sweeping over the land
        fill.color = 0x211E3C14
        nearClouds.forEach {
            val x = wrapX(it.x * d, 22 * d * t, w + 700 * d)
            rect.set(x + 10 * d - 95 * d * it.scale, h * 0.74f - 10 * d * it.scale, x + 10 * d + 95 * d * it.scale, h * 0.74f + 10 * d * it.scale)
            canvas.drawOval(rect, fill)
        }

        drawMeadow(canvas, w, h, camera, t)
        drawTelegraphPoles(canvas, w, h, camera)
        drawTrack(canvas, w, trackY, camera)

        val front = w * 0.62f + sin(t * 0.35f) * 40 * d
        when (trainStyle) {
            TrainStyle.SPIRIT -> drawSpiritTrain(canvas, front, trackY - 7 * d, t)
            TrainStyle.CLASSIC -> drawClassicTrain(canvas, front, trackY - 7 * d, t)
        }
        drawSteam(canvas, t)

        drawButterflies(canvas, w, h, t)
        drawForegroundGrass(canvas, w, h, camera, t)
        nearClouds.forEach { drawCloud(canvas, wrapX(it.x * d, 22 * d * t, w + 700 * d), it.y * d, it.scale * 0.75f, 0.95f) }

        canvas.restore()
    }

    /**
     * Time in seconds that drives the scene's animations. It runs at half speed in battery saver mode.
     */
    private fun sceneTime(): Float {
        val seconds = (SystemClock.uptimeMillis() - startTime) / 1000f
        return if (isLowPower) seconds * 0.5f else seconds
    }

    /**
     * Position of a repeating element that scrolls left: [x] is its position in the pattern, [offset] how far the pattern has scrolled and [span] its length.
     */
    private fun wrapX(x: Float, offset: Float, span: Float): Float {
        return ((x - offset) % span + span) % span - 150 * d
    }

    private fun hillY(x: Float, baseY: Float, amplitude: Float, wavelength: Float, offset: Float, phase: Float): Float {
        val p = (x + offset) / wavelength * TAU + phase
        return baseY - amplitude * (sin(p) * 0.65f + sin(p * 0.43f + phase + 1.3f) * 0.35f)
    }

    private fun noise(x: Float): Float {
        return sin(x * 1.7f) * 0.5f + sin(x * 3.1f + 1.3f) * 0.3f + sin(x * 7.3f + 2.1f) * 0.2f
    }

    private fun drawSky(canvas: Canvas, w: Float, h: Float) {
        shaded.shader = LinearGradient(
            0f, 0f, 0f, h * 0.75f,
            intArrayOf(0xFF3577B8.toInt(), 0xFF7FBFE3.toInt(), 0xFFF4D6AA.toInt(), 0xFFF9E7C4.toInt()),
            floatArrayOf(0f, 0.4f, 0.78f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, shaded)

        val sx = w * 0.78f
        val sy = h * 0.18f
        fill.color = 0x21FFF2C8
        val length = max(w, h)
        for (i in 0 until 12) {
            rect.set(sx - length, sy - length, sx + length, sy + length)
            canvas.drawArc(rect, i * 30f, 5f, true, fill)
        }
        for ((radius, alpha) in listOf(130f to 0.22f, 75f to 0.4f, 44f to 0.77f)) {
            shaded.shader = RadialGradient(sx, sy, radius * d, (((alpha.coerceAtMost(1f)) * 255).toInt() shl 24) or 0xFFF0BE, 0x00FFF0BE, Shader.TileMode.CLAMP)
            canvas.drawCircle(sx, sy, radius * d, shaded)
        }
        fill.color = 0xFFFFF6D8.toInt()
        canvas.drawCircle(sx, sy, 20 * d, fill)

        // Soft darkening towards the edges
        shaded.shader = RadialGradient(
            w / 2f, h / 2f, max(w, h) * 0.75f,
            intArrayOf(0x00281405, 0x00281405, 0x47281405),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, shaded)
    }

    private fun drawFarClouds(canvas: Canvas, w: Float, t: Float) {
        fill.color = 0x66FFFFFF
        farClouds.forEach {
            val x = wrapX(it.x * d, 6 * d * t, w + 300 * d)
            val y = it.y * d
            rect.set(x - 60 * d * it.scale, y - 7 * d * it.scale, x + 60 * d * it.scale, y + 7 * d * it.scale)
            canvas.drawOval(rect, fill)
            rect.set(x - 5 * d * it.scale, y - 10 * d * it.scale, x + 75 * d * it.scale, y + 2 * d * it.scale)
            canvas.drawOval(rect, fill)
        }
    }

    /**
     * A puffy cloud with a bluish shaded belly and bright highlights on top.
     */
    private fun drawCloud(canvas: Canvas, x: Float, y: Float, scale: Float, alpha: Float) {
        val s = scale * d
        val a = (alpha * 255).toInt()
        fill.color = 0x7896AFC3 and 0x00FFFFFF or ((a * 0.55f).toInt() shl 24)
        CLOUD_PARTS.forEach { (ox, oy, r) -> canvas.drawCircle(x + ox * s, y + (oy + 7) * s, r * s, fill) }
        fill.color = (a shl 24) or 0xFFFFFF
        CLOUD_PARTS.forEach { (ox, oy, r) -> canvas.drawCircle(x + ox * s, y + oy * s, r * s, fill) }
        for (i in 0 until 3) {
            val (ox, oy, r) = CLOUD_PARTS[i]
            canvas.drawCircle(x + (ox - 4) * s, y + (oy - 6) * s, r * 0.55f * s, fill)
        }
    }

    private val CLOUD_PARTS = listOf(
        Triple(0f, 0f, 22f), Triple(22f, -10f, 26f), Triple(48f, -4f, 22f), Triple(66f, 4f, 16f), Triple(-18f, 6f, 14f), Triple(34f, 8f, 20f),
    )

    private fun drawMountains(canvas: Canvas, w: Float, h: Float, camera: Float) {
        val backOffset = camera * 0.03f
        val frontOffset = camera * 0.07f
        val step = 8 * d

        // Tall snowy range at the back, with uneven peaks
        path.reset()
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w + step) {
            val peak = 48 + 30 * noise((x + backOffset) / (140 * d))
            path.lineTo(x, h * 0.44f - peak * d * abs(sin((x + backOffset) / (170 * d) * PI.toFloat())) - 12 * d)
            x += step
        }
        path.lineTo(w, h)
        path.close()
        shaded.shader = backMountainShader
        canvas.drawPath(path, shaded)

        // Smooth snow caps on the parts above the snow line
        canvas.save()
        canvas.clipPath(path)
        path2.reset()
        path2.moveTo(0f, 0f)
        x = 0f
        while (x <= w + step) {
            path2.lineTo(x, h * 0.31f + sin(x / (23 * d)) * 3 * d + sin(x / (9 * d)) * 1.5f * d)
            x += step
        }
        path2.lineTo(w, 0f)
        path2.close()
        fill.color = 0xE6FFFFFF.toInt()
        canvas.drawPath(path2, fill)
        canvas.restore()

        // Smaller green range in front
        path.reset()
        path.moveTo(0f, h)
        x = 0f
        while (x <= w + step) {
            val peak = 20 + 16 * noise((x + frontOffset) / (90 * d) + 3)
            path.lineTo(x, h * 0.5f - peak * d * abs(sin((x + frontOffset) / (110 * d) * PI.toFloat() + 0.7f)) - 4 * d)
            x += step
        }
        path.lineTo(w, h)
        path.close()
        shaded.shader = frontMountainShader
        canvas.drawPath(path, shaded)
    }

    private fun drawFarHills(canvas: Canvas, w: Float, h: Float, camera: Float, t: Float) {
        val offset = camera * 0.18f
        val baseY = h * 0.57f
        fillHill(canvas, w, h, baseY, 15 * d, 300 * d, offset, 0f, farHillShader, ridge = false)

        fill.color = 0xFF6E9F55.toInt()
        farTrees.forEach {
            val x = ((it.x * d - offset) % (1820 * d) + 1820 * d) % (1820 * d) - 60 * d
            val y = hillY(x, baseY, 15 * d, 300 * d, offset, 0f)
            canvas.drawCircle(x, y - 6 * d * it.scale, 7 * d * it.scale, fill)
        }

        // Windmills, each turning at its own pace
        for ((base, phase) in listOf(700f to 0f, 1180f to 1.7f)) {
            val span = 1400 * d
            val wx = ((base * d - offset) % span + span) % span - 100 * d
            val wy = hillY(wx, baseY, 15 * d, 300 * d, offset, 0f)
            path.reset()
            path.moveTo(wx - 7 * d, wy)
            path.lineTo(wx - 4 * d, wy - 34 * d)
            path.lineTo(wx + 4 * d, wy - 34 * d)
            path.lineTo(wx + 7 * d, wy)
            path.close()
            fill.color = 0xFFE8DCC0.toInt()
            canvas.drawPath(path, fill)
            fill.color = 0x40785A3C
            canvas.drawRect(wx, wy - 34 * d, wx + 4 * d, wy, fill)
            path.reset()
            path.moveTo(wx - 6 * d, wy - 34 * d)
            path.lineTo(wx, wy - 42 * d)
            path.lineTo(wx + 6 * d, wy - 34 * d)
            path.close()
            fill.color = 0xFFA0522D.toInt()
            canvas.drawPath(path, fill)

            canvas.save()
            canvas.translate(wx, wy - 34 * d)
            canvas.rotate((t * 1.4f + phase) * 180f / PI.toFloat())
            stroke.color = WOOD
            stroke.strokeWidth = 2 * d
            fill.color = 0xF2F6EDD3.toInt()
            for (i in 0 until 4) {
                canvas.rotate(90f)
                canvas.drawLine(0f, 0f, 0f, -20 * d, stroke)
                canvas.drawRect(1 * d, -20 * d, 6 * d, -6 * d, fill)
            }
            canvas.restore()
        }
    }

    private fun fillHill(canvas: Canvas, w: Float, h: Float, baseY: Float, amplitude: Float, wavelength: Float, offset: Float, phase: Float, shader: Shader?, ridge: Boolean) {
        path.reset()
        path2.reset()
        path.moveTo(0f, h)
        val step = 8 * d
        var x = 0f
        while (x <= w + step) {
            val y = hillY(x, baseY, amplitude, wavelength, offset, phase)
            path.lineTo(x, y)
            if (x == 0f) path2.moveTo(x, y + d) else path2.lineTo(x, y + d)
            x += step
        }
        path.lineTo(w, h)
        path.close()
        shaded.shader = shader
        canvas.drawPath(path, shaded)
        if (ridge) {
            // Sunlit ridge along the top
            stroke.color = 0x59FFFFDC
            stroke.strokeWidth = 2 * d
            canvas.drawPath(path2, stroke)
        }
    }

    private fun drawMidHills(canvas: Canvas, w: Float, h: Float, camera: Float, t: Float) {
        val offset = camera * 0.45f
        val baseY = h * 0.69f
        val amplitude = 13 * d
        val wavelength = 210 * d
        fillHill(canvas, w, h, baseY, amplitude, wavelength, offset, 2f, midHillShader, ridge = true)

        // Grass tufts on the hills, as one shape
        path.reset()
        hillTufts.forEach {
            val x = ((it.x * d - offset) % (1750 * d) + 1750 * d) % (1750 * d) - 20 * d
            val y = hillY(x, baseY, amplitude, wavelength, offset, 2f) + 6 * d + (it.x % 11) * d
            val sway = sin(t * 2.5f + it.x) * 1.2f * d
            for (dx in floatArrayOf(-2f, 0f, 2f)) {
                path.moveTo(x + dx * d, y)
                path.lineTo(x + dx * 1.5f * d + sway, y - it.height * d)
            }
        }
        stroke.color = 0xCC3C782D.toInt()
        stroke.strokeWidth = 1.2f * d
        canvas.drawPath(path, stroke)

        // Round trees that sway a little, with shadows cast away from the sun
        trees.forEach { tree ->
            val x = ((tree.x * d - offset) % (1760 * d) + 1760 * d) % (1760 * d) - 80 * d
            val y = hillY(x, baseY, amplitude, wavelength, offset, 2f) + 4 * d
            val s = tree.scale * d
            val sway = sin(t * 1.5f + tree.x) * 1.2f * s
            fill.color = 0x2E000000
            rect.set(x - 6 * s - 16 * s, y + 2 * s - 4 * s, x - 6 * s + 16 * s, y + 2 * s + 4 * s)
            canvas.drawOval(rect, fill)
            fill.color = WOOD
            canvas.drawRect(x - 2 * s, y - 14 * s, x + 2 * s, y, fill)
            val colours = treeColours[tree.colour]
            fill.color = colours[1]
            canvas.drawCircle(x + sway, y - 22 * s, 13 * s, fill)
            canvas.drawCircle(x - 9 * s + sway * 0.7f, y - 16 * s, 9 * s, fill)
            canvas.drawCircle(x + 9 * s + sway * 0.7f, y - 16 * s, 9 * s, fill)
            fill.color = colours[0]
            canvas.drawCircle(x + 4 * s + sway, y - 25 * s, 9 * s, fill)
            fill.color = 0x40FFFFDC
            canvas.drawCircle(x + 7 * s + sway, y - 28 * s, 4 * s, fill)
        }

        // Sparkling stream
        val span = 1600 * d
        val sx = ((900 * d - offset) % span + span) % span - 200 * d
        val sy = hillY(sx + 60 * d, baseY, amplitude, wavelength, offset, 2f) + 10 * d
        fill.color = 0xFF7EC4E0.toInt()
        rect.set(sx - 10 * d, sy - 7 * d, sx + 130 * d, sy + 7 * d)
        canvas.drawOval(rect, fill)
        fill.color = 0x59FFFFFF
        rect.set(sx + 10 * d, sy - 4.5f * d, sx + 90 * d, sy + 0.5f * d)
        canvas.drawOval(rect, fill)
        for (i in 0 until 5) {
            val p = (t * 1.5f + i * 0.37f) % 1f
            if (p < 0.5f) {
                fill.color = (((1f - p * 2f) * 255).toInt() shl 24) or 0xFFFFFF
                val px = sx + (15 + i * 24) * d
                val py = sy + (-1 + i * 3 % 4) * d
                canvas.drawRect(px - 2 * d, py - 0.5f * d, px + 2 * d, py + 0.5f * d, fill)
                canvas.drawRect(px - 0.5f * d, py - 2 * d, px + 0.5f * d, py + 2 * d, fill)
            }
        }
    }

    private fun drawMeadow(canvas: Canvas, w: Float, h: Float, camera: Float, t: Float) {
        shaded.shader = groundShader
        canvas.drawRect(0f, h * 0.79f, w, h, shaded)

        val offset = camera * 0.8f
        val span = 1680 * d
        stroke.strokeWidth = 1.3f * d
        for (colour in 0 until 3) {
            path.reset()
            meadowGrass.forEach {
                if (it.colour != colour) return@forEach
                val x = ((it.x * d - offset) % span + span) % span - 20 * d
                val y = h * 0.8f + (it.x * 7 % 10) * d
                val sway = sin(t * 3f + it.x * 0.3f) * 1.5f * d
                for (dx in floatArrayOf(-2f, 0f, 2f)) {
                    path.moveTo(x + dx * d, y + 3 * d)
                    path.quadTo(x + dx * d + sway * 0.4f, y - it.height * 0.5f * d, x + dx * 1.4f * d + sway, y - it.height * d)
                }
            }
            stroke.color = grassColours[colour]
            canvas.drawPath(path, stroke)
        }
        meadowGrass.forEach {
            if (!it.hasFlower) return@forEach
            val x = ((it.x * d - offset) % span + span) % span - 20 * d
            val y = h * 0.8f + (it.x * 7 % 10) * d
            val sway = sin(t * 3f + it.x * 0.3f) * 1.5f * d
            fill.color = flowerColours[it.colour]
            canvas.drawCircle(x + sway, y - it.height * d - d, 2.2f * d, fill)
        }
    }

    private fun drawTelegraphPoles(canvas: Canvas, w: Float, h: Float, camera: Float) {
        val spacing = 230 * d
        val offset = camera * 0.85f
        val firstX = -((offset % spacing + spacing) % spacing)
        val top = h * 0.6f
        val wireY = h * 0.615f
        path.reset()
        var x = firstX - spacing
        while (x <= w + spacing) {
            fill.color = WOOD
            canvas.drawRect(x - 1.5f * d, top, x + 1.5f * d, h * 0.8f + 6 * d, fill)
            fill.color = 0x33000000
            canvas.drawRect(x + 1.5f * d, top, x + 2.5f * d, h * 0.8f + 6 * d, fill)
            fill.color = 0xFF4A2D15.toInt()
            canvas.drawRect(x - 9 * d, h * 0.61f, x + 9 * d, h * 0.61f + 2.5f * d, fill)
            // Sagging wires to the next pole
            for (dx in floatArrayOf(-7f, 7f)) {
                val a = x + dx * d
                val b = x + spacing + dx * d
                path.moveTo(a, wireY)
                path.quadTo((a + b) / 2f, wireY + 14 * d, b, wireY)
            }
            x += spacing
        }
        stroke.color = 0x99281E19.toInt()
        stroke.strokeWidth = 0.8f * d
        canvas.drawPath(path, stroke)
    }

    private fun drawTrack(canvas: Canvas, w: Float, trackY: Float, camera: Float) {
        fill.color = 0xFFB59A6A.toInt()
        canvas.drawRect(0f, trackY - 7 * d, w, trackY + 9 * d, fill)
        fill.color = 0x2E000000
        canvas.drawRect(0f, trackY + 6 * d, w, trackY + 9 * d, fill)

        // Sleepers, scrolling with the world
        val spacing = 22 * d
        var x = -(camera % spacing)
        while (x < w) {
            fill.color = 0xFF5C3A1E.toInt()
            canvas.drawRect(x, trackY - 4 * d, x + 8 * d, trackY + 7 * d, fill)
            fill.color = 0x40FFDCAA
            canvas.drawRect(x, trackY - 4 * d, x + 8 * d, trackY - 2 * d, fill)
            x += spacing
        }

        stroke.color = 0xFF55555E.toInt()
        stroke.strokeWidth = 2.5f * d
        canvas.drawLine(0f, trackY - 2 * d, w, trackY - 2 * d, stroke)
        canvas.drawLine(0f, trackY + 4 * d, w, trackY + 4 * d, stroke)
        stroke.color = 0xD9E6EBF5.toInt()
        stroke.strokeWidth = 1 * d
        canvas.drawLine(0f, trackY - 3 * d, w, trackY - 3 * d, stroke)
    }

    private fun drawButterflies(canvas: Canvas, w: Float, h: Float, t: Float) {
        for (i in 0 until 3) {
            val baseX = 200 + i * 260
            val bx = ((baseX * d + t * 25 * d) % (w + 100 * d)) - 50 * d + sin(t * 0.9f + baseX) * 30 * d
            val by = h * 0.73f + (i * 37 % 30) * d + sin(t * 2.1f + baseX) * 12 * d
            val flap = abs(sin(t * 14f + baseX))
            fill.color = flowerColours[i]
            for (side in floatArrayOf(-1f, 1f)) {
                canvas.save()
                canvas.rotate(side * 28f, bx + side * 2 * d, by)
                rect.set(bx + side * 2 * d - 3.2f * d, by - (1.6f + flap * 2.4f) * d, bx + side * 2 * d + 3.2f * d, by + (1.6f + flap * 2.4f) * d)
                canvas.drawOval(rect, fill)
                canvas.restore()
            }
            fill.color = 0xFF3A2614.toInt()
            canvas.drawRect(bx - 0.5f * d, by - 2 * d, bx + 0.5f * d, by + 2 * d, fill)
        }
    }

    private fun drawForegroundGrass(canvas: Canvas, w: Float, h: Float, camera: Float, t: Float) {
        val offset = camera * 1.25f
        val span = 1680 * d
        stroke.strokeWidth = 2 * d
        for (row in 0 until 2) {
            path.reset()
            foregroundGrass.forEach {
                val x = ((it.x * d + row * 14 * d - offset) % span + span) % span - 20 * d
                val sway = sin(t * 3f + it.x + row) * 2.2f * d
                val height = (it.height + row * 4) * d
                for (dx in floatArrayOf(-3f, 0f, 3f)) {
                    path.moveTo(x + dx * d, h)
                    path.quadTo(x + dx * d + sway * 0.5f, h - height * 0.6f, x + dx * 1.6f * d + sway, h - height)
                }
            }
            stroke.color = if (row == 0) 0xFF3E7A2C.toInt() else 0xFF356B26.toInt()
            canvas.drawPath(path, stroke)
        }
        foregroundGrass.forEach {
            if (!it.hasFlower) return@forEach
            val x = ((it.x * d - offset) % span + span) % span - 20 * d
            val sway = sin(t * 3f + it.x) * 2.2f * d
            fill.color = 0xFFF6EDD3.toInt()
            canvas.drawCircle(x + sway, h - it.height * d - 2 * d, 3 * d, fill)
            fill.color = GOLD
            canvas.drawCircle(x + sway, h - it.height * d - 2 * d, 1.3f * d, fill)
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------
    // Trains
    // ---------------------------------------------------------------------------------------------------------------------------------------------------

    private fun vGradient(top: Float, bottom: Float, vararg colours: Int): Paint {
        val key = (colours.contentHashCode().toLong() shl 32) xor (top.toBits().toLong() * 31 + bottom.toBits().toLong())
        shaded.shader = gradientCache.getOrPut(key) { LinearGradient(0f, top, 0f, bottom, colours, null, Shader.TileMode.CLAMP) }
        return shaded
    }

    /**
     * Sets a radial gradient centred on ([x], [y]) on the shaded paint. The gradient is created once around the origin and moved into place.
     */
    private fun radial(x: Float, y: Float, radius: Float, inner: Int, outer: Int): Paint {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Older versions share a shader's native object with frames already drawn, so moving a reused shader would move it there too
            shaded.shader = RadialGradient(x, y, radius, inner, outer, Shader.TileMode.CLAMP)
            return shaded
        }
        val key = ((inner.toLong() * 31 + outer.toLong()) shl 20) xor radius.toBits().toLong() xor 0x5EEDL
        val shader = gradientCache.getOrPut(key) { RadialGradient(0f, 0f, radius, inner, outer, Shader.TileMode.CLAMP) }
        shaderMatrix.setTranslate(x, y)
        shader.setLocalMatrix(shaderMatrix)
        shaded.shader = shader
        return shaded
    }

    private fun roundRect(left: Float, top: Float, right: Float, bottom: Float, radius: Float, paint: Paint, outline: Boolean = false) {
        rect.set(left, top, right, bottom)
        currentCanvas?.drawRoundRect(rect, radius, radius, paint)
        if (outline) {
            outlinePaint.strokeWidth = 1.2f * d
            currentCanvas?.drawRoundRect(rect, radius, radius, outlinePaint)
        }
    }

    private var currentCanvas: Canvas? = null

    private fun rivets(canvas: Canvas, fromX: Float, toX: Float, y: Float, step: Float) {
        var x = fromX
        while (x <= toX) {
            fill.color = 0x59281A0A
            canvas.drawCircle(x + 0.4f * d, y + 0.4f * d, 0.9f * d, fill)
            fill.color = 0xE6FFF0BE.toInt()
            canvas.drawCircle(x, y, 0.9f * d, fill)
            x += step
        }
    }

    private fun woodGrain(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, seed: Int) {
        canvas.save()
        canvas.clipRect(left, top, left + width, top + height)
        path.reset()
        var i = 0
        while (i * 3 * d < height) {
            val y = top + (2 + i * 3 + (seed * 7 + i * 3) % 2) * d
            path.moveTo(left, y)
            var x = left
            while (x <= left + width) {
                path.lineTo(x, y + sin((x / d + seed * 11 + i * 5) * 0.25f) * 0.6f * d)
                x += 6 * d
            }
            i++
        }
        stroke.color = 0x47462810
        stroke.strokeWidth = 0.7f * d
        canvas.drawPath(path, stroke)
        canvas.restore()
    }

    private fun emitSteam(t: Float, x: Float, y: Float) {
        if (lastPuffTime < 0f || t - lastPuffTime > SMOKE_INTERVAL_SECONDS || t < lastPuffTime) {
            lastPuffTime = t
            puffs.addLast(Puff(x, y, t))
        }
    }

    private fun drawSteam(canvas: Canvas, t: Float) {
        while (puffs.isNotEmpty() && (t - puffs.first().bornAt > SMOKE_LIFETIME_SECONDS || t < puffs.first().bornAt)) {
            puffs.removeFirst()
        }
        puffs.forEach { puff ->
            val age = (t - puff.bornAt) / SMOKE_LIFETIME_SECONDS
            val radius = (6 + age * 17) * d
            val px = puff.x - age * 120 * d
            val py = puff.y - age * 50 * d
            fill.color = (((1f - age) * 0.45f * 255).toInt() shl 24) or 0xAAA5A0
            canvas.drawCircle(px + radius * 0.2f, py + radius * 0.25f, radius, fill)
            fill.color = (((1f - age) * 0.85f * 255).toInt() shl 24) or 0xF8F6F0
            canvas.drawCircle(px, py, radius, fill)
        }
    }

    /**
     * A wheel resting on the rail: its bottom touches the top of the rail, with a small contact shadow.
     */
    private fun railWheel(canvas: Canvas, cx: Float, radius: Float, angle: Float, railTop: Float) {
        val cy = railTop - radius
        fill.color = INK
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius * 0.74f, radial(cx - radius * 0.3f, cy - radius * 0.3f, radius, 0xFFE0604A.toInt(), 0xFF8A2A1E.toInt()))
        stroke.color = 0xFF2A1A12.toInt()
        stroke.strokeWidth = max(d, radius * 0.16f)
        for (i in 0 until 4) {
            val a = angle + i * PI.toFloat() / 4
            val dx = cos(a) * radius * 0.74f
            val dy = sin(a) * radius * 0.74f
            canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, stroke)
        }
        fill.color = GOLD
        canvas.drawCircle(cx, cy, radius * 0.25f, fill)
        fill.color = 0x73140C06
        rect.set(cx - radius * 0.55f, railTop - 0.5f * d, cx + radius * 0.55f, railTop + 1.7f * d)
        canvas.drawOval(rect, fill)
    }

    private fun drawSpiritTrain(canvas: Canvas, front: Float, wy: Float, t: Float) {
        currentCanvas = canvas
        val angle = t * WORLD_SPEED_DP / 8f
        val bob = sin(t * 12f) * 1.1f * d
        val railTop = wy + 3.75f * d

        // Ground shadow, cast away from the sun (top right)
        path.reset()
        path.moveTo(front + 8 * d, wy + 5 * d)
        path.lineTo(front - 178 * d, wy + 5 * d)
        path.lineTo(front - 190 * d, wy + 13 * d)
        path.lineTo(front - 4 * d, wy + 13 * d)
        path.close()
        fill.color = 0x47141E0A
        canvas.drawPath(path, fill)

        // Cargo car with crates
        run {
            val right = front - 122 * d
            val left = right - 40 * d
            canvas.save()
            canvas.translate(0f, sin(t * 8f + 2.1f) * d)
            roundRect(left, wy - 18 * d, right, wy - 3 * d, 3 * d, vGradient(wy - 18 * d, wy - 3 * d, 0xFFD39A50.toInt(), 0xFFA86F34.toInt(), 0xFF7E5226.toInt()))
            woodGrain(canvas, left, wy - 17 * d, right - left, 13 * d, 1)
            stroke.color = 0x73462810
            stroke.strokeWidth = 0.8f * d
            var x = left + 10 * d
            while (x < right) {
                canvas.drawLine(x, wy - 18 * d, x, wy - 3 * d, stroke)
                x += 10 * d
            }
            fill.color = GOLD
            canvas.drawRect(left, wy - 18 * d, right, wy - 16 * d, fill)
            canvas.drawRect(left, wy - 6 * d, right, wy - 4.5f * d, fill)
            rivets(canvas, left + 3 * d, right - 3 * d, wy - 17 * d, 6 * d)
            outlinePaint.strokeWidth = 1.2f * d
            rect.set(left, wy - 18 * d, right, wy - 3 * d)
            canvas.drawRoundRect(rect, 3 * d, 3 * d, outlinePaint)
            for ((cx, cy, size) in listOf(Triple(left + 5 * d, wy - 29 * d, 13f to 11f), Triple(left + 20 * d, wy - 27 * d, 14f to 9f))) {
                val cw = size.first * d
                val ch = size.second * d
                roundRect(cx, cy, cx + cw, cy + ch, 1.5f * d, vGradient(cy, cy + ch, 0xFFE3B677.toInt(), 0xFFA87A42.toInt()), outline = true)
                woodGrain(canvas, cx, cy, cw, ch, 2)
                stroke.color = 0x99462810.toInt()
                stroke.strokeWidth = 0.9f * d
                canvas.drawLine(cx, cy, cx + cw, cy + ch, stroke)
                canvas.drawLine(cx + cw, cy, cx, cy + ch, stroke)
            }
            canvas.restore()
            fill.color = INK
            canvas.drawRect(right, wy - 8 * d, right + 6 * d, wy - 6 * d, fill)
            railWheel(canvas, left + 9 * d, 5.5f * d, angle * 1.4f, railTop)
            railWheel(canvas, right - 9 * d, 5.5f * d, angle * 1.4f, railTop)
        }

        // Passenger car with round windows
        run {
            val right = front - 66 * d
            val left = right - 50 * d
            canvas.save()
            canvas.translate(0f, sin(t * 8f + 1f) * d)
            rect.set(left, wy - 30 * d, right, wy - 3 * d)
            canvas.drawRoundRect(rect, 6 * d, 6 * d, vGradient(wy - 30 * d, wy - 3 * d, 0xFFFBF2D8.toInt(), 0xFFEADBB4.toInt(), 0xFFC9AE74.toInt()))
            // Lower wooden panel, and shading under the roof and along the bottom
            canvas.save()
            path.reset()
            path.addRoundRect(rect, 6 * d, 6 * d, Path.Direction.CW)
            canvas.clipPath(path)
            fill.color = 0xFFA06C40.toInt()
            canvas.drawRect(left, wy - 11 * d, right, wy - 2 * d, fill)
            woodGrain(canvas, left, wy - 11 * d, right - left, 9 * d, 4)
            stroke.color = 0x733C230F
            stroke.strokeWidth = 0.8f * d
            var x = left + 8 * d
            while (x < right) {
                canvas.drawLine(x, wy - 11 * d, x, wy - 2 * d, stroke)
                x += 8 * d
            }
            fill.color = 0x383C230A
            canvas.drawRect(left, wy - 29 * d, right, wy - 24 * d, fill)
            fill.color = 0x2E3C230A
            canvas.drawRect(left, wy - 6 * d, right, wy - 3 * d, fill)
            canvas.restore()
            outlinePaint.strokeWidth = 1.2f * d
            canvas.drawRoundRect(rect, 6 * d, 6 * d, outlinePaint)
            // Roof
            roundRect(left - 3 * d, wy - 37 * d, right + 3 * d, wy - 28 * d, 4 * d, vGradient(wy - 37 * d, wy - 28 * d, 0xFFD9533F.toInt(), 0xFFB33A2A.toInt(), 0xFF7E2318.toInt()), outline = true)
            stroke.color = 0x59FFDCC8
            stroke.strokeWidth = d
            canvas.drawLine(left + 2 * d, wy - 35 * d, right - 2 * d, wy - 35 * d, stroke)
            fill.color = GOLD
            canvas.drawRect(left, wy - 29 * d, right, wy - 27 * d, fill)
            canvas.drawRect(left, wy - 12 * d, right, wy - 10.5f * d, fill)
            rivets(canvas, left + 4 * d, right - 4 * d, wy - 28 * d, 5 * d)
            for (k in 0 until 3) {
                val cx = left + (9 + k * 16) * d
                val cy = wy - 19 * d
                fill.color = GOLD
                canvas.drawCircle(cx, cy, 5.6f * d, fill)
                fill.color = WOOD
                canvas.drawCircle(cx, cy, 4.8f * d, fill)
                canvas.drawCircle(cx, cy, 3.9f * d, radial(cx - d, cy - d, 4.2f * d, 0xFFFFF6D0.toInt(), 0xFFF2C46A.toInt()))
                fill.color = 0xB3FFFFFF.toInt()
                canvas.drawCircle(cx - 1.4f * d, cy - 1.4f * d, 1.1f * d, fill)
            }
            canvas.restore()
            fill.color = INK
            canvas.drawRect(right, wy - 8 * d, right + 6 * d, wy - 6 * d, fill)
            railWheel(canvas, left + 10 * d, 5.5f * d, angle * 1.4f, railTop)
            railWheel(canvas, right - 10 * d, 5.5f * d, angle * 1.4f, railTop)
        }

        // Locomotive, bobbing gently
        canvas.save()
        canvas.translate(0f, bob)
        drawSpiritLocomotive(canvas, front, wy, t)
        canvas.restore()

        // Driving wheels and coupling rod, on the rail
        val wheels = listOf(front - 50 * d to 9 * d, front - 30 * d to 6 * d, front - 14 * d to 6 * d)
        wheels.forEach { (x, r) -> railWheel(canvas, x, r, angle * (8 * d / r), railTop) }
        val rodAngle = angle * (8f / 6f)
        val rx = cos(rodAngle) * 4 * d
        val ry = sin(rodAngle) * 4 * d
        path.reset()
        path.moveTo(wheels[0].first + rx * 1.3f, railTop - wheels[0].second + ry * 1.3f)
        path.lineTo(wheels[1].first + rx, railTop - wheels[1].second + ry)
        path.lineTo(wheels[2].first + rx, railTop - wheels[2].second + ry)
        stroke.color = 0xFFE5E9F0.toInt()
        stroke.strokeWidth = 2.6f * d
        canvas.drawPath(path, stroke)
        stroke.color = 0xCC3C3C46.toInt()
        stroke.strokeWidth = 0.8f * d
        canvas.drawPath(path, stroke)

        emitSteam(t, front - 10 * d, wy - 52 * d + bob)
        drawCylinderSteam(canvas, front - 46 * d, wy + 4 * d, t, 3.5f)
        currentCanvas = null
    }

    private fun drawSpiritLocomotive(canvas: Canvas, front: Float, wy: Float, t: Float) {
        // Cab with a door, seam, shading and rivets
        rect.set(front - 64 * d, wy - 44 * d, front - 36 * d, wy - 4 * d)
        canvas.drawRoundRect(rect, 6 * d, 6 * d, vGradient(wy - 44 * d, wy - 4 * d, 0xFF3C86AA.toInt(), 0xFF2F6E8E.toInt(), 0xFF1A3F57.toInt()))
        canvas.save()
        path.reset()
        path.addRoundRect(rect, 6 * d, 6 * d, Path.Direction.CW)
        canvas.clipPath(path)
        stroke.color = 0x800A1E2D.toInt()
        stroke.strokeWidth = 0.8f * d
        canvas.drawLine(front - 64 * d, wy - 22 * d, front - 36 * d, wy - 22 * d, stroke)
        rect.set(front - 60 * d, wy - 21 * d, front - 50 * d, wy - 5 * d)
        fill.color = 0x590F283C
        canvas.drawRoundRect(rect, 2 * d, 2 * d, fill)
        stroke.color = 0x990A1E2D.toInt()
        canvas.drawRoundRect(rect, 2 * d, 2 * d, stroke)
        fill.color = GOLD
        canvas.drawCircle(front - 52 * d, wy - 13 * d, d, fill)
        fill.color = 0x4D051423
        canvas.drawRect(front - 64 * d, wy - 41 * d, front - 36 * d, wy - 36 * d, fill)
        canvas.drawRect(front - 64 * d, wy - 9 * d, front - 36 * d, wy - 4 * d, fill)
        canvas.restore()
        outlinePaint.strokeWidth = 1.2f * d
        rect.set(front - 64 * d, wy - 44 * d, front - 36 * d, wy - 4 * d)
        canvas.drawRoundRect(rect, 6 * d, 6 * d, outlinePaint)
        rivets(canvas, front - 62 * d, front - 38 * d, wy - 42 * d, 4 * d)
        rivets(canvas, front - 62 * d, front - 38 * d, wy - 6 * d, 4 * d)
        stroke.color = 0xFFC9CED8.toInt()
        stroke.strokeWidth = 1.3f * d
        canvas.drawLine(front - 46 * d, wy - 20 * d, front - 46 * d, wy - 6 * d, stroke)
        fill.color = 0xFF3A3840.toInt()
        canvas.drawRect(front - 63 * d, wy - 3 * d, front - 54 * d, wy - d, fill)
        // Cab roof
        roundRect(front - 68 * d, wy - 50 * d, front - 32 * d, wy - 41 * d, 4 * d, vGradient(wy - 50 * d, wy - 41 * d, 0xFFD9533F.toInt(), 0xFFB33A2A.toInt(), 0xFF7E2318.toInt()), outline = true)
        fill.color = GOLD
        canvas.drawRect(front - 64 * d, wy - 41 * d, front - 36 * d, wy - 39 * d, fill)
        // Cab window
        roundRect(front - 58 * d, wy - 38 * d, front - 42 * d, wy - 25 * d, 4 * d, fill)
        roundRect(front - 56.5f * d, wy - 36.5f * d, front - 43.5f * d, wy - 26.5f * d, 3 * d, vGradient(wy - 36 * d, wy - 26 * d, 0xFFFFF8DA.toInt(), 0xFFF2C46A.toInt()))
        stroke.color = 0xB3FFFFFF.toInt()
        stroke.strokeWidth = 1.2f * d
        canvas.drawLine(front - 54 * d, wy - 28 * d, front - 49 * d, wy - 35 * d, stroke)

        // Boiler with metallic sheen, shading, seams and gold bands
        rect.set(front - 40 * d, wy - 30 * d, front - 2 * d, wy - 5 * d)
        canvas.drawRoundRect(rect, 12 * d, 12 * d, vGradient(wy - 30 * d, wy - 5 * d, 0xFF5AA7C6.toInt(), 0xFF3A88A9.toInt(), 0xFF1D4B63.toInt()))
        canvas.save()
        path.reset()
        path.addRoundRect(rect, 12 * d, 12 * d, Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawRect(front - 38 * d, wy - 29 * d, front - 4 * d, wy - 21 * d, vGradient(wy - 29 * d, wy - 21 * d, 0x8CFFFFFF.toInt(), 0x00FFFFFF))
        fill.color = 0x47051423
        canvas.drawRect(front - 40 * d, wy - 11 * d, front - 2 * d, wy - 5 * d, fill)
        rect.set(front - 19 * d, wy - 31 * d, front - 5 * d, wy - 25 * d)
        canvas.drawOval(rect, fill)
        rect.set(front - 33 * d, wy - 31 * d, front - 15 * d, wy - 26 * d)
        canvas.drawOval(rect, fill)
        stroke.color = 0x590A1E2D
        stroke.strokeWidth = 0.8f * d
        for (sx in floatArrayOf(-34f, -24f, -13f)) {
            canvas.drawLine(front + sx * d, wy - 30 * d, front + sx * d, wy - 5 * d, stroke)
        }
        canvas.restore()
        rect.set(front - 40 * d, wy - 30 * d, front - 2 * d, wy - 5 * d)
        canvas.drawRoundRect(rect, 12 * d, 12 * d, outlinePaint)
        for (bx in floatArrayOf(-30f, -19f)) {
            canvas.drawRect(front + bx * d, wy - 30 * d, front + (bx + 3) * d, wy - 5 * d, vGradient(wy - 30 * d, wy - 5 * d, 0xFFFBE7A1.toInt(), GOLD, 0xFF9C6E1E.toInt()))
            fill.color = 0xCC785014.toInt()
            var y = wy - 27 * d
            while (y < wy - 6 * d) {
                canvas.drawCircle(front + (bx + 1.5f) * d, y, 0.7f * d, fill)
                y += 5 * d
            }
        }
        // Number plate with emblem
        fill.color = GOLD
        roundRect(front - 27 * d, wy - 14 * d, front - 19 * d, wy - 8 * d, 1.5f * d, fill)
        fill.color = WOOD
        canvas.drawCircle(front - 23 * d, wy - 11 * d, 1.6f * d, fill)
        // Round front with a gold ring
        fill.color = GOLD
        rect.set(front - 14 * d, wy - 28 * d, front + 8 * d, wy - 6 * d)
        canvas.drawArc(rect, -90f, 180f, true, fill)
        fill.color = 0xFF1D4B63.toInt()
        rect.set(front - 11.5f * d, wy - 25.5f * d, front + 5.5f * d, wy - 8.5f * d)
        canvas.drawArc(rect, -90f, 180f, true, fill)
        // Bell
        path.reset()
        path.moveTo(front - 37 * d, wy - 30 * d)
        path.quadTo(front - 37 * d, wy - 37 * d, front - 34 * d, wy - 37 * d)
        path.quadTo(front - 31 * d, wy - 37 * d, front - 31 * d, wy - 30 * d)
        path.close()
        fill.color = GOLD
        canvas.drawPath(path, fill)
        outlinePaint.strokeWidth = 0.8f * d
        canvas.drawPath(path, outlinePaint)
        // Cannon on top, slowly aiming up and down
        canvas.save()
        canvas.translate(front - 24 * d, wy - 31 * d)
        canvas.rotate((-0.18f + sin(t * 0.8f) * 0.05f) * 180f / PI.toFloat())
        roundRect(-2 * d, -6 * d, 20 * d, 0f, 3 * d, vGradient(-6 * d, 0f, 0xFF6A6872.toInt(), 0xFF3A3840.toInt(), 0xFF18171C.toInt()), outline = true)
        fill.color = 0x59FFFFFF
        canvas.drawRect(d, -5.5f * d, 17 * d, -4.3f * d, fill)
        fill.color = GOLD
        canvas.drawRect(17 * d, -6.5f * d, 20 * d, 0.5f * d, fill)
        canvas.drawRect(6 * d, -6.3f * d, 8 * d, 0.3f * d, fill)
        canvas.restore()
        fill.color = WOOD
        rect.set(front - 28.5f * d, wy - 35.5f * d, front - 19.5f * d, wy - 26.5f * d)
        canvas.drawArc(rect, 180f, 180f, true, fill)
        // Whistle
        fill.color = GOLD
        canvas.drawRect(front - 41 * d, wy - 49 * d, front - 38.5f * d, wy - 43 * d, fill)
        canvas.drawCircle(front - 39.8f * d, wy - 49 * d, 1.8f * d, fill)
        // Metallic funnel smokestack that pulses with each chuff
        val chuff = (t * 3.5f) % 1f
        val pulse = 1f + max(0f, 1f - chuff * 5f) * 0.06f
        canvas.save()
        canvas.translate(front - 10 * d, wy - 30 * d)
        canvas.scale(1f, pulse)
        path.reset()
        path.moveTo(-4 * d, 0f)
        path.lineTo(-3 * d, -10 * d)
        path.lineTo(-9 * d, -19 * d)
        path.lineTo(9 * d, -19 * d)
        path.lineTo(3 * d, -10 * d)
        path.lineTo(4 * d, 0f)
        path.close()
        shaded.shader = gradientCache.getOrPut(0x57AC4L) {
            LinearGradient(-9 * d, 0f, 9 * d, 0f, intArrayOf(0xFF18171C.toInt(), 0xFF5A5862.toInt(), 0xFF18171C.toInt()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(path, shaded)
        outlinePaint.strokeWidth = 0.9f * d
        canvas.drawPath(path, outlinePaint)
        fill.color = GOLD
        canvas.drawRect(-9.5f * d, -21 * d, 9.5f * d, -18 * d, fill)
        fill.color = 0x80FFFFFF.toInt()
        canvas.drawRect(-8 * d, -20.5f * d, 2 * d, -19.5f * d, fill)
        canvas.restore()
        // Big headlamp with a flickering glow
        drawHeadlamp(canvas, front + 5 * d, wy - 17 * d, t, 5.5f * d)
        // Cowcatcher with slats
        path.reset()
        path.moveTo(front - 2 * d, wy - 5 * d)
        path.lineTo(front + 10 * d, wy + 3 * d)
        path.lineTo(front - 2 * d, wy + 3 * d)
        path.close()
        canvas.drawPath(path, vGradient(wy - 5 * d, wy + 3 * d, 0xFFD9533F.toInt(), 0xFF8A2A1E.toInt()))
        canvas.drawPath(path, outlinePaint)
    }

    private fun drawHeadlamp(canvas: Canvas, x: Float, y: Float, t: Float, radius: Float) {
        val flicker = 0.85f + sin(t * 23f) * 0.08f + sin(t * 7f) * 0.07f
        val glow = radial(x, y, radius * 3.6f, 0xFFFFE48A.toInt(), 0x00FFE48A)
        glow.alpha = (0.65f * flicker * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(x, y, radius * 3.6f, glow)
        glow.alpha = 255
        fill.color = GOLD
        canvas.drawCircle(x, y, radius, fill)
        fill.color = 0xFFFFF6D0.toInt()
        canvas.drawCircle(x, y, radius * 0.7f, fill)
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x - radius * 0.22f, y - radius * 0.24f, radius * 0.22f, fill)
    }

    private fun drawCylinderSteam(canvas: Canvas, x: Float, y: Float, t: Float, chuffsPerSecond: Float) {
        val cycle = (t * chuffsPerSecond) % 1f
        if (cycle < 0.35f) {
            fill.color = (((0.5f * (1f - cycle / 0.35f)) * 255).toInt() shl 24) or 0xFFFFFF
            val radius = (6 + cycle * 20) * d
            rect.set(x - radius, y - 3 * d, x + radius, y + 3 * d)
            canvas.drawOval(rect, fill)
        }
    }

    private fun drawClassicTrain(canvas: Canvas, front: Float, wy: Float, t: Float) {
        currentCanvas = canvas
        val angle = t * WORLD_SPEED_DP / 7f
        val bob = sin(t * 14f) * 0.8f * d
        val railTop = wy + 3.75f * d

        fill.color = 0x40000000
        rect.set(front - 175 * d, wy + 5 * d, front + 15 * d, wy + 15 * d)
        canvas.drawOval(rect, fill)

        for (i in 0 until 2) {
            val right = front - 62 * d - i * 50 * d
            val left = right - 44 * d
            canvas.save()
            canvas.translate(0f, sin(t * 9f + i * 1.7f) * 0.9f * d)
            roundRect(left, wy - 28 * d, right, wy - 3 * d, 3 * d, vGradient(wy - 28 * d, wy - 3 * d, 0xFFA06C40.toInt(), 0xFF6E4626.toInt()), outline = true)
            roundRect(left - 2 * d, wy - 32 * d, right + 2 * d, wy - 27 * d, 2 * d, vGradient(wy - 32 * d, wy - 27 * d, 0xFF4A2D15.toInt(), 0xFF2A180B.toInt()))
            fill.color = GOLD
            canvas.drawRect(left, wy - 9 * d, right, wy - 7 * d, fill)
            val windowWidth = (right - left - 12 * d) / 3
            for (k in 0 until 3) {
                val wx = left + 3 * d + k * (windowWidth + 3 * d)
                canvas.drawRect(wx, wy - 23 * d, wx + windowWidth, wy - 13 * d, vGradient(wy - 23 * d, wy - 13 * d, 0xFFFFF4C8.toInt(), 0xFFF2C46A.toInt()))
            }
            canvas.restore()
            fill.color = INK
            canvas.drawRect(right, wy - 8 * d, right + 6 * d, wy - 6 * d, fill)
            railWheel(canvas, left + 9 * d, 5 * d, angle * 1.4f, railTop)
            railWheel(canvas, right - 9 * d, 5 * d, angle * 1.4f, railTop)
        }

        canvas.save()
        canvas.translate(0f, bob)
        roundRect(front - 60 * d, wy - 36 * d, front - 36 * d, wy - 4 * d, 2 * d, vGradient(wy - 36 * d, wy - 4 * d, 0xFF7E5230.toInt(), 0xFF553418.toInt()), outline = true)
        roundRect(front - 63 * d, wy - 41 * d, front - 33 * d, wy - 35 * d, 2 * d, vGradient(wy - 41 * d, wy - 35 * d, 0xFF4A2D15.toInt(), 0xFF2A180B.toInt()))
        fill.color = 0xFFFFF4C8.toInt()
        canvas.drawRect(front - 55 * d, wy - 31 * d, front - 43 * d, wy - 21 * d, fill)
        roundRect(front - 38 * d, wy - 26 * d, front - 2 * d, wy - 6 * d, 9 * d, vGradient(wy - 26 * d, wy - 6 * d, 0xFF3F7A52.toInt(), 0xFF21452D.toInt()), outline = true)
        fill.color = 0x4DFFFFFF
        roundRect(front - 35 * d, wy - 24 * d, front - 5 * d, wy - 20 * d, 2 * d, fill)
        fill.color = GOLD
        canvas.drawRect(front - 28 * d, wy - 26 * d, front - 25 * d, wy - 6 * d, fill)
        canvas.drawRect(front - 15 * d, wy - 26 * d, front - 12 * d, wy - 6 * d, fill)
        fill.color = INK
        canvas.drawRect(front - 13 * d, wy - 38 * d, front - 6 * d, wy - 25 * d, fill)
        roundRect(front - 16 * d, wy - 40 * d, front - 3 * d, wy - 36 * d, 1.5f * d, fill)
        drawHeadlamp(canvas, front - d, wy - 20 * d, t, 2.8f * d)
        path.reset()
        path.moveTo(front - 3 * d, wy - 6 * d)
        path.lineTo(front + 7 * d, wy + 3 * d)
        path.lineTo(front - 3 * d, wy + 3 * d)
        path.close()
        fill.color = INK
        canvas.drawPath(path, fill)
        canvas.restore()

        val wheels = listOf(front - 48 * d to 7.5f * d, front - 29 * d to 5.5f * d, front - 15 * d to 5.5f * d)
        wheels.forEach { (x, r) -> railWheel(canvas, x, r, angle * (7 * d / r), railTop) }
        val rodAngle = angle * (7f / 5.5f)
        val rx = cos(rodAngle) * 3.6f * d
        val ry = sin(rodAngle) * 3.6f * d
        stroke.color = 0xFFC9CED8.toInt()
        stroke.strokeWidth = 2.2f * d
        canvas.drawLine(wheels[1].first + rx, railTop - wheels[1].second + ry, wheels[2].first + rx, railTop - wheels[2].second + ry, stroke)

        emitSteam(t, front - 10 * d, wy - 40 * d + bob)
        drawCylinderSteam(canvas, front - 44 * d, wy + 4 * d, t, 4f)
        currentCanvas = null
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------
    // Spirit swirls background
    // ---------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Plain parchment, in the colour of the button faces, with small swirls and little spirits in a slightly darker tone drifting slowly to the left. The
     * screens cast a soft shadow on it, so that they seem to float above it.
     */
    private fun drawSpiritSwirls(canvas: Canvas, w: Float, h: Float) {
        val t = sceneTime()
        canvas.save()
        screenAreas.forEach { clipOutRect(canvas, it.rect) }

        val parchment = parchmentCache ?: Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).also {
            drawParchment(Canvas(it), w, h)
            parchmentCache = it
        }
        canvas.drawBitmap(parchment, 0f, 0f, null)

        if (spiritPath.isEmpty) {
            buildPatternPaths()
        }
        val cell = PATTERN_CELL_DP * d
        // The pattern repeats every two cells, since spirits and swirls alternate
        val offset = (t * PATTERN_SPEED_DP * d) % (cell * 2)
        val rows = (h / cell).toInt() + 2
        val columns = (w / cell).toInt() + 4
        for (row in 0 until rows) {
            for (column in -2 until columns) {
                val x = column * cell - offset + (row % 2) * cell / 2f
                val y = row * cell + cell * 0.35f
                val seed = ((row * 7 + column * 13) % 5 + 5) % 5
                canvas.save()
                if ((row + column) % 2 == 0) {
                    canvas.translate(x, y + sin(t * 1.3f + column + row) * 3f * d)
                    val scale = (0.9f + seed * 0.08f) * 0.75f * d
                    canvas.scale(scale, scale)
                    patternPaint.style = Paint.Style.FILL
                    patternPaint.color = PATTERN_SPIRIT
                    canvas.drawPath(spiritPath, patternPaint)
                } else {
                    canvas.translate(x, y)
                    canvas.rotate((t * 0.25f + seed) * 180f / PI.toFloat())
                    val scale = (0.8f + seed * 0.06f) * 0.75f * d
                    canvas.scale(scale, scale)
                    patternPaint.style = Paint.Style.STROKE
                    patternPaint.strokeWidth = 3.2f
                    patternPaint.strokeCap = Paint.Cap.ROUND
                    patternPaint.color = PATTERN_SWIRL
                    canvas.drawPath(swirlPath, patternPaint)
                }
                canvas.restore()
            }
        }

        screenAreas.forEach { drawScreenShadow(canvas, it) }
        canvas.restore()
    }

    private fun drawParchment(canvas: Canvas, w: Float, h: Float) {
        canvas.drawColor(PARCHMENT)
        shaded.shader = RadialGradient(
            w / 2f, h / 2f, max(w, h) * 0.7f,
            intArrayOf(0x59FFFAEB, 0x00FFFAEB, 0x2E78501E),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, shaded)
    }

    /**
     * Builds the pattern's shapes, in their own units: a little spirit (the same shape as in the app's logo, with two eye holes) and a swirl with a tail.
     */
    private fun buildPatternPaths() {
        spiritPath.apply {
            reset()
            fillType = Path.FillType.EVEN_ODD
            moveTo(0f, -14f)
            cubicTo(12f, -14f, 18f, -5f, 18f, 4f)
            cubicTo(18f, 11f, 15f, 15f, 13f, 18f)
            cubicTo(12f, 21f, 14f, 24f, 17f, 26f)
            cubicTo(12f, 27f, 9f, 25f, 8f, 23f)
            cubicTo(6f, 27f, 2f, 29f, -3f, 28f)
            cubicTo(0f, 26f, 0f, 23f, -2f, 21f)
            cubicTo(-6f, 24f, -11f, 24f, -14f, 22f)
            cubicTo(-11f, 20f, -11f, 18f, -12f, 16f)
            cubicTo(-17f, 12f, -18f, 7f, -18f, 2f)
            cubicTo(-18f, -7f, -11f, -14f, 0f, -14f)
            close()
            addOval(RectF(-8.2f, -4f, -3.8f, 2f), Path.Direction.CW)
            addOval(RectF(3.8f, -4f, 8.2f, 2f), Path.Direction.CW)
        }

        swirlPath.apply {
            reset()
            val turns = PI.toFloat() * 3.2f
            var angle = 0f
            while (angle <= turns) {
                val r = 1.6f + angle * 2.6f
                if (angle == 0f) moveTo(cos(angle) * r, sin(angle) * r) else lineTo(cos(angle) * r, sin(angle) * r)
                angle += 0.12f
            }
            val endRadius = 1.6f + turns * 2.6f
            val endX = cos(turns) * endRadius
            val endY = sin(turns) * endRadius
            lineTo(endX, endY)
            quadTo(30f, 18f, 46f, 8f)
        }
    }

    private fun drawScreenShadow(canvas: Canvas, area: ScreenArea) {
        val shadow = getScreenShadow(area.rect.width().toInt(), area.rect.height().toInt(), if (area.isMiniScreen) 3 * d else 0f)
        val padding = screenShadowPadding()
        val alpha = if (area.isMiniScreen) miniScreenAlpha else 1f
        patternPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(shadow, area.rect.left - padding, area.rect.top - padding, patternPaint)
        patternPaint.alpha = 255
    }

    private fun screenShadowPadding() = 36 * d

    /**
     * Returns a blurred shadow for a screen of the given size, drawn once and reused. Blurring is done in software, where it works on every Android version.
     */
    private fun getScreenShadow(screenWidth: Int, screenHeight: Int, corner: Float): Bitmap {
        val key = (screenWidth.toLong() shl 32) or screenHeight.toLong()
        return screenShadowCache.getOrPut(key) {
            val padding = screenShadowPadding()
            val bitmap = Bitmap.createBitmap((screenWidth + padding * 2).toInt().coerceAtLeast(1), (screenHeight + padding * 2).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val shadowCanvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            // A wide shadow falling downwards, and a tighter one close to the screen's edge
            paint.color = SCREEN_SHADOW
            paint.maskFilter = BlurMaskFilter(10 * d, BlurMaskFilter.Blur.NORMAL)
            shadowCanvas.drawRoundRect(RectF(padding + 2 * d, padding + 16 * d, padding + screenWidth - 2 * d, padding + screenHeight + 14 * d), corner, corner, paint)
            paint.color = SCREEN_SHADOW_NEAR
            paint.maskFilter = BlurMaskFilter(3.5f * d, BlurMaskFilter.Blur.NORMAL)
            shadowCanvas.drawRoundRect(RectF(padding, padding + 5 * d, padding + screenWidth, padding + screenHeight + 5 * d), corner, corner, paint)
            bitmap
        }
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------
    // Screen frames
    // ---------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Draws a carved frame around each screen: a gold inlay between two dark lines next to the picture, then wood and a dark outer edge. The frame is drawn
     * outside the screen so that it never covers the game's HUD.
     */
    private fun drawScreenFrames(canvas: Canvas) {
        screenAreas.forEach { area ->
            val alpha = if (area.isMiniScreen) miniScreenAlpha else 1f
            val corner = if (area.isMiniScreen) 3 * d else 0f

            // (colour, stroke width, distance of the stroke's centre from the edge; negative is outside the screen)
            drawFrameLine(canvas, area.rect, 0xFF2A180B.toInt(), 0.6f * d, -0.3f * d, corner, alpha)
            drawFrameLine(canvas, area.rect, GOLD, 1.3f * d, -1.25f * d, corner, alpha)
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
}
