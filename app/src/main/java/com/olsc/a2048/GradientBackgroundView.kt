package com.olsc.a2048

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * 液态玻璃背景：深色渐变 + 动态彩虹光晕色斑。
 *
 * 一组覆盖红橙黄绿青蓝紫的色斑沿各自的轨迹缓慢漂移、呼吸缩放，
 * 模糊的光晕为玻璃面板的折射与高光提供不断流动的视觉内容，
 * 让液态玻璃的模糊、色散与边缘高光始终"活"着。
 */
class GradientBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 每次约 0.5s 回调一次，供棋盘玻璃刷新背景捕获（低频，避免动画卡顿）。 */
    var onAnimatedFrame: (() -> Unit)? = null

    private class Blob(
        val anchorX: Float,
        val anchorY: Float,
        val radiusRatio: Float,
        val hue: Float,
        val speed: Float,
        val drift: Float,
        val phase: Float,
    )

    private val blobs = listOf(
        Blob(0.86f, 0.14f, 0.46f, 340f, 0.30f, 0.90f, 0.0f), // 玫红
        Blob(0.14f, 0.30f, 0.40f, 15f, 0.24f, 0.65f, 1.7f),  // 橙粉
        Blob(0.72f, 0.74f, 0.44f, 48f, 0.20f, 1.00f, 3.1f),  // 琥珀
        Blob(0.30f, 0.82f, 0.34f, 92f, 0.26f, 0.72f, 4.5f),  // 青绿
        Blob(0.52f, 0.46f, 0.32f, 185f, 0.18f, 0.55f, 2.3f), // 天蓝
        Blob(0.20f, 0.56f, 0.30f, 240f, 0.22f, 0.80f, 5.2f), // 蓝紫
        Blob(0.64f, 0.28f, 0.28f, 292f, 0.21f, 0.62f, 0.9f), // 紫罗兰
    )

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaints = ArrayList<Pair<Blob, Paint>>()
    private var w = 0
    private var h = 0
    private var time = 0f
    private var lastNanos = 0L
    private var frameCount = 0

    private val frameCallback: Choreographer.FrameCallback = Choreographer.FrameCallback { nanos ->
        if (lastNanos != 0L) {
            val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            time += dt
            if (w > 0 && h > 0) {
                // 隔帧重绘约 30fps，兼顾动态观感与低端机渲染压力
                if (++frameCount % 2 == 0) invalidate()
                if (frameCount % 60 == 0) onAnimatedFrame?.invoke()
            }
        }
        lastNanos = nanos
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.w = w
        this.h = h
        bgPaint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(
                0xFF3A1F6E.toInt(),
                0xFF22306B.toInt(),
                0xFF141B3C.toInt(),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        blobPaints.clear()
        for (b in blobs) {
            blobPaints.add(
                b to Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = buildHalo(b.hue, b.radiusRatio * w)
                },
            )
        }
    }

    /** 构造一枚模糊光晕：中心亮、边缘透明，饱和度略降让色彩更柔和。 */
    private fun buildHalo(hue: Float, radius: Float): RadialGradient {
        val core = Color.HSVToColor(255, floatArrayOf(hue, 0.85f, 1f))
        val mid = Color.HSVToColor(140, floatArrayOf(hue, 0.78f, 1f))
        val edge = 0x00000000
        return RadialGradient(
            0f, 0f, radius,
            intArrayOf(core, mid, edge),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // 先画大色斑（背景层），再画柔光（前景层），增加纵深
        for ((b, p) in blobPaints) {
            val cx = (b.anchorX + sin(time * b.speed + b.phase) * b.drift * 0.05f) * w
            val cy = (b.anchorY + cos(time * b.speed * 0.8f + b.phase) * b.drift * 0.05f) * h
            val breath = 1f + 0.10f * sin(time * b.speed * 1.6f + b.phase)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(breath, breath)
            canvas.drawCircle(0f, 0f, b.radiusRatio * w, p)
            canvas.restore()
        }
    }
}
