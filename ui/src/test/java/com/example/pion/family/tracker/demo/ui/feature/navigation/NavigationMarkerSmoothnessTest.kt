package com.example.pion.family.tracker.demo.ui.feature.navigation

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **feedback #2 (2026-08-26) — màn Dẫn đường phải dùng CHUNG bộ nội suy marker với màn Bản đồ.**
 *
 * Trước thay đổi này `NavigationMap` gắn thẳng `rememberUpdatedMarkerState(position = LatLng(...))`
 * vào mẫu THÔ, nên marker Minh/Lan nhảy một bước mỗi 2.5s (nhịp ghi của `MemberMovementSimulator`)
 * trong khi đúng những con người đó trượt mượt ở màn Bản đồ. Cả hai màn đều "chạy đúng", nên không
 * ca nào trong 121 test lúc đó đỏ — thứ hỏng là *chuyển động giữa hai mẫu*, và nó chỉ tồn tại lúc
 * chạy trên thiết bị.
 *
 * **Vì sao là test đọc mã nguồn:** cùng lý do `MapBlockerIsNotADialogTest` (LLM.md §11) — `:ui`
 * không có `compose-ui-test`, thân một `@Composable` là vùng không test nào với tới được. Bù lại,
 * phần TOÁN của phép nội suy đã có test hành vi thật ở `core/motion/MarkerInterpolationTest` +
 * `designsystem/component/AnimatedMarkerPositionsThresholdTest`; ca ở đây chỉ khoá một điều mà hai
 * file kia không thể biết: **màn Dẫn đường có thật sự gọi tới bộ nội suy đó hay không.**
 *
 * Chép một bản nội suy riêng cho màn này cũng sẽ làm ca thứ nhất xanh — nhưng ca thứ hai thì không,
 * vì bản chép nào rồi cũng phải nối `rememberUpdatedMarkerState` vào một `LatLng` dựng tại chỗ từ
 * mẫu thô, đúng chuỗi bị cấm bên dưới.
 */
class NavigationMarkerSmoothnessTest {

    @Test
    fun `the navigation map interpolates through the shared marker animator`() {
        assertTrue(
            "NavigationMap.kt phải gọi rememberAnimatedMarkerPositions( — cùng bộ nội suy mà " +
                "MemberMarkers/FamilyTrackerMap dùng, không phải một bản sao của riêng màn này",
            navigationMapCode().any { it.contains("rememberAnimatedMarkerPositions(") },
        )
    }

    @Test
    fun `no marker is bound straight to a raw sample coordinate`() {
        val rawBindings = navigationMapCode().filter { it.contains("rememberUpdatedMarkerState(position = LatLng(") }

        assertTrue(
            "marker phải đọc toạ độ ĐÃ NỘI SUY (`animated[...]`), không dựng LatLng thẳng từ mẫu " +
                "thô trong lời gọi rememberUpdatedMarkerState — đó đúng là chuyển động giật mà " +
                "feedback #2 sinh ra để bỏ đi. Tìm thấy: $rawBindings",
            rawBindings.isEmpty(),
        )
    }

    /**
     * Đoạn nối nét đứt (feedback #4) phải bám vào toạ độ ĐANG HIỂN THỊ của marker, không phải mẫu
     * thô: dùng mẫu thô thì đầu đoạn nối nhảy một nhịp mỗi 2.5s trong khi marker trượt mượt — tức
     * là mang chính cảm giác giật của feedback #2 quay lại bằng cửa khác.
     */
    @Test
    fun `the dashed connectors are anchored to the interpolated marker coordinates`() {
        val lines = navigationMapCode()
        val animatedLatLngs = lines.filter { it.contains("animated[") }
        val connectors = lines.filter { it.contains("NavigationConnector(points = segment(") }

        assertTrue("không tìm thấy toạ độ nội suy (animated[...]) trong NavigationMap.kt", animatedLatLngs.isNotEmpty())
        assertTrue("không tìm thấy đoạn nối NavigationConnector(points = segment(", connectors.isNotEmpty())
        assertTrue(
            "mỗi đoạn nối phải có ít nhất một đầu là selfLatLng/targetLatLng (dựng từ animated[...]): $connectors",
            connectors.all { it.contains("selfLatLng") || it.contains("targetLatLng") },
        )
    }

    /** Cùng mẫu `MapBlockerIsNotADialogTest.codeLinesOf` — hợp đồng nằm ở MÃ, không ở văn xuôi. */
    private fun navigationMapCode(): List<String> =
        File(uiSrcMain(), "java/com/example/pion/family/tracker/demo/ui/feature/navigation/component/NavigationMap.kt")
            .readLines()
            .map { it.trim() }
            .filterNot { it.isEmpty() || it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }

    /** Gradle chạy test với working dir là thư mục module (`ui/`) — cùng mẫu
     * `CoroutineSafetyArchitectureTest.findUiSrcMain()`. */
    private fun uiSrcMain(): File {
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
