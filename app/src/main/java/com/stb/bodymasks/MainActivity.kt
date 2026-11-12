package com.stb.bodymasks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import com.stb.bodymasks.camera.CameraController
import com.stb.bodymasks.databinding.ActivityMainBinding
import com.stb.bodymasks.mask.LoadedMask
import com.stb.bodymasks.mask.MaskRepository
import com.stb.bodymasks.pose.AnalysisSize
import com.stb.bodymasks.pose.BODY_MASKS_PERF_TAG
import com.stb.bodymasks.pose.DelegateKind
import com.stb.bodymasks.pose.PoseConfig
import com.stb.bodymasks.pose.PoseEngine
import com.stb.bodymasks.pose.PoseFrame
import com.stb.bodymasks.pose.PoseModel
import com.stb.bodymasks.pose.PosePoint
import com.stb.bodymasks.pose.PREFS_VERSION
import com.stb.bodymasks.pose.bodyMasksPerfLine
import com.stb.bodymasks.pose.choiceAfterUpgrade
import com.stb.bodymasks.pose.loadReportLine
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), PoseEngine.Listener {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val cameraExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "camera-analysis").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val perfHandler = Handler(Looper.getMainLooper())
    private val perfTick = object : Runnable {
        override fun run() {
            if (!isDestroyed) logPerf()
            perfHandler.postDelayed(this, 1000L)
        }
    }

    private var masks: List<LoadedMask> = emptyList()
    private var maskIndex = 0
    private var mirrorEnabled = true
    private var debugEnabled = false
    private var model = PoseModel.LITE
    private var delegate = DelegateKind.GPU
    private var analysis = AnalysisSize.R320
    private var loaded = PoseConfig(PoseModel.LITE, DelegateKind.GPU, AnalysisSize.R320)
    private var modelReady = false
    private var askedPermission = false
    private var notice: String? = null
    private var poseEngine: PoseEngine? = null
    private var cameraController: CameraController? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        askedPermission = true
        if (granted) startPipeline() else showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insets = WindowInsetsControllerCompat(window, binding.root)
        insets.hide(WindowInsetsCompat.Type.systemBars())
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        binding.preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        binding.preview.scaleType = PreviewView.ScaleType.FIT_CENTER
        loadPrefs()
        applyMirrorLabel()
        refreshConfig()

        if (hasCameraPermission()) startPipeline() else showNeedPermission()
    }

    override fun onStart() {
        super.onStart()
        perfHandler.removeCallbacks(perfTick)
        perfHandler.post(perfTick)
        maybeStartCamera()
    }

    override fun onStop() {
        perfHandler.removeCallbacks(perfTick)
        cameraController?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        perfHandler.removeCallbacksAndMessages(null)
        cameraController?.stop()
        cameraController = null
        poseEngine?.close()
        poseEngine = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.messagePanel.visibility == View.VISIBLE) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    stepMask(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    stepMask(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_BUTTON_A,
                -> {
                    toggleMirror()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    toggleDebug()
                    return true
                }
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4, KeyEvent.KEYCODE_PROG_BLUE -> {
                    toggleModel()
                    return true
                }
                KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5, KeyEvent.KEYCODE_PROG_GREEN -> {
                    toggleDelegate()
                    return true
                }
                KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6, KeyEvent.KEYCODE_PROG_RED -> {
                    toggleResolution()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onReady(applied: PoseConfig, requested: PoseConfig, loadError: String?) {
        if (isDestroyed) return
        loaded = applied
        modelReady = true
        Log.i(BODY_MASKS_PERF_TAG, loadReportLine(currentConfig(), applied, loadError))
        notice = when {
            loadError != null && requested.model != applied.model -> getString(R.string.notice_model_fallback)
            loadError != null && requested.delegate != applied.delegate -> getString(R.string.notice_gpu_fallback)
            else -> null
        }
        binding.statusText.text = getString(R.string.hud_searching)
        refreshConfig()
        maybeStartCamera()
    }

    override fun onGpuGaveUp() {
        if (isDestroyed) return
        Log.i(BODY_MASKS_PERF_TAG, "load event=gpu_runtime_error fallback=CPU")
        notice = getString(R.string.notice_gpu_fallback)
        refreshConfig()
    }

    override fun onFatal(error: Throwable) {
        if (isDestroyed) return
        modelReady = false
        showPanel(getString(R.string.msg_model_error), getString(R.string.btn_retry)) {
            poseEngine?.close()
            poseEngine = null
            startPipeline()
        }
    }

    override fun onPose(frame: PoseFrame) {
        if (isDestroyed) return
        binding.overlay.setFrame(frame)
        binding.statusText.text = getString(
            if (frame.isTracking()) R.string.hud_tracking else R.string.hud_searching,
        )
    }

    private fun startPipeline() {
        if (!hasCameraPermission()) {
            showNeedPermission()
            return
        }
        hidePanel()
        binding.statusText.text = getString(R.string.hud_loading)
        if (masks.isEmpty()) {
            Thread({
                val loadedMasks = try {
                    MaskRepository(applicationContext).loadAll()
                } catch (error: Throwable) {
                    runOnUiThread {
                        if (!isDestroyed) {
                            showPanel(getString(R.string.msg_masks_error), getString(R.string.btn_retry)) {
                                startPipeline()
                            }
                        }
                    }
                    return@Thread
                }
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (loadedMasks.isEmpty()) {
                        showPanel(getString(R.string.msg_masks_error), getString(R.string.btn_retry)) {
                            masks = emptyList()
                            startPipeline()
                        }
                        return@runOnUiThread
                    }
                    masks = loadedMasks
                    selectInitialMask()
                    restartEngine()
                }
            }, "mask-load").start()
        } else {
            restartEngine()
        }
    }

    private fun restartEngine() {
        modelReady = false
        binding.statusText.text = getString(R.string.hud_loading)
        val engine = poseEngine ?: PoseEngine(this, this).also { poseEngine = it }
        engine.start(currentConfig())
    }

    private fun maybeStartCamera() {
        if (!modelReady || !hasCameraPermission()) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val controller = cameraController ?: CameraController(
            owner = this,
            previewView = binding.preview,
            analysisExecutor = cameraExecutor,
            analysisSize = { Size(analysis.width, analysis.height) },
            engineProvider = { poseEngine },
            onUnavailable = { kind ->
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    val message = if (kind == CameraController.Unavailable.NONE) {
                        R.string.msg_no_camera
                    } else {
                        R.string.msg_camera_error
                    }
                    showPanel(getString(message), getString(R.string.btn_retry)) {
                        startPipeline()
                    }
                }
            },
        ).also { cameraController = it }
        controller.start()
    }

    private fun currentConfig(): PoseConfig = PoseConfig(model, delegate, analysis)

    private fun selectInitialMask() {
        val saved = prefs.getString(KEY_MASK, null)
        val found = masks.indexOfFirst { it.definition.id == saved }
        maskIndex = if (found >= 0) found else 0
        applyMask()
    }

    private fun applyMask() {
        val mask = masks.getOrNull(maskIndex) ?: return
        binding.overlay.setMask(mask)
        binding.maskText.text = getString(R.string.hud_mask, mask.definition.name)
    }

    private fun stepMask(delta: Int) {
        if (masks.isEmpty()) return
        maskIndex = (maskIndex + delta).floorMod(masks.size)
        prefs.edit().putString(KEY_MASK, masks[maskIndex].definition.id).apply()
        applyMask()
    }

    private fun toggleMirror() {
        mirrorEnabled = !mirrorEnabled
        prefs.edit().putBoolean(KEY_MIRROR, mirrorEnabled).apply()
        binding.overlay.mirror = mirrorEnabled
        applyMirrorLabel()
    }

    private fun applyMirrorLabel() {
        binding.preview.scaleX = if (mirrorEnabled) -1f else 1f
        binding.mirrorText.text = getString(
            if (mirrorEnabled) R.string.hud_mirror_on else R.string.hud_mirror_off,
        )
    }

    private fun toggleDebug() {
        debugEnabled = !debugEnabled
        prefs.edit().putBoolean(KEY_DEBUG, debugEnabled).apply()
        binding.overlay.showDebug = debugEnabled
    }

    private fun toggleModel() {
        model = if (model == PoseModel.LITE) PoseModel.FULL else PoseModel.LITE
        saveDetectorPrefs()
        restartEngine()
        refreshConfig()
    }

    private fun toggleDelegate() {
        delegate = if (delegate == DelegateKind.CPU) DelegateKind.GPU else DelegateKind.CPU
        saveDetectorPrefs()
        restartEngine()
        refreshConfig()
    }

    private fun toggleResolution() {
        analysis = analysis.next()
        saveDetectorPrefs()
        refreshConfig()
        cameraController?.restart()
    }

    private fun refreshConfig() {
        val line = getString(
            R.string.hud_config,
            loaded.model.logName,
            loaded.delegate.logName,
            analysis.label,
        )
        binding.configText.text = notice?.let { "$it\n$line" } ?: line
    }

    private fun logPerf() {
        val sample = poseEngine?.takeSample()
        val fps = sample?.fps ?: 0
        if (sample != null && sample.frames > 0) {
            binding.fpsText.text = getString(R.string.hud_fps, fps)
        } else if (sample == null) {
            binding.fpsText.text = getString(R.string.hud_fps_unknown)
        } else {
            binding.fpsText.text = getString(R.string.hud_fps, 0)
        }
        Log.i(
            BODY_MASKS_PERF_TAG,
            bodyMasksPerfLine(
                fps = fps,
                inferMs = sample?.inferMs ?: 0,
                convertMs = sample?.convertMs ?: 0,
                drawMs = binding.overlay.lastDrawMs,
                frames = sample?.frames ?: 0,
                model = loaded.model.logName,
                requested = model.logName,
                delegate = loaded.delegate.logName,
                targetW = analysis.width,
                targetH = analysis.height,
                frameW = sample?.frameW ?: 0,
                frameH = sample?.frameH ?: 0,
                tracked = sample?.tracked ?: 0,
            ),
        )
    }

    private fun loadPrefs() {
        mirrorEnabled = prefs.getBoolean(KEY_MIRROR, true)
        debugEnabled = prefs.getBoolean(KEY_DEBUG, false)
        val choice = choiceAfterUpgrade(
            storedVersion = prefs.getInt(KEY_VERSION, 0),
            model = prefs.getString(KEY_MODEL, null),
            delegate = prefs.getString(KEY_DELEGATE, null),
            res = prefs.getString(KEY_RES, null),
        )
        model = choice.model
        delegate = choice.delegate
        analysis = choice.analysis
        loaded = currentConfig()
        if (prefs.getInt(KEY_VERSION, 0) < PREFS_VERSION) {
            saveDetectorPrefs()
        }
        binding.overlay.mirror = mirrorEnabled
        binding.overlay.showDebug = debugEnabled
    }

    /** Solo la elección del mando. Un respaldo de carga no pasa por aquí. */
    private fun saveDetectorPrefs() {
        prefs.edit()
            .putString(KEY_MODEL, model.logName)
            .putString(KEY_DELEGATE, delegate.logName.lowercase())
            .putString(KEY_RES, if (analysis == AnalysisSize.R320) "320" else "640")
            .putInt(KEY_VERSION, PREFS_VERSION)
            .apply()
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun showNeedPermission() {
        showPanel(getString(R.string.msg_need_permission), getString(R.string.btn_allow_camera)) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPermissionDenied() {
        val openSettings = askedPermission &&
            !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        if (openSettings) {
            showPanel(getString(R.string.msg_permission_denied), getString(R.string.btn_settings)) {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                )
                runCatching { startActivity(intent) }
            }
        } else {
            showPanel(getString(R.string.msg_permission_denied), getString(R.string.btn_retry)) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showPanel(message: String, action: String, onClick: () -> Unit) {
        binding.messagePanel.visibility = View.VISIBLE
        binding.messageText.text = message
        binding.actionButton.text = action
        binding.actionButton.setOnClickListener { onClick() }
        binding.actionButton.post { binding.actionButton.requestFocus() }
    }

    private fun hidePanel() {
        binding.messagePanel.visibility = View.GONE
    }

    private fun PoseFrame.isTracking(): Boolean {
        val keys = arrayOf(PosePoint.NOSE, PosePoint.LEFT_SHOULDER, PosePoint.RIGHT_SHOULDER)
        return keys.count { (points[it]?.visibility ?: 0f) >= 0.45f } >= 2
    }

    private fun Int.floorMod(size: Int): Int {
        val mod = this % size
        return if (mod < 0) mod + size else mod
    }

    private companion object {
        const val PREFS = "bodymasks"
        const val KEY_MASK = "mask"
        const val KEY_MIRROR = "mirror"
        const val KEY_DEBUG = "debug"
        const val KEY_MODEL = "model"
        const val KEY_DELEGATE = "delegate"
        const val KEY_RES = "res"
        const val KEY_VERSION = "prefs_version"
    }
}
