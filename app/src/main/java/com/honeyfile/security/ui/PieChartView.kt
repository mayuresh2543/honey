package com.honeyfile.security.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

data class PieSlice(
    val label: String,
    val value: Float,
    val color: Int
)

class PieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val slices = mutableListOf<PieSlice>()
    private var centerText: String = ""
    private var centerSubText: String = ""

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1E1E2C") // Default dark inner donut color
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    private val rectF = RectF()

    fun setData(slicesList: List<PieSlice>, title: String = "", subTitle: String = "") {
        slices.clear()
        slices.addAll(slicesList)
        centerText = title
        centerSubText = subTitle
        invalidate()
    }

    fun setCenterBackgroundColor(color: Int) {
        centerPaint.color = color
        invalidate()
    }

    fun setTextColor(primaryColor: Int, secondaryColor: Int) {
        textPaint.color = primaryColor
        subTextPaint.color = secondaryColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (slices.isEmpty()) return

        val totalValue = slices.sumOf { it.value.toDouble() }.toFloat()
        if (totalValue <= 0f) return

        val width = width.toFloat()
        val height = height.toFloat()
        val size = Math.min(width, height)
        val padding = 20f

        val radius = (size / 2f) - padding
        val centerX = width / 2f
        val centerY = height / 2f

        rectF.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        var startAngle = -90f

        for (slice in slices) {
            val sweepAngle = (slice.value / totalValue) * 360f
            paint.color = slice.color
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }

        // Draw inner donut circle (65% radius)
        val innerRadius = radius * 0.62f
        canvas.drawCircle(centerX, centerY, innerRadius, centerPaint)

        // Draw Center Title & Subtitle Text
        if (centerText.isNotEmpty()) {
            canvas.drawText(centerText, centerX, centerY - 8f, textPaint)
        }
        if (centerSubText.isNotEmpty()) {
            canvas.drawText(centerSubText, centerX, centerY + 30f, subTextPaint)
        }
    }
}
