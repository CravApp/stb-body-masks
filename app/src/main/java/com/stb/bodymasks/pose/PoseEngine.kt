package com.stb.bodymasks.pose

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pose Landmarker lite en CPU y LIVE_STREAM.
 * El delegado GPU de MediaPipe es inestable en el Mali-G31 del decodificador, así que no se usa.
 * Solo hay un fotograma en vuelo para no llenar la RAM de 2 GB.
 */
class PoseEngine(
    context: Context,
    listener: Listener,
) {
    interface Listener {
        fun onReady()
        fun onFatal(error: Throwable)
        fun onResult(frame: PoseFrame, fps: Int?)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val smoother = LandmarkSmoother()
    private val lock = Any()

    @Volatile
    private var listenerRef: Listener? = listener

    @Volatile
    private var landmarker: PoseLandmarker? = null

    @Volatile
    private var closed = false

    private val inFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val inFlightSince = java.util.concurrent.atomic.AtomicLong(0L)
    private var lastTimestampMs = Long.MIN_VALUE
    private var fpsFrames = 0
    private var fpsWindowStart = 0L

    fun start() {
        Thread({
            try {
                val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET)
                            .setDelegate(Delegate.CPU)
                            .build(),
                    )
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setOutputSegmentationMasks(false)
                    .setResultListener { result, image -> deliver(result.landmarks().firstOrNull(), image) }
                    .setErrorListener { error ->
                        inFlight.set(false)
                        Log.e(TAG, "Fallo en la detección de pose", error)
                    }
                    .build()
                val created = PoseLandmarker.createFromOptions(appContext, options)
                synchronized(lock) {
                    if (closed) {
                        created.close()
                        return@Thread
                    }
                    landmarker = created
                }
                Log.i(TAG, "Pose Landmarker listo (CPU, lite, armeabi-v7a)")
                mainHandler.post { listenerRef?.onReady() }
            } catch (error: Throwable) {
                Log.e(TAG, "No se pudo crear el Pose Landmarker", error)
                mainHandler.post { listenerRef?.onFatal(error) }
            }
        }, "pose-init").start()
    }

    fun canAcceptFrame(): Boolean {
        if (closed || landmarker == null) return false
        if (inFlight.get()) {
            val elapsed = SystemClock.elapsedRealtime() - inFlightSince.get()
            if (elapsed > STUCK_MS) {
                inFlight.set(false)
            } else {
                return false
            }
        }
        return true
    }

    fun submit(bitmap: Bitmap, timestampNs: Long) {
        if (!canAcceptFrame()) return
        val timestampMs = nextTimestamp(timestampNs / 1_000_000L)
        val image = BitmapImageBuilder(bitmap).build()
        try {
            synchronized(lock) {
                val engine = landmarker
                if (engine == null || closed) {
                    image.close()
                    return
                }
                inFlight.set(true)
                inFlightSince.set(SystemClock.elapsedRealtime())
                engine.detectAsync(image, timestampMs)
            }
        } catch (error: Throwable) {
            inFlight.set(false)
            image.close()
            Log.e(TAG, "detectAsync rechazó el fotograma", error)
        }
    }

    fun close() {
        listenerRef = null
        synchronized(lock) {
            closed = true
            landmarker?.close()
            landmarker = null
        }
        smoother.reset()
    }

    private fun deliver(landmarks: List<NormalizedLandmark>?, image: MPImage) {
        val width = image.width
        val height = image.height
        val raw = if (landmarks != null && landmarks.size >= 33) {
            landmarks.map { landmark -> PoseCoord(landmark.x(), landmark.y(), score(landmark)) }
        } else {
            null
        }
        try {
            image.close()
        } catch (error: Throwable) {
            Log.w(TAG, "No se pudo cerrar la imagen de entrada", error)
        } finally {
            inFlight.set(false)
        }
        val frame = if (raw == null) {
            smoother.reset()
            PoseFrame(emptyMap(), width, height)
        } else {
            val points = resolvePosePoints(smoother.apply(raw)) ?: emptyMap()
            PoseFrame(points, width, height)
        }
        val fps = tickFps()
        mainHandler.post {
            listenerRef?.onResult(frame, fps)
        }
    }

    private fun score(landmark: NormalizedLandmark): Float {
        val visibility = landmark.visibility().orElse(1f)
        val presence = landmark.presence().orElse(1f)
        return min(visibility, presence)
    }

    private fun nextTimestamp(candidate: Long): Long {
        val timestamp = if (candidate > lastTimestampMs) candidate else lastTimestampMs + 1L
        lastTimestampMs = timestamp
        return timestamp
    }

    private fun tickFps(): Int? {
        val now = SystemClock.elapsedRealtime()
        if (fpsWindowStart == 0L) fpsWindowStart = now
        fpsFrames += 1
        val elapsed = now - fpsWindowStart
        if (elapsed < 500L) return null
        val fps = (fpsFrames * 1000f / elapsed).roundToInt()
        fpsFrames = 0
        fpsWindowStart = now
        return fps
    }

    companion object {
        const val MODEL_ASSET = "pose_landmarker_lite.task"
        private const val TAG = "BodyMasks"
        private const val STUCK_MS = 2000L
    }
}
