package com.olsc.a2048

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout

/**
 * 液态玻璃卡片/面板（Canvas 自绘，不依赖 GL）。
 *
 * 目标设备上 EGL 内容无法上屏（GLSurfaceView 与 TextureView 均显示黑/空方块），
 * 因此用普通 View 绘制玻璃质感：半透明渐变底色（透出背景彩虹）+ 顶部高光带 +
 * 玻璃描边 + 内部柔和彩色光晕（模拟折射）+ 底部投影。视觉效果接近 iOS 液态玻璃，
 * 且在任何设备上都保证显示。
 */
open class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** 圆角半径（dp），子类或外部可调整。 */
    var cornerRadius = 22f
        set(value) {
            field = value
            invalidate()
        }

    /** 是否绘制内部彩色光晕（模拟折射），面板等大组件开启。 */
    var showInnerGlow = true
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val glowPaints = ArrayList<Pair<FloatArray, Paint>>() // (cx, cy, r) -> paint

    private var glassShader: LinearGradient? = null
    private var highlightShader: LinearGradient? = null

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 玻璃主体：上白下透，透出背景彩虹
        glassShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(0x45FFFFFF.toInt(), 0x1CFFFFFF.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        // 顶部高光带
        highlightShader = LinearGradient(
            0f, 0f, 0f, h * 0.4f,
            intArrayOf(0x38FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        if (showInnerGlow) {
            glowPaints.clear()
            val rainbow = intArrayOf(0xFF5E9C.toInt(), 0xFF8E7CFF.toInt(), 0xFF39C6E8.toInt(), 0xFF3BE8B0.toInt())
            val anchors = arrayOf(
                floatArrayOf(0.18f, 0.25f, 0.55f),
                floatArrayOf(0.82f, 0.2f, 0.5f),
                floatArrayOf(0.7f, 0.75f, 0.55f),
                floatArrayOf(0.28f, 0.8f, 0.42f),
            )
            for (i in anchors.indices) {
                val a = anchors[i]
                val r = a[2] * w
                glowPaints.add(
                    a to Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            a[0] * w, a[1] * h, r,
                            intArrayOf(
                                Color.argb(64, Color.red(rainbow[i]), Color.green(rainbow[i]), Color.blue(rainbow[i])),
                                0x00000000,
                            ),
                            floatArrayOf(0f, 1f),
                            Shader.TileMode.CLAMP,
                        )
                    },
                )
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val corner = dp(cornerRadius)

        // 1) 底部投影
        rect.set(0f, dp(4f), w, h + dp(4f))
        path.reset()
        path.addRoundRect(rect, corner, corner, Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x2E000000.toInt()
        canvas.drawPath(path, paint)

        // 2) 玻璃主体（半透明，透出背景）
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, corner, corner, Path.Direction.CW)
        paint.shader = glassShader
        canvas.drawPath(path, paint)

        // 3) 内部彩色光晕（模拟折射的彩虹，裁剪在圆角内）
        if (showInnerGlow) {
            canvas.save()
            canvas.clipPath(path)
            for ((loc, p) in glowPaints) {
                canvas.drawCircle(loc[0], loc[1], loc[2], p)
            }
            canvas.restore()
        }
        paint.shader = null

        // 4) 顶部高光带
        paint.shader = highlightShader
        canvas.drawPath(path, paint)
        paint.shader = null

        // 5) 玻璃边缘描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.color = 0x80FFFFFF.toInt()
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
