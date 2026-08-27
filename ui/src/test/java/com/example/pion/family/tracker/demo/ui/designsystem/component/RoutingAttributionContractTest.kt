package com.example.pion.family.tracker.demo.ui.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Khoá **ba trạng thái pháp lý** của `RoutingAttribution` — `docs/routing-and-map-attribution.md`
 * §3 mục 1 và câu "Chỉ hiện credit OSM khi đang thật sự hiển thị dữ liệu OSM".
 *
 * **Vì sao là test ĐỌC MÃ NGUỒN, không phải test Compose** (thêm ở review phase-05): module `:ui`
 * **không có** hạ tầng test Compose nào — không `ui/src/androidTest`, không `compose-ui-test` trong
 * `ui/build.gradle.kts` — nên thân của một `@Composable` là vùng KHÔNG test nào với tới được. Đã đo
 * bằng mutation: đổi nhánh `isFallbackStraightLine` thành
 * `stringResource(R.string.route_attribution_route, "OpenStreetMap contributors")` — tức HIỆN CREDIT
 * OSM ĐÚNG LÚC đang chạy tầng `SyntheticPath`, vi phạm thẳng X5 và §3 — thì **314/314 test vẫn
 * XANH**. `MapViewModelTest` chỉ khoá được ĐẦU VÀO (`attributionLines`/`isFallbackRoute`), không
 * khoá được quyết định vẽ. Ảnh chụp màn hình bắt được, nhưng ảnh chụp không chạy trong CI.
 *
 * Ba dòng `when` dưới đây LÀ hợp đồng pháp lý; đây là nơi rẻ nhất còn lại để ghim chúng. Nếu ngày
 * nào `:ui` có `compose-ui-test`, hãy thay ca này bằng ba ca dựng thật rồi xoá file này — nó là
 * giải pháp cho một hạn chế của hạ tầng, không phải một mẫu đáng nhân rộng.
 */
class RoutingAttributionContractTest {

    private val routeString = "route_attribution_route"
    private val fallbackString = "route_attribution_fallback"

    @Test
    fun `the OSM credit string is only reachable when attribution lines exist`() {
        val branch = codeLines().single { it.contains("attributionLines.isNotEmpty() ->") }

        assertTrue("nhánh có attribution phải hiện chuỗi credit: $branch", branch.contains(routeString))
        assertTrue("nhánh có attribution không được hiện nhãn ước tính: $branch", !branch.contains(fallbackString))
    }

    /**
     * Nhánh tầng 3: trên màn KHÔNG có một byte dữ liệu OSM nào, nên hiện credit lúc này là **ghi sai
     * nguồn** (`decisions.md` §C2 D5, PRD delta X5) — nhánh này chỉ được phép dùng nhãn ước tính.
     */
    @Test
    fun `the fallback branch never shows the OSM credit string`() {
        val branch = codeLines().single { it.contains("isFallbackStraightLine ->") }

        assertTrue("nhánh tầng 3 phải hiện nhãn ước tính: $branch", branch.contains(fallbackString))
        assertTrue("nhánh tầng 3 KHÔNG được hiện credit OSM: $branch", !branch.contains(routeString))
    }

    /** Trạng thái thứ ba (chưa có nguồn nào) = **ẩn hẳn**, không vẽ `Text` rỗng (FR-4/S4 phase-05). */
    @Test
    fun `there is a third state that renders nothing`() {
        assertEquals(
            "phải còn đúng một nhánh `else -> null` — ba trạng thái, không có trạng thái thứ tư",
            1,
            codeLines().count { it.contains("else -> null") },
        )
    }

    /**
     * `engineId` chỉ để log/chẩn đoán. Ghép chuỗi ghi công từ nó nghĩa là đổi tên engine trong log
     * sẽ âm thầm đổi nội dung PHÁP LÝ hiện trên màn hình — `docs/routing-and-map-attribution.md` §3
     * "Credit lấy từ nhà cung cấp, không tự viết".
     */
    @Test
    fun `attribution is never built from engineId`() {
        val violations = codeLines().filter { it.contains("engineId") }

        assertTrue("credit phải lấy nguyên văn từ Directions.attribution:\n$violations", violations.isEmpty())
    }

    /**
     * Không ai được vẽ hai chuỗi này ngoài composable trên — một chỗ dùng thứ hai là một bản sao
     * của luật ba trạng thái, và bản sao thứ hai là chỗ luật đi chệch.
     */
    @Test
    fun `no other file in ui renders the attribution strings`() {
        val others = uiSrcMain().walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "RoutingAttribution.kt" }
            .filter { file -> file.readText().let { it.contains(routeString) || it.contains(fallbackString) } }
            .map { it.name }
            .toList()

        assertTrue("chỉ RoutingAttribution.kt được dựng chuỗi ghi công, thấy thêm: $others", others.isEmpty())
    }

    /** Bỏ dòng trắng, dòng chú thích KDoc (`*`) và `//` — hợp đồng nằm ở MÃ, không ở văn xuôi. */
    private fun codeLines(): List<String> =
        File(uiSrcMain(), "java/com/example/pion/family/tracker/demo/ui/designsystem/component/RoutingAttribution.kt")
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
