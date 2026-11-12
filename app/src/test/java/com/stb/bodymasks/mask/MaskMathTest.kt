package com.stb.bodymasks.mask

import com.stb.bodymasks.pose.PoseCoord
import com.stb.bodymasks.pose.PosePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.PI

class MaskMathTest {
    @Test
    fun letterboxCentersSquareImageInWideView() {
        val mapping = FrameMapping(
            viewWidth = 160f,
            viewHeight = 90f,
            imageWidth = 100f,
            imageHeight = 100f,
            mirror = false,
        )
        val origin = mapping.toView(0f, 0f)
        val opposite = mapping.toView(1f, 1f)
        assertEquals(35f, origin.x, 0.01f)
        assertEquals(0f, origin.y, 0.01f)
        assertEquals(125f, opposite.x, 0.01f)
        assertEquals(90f, opposite.y, 0.01f)
    }

    @Test
    fun mirrorFlipsNormalizedX() {
        val mapping = FrameMapping(100f, 100f, 100f, 100f, mirror = true)
        val point = mapping.toView(0.25f, 0.4f)
        assertEquals(75f, point.x, 0.01f)
        assertEquals(40f, point.y, 0.01f)
    }

    @Test
    fun downwardBoneHasZeroRotationAndScaledSize() {
        val points = mapOf(
            PosePoint.LEFT_HIP to PoseCoord(0.5f, 0.2f, 1f),
            PosePoint.LEFT_KNEE to PoseCoord(0.5f, 0.7f, 1f),
        )
        val placed = MaskMath.place(
            part = limb(),
            points = points,
            mapping = FrameMapping(100f, 100f, 100f, 100f, mirror = false),
            bitmapWidth = 10,
            bitmapHeight = 40,
        ) ?: error("la pieza debería colocarse")
        assertTrue(abs(placed.rotationRad) < 0.001f)
        assertEquals(50f, placed.pivotViewX, 0.01f)
        assertEquals(20f, placed.pivotViewY, 0.01f)
        assertEquals(100f, placed.drawHeight, 0.01f)
        assertEquals(25f, placed.drawWidth, 0.01f)
    }

    @Test
    fun rightwardBoneRotatesNegativeQuarterTurn() {
        val points = mapOf(
            PosePoint.LEFT_HIP to PoseCoord(0.2f, 0.5f, 1f),
            PosePoint.LEFT_KNEE to PoseCoord(0.8f, 0.5f, 1f),
        )
        val placed = MaskMath.place(
            part = limb(),
            points = points,
            mapping = FrameMapping(100f, 100f, 100f, 100f, mirror = false),
            bitmapWidth = 10,
            bitmapHeight = 40,
        ) ?: error("la pieza debería colocarse")
        assertEquals(-PI.toFloat() / 2f, placed.rotationRad, 0.001f)
    }

    @Test
    fun hidesPartBelowVisibility() {
        val points = mapOf(
            PosePoint.LEFT_HIP to PoseCoord(0.5f, 0.2f, 0.2f),
            PosePoint.LEFT_KNEE to PoseCoord(0.5f, 0.7f, 1f),
        )
        assertNull(
            MaskMath.place(
                part = limb(),
                points = points,
                mapping = FrameMapping(100f, 100f, 100f, 100f, mirror = false),
                bitmapWidth = 10,
                bitmapHeight = 40,
            ),
        )
    }

    private fun limb() = MaskPart(
        id = "left_thigh",
        src = "thigh.png",
        zIndex = 1,
        anchor = PosePoint.LEFT_HIP,
        aimFrom = PosePoint.LEFT_HIP,
        aimTo = PosePoint.LEFT_KNEE,
        sizeFrom = PosePoint.LEFT_HIP,
        sizeTo = PosePoint.LEFT_KNEE,
        pivotX = 0.5f,
        pivotY = 0.1f,
        lengthScale = 2f,
        widthScale = 1f,
        minVisibility = 0.5f,
        flipHorizontal = false,
        opacity = 1f,
    )
}
