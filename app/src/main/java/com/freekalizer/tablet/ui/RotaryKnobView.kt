package com.freekalizer.tablet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.freekalizer.tablet.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rotary control mapped to an integer range (same semantics as a horizontal [android.widget.SeekBar]
 * with [maxValue] steps). Used for filter cutoff / resonance on the performance board.
 *
 * Interaction: drag **up** to increase, **down** to decrease (vertical rack-style), not angular scrubbing.
 * Notch (clock face, 12 at top): **min** at **7 o’clock**, **max** at **5 o’clock**, sweeping **clockwise**
 * **up** past **10**, **12**, **3** (300° long arc). Radial ticks use the **same** angles as the pointer;
 * Only ticks from **min** (index 0) **clockwise** up to the current value are **drawn** (no idle ring);
 * at **min** nothing is drawn; at **max** the full arc shows major/minor ticks.
 */
class RotaryKnobView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maxProgress: Int
    private var internalProgress: Int = 0

    private var pointerId: Int = MotionEvent.INVALID_POINTER_ID
    /** Last touch Y for vertical drag delta (up = increase). */
    private var lastTouchY: Float = 0f

    /** When non-null, double-tap snaps to this value and notifies [setOnKnobChangeListener]. */
    var doubleTapResetProgress: Int? = null
    private var onKnobChange: ((progress: Int, fromUser: Boolean) -> Unit)? = null

    private val gestureDetector: GestureDetector

    /**
     * Notch at 12 o’clock, then [Canvas.rotate] clockwise from there (same as ticks + pointer).
     * 7 o’clock = 210° CW from 12; 5 o’clock = 150° CW; long arc = +300° CW (via 10, 12, 3).
     */
    private val rotationAtMinClockwiseDeg = 210f
    private val sweepClockwiseDeg = 300f

    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val knobFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val capRidgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.1f)
    }
    private val notchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF151515.toInt()
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Major tick every N segments along the value arc. */
    private val tickSegmentCount = 45

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RotaryKnobView, defStyleAttr, 0)
        maxProgress = max(1, a.getInt(R.styleable.RotaryKnobView_maxValue, 1000))
        internalProgress = a.getInt(R.styleable.RotaryKnobView_knobProgress, 0).coerceIn(0, maxProgress)
        a.recycle()
        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val target = doubleTapResetProgress ?: return false
                    val v = target.coerceIn(0, maxProgress)
                    if (v != internalProgress) {
                        internalProgress = v
                        invalidate()
                        onKnobChange?.invoke(internalProgress, true)
                    }
                    return true
                }
            }
        )
    }

    fun setOnKnobChangeListener(listener: (progress: Int, fromUser: Boolean) -> Unit) {
        onKnobChange = listener
    }

    /** Sync from engine / UI refresh without treating it as user input. */
    fun syncProgressFromEngine(progress: Int) {
        val v = progress.coerceIn(0, maxProgress)
        if (v == internalProgress) return
        internalProgress = v
        invalidate()
    }

    val max: Int get() = maxProgress

    var progress: Int
        get() = internalProgress
        set(value) {
            val v = value.coerceIn(0, maxProgress)
            if (v == internalProgress) return
            internalProgress = v
            invalidate()
        }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                pointerId = event.getPointerId(0)
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return false
                val y = event.getY(index)
                val dy = y - lastTouchY
                lastTouchY = y
                val trackPx = max(minOf(width, height) * 0.88f, dp(32f))
                val deltaProgress = (-dy / trackPx * maxProgress).roundToInt()
                if (deltaProgress == 0) return true
                val newP = (internalProgress + deltaProgress).coerceIn(0, maxProgress)
                if (newP != internalProgress) {
                    internalProgress = newP
                    invalidate()
                    onKnobChange?.invoke(internalProgress, true)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pointerId = MotionEvent.INVALID_POINTER_ID
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * 0.5f
        val cy = height * 0.5f
        val r = minOf(width, height) * 0.5f - dp(3f)

        bezelPaint.shader = RadialGradient(
            cx,
            cy - r * 0.12f,
            r * 1.05f,
            intArrayOf(0xFF4A4A4A.toInt(), 0xFF202020.toInt(), 0xFF0A0A0A.toInt()),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r + dp(2f), bezelPaint)

        val capR = r * 0.92f
        knobFillPaint.shader = RadialGradient(
            cx - capR * 0.18f,
            cy - capR * 0.22f,
            capR * 1.15f,
            intArrayOf(0xFFF2F2F2.toInt(), 0xFFC8C8C8.toInt(), 0xFF9A9A9A.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, capR, knobFillPaint)
        knobFillPaint.shader = null

        capRidgePaint.shader = LinearGradient(
            cx - capR,
            cy - capR,
            cx + capR,
            cy + capR,
            0x88FFFFFF.toInt(),
            0x22333333.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, capR * 0.97f, capRidgePaint)
        capRidgePaint.shader = null

        // Ticks sit in the ring above the cap (same geometry as pointer); draw before the hash mark.
        drawValueArcTicks(canvas, cx, cy, r, capR)

        canvas.save()
        val rot = progressToRotationDeg()
        canvas.rotate(rot, cx, cy)
        val ptrLen = capR * 0.42f
        val ptrW = dp(5f)
        canvas.drawRoundRect(
            cx - ptrW * 0.5f,
            cy - ptrLen,
            cx + ptrW * 0.5f,
            cy - capR * 0.18f,
            dp(1.5f),
            dp(1.5f),
            notchPaint
        )
        canvas.restore()
    }

    /** Clockwise degrees from 12 o’clock for fraction `t` in [0,1] along the value arc. */
    private fun angleAtFraction(t: Float): Float {
        var deg = rotationAtMinClockwiseDeg + sweepClockwiseDeg * t.coerceIn(0f, 1f)
        deg %= 360f
        if (deg < 0f) deg += 360f
        return deg
    }

    private fun drawValueArcTicks(canvas: Canvas, cx: Float, cy: Float, r: Float, capR: Float) {
        val outerR = min(r + dp(2.2f), min(width, height) * 0.5f - dp(0.5f))
        val innerMinor = capR + dp(5f)
        val innerMajor = capR + dp(2.8f)
        // Arc parameter t = i / n matches the pointer: i=0 → 7 o'clock (min), i=n → 5 o'clock (max), same as
        // angleAtFraction(t). Active sweep = ticks from min up to current value (clockwise along the 300° arc).
        // Integer inequality: tick i is at or before the pointer iff i * maxProgress <= internalProgress * n
        // (with ceil bias so 999/1000 still includes the last tick). Only draw active ticks — no “dim” ring.
        for (i in 0..tickSegmentCount) {
            val t = i / tickSegmentCount.toFloat()
            val isMajor = i % 5 == 0
            val innerR = if (isMajor) innerMajor else innerMinor
            val active = internalProgress > 0 &&
                internalProgress * tickSegmentCount + maxProgress - 1 >= i * maxProgress
            if (!active) continue
            tickPaint.strokeWidth = if (isMajor) dp(1.25f) else dp(0.85f)
            // Contrast on silver bezel (avoid ultra-dark strokes that read as “missing” at max).
            tickPaint.color = if (isMajor) 0xFF343840.toInt() else 0xFF5C626C.toInt()
            canvas.save()
            canvas.rotate(angleAtFraction(t), cx, cy)
            canvas.drawLine(cx, cy - outerR, cx, cy - innerR, tickPaint)
            canvas.restore()
        }
    }

    private fun progressToRotationDeg(): Float {
        return angleAtFraction(internalProgress / maxProgress.toFloat())
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
