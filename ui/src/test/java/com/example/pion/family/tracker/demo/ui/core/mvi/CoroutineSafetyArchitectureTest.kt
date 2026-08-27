package com.example.pion.family.tracker.demo.ui.core.mvi

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture test — `LLM.md` §4, §13; `docs/android-mvi-best-practices.md` §9/§10.
 *
 * Fails if any `.kt` file under `ui/src/main`, other than `MviViewModel.kt` itself (the one
 * file allowed to touch `viewModelScope` directly), contains a raw coroutine-launch bypass.
 * Phase-04 shipped `.launchIn(viewModelScope)` in `MapViewModel.init` past a tester whose grep
 * matched only the string `viewModelScope.launch` — see
 * `plans/260821-1113-geofence-zone-and-history-tracking/reports/fix-phase-04-report.md`. This
 * test exists so the next bypass, spelled any of the ways below, fails the build instead of
 * shipping.
 */
class CoroutineSafetyArchitectureTest {

    private val forbidden = listOf(
        "viewModelScope.launch",
        "launchIn(viewModelScope)",
        "GlobalScope",
        "runBlocking",
    )

    private val exemptFileNames = setOf("MviViewModel.kt")

    @Test
    fun `no file under ui src main bypasses launchSafely or collectSafely`() {
        val sourceRoot = findUiSrcMain()

        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in exemptFileNames }
            .flatMap { file -> violationsIn(file, sourceRoot) }
            .toList()

        assertTrue(
            "Coroutine safety bypass found outside MviViewModel.kt — route the Flow/coroutine " +
                "through launchSafely/collectSafely instead:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun violationsIn(file: File, sourceRoot: File): List<String> =
        file.readLines().mapIndexedNotNull { index, rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@mapIndexedNotNull null
            val hit = forbidden.firstOrNull { line.contains(it) } ?: return@mapIndexedNotNull null
            "${file.relativeTo(sourceRoot)}:${index + 1} contains `$hit`"
        }

    /** Gradle's test working dir is the module dir (`ui/`), but walk upward defensively in case
     * that ever changes — this is a demo project, not worth a build-config dependency for it. */
    private fun findUiSrcMain(): File {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir = File(workingDir)
        repeat(5) {
            val candidate = File(dir, "src/main")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate ui/src/main from working dir $workingDir")
    }
}
