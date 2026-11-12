package com.stb.bodymasks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import com.stb.bodymasks.pose.PoseEngine
import com.stb.bodymasks.pose.PoseFrame
import com.stb.bodymasks.pose.PosePoint
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), PoseEngine.Listener {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val cameraExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "camera-analysis").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    private var masks: List<LoadedMask> = emptyList()
    private var maskIndex = 0
    private var mirrorEnabled = true
    private var debugEnabled = false
    private var modelReady = false
    private var askedPermission = false
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
        mirrorEnabled = prefs.getBoolean(KEY_MIRROR, true)
        debugEnabled = prefs.getBoolean(KEY_DEBUG, false)
        binding.overlay.mirror = mirrorEnabled
        binding.overlay.showDebug = debugEnabled
        applyMirrorLabel()

        if (hasCameraPermission()) startPipeline() else showNeedPermission()
    }

    override fun onStart() {
        super.onStart()
        maybeStartCamera()
    }

    override fun onStop() {
        cameraController?.stop()
        super.onStop()
    }

    override fun onDestroy() {
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
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onReady() {
        if (isDestroyed) return
        modelReady = true
        binding.statusText.text = getString(R.string.hud_searching)
        maybeStartCamera()
    }

    override fun onFatal(error: Throwable) {
        if (isDestroyed) return
        showPanel(getString(R.string.msg_model_error), getString(R.string.btn_retry)) {
            poseEngine?.close()
            poseEngine = null
            modelReady = false
            startPipeline()
        }
    }

    override fun onResult(frame: PoseFrame, fps: Int?) {
        if (isDestroyed) return
        binding.overlay.setFrame(frame)
        binding.statusText.text = getString(
            if (frame.isTracking()) R.string.hud_tracking else R.string.hud_searching,
        )
        if (fps != null) binding.fpsText.text = getString(R.string.hud_fps, fps)
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
                val loaded = try {
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
                    if (loaded.isEmpty()) {
                        showPanel(getString(R.string.msg_masks_error), getString(R.string.btn_retry)) {
                            masks = emptyList()
                            startPipeline()
                        }
                        return@runOnUiThread
                    }
                    masks = loaded
                    selectInitialMask()
                    ensureEngine()
                }
            }, "mask-load").start()
        } else {
            ensureEngine()
        }
    }

    private fun ensureEngine() {
        if (poseEngine == null) {
            modelReady = false
            poseEngine = PoseEngine(this, this).also { it.start() }
        } else if (modelReady) {
            maybeStartCamera()
        }
    }

    private fun maybeStartCamera() {
        if (!modelReady || !hasCameraPermission()) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val controller = cameraController ?: CameraController(
            owner = this,
            previewView = binding.preview,
            analysisExecutor = cameraExecutor,
            onFrame = { bitmap, timestamp ->
                val engine = poseEngine
                if (engine != null && engine.canAcceptFrame()) {
                    engine.submit(bitmap, timestamp)
                }
            },
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
    }
}
