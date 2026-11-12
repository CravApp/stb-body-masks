package com.stb.bodymasks.pose

import kotlin.math.hypot

/**
 * Suaviza los puntos para que el disfraz no tiemble.
 * Una medición poco visible no arrastra la posición guardada.
 */
class LandmarkSmoother(private val alpha: Float = 0.42f) {
    private var state: FloatArray? = null

    fun reset() {
        state = null
    }

    fun apply(raw: List<PoseCoord>): List<PoseCoord> {
        val previous = state
        val next = FloatArray(raw.size * 2)
        val out = ArrayList<PoseCoord>(raw.size)
        for (index in raw.indices) {
            val sample = raw[index]
            val x: Float
            val y: Float
            if (previous == null) {
                x = sample.x
                y = sample.y
            } else if (sample.visibility < HIDDEN) {
                x = previous[index * 2]
                y = previous[index * 2 + 1]
            } else {
                val px = previous[index * 2]
                val py = previous[index * 2 + 1]
                val jump = hypot(sample.x - px, sample.y - py)
                val blend = if (jump > JUMP) FAST else alpha
                x = px + (sample.x - px) * blend
                y = py + (sample.y - py) * blend
            }
            next[index * 2] = x
            next[index * 2 + 1] = y
            out.add(PoseCoord(x, y, sample.visibility))
        }
        state = next
        return out
    }

    private companion object {
        const val HIDDEN = 0.35f
        const val JUMP = 0.20f
        const val FAST = 0.85f
    }
}
