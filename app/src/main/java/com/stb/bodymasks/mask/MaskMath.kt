package com.stb.bodymasks.mask

import com.stb.bodymasks.pose.PoseCoord
import com.stb.bodymasks.pose.PosePoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

data class ViewPoint(val x: Float, val y: Float)

/**
 * Proyección FIT_CENTER (letterbox) de la imagen de análisis sobre la vista.
 * Con espejo, x se invierte antes de proyectar para coincidir con un PreviewView scaleX = -1
 * cuando el letterbox es simétrico.
 */
data class FrameMapping(
    val viewWidth: Float,
    val viewHeight: Float,
    val imageWidth: Float,
    val imageHeight: Float,
    val mirror: Boolean,
) {
    val scale: Float
    val offsetX: Float
    val offsetY: Float

    init {
        if (imageWidth <= 0f || imageHeight <= 0f || viewWidth <= 0f || viewHeight <= 0f) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        } else {
            val fitted = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
            scale = fitted
            offsetX = (viewWidth - imageWidth * fitted) / 2f
            offsetY = (viewHeight - imageHeight * fitted) / 2f
        }
    }

    fun toView(normX: Float, normY: Float): ViewPoint {
        val x = if (mirror) 1f - normX else normX
        return ViewPoint(
            x = offsetX + x * imageWidth * scale,
            y = offsetY + normY * imageHeight * scale,
        )
    }
}

data class Placement(
    val pivotViewX: Float,
    val pivotViewY: Float,
    val rotationRad: Float,
    val drawWidth: Float,
    val drawHeight: Float,
    val pivotX: Float,
    val pivotY: Float,
    val flipHorizontal: Boolean,
    val opacity: Float,
)

object MaskMath {
    fun place(
        part: MaskPart,
        points: Map<PosePoint, PoseCoord>,
        mapping: FrameMapping,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): Placement? {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return null
        if (mapping.viewWidth <= 0f || mapping.imageWidth <= 0f) return null
        val needed = listOf(part.anchor, part.aimFrom, part.aimTo, part.sizeFrom, part.sizeTo)
        for (name in needed) {
            val coord = points[name] ?: return null
            if (coord.visibility < part.minVisibility) return null
        }
        val anchor = mapping.toView(points.getValue(part.anchor).x, points.getValue(part.anchor).y)
        val aimA = mapping.toView(points.getValue(part.aimFrom).x, points.getValue(part.aimFrom).y)
        val aimB = mapping.toView(points.getValue(part.aimTo).x, points.getValue(part.aimTo).y)
        val sizeA = mapping.toView(points.getValue(part.sizeFrom).x, points.getValue(part.sizeFrom).y)
        val sizeB = mapping.toView(points.getValue(part.sizeTo).x, points.getValue(part.sizeTo).y)
        val aimDx = aimB.x - aimA.x
        val aimDy = aimB.y - aimA.y
        val aimLen = hypot(aimDx, aimDy)
        val sizeLen = hypot(sizeB.x - sizeA.x, sizeB.y - sizeA.y)
        if (aimLen < 4f || sizeLen < 4f) return null
        val rotation = atan2(aimDy, aimDx) - (PI.toFloat() / 2f)
        val drawHeight = sizeLen * part.lengthScale
        val drawWidth = drawHeight * (bitmapWidth.toFloat() / bitmapHeight.toFloat()) * part.widthScale
        if (!rotation.isFinite() || !drawWidth.isFinite() || !drawHeight.isFinite()) return null
        return Placement(
            pivotViewX = anchor.x,
            pivotViewY = anchor.y,
            rotationRad = rotation,
            drawWidth = drawWidth,
            drawHeight = drawHeight,
            pivotX = part.pivotX,
            pivotY = part.pivotY,
            flipHorizontal = part.flipHorizontal,
            opacity = part.opacity,
        )
    }
}
