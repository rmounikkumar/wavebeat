package com.wavebeat

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

class DancingLogoDrawable : Drawable() {

    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0C0C14.toInt()
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1F1F30.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private class Bar(val x0: Float, val x1: Float, val bottom: Float, val fullHeight: Float, val color: Int)
    private class Glow(val x0: Float, val x1: Float, val top: Float, val color: Int)

    private val bars = listOf(
        Bar(24f, 36f, 100f, 56f, 0xFFE94560.toInt()),
        Bar(42f, 54f, 104f, 82f, 0xFFFF6B81.toInt()),
        Bar(60f, 72f, 104f, 68f, 0xFFE94560.toInt()),
        Bar(78f, 90f, 104f, 86f, 0xFF00E5C7.toInt())
    )
    private val glows = listOf(
        Glow(22f, 32f, 46f, 0x26E94560), Glow(40f, 50f, 24f, 0x16E94560),
        Glow(58f, 68f, 38f, 0x26E94560), Glow(76f, 86f, 20f, 0x16E94560)
    )
    private val glowPaints = glows.map { g ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = g.color; style = Paint.Style.FILL }
    }
    private val speeds = doubleArrayOf(2.1, 1.6, 2.6, 1.9)
    private val phases = doubleArrayOf(0.0, 2.2, 4.3, 1.1)
    private val barPaints = bars.map { b ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = b.color; style = Paint.Style.FILL }
    }

    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0) return
        val s = w / 108f

        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        canvas.drawRoundRect(rect, 14f * s, 14f * s, backdropPaint)

        ringPaint.strokeWidth = 3f * s
        rect.set(20f * s, 14f * s, 88f * s, 94f * s)
        canvas.drawRoundRect(rect, 6f * s, 6f * s, ringPaint)

        for (i in glows.indices) {
            val g = glows[i]
            rect.set(g.x0 * s, g.top * s, g.x1 * s, 98f * s)
            canvas.drawRect(rect, glowPaints[i])
        }

        val now = System.currentTimeMillis().toDouble()
        val t = (now % 100000.0) / 1000.0
        for (i in bars.indices) {
            val bar = bars[i]
            val lvl = 0.25 + 0.75 * abs(sin(t * speeds[i] + phases[i]))
            val barH = bar.fullHeight * s * lvl.toFloat()
            val top = (bar.bottom * s) - barH
            val r = min(6f * s, barH / 2f)
            rect.set(bar.x0 * s, top, bar.x1 * s, bar.bottom * s)
            canvas.drawRoundRect(rect, r, r, barPaints[i])
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}