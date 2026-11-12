package com.stb.bodymasks.pose

/**
 * Puntos del Pose Landmarker (33) más combinaciones útiles para el rig.
 * El índice coincide con el orden de MediaPipe. Los puntos virtuales no tienen índice.
 */
enum class PosePoint(val landmarkIndex: Int?) {
    NOSE(0),
    LEFT_EYE_INNER(1),
    LEFT_EYE(2),
    LEFT_EYE_OUTER(3),
    RIGHT_EYE_INNER(4),
    RIGHT_EYE(5),
    RIGHT_EYE_OUTER(6),
    LEFT_EAR(7),
    RIGHT_EAR(8),
    MOUTH_LEFT(9),
    MOUTH_RIGHT(10),
    LEFT_SHOULDER(11),
    RIGHT_SHOULDER(12),
    LEFT_ELBOW(13),
    RIGHT_ELBOW(14),
    LEFT_WRIST(15),
    RIGHT_WRIST(16),
    LEFT_PINKY(17),
    RIGHT_PINKY(18),
    LEFT_INDEX(19),
    RIGHT_INDEX(20),
    LEFT_THUMB(21),
    RIGHT_THUMB(22),
    LEFT_HIP(23),
    RIGHT_HIP(24),
    LEFT_KNEE(25),
    RIGHT_KNEE(26),
    LEFT_ANKLE(27),
    RIGHT_ANKLE(28),
    LEFT_HEEL(29),
    RIGHT_HEEL(30),
    LEFT_FOOT_INDEX(31),
    RIGHT_FOOT_INDEX(32),
    MID_SHOULDERS(null),
    MID_HIPS(null),
    MID_EARS(null),
    ;

    companion object {
        fun fromName(raw: String): PosePoint =
            entries.firstOrNull { it.name == raw }
                ?: throw IllegalArgumentException(raw)
    }
}

data class PoseCoord(val x: Float, val y: Float, val visibility: Float)

data class PoseFrame(
    val points: Map<PosePoint, PoseCoord>,
    val imageWidth: Int,
    val imageHeight: Int,
)

/** Segmentos que se dibujan solo en el modo de depuración. */
val DEBUG_BONES: List<Pair<PosePoint, PosePoint>> = listOf(
    PosePoint.LEFT_SHOULDER to PosePoint.RIGHT_SHOULDER,
    PosePoint.LEFT_SHOULDER to PosePoint.LEFT_ELBOW,
    PosePoint.LEFT_ELBOW to PosePoint.LEFT_WRIST,
    PosePoint.RIGHT_SHOULDER to PosePoint.RIGHT_ELBOW,
    PosePoint.RIGHT_ELBOW to PosePoint.RIGHT_WRIST,
    PosePoint.LEFT_SHOULDER to PosePoint.LEFT_HIP,
    PosePoint.RIGHT_SHOULDER to PosePoint.RIGHT_HIP,
    PosePoint.LEFT_HIP to PosePoint.RIGHT_HIP,
    PosePoint.LEFT_HIP to PosePoint.LEFT_KNEE,
    PosePoint.LEFT_KNEE to PosePoint.LEFT_ANKLE,
    PosePoint.RIGHT_HIP to PosePoint.RIGHT_KNEE,
    PosePoint.RIGHT_KNEE to PosePoint.RIGHT_ANKLE,
    PosePoint.NOSE to PosePoint.MID_SHOULDERS,
)

fun resolvePosePoints(landmarks: List<PoseCoord>): Map<PosePoint, PoseCoord>? {
    if (landmarks.size < 33) return null
    val map = HashMap<PosePoint, PoseCoord>(PosePoint.entries.size)
    for (point in PosePoint.entries) {
        val index = point.landmarkIndex ?: continue
        map[point] = landmarks[index]
    }
    fun mid(a: PosePoint, b: PosePoint): PoseCoord {
        val left = map.getValue(a)
        val right = map.getValue(b)
        return PoseCoord(
            x = (left.x + right.x) / 2f,
            y = (left.y + right.y) / 2f,
            visibility = minOf(left.visibility, right.visibility),
        )
    }
    map[PosePoint.MID_SHOULDERS] = mid(PosePoint.LEFT_SHOULDER, PosePoint.RIGHT_SHOULDER)
    map[PosePoint.MID_HIPS] = mid(PosePoint.LEFT_HIP, PosePoint.RIGHT_HIP)
    map[PosePoint.MID_EARS] = mid(PosePoint.LEFT_EAR, PosePoint.RIGHT_EAR)
    return map
}
