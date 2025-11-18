package com.m.s.micosaver.ui.view

import android.content.Context
import android.graphics.*
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class RectProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val progressColor = Color.parseColor("#80000000")
    private val cornerRadius: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        8f,
        resources.displayMetrics
    )

    private val path = Path()

    // 用于绘制进度的矩形
    private val rectF = RectF()

    private var downloadProgress: Int = 0
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = progressColor
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (downloadProgress == 0) return

        val width = width.toFloat()
        val height = height.toFloat()

        // 计算进度高度（从底部开始）
        val progressHeight = height * downloadProgress / 100f

        // 设置绘制区域（从底部向上的矩形）
        rectF.set(0f, height - progressHeight, width, height)

        // 重置路径
        path.reset()

        handleRound(progressHeight)

        // 绘制进度
        canvas.drawPath(path, paint)
    }

    private fun handleRound(progressHeight: Float) {
        // 根据进度位置决定如何绘制圆角
        when {
            progressHeight >= height -> {
                // 进度到达或超过顶部，绘制所有圆角
                path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
            }
            progressHeight <= cornerRadius * 2 -> {
                // 进度很低，需要特殊处理以确保底部两个圆角都显示
                if (progressHeight > 0) {
                    // 创建一个只包含底部圆角的路径
                    path.addRoundRect(
                        rectF,
                        floatArrayOf(
                            0f, 0f,                       // 左上
                            0f, 0f,                       // 右上
                            cornerRadius, cornerRadius,    // 右下
                            cornerRadius, cornerRadius     // 左下
                        ),
                        Path.Direction.CW
                    )
                }
            }
            else -> {
                // 进度在中间，只绘制底部两个圆角
                path.addRoundRect(
                    rectF,
                    floatArrayOf(
                        0f, 0f,                       // 左上
                        0f, 0f,                       // 右上
                        cornerRadius, cornerRadius,    // 右下
                        cornerRadius, cornerRadius     // 左下
                    ),
                    Path.Direction.CW
                )
            }
        }
    }

    /**
     * @param progress 进度值（0-100）
     */
    fun setProgress(progress: Int) {
        this.downloadProgress = progress
    }

    fun getProgress(): Int {
        return downloadProgress
    }
}