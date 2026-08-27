package com.example.pion.family.tracker.demo.ui.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **feedback #1 (2026-08-26) — hai điều khiển nổi không được cùng neo ở đáy khung bản đồ.**
 *
 * Lỗi đã xảy ra thật: `TrackingToggle` neo `BottomEnd` và là một `Card` chứa cả nhãn "Theo dõi gia
 * đình" lẫn `Switch`, nên nó rộng quá nửa màn; `NavigateToMemberButton` neo `BottomCenter` nằm gọn
 * dưới nó. Chọn Minh hoặc Lan là hai nút chồng lên nhau và nút "Chỉ đường" gần như không bấm được.
 * Không một test nào trong 121 ca lúc đó đỏ, vì cả hai vẫn được compose đúng — thứ hỏng là *chỗ
 * đứng*, và chỗ đứng của một composable là dữ liệu chỉ tồn tại lúc chạy.
 *
 * **Vì sao lại là test ĐỌC MÃ NGUỒN:** `:ui` không có `androidTest`/`compose-ui-test`, nên bound
 * thật của một composable là vùng không test nào với tới được (LLM.md §11, cùng lý do tồn tại của
 * `MapBlockerIsNotADialogTest` và `RoutingAttributionContractTest`). Đây là giải pháp cho hạn chế
 * hạ tầng — nếu ngày nào `:ui` có `compose-ui-test`, thay bằng ca đo bound thật rồi xoá file này.
 *
 * Ca dưới đây KHÔNG khoá "nút phải ở đúng góc phải trên". Nó khoá thứ rẻ hơn và bền hơn: **đáy
 * khung bản đồ chỉ được có MỘT chủ**. Ai muốn thêm một điều khiển nổi thứ ba sẽ phải đọc dòng này
 * trước, thay vì phát hiện ra khi người dùng báo lỗi.
 */
class FloatingControlsPlacementTest {

    @Test
    fun `only one floating control is anchored to the bottom of the map`() {
        val bottomAnchored = mapScreenCode().filter { it.contains("Alignment.Bottom") }

        assertEquals(
            "Đáy khung bản đồ chỉ được có MỘT điều khiển nổi (công tắc theo dõi). Thêm cái thứ hai " +
                "vào đó là dựng lại đúng lỗi chồng nút của feedback #1. Tìm thấy: $bottomAnchored",
            1,
            bottomAnchored.size,
        )
    }

    @Test
    fun `the navigate button lives in the top-anchored column, above anything bottom-anchored`() {
        val lines = mapScreenCode()
        val topColumn = lines.indexOfFirst { it.contains("Alignment.TopStart") }
        val navigate = lines.indexOfFirst { it.contains("NavigateToMemberButton(") }
        val bottomAnchor = lines.indexOfFirst { it.contains("Alignment.Bottom") }

        assertTrue("không tìm thấy Column neo TopStart", topColumn >= 0)
        assertTrue("không tìm thấy chỗ gọi NavigateToMemberButton(", navigate >= 0)
        assertTrue("không tìm thấy điều khiển nào neo ở đáy", bottomAnchor >= 0)
        assertTrue(
            "nút \"Chỉ đường\" phải nằm TRONG Column neo ở trên và trước mọi thứ neo ở đáy — " +
                "topColumn=$topColumn navigate=$navigate bottomAnchor=$bottomAnchor",
            topColumn < navigate && navigate < bottomAnchor,
        )
    }

    /**
     * Nút nằm trong một `Column` `fillMaxWidth()` nên mặc định nó dạt về mép TRÁI, đè lên chỗ của
     * `PermissionBanner` ngay bên dưới thay vì đứng ở góc phải như yêu cầu. `Alignment.End` là thứ
     * duy nhất đẩy nó sang phải, và mất nó thì không có gì đỏ ngoài mắt người dùng.
     */
    @Test
    fun `the navigate button is pushed to the end of that column`() {
        val lines = mapScreenCode()
        val navigate = lines.indexOfFirst { it.contains("NavigateToMemberButton(") }

        assertTrue("không tìm thấy chỗ gọi NavigateToMemberButton(", navigate >= 0)
        assertTrue(
            "nút \"Chỉ đường\" phải có Modifier.align(Alignment.End) — không thì nó dạt về mép trái " +
                "và đè lên banner quyền",
            lines.drop(navigate).take(3).any { it.contains("Alignment.End") },
        )
    }

    /** Cùng mẫu `MapBlockerIsNotADialogTest.codeLinesOf` — hợp đồng nằm ở MÃ, không ở văn xuôi. */
    private fun mapScreenCode(): List<String> =
        File(uiSrcMain(), "java/com/example/pion/family/tracker/demo/ui/feature/map/MapScreen.kt")
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
