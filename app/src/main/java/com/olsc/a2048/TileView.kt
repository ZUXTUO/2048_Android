package com.olsc.a2048

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View

/**
 * 2048 数字瓦片（Canvas 自绘经典棋子效果）。
 *
 * 经典 2048 配色圆角色块（85% 不透明）+ 顶部高光 + 玻璃描边 + 底部投影，
 * 清晰醒目；棋盘面板与卡片由 GlassCardView 呈现液态玻璃质感。
 */
class TileView constructor(context: Context) : View(context) {

    var value: Int = 0
        private set

    /** 当前所在格子（由 BoardView 维护）。 */
    var row: Int = -1
    var col: Int = -1

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bodyPath = Path()
    private val shadowPath = Path()
    private val bodyRect = RectF()
    private val shadowRect = RectF()
    private val corner = dp(11f)

    private var highlightTop: LinearGradient? = null
    private var bgColor = 0xD9EEE4DA.toInt()
    private var textColor = COLOR_DARK_TEXT

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        highlightTop = LinearGradient(
            0f, 0f, 0f, h * 0.55f,
            intArrayOf(0x78FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun setValue(v: Int) {
        value = v
        val (tileColor, textColor) = palette(v)
        bgColor = (tileColor and 0x00FFFFFF) or 0xD9000000.toInt()
        this.textColor = textColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 1) 底部投影：让瓦片像悬浮在玻璃棋盘上
        val shadowY = dp(3f)
        shadowRect.set(0f, shadowY, w, h + shadowY)
        shadowPath.reset()
        shadowPath.addRoundRect(shadowRect, corner, corner, Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = 0x2B000000.toInt()
        canvas.drawPath(shadowPath, paint)

        // 2) 棋子底色（经典 2048 配色，85% 不透明）
        bodyRect.set(0f, 0f, w, h)
        bodyPath.reset()
        bodyPath.addRoundRect(bodyRect, corner, corner, Path.Direction.CW)
        paint.color = bgColor
        canvas.drawPath(bodyPath, paint)

        // 3) 顶部高光：模拟玻璃受光面
        paint.shader = highlightTop
        canvas.drawPath(bodyPath, paint)
        paint.shader = null

        // 4) 边缘描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = 0x66FFFFFF.toInt()
        canvas.drawPath(bodyPath, paint)
        paint.style = Paint.Style.FILL

        // 5) 数字
        textPaint.color = textColor
        textPaint.textSize = when {
            value < 100 -> dp(30f)
            value < 1000 -> dp(26f)
            value < 10000 -> dp(22f)
            else -> dp(18f)
        }
        val baseline = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(value.toString(), w / 2f, baseline, textPaint)
    }

    /** 经典 2048 配色：棋子底色（RGB）+ 数字色。 */
    private fun palette(v: Int): Pair<Int, Int> = when (v) {
        2 -> 0xEEE4DA to COLOR_DARK_TEXT
        4 -> 0xEDE0C8 to COLOR_DARK_TEXT
        8 -> 0xF2B179 to COLOR_LIGHT_TEXT
        16 -> 0xF59563 to COLOR_LIGHT_TEXT
        32 -> 0xF67C5F to COLOR_LIGHT_TEXT
        64 -> 0xF65E3B to COLOR_LIGHT_TEXT
        128 -> 0xEDCF72 to COLOR_LIGHT_TEXT
        256 -> 0xEDCC61 to COLOR_LIGHT_TEXT
        512 -> 0xEDC850 to COLOR_LIGHT_TEXT
        1024 -> 0xEDC53F to COLOR_LIGHT_TEXT
        2048 -> 0xEDC22E to COLOR_LIGHT_TEXT
        else -> 0x3C3A32 to COLOR_LIGHT_TEXT
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private companion object {
        val COLOR_DARK_TEXT: Int = 0xFF776E65.toInt()
        val COLOR_LIGHT_TEXT: Int = 0xFFF9F6F2.toInt()
    }
}
