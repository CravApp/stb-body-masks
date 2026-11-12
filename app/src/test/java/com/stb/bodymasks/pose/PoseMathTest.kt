package com.stb.bodymasks.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoseMathTest {
    @Test
    fun virtualPointsAreMidpoints() {
        val raw = MutableList(33) { PoseCoord(0f, 0f, 1f) }
        raw[PosePoint.LEFT_SHOULDER.landmarkIndex!!] = PoseCoord(0.2f, 0.4f, 0.9f)
        raw[PosePoint.RIGHT_SHOULDER.landmarkIndex!!] = PoseCoord(0.8f, 0.6f, 0.4f)
        val resolved = resolvePosePoints(raw) ?: error("se esperaban 33 puntos")
        val mid = resolved.getValue(PosePoint.MID_SHOULDERS)
        assertEquals(0.5f, mid.x, 0.001f)
        assertEquals(0.5f, mid.y, 0.001f)
        assertEquals(0.4f, mid.visibility, 0.001f)
    }

    @Test
    fun shortListIsIgnored() {
        assertNull(resolvePosePoints(List(32) { PoseCoord(0f, 0f, 1f) }))
    }

    @Test
    fun smootherKeepsHiddenPointsFromDragging() {
        val smoother = LandmarkSmoother(alpha = 0.5f)
        smoother.apply(listOf(PoseCoord(0.2f, 0.3f, 1f)))
        val hidden = smoother.apply(listOf(PoseCoord(0.9f, 0.9f, 0.1f)))
        assertEquals(0.2f, hidden[0].x, 0.001f)
        assertEquals(0.3f, hidden[0].y, 0.001f)
        assertEquals(0.1f, hidden[0].visibility, 0.001f)
        val back = smoother.apply(listOf(PoseCoord(0.2f, 0.3f, 1f)))
        assertEquals(0.2f, back[0].x, 0.001f)
    }

    @Test
    fun smootherFollowsALargeJumpFaster() {
        val smoother = LandmarkSmoother(alpha = 0.5f)
        smoother.apply(listOf(PoseCoord(0f, 0f, 1f)))
        val jumped = smoother.apply(listOf(PoseCoord(1f, 0f, 1f)))
        assertEquals(0.85f, jumped[0].x, 0.001f)
    }
}
