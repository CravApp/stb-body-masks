package com.stb.bodymasks.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseConfigTest {
    @Test
    fun upgradeResetsToLiteGpu320() {
        val choice = choiceAfterUpgrade(0, "full", "cpu", "640")
        assertEquals(PoseModel.LITE, choice.model)
        assertEquals(DelegateKind.GPU, choice.delegate)
        assertEquals(AnalysisSize.R320, choice.analysis)
    }

    @Test
    fun savedChoiceSurvivesAfterTheReset() {
        val choice = choiceAfterUpgrade(PREFS_VERSION, "full", "cpu", "640")
        assertEquals(PoseModel.FULL, choice.model)
        assertEquals(DelegateKind.CPU, choice.delegate)
        assertEquals(AnalysisSize.R640, choice.analysis)
    }

    @Test
    fun liteGpuTriesFullAndCpuBeforeGivingUp() {
        val attempts = creationAttempts(PoseConfig(PoseModel.LITE, DelegateKind.GPU, AnalysisSize.R320))
        assertEquals(
            listOf(
                PoseConfig(PoseModel.LITE, DelegateKind.GPU, AnalysisSize.R320),
                PoseConfig(PoseModel.FULL, DelegateKind.GPU, AnalysisSize.R320),
                PoseConfig(PoseModel.LITE, DelegateKind.CPU, AnalysisSize.R320),
                PoseConfig(PoseModel.FULL, DelegateKind.CPU, AnalysisSize.R320),
            ),
            attempts,
        )
    }

    @Test
    fun fullCpuDoesNotInventAFallback() {
        val requested = PoseConfig(PoseModel.FULL, DelegateKind.CPU, AnalysisSize.R640)
        assertEquals(listOf(requested), creationAttempts(requested))
    }

    @Test
    fun perfLineNamesRequestedAndLoaded() {
        val line = bodyMasksPerfLine(
            fps = 14,
            inferMs = 40,
            convertMs = 3,
            drawMs = 2,
            frames = 14,
            model = "lite",
            requested = "full",
            delegate = "GPU",
            targetW = 320,
            targetH = 240,
            frameW = 320,
            frameH = 240,
            tracked = 1,
        )
        assertTrue(line.contains("fps=14"))
        assertTrue(line.contains("inferMs=40"))
        assertTrue(line.contains("convertMs=3"))
        assertTrue(line.contains("drawMs=2"))
        assertTrue(line.contains("model=lite"))
        assertTrue(line.contains("requested=full"))
        assertTrue(line.contains("delegate=GPU"))
        assertTrue(line.contains("target=320x240"))
        val report = loadReportLine(
            PoseConfig(PoseModel.LITE, DelegateKind.GPU, AnalysisSize.R320),
            PoseConfig(PoseModel.FULL, DelegateKind.CPU, AnalysisSize.R320),
            "lite/GPU: boom",
        )
        assertEquals(
            "load requested=lite/GPU loaded=full/CPU error=lite/GPU: boom",
            report,
        )
    }
}
