package com.freekalizer.tablet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.freekalizer.tablet.R
import kotlin.math.ceil
import kotlin.math.max

/**
 * Horizontal transport progress with optional BPM/beat grid and emphasis every [loopBars] bars.
 */
class TransportGridProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridMinor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val gridBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val gridLoop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val trackRect = RectF()
    private val fillRect = RectF()

    private var progressNorm: Float = 0f
    private var recordingTint: Boolean = false
    private var gridVisible: Boolean = false

    private var bpm: Double = 120.0
    private var durationSec: Double = 0.0
    private var loopBars: Int = 4

    init {
        refreshColors()
    }

    private fun refreshColors() {
        trackPaint.color = ContextCompat.getColor(context, R.color.surface_zone)
        fillPaint.color = ContextCompat.getColor(context, R.color.accent_led_cyan)
        gridMinor.color = ContextCompat.getColor(context, R.color.text_muted)
        gridMinor.alpha = 90
        gridBar.color = ContextCompat.getColor(context, R.color.text_muted)
        gridBar.alpha = 160
        gridLoop.color = ContextCompat.getColor(context, R.color.text_primary)
        gridLoop.alpha = 200
    }

    fun setTransportState(
        progressNormalized: Float,
        recordingProgress: Boolean,
        showBeatGrid: Boolean,
        bpmValue: Double,
        sampleDurationSec: Double,
        loopBarsSelection: Int
    ) {
        progressNorm = progressNormalized.coerceIn(0f, 1f)
        recordingTint = recordingProgress
        gridVisible = showBeatGrid
        bpm = bpmValue.coerceAtLeast(1.0)
        durationSec = max(0.0, sampleDurationSec)
        loopBars = loopBarsSelection.coerceAtLeast(1)
        refreshColors()
        if (recordingProgress) {
            fillPaint.color = ContextCompat.getColor(context, R.color.overload)
        } else {
            fillPaint.color = ContextCompat.getColor(context, R.color.accent_led_cyan)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val r = h * 0.35f
        trackRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(trackRect, r, r, trackPaint)

        val pw = w * progressNorm
        if (pw > 0f) {
            fillRect.set(0f, 0f, pw, h)
            canvas.drawRoundRect(fillRect, r, r, fillPaint)
        }

        if (gridVisible && !recordingTint && durationSec > 0.01) {
            val beatSec = 60.0 / bpm
            if (beatSec > 0.0) {
                val totalBeats = ceil(durationSec / beatSec).toInt().coerceAtLeast(1)
                val beatsPerLoop = (loopBars * 4).coerceAtLeast(4)
                // Light strokes read on dark cyan fill; recording mode uses orange fill with grid off.
                gridMinor.color = ContextCompat.getColor(context, R.color.background_app)
                gridMinor.alpha = 255
                gridBar.color = ContextCompat.getColor(context, R.color.surface_zone)
                gridBar.alpha = 255
                gridLoop.color = ContextCompat.getColor(context, R.color.overload)
                gridLoop.alpha = 255
                gridMinor.strokeWidth = max(1.1f, h * 0.065f)
                gridBar.strokeWidth = max(1.8f, h * 0.095f)
                gridLoop.strokeWidth = max(2.4f, h * 0.12f)
                for (i in 1 until totalBeats) {
                    val t = i * beatSec
                    val x = (t / durationSec).toFloat() * w
                    if (x <= 0f || x >= w) continue
                    val p = when {
                        i % beatsPerLoop == 0 -> gridLoop
                        i % 4 == 0 -> gridBar
                        else -> gridMinor
                    }
                    canvas.drawLine(x, h * 0.15f, x, h * 0.85f, p)
                }
            }
        }
    }
}
