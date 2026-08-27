package com.example.pion.family.tracker.demo.ui.feature.map

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * phase-07 (US-47, D8) — khoá quyết định **2026-08-25**: cơ chế chặn màn Bản đồ là một **lớp phủ
 * trong nội dung**, KHÔNG phải `Dialog`/`AlertDialog` (phase doc Key Insight #7, Risk #1).
 *
 * `Dialog` của Compose dựng một **window** riêng nuốt mọi chạm, nên thanh tab bên dưới không bấm
 * được ⇒ người mở app lúc đang ngoại tuyến kẹt ở màn Bản đồ. Đó là vỡ thẳng AC "Zone, Lịch sử …
 * vẫn dùng bình thường" của US-47 và trúng đúng tiêu chí KHÔNG ĐẠT "cả app bị khoá cứng" của
 * UAT-04. Đổi về `AlertDialog` trông "đúng hơn" với người đọc code lần đầu — đó chính là lý do
 * lệnh cấm cần một test chứ không phải một dòng chú thích.
 *
 * **Vì sao là test ĐỌC MÃ NGUỒN:** `:ui` không có `androidTest`, không có `compose-ui-test`, nên
 * thân của một `@Composable` là vùng không test nào với tới được — đã đo bằng mutation ở review
 * phase-05 (hai đột biến vi phạm pháp lý sống sót với 314/314 xanh, xem
 * `RoutingAttributionContractTest`). Ba cơ chế của lớp phủ dưới đây, mỗi cái khoá một bước của
 * QA-SRM-13, đều nằm trong vùng đó. Nếu ngày nào `:ui` có `compose-ui-test`, thay bằng ca dựng
 * thật rồi xoá file này — nó là giải pháp cho hạn chế hạ tầng, không phải mẫu đáng nhân rộng.
 */
class MapBlockerIsNotADialogTest {

    private val bannedDialogApis = listOf("AlertDialog", "DialogProperties", "androidx.compose.ui.window.Dialog")

    /**
     * Quét **mã**, không quét văn xuôi. `NoInternetOverlay.kt` nhắc `AlertDialog` ba lần trong KDoc
     * để ghi lại *vì sao* phương án đó bị bác — cấm nhắc tới nghĩa là ai đó sẽ xoá lời giải thích
     * cho test xanh, tức là test ăn mất chính tài liệu nó tồn tại để bảo vệ. Lệnh cấm là cấm
     * **dùng**.
     */
    @Test
    fun `no file in the map feature builds a Dialog`() {
        val violations = mapFeatureFiles().flatMap { file ->
            codeLinesOf(file).mapNotNull { line ->
                val hit = bannedDialogApis.firstOrNull { line.contains(it) } ?: return@mapNotNull null
                "${file.name}: `$hit` trong `$line`"
            }
        }

        assertTrue(
            "Cơ chế chặn màn Bản đồ phải là lớp phủ trong nội dung, không phải window Dialog — " +
                "Dialog nuốt chạm của thanh tab và khoá cứng cả app (UAT-04). Tìm thấy:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /**
     * QA-SRM-13 bước 3. Thiếu dòng này thì Back rời màn Bản đồ, và người dùng quay lại nhìn đúng
     * cái bản đồ giả-vờ-đang-chạy mà US-47 sinh ra để chặn.
     */
    @Test
    fun `the overlay blocks the back button`() {
        assertTrue(
            "lớp phủ phải có BackHandler(enabled = true) — đây là thứ thay cho " +
                "DialogProperties(dismissOnBackPress = false) của phương án đã bị bác",
            overlayCode().any { it.contains("BackHandler(enabled = true)") },
        )
    }

    /**
     * QA-SRM-13 bước 4. Thiếu `.clickable{}` trên scrim thì chạm xuyên qua được: nhấn giữ vẫn mở
     * được trình sửa zone qua một bản đồ đang bị chặn.
     */
    @Test
    fun `the scrim swallows touches meant for the map`() {
        val scrim = overlayCode().singleOrNull { it.contains(".clickable(") }

        assertTrue("scrim phải bắt chạm bằng .clickable{}", scrim != null)
        assertTrue(
            "scrim không được có ripple/nảy — indication = null: $scrim",
            scrim!!.contains("indication = null"),
        )
    }

    /**
     * FR-4. Lớp phủ đóng CHỈ khi `hasInternet` đổi. Một nút "Đóng"/"Thử lại" biến nó thành thứ bỏ
     * qua được, và ca demo mà D8 sinh ra để chặn quay lại nguyên vẹn.
     */
    @Test
    fun `the overlay offers no way to dismiss it by hand`() {
        val buttons = overlayCode().filter { line ->
            listOf("Button(", "TextButton(", "IconButton(", "onClick =").any { line.contains(it) }
        }

        assertTrue("lớp phủ KHÔNG được có nút nào: $buttons", buttons.isEmpty())
    }

    /**
     * **S14 — tiêu chí mua được bằng việc bỏ `AlertDialog`.** Lớp phủ phải nằm TRONG
     * `Box(Modifier.weight(1f))` của khung bản đồ, tức giữa dòng mở `Box` và `RoutingAttribution`
     * (phần tử anh em nằm sau khi `Box` đóng). Nâng nó lên một bậc — ra ngoài `Column`, lên
     * `Scaffold` — thì scrim phủ cả `bottomBar` và lấy lại đúng chế độ hỏng "cả app bị khoá cứng".
     * Vị trí này LÀ toàn bộ cơ chế của FR-7/QA-SRM-39, nên nó được ghim ở đây chứ không chỉ ở
     * một dòng chú thích.
     *
     * **Ca này so khớp chuỗi nguyên văn `Box(modifier = Modifier.weight(1f))`, nên đổi cách viết
     * modifier sẽ làm nó ĐỎ GIẢ** (review phase-07). Đừng nới thành so khớp lỏng: ở đây đỏ giả rẻ
     * — đọc một dòng rồi sửa chuỗi — còn xanh giả đắt, vì thứ nó bảo vệ là "thanh tab còn bấm
     * được", mà hỏng cái đó thì cả app khoá cứng và không test nào khác thấy.
     */
    @Test
    fun `the overlay is rendered inside the map box, not over the bottom bar`() {
        val lines = mapScreenCode()
        val boxOpen = lines.indexOfFirst { it.contains("Box(modifier = Modifier.weight(1f))") }
        val overlay = lines.indexOfFirst { it.contains("NoInternetOverlay()") }
        val strip = lines.indexOfFirst { it.contains("RoutingAttribution(") }

        assertTrue("không tìm thấy Box(weight(1f)) của khung bản đồ", boxOpen >= 0)
        assertTrue("không tìm thấy chỗ gọi NoInternetOverlay()", overlay >= 0)
        assertTrue("không tìm thấy dải ghi công RoutingAttribution(", strip >= 0)
        assertTrue(
            "lớp phủ phải nằm TRONG Box(weight(1f)) của bản đồ — thanh tab phải còn bấm được (S14)",
            boxOpen < overlay && overlay < strip,
        )
    }

    /**
     * **§13 Fixed #33 — thứ tự trong `Box` quyết định thứ nào bị chặn.** Trong `Box`, phần tử viết
     * SAU vẽ đè lên và nhận chạm trước. Bản đầu đặt lớp phủ làm phần tử cuối (đúng chỉ dẫn Step 8
     * của phase doc), nên scrim nuốt luôn `TrackingToggle` — và mở app lúc ngoại tuyến với theo dõi
     * đang TẮT thì không bật được, tức không một `location_points` nào được ghi suốt thời gian mất
     * mạng. Đó đúng là cái lỗ mà D8 sinh ra để chặn, tới bằng cửa khác. Xác nhận trên emulator:
     * chạm đúng bounds công tắc ⇒ `tracking_toggled enabled=true` = 0.
     *
     * Luật nay là: **theo dõi chạy được ngoại tuyến ⇒ công tắc nổi TRÊN scrim; màn Dẫn đường cần
     * mạng ⇒ nút "Chỉ đường" nằm DƯỚI scrim.** Hai khẳng định dưới đây khoá cả hai chiều — một mình
     * chiều "công tắc ở trên" thì ai đó có thể kéo luôn nút Chỉ đường lên theo mà không ai thấy.
     */
    @Test
    fun `the tracking toggle floats above the scrim but the navigate button does not`() {
        val lines = mapScreenCode()
        val navigate = lines.indexOfFirst { it.contains("NavigateToMemberButton") }
        val overlay = lines.indexOfFirst { it.contains("NoInternetOverlay()") }
        val toggle = lines.indexOfFirst { it.contains("TrackingToggle(") }

        assertTrue("không tìm thấy NavigateToMemberButton", navigate >= 0)
        assertTrue("không tìm thấy TrackingToggle(", toggle >= 0)
        assertTrue(
            "`TrackingToggle` phải đứng SAU lớp phủ để nổi lên trên scrim — theo dõi chạy được " +
                "hoàn toàn ngoại tuyến, chặn nó là tạo lại đúng lỗ mà D8 sinh ra để chặn",
            overlay < toggle,
        )
        assertTrue(
            "nút \"Chỉ đường\" phải đứng TRƯỚC lớp phủ để bị scrim chặn — màn Dẫn đường thật sự " +
                "cần internet để lấy tuyến",
            navigate < overlay,
        )
    }

    private fun mapFeatureFiles(): List<File> =
        File(uiSrcMain(), "java/com/example/pion/family/tracker/demo/ui/feature/map")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    /** Bỏ dòng trắng và chú thích — hợp đồng nằm ở MÃ, không ở văn xuôi (cùng mẫu
     * `RoutingAttributionContractTest.codeLines()`). */
    private fun codeLinesOf(file: File): List<String> = file.readLines()
        .map { it.trim() }
        .filterNot { it.isEmpty() || it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }

    private fun overlayCode(): List<String> =
        codeLinesOf(File(uiSrcMain(), "java/com/example/pion/family/tracker/demo/ui/feature/map/component/NoInternetOverlay.kt"))

    private fun mapScreenCode(): List<String> =
        codeLinesOf(File(uiSrcMain(), "java/com/example/pion/family/tracker/demo/ui/feature/map/MapScreen.kt"))

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
