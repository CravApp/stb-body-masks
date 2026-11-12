package com.stb.bodymasks.mask

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.stb.bodymasks.pose.DEBUG_BONES
import com.stb.bodymasks.pose.PoseFrame
import kotlin.math.roundToInt

class MaskOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var mirror: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var showDebug: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var mask: LoadedMask? = null
    private var frame: PoseFrame? = null
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5CE1C5")
        style = Paint.Style.FILL
    }
    private val dotRadius = 4f * resources.displayMetrics.density

    fun setMask(mask: LoadedMask?) {
        this.mask = mask
        invalidate()
    }

    fun setFrame(frame: PoseFrame?) {
        this.frame = frame
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = frame ?: return
        if (width == 0 || height == 0 || current.imageWidth <= 0 || current.imageHeight <= 0) return
        val mapping = FrameMapping(
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
            imageWidth = current.imageWidth.toFloat(),
            imageHeight = current.imageHeight.toFloat(),
            mirror = mirror,
        )
        val currentMask = mask
        if (currentMask != null && current.points.isNotEmpty()) {
            val parts = currentMask.definition.parts.sortedBy { it.zIndex }
            for (part in parts) {
                val bitmap = currentMask.bitmaps[part.id] ?: continue
                val placement = MaskMath.place(part, current.points, mapping, bitmap.width, bitmap.height)
                    ?: continue
                spritePaint.alpha = (placement.opacity * 255f).roundToInt().coerceIn(0, 255)
                canvas.save()
                canvas.translate(placement.pivotViewX, placement.pivotViewY)
                canvas.rotate(Math.toDegrees(placement.rotationRad.toDouble()).toFloat())
                if (placement.flipHorizontal) canvas.scale(-1f, 1f)
                val left = -placement.pivotX * placement.drawWidth
                val top = -placement.pivotY * placement.drawHeight
                canvas.drawBitmap(
                    bitmap,
                    null,
                    android.graphics.RectF(
                        left,
                        top,
                        left + placement.drawWidth,
                        top + placement.drawHeight,
                    ),
                    spritePaint,
                )
                canvas.restore()
            }
        }
        if (showDebug && current.points.isNotEmpty()) {
            drawDebug(canvas, current, mapping)
        }
    }

    private fun drawDebug(canvas: Canvas, frame: PoseFrame, mapping: FrameMapping) {
        for ((from, to) in DEBUG_BONES) {
            val a = frame.points[from] ?: continue
            val b = frame.points[to] ?: continue
            if (a.visibility < 0.4f || b.visibility < 0.4f) continue
            val pa = mapping.toView(a.x, a.y)
            val pb = mapping.toView(b.x, b.y)
            canvas.drawLine(pa.x, pa.y, pb.x, pb.y, bonePaint)
        }
        for ((point, coord) in frame.points) {
            if (point.landmarkIndex == null || coord.visibility < 0.4f) continue
            val view = mapping.toView(coord.x, coord.y)
            canvas.drawCircle(view.x, view.y, dotRadius, dotPaint)
        }
    }
}
