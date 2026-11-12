package com.stb.bodymasks.pose

enum class PoseModel(val assetName: String, val logName: String) {
    LITE("pose_landmarker_lite.task", "lite"),
    FULL("pose_landmarker_full.task", "full"),
}

enum class DelegateKind(val logName: String) {
    CPU("CPU"),
    GPU("GPU"),
}

enum class AnalysisSize(val width: Int, val height: Int, val label: String) {
    R640(640, 480, "640×480"),
    R320(320, 240, "320×240"),
    ;

    fun next(): AnalysisSize = if (this == R640) R320 else R640
}

data class PoseConfig(
    val model: PoseModel,
    val delegate: DelegateKind,
    val analysis: AnalysisSize,
)

/** Versión de preferencias. La 1 fuerza lite, GPU y 320×240 una sola vez. */
const val PREFS_VERSION = 1

data class StoredPoseChoice(
    val model: PoseModel,
    val delegate: DelegateKind,
    val analysis: AnalysisSize,
)

/**
 * Si la versión guardada es anterior, no se leen modelo, delegado ni resolución:
 * un fallo de carga no debe dejar grabado el respaldo, y la actualización
 * vuelve al ajuste que rinde en el decodificador.
 */
fun choiceAfterUpgrade(
    storedVersion: Int,
    model: String?,
    delegate: String?,
    res: String?,
): StoredPoseChoice {
    if (storedVersion < PREFS_VERSION) {
        return StoredPoseChoice(PoseModel.LITE, DelegateKind.GPU, AnalysisSize.R320)
    }
    return StoredPoseChoice(
        model = if (model == "full") PoseModel.FULL else PoseModel.LITE,
        delegate = if (delegate == "cpu") DelegateKind.CPU else DelegateKind.GPU,
        analysis = if (res == "640") AnalysisSize.R640 else AnalysisSize.R320,
    )
}

/**
 * Si lite no arranca se prueba el full con el mismo delegado. La GPU va antes
 * que la CPU: en este Mali-G31 la GPU sí corre y es más rápida.
 */
fun creationAttempts(requested: PoseConfig): List<PoseConfig> {
    val out = LinkedHashSet<PoseConfig>()
    out.add(requested)
    if (requested.model == PoseModel.LITE) {
        out.add(requested.copy(model = PoseModel.FULL))
    }
    if (requested.delegate == DelegateKind.GPU) {
        out.add(requested.copy(delegate = DelegateKind.CPU))
        if (requested.model == PoseModel.LITE) {
            out.add(requested.copy(model = PoseModel.FULL, delegate = DelegateKind.CPU))
        }
    }
    return out.toList()
}

fun loadFailureLine(config: PoseConfig, error: Throwable): String {
    val detail = error.message?.lineSequence()?.firstOrNull()?.take(180)?.ifBlank { null }
        ?: error.javaClass.simpleName
    return "${config.model.logName}/${config.delegate.logName}: ${error.javaClass.simpleName}: $detail"
}

fun loadReportLine(requested: PoseConfig, loaded: PoseConfig, error: String?): String =
    "load requested=${requested.model.logName}/${requested.delegate.logName} " +
        "loaded=${loaded.model.logName}/${loaded.delegate.logName} error=${error ?: "none"}"

fun bodyMasksPerfLine(
    fps: Int,
    inferMs: Int,
    convertMs: Int,
    drawMs: Int,
    frames: Int,
    model: String,
    requested: String,
    delegate: String,
    targetW: Int,
    targetH: Int,
    frameW: Int,
    frameH: Int,
    tracked: Int,
): String =
    "fps=$fps inferMs=$inferMs convertMs=$convertMs drawMs=$drawMs frames=$frames " +
        "model=$model requested=$requested delegate=$delegate " +
        "target=${targetW}x$targetH frame=${frameW}x$frameH tracked=$tracked"

const val BODY_MASKS_PERF_TAG = "BodyMasksPerf"
