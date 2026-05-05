package com.household.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class SpendingDonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Segment(val value: Float, val color: Int)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
        color = 0x22FFFFFF
        strokeCap = Paint.Cap.ROUND
    }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
        strokeCap = Paint.Cap.ROUND
    }

    private val arcRect = RectF()
    private var segments: List<Segment> = emptyList()

    fun setSegments(values: List<Pair<Float, Int>>) {
        segments = values
            .filter { it.first > 0f }
            .map { Segment(it.first, it.second) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return

        val padding = 18f
        val left = (width - size) / 2f + padding
        val top = (height - size) / 2f + padding
        val right = (width + size) / 2f - padding
        val bottom = (height + size) / 2f - padding
        arcRect.set(left, top, right, bottom)

        canvas.drawArc(arcRect, -90f, 360f, false, trackPaint)

        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return

        var currentStart = -90f
        val gap = 2.5f
        for (segment in segments) {
            val sweep = (segment.value / total) * 360f
            val adjustedSweep = (sweep - gap).coerceAtLeast(2f)
            segmentPaint.color = segment.color
            canvas.drawArc(arcRect, currentStart, adjustedSweep, false, segmentPaint)
            currentStart += sweep
        }
    }
}
