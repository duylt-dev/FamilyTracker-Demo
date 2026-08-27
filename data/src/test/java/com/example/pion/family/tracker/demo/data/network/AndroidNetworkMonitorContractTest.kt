package com.example.pion.family.tracker.demo.data.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * phase-07 (US-47, D8) — ghim NĂM quyết định của [AndroidNetworkMonitor] mà **không một test hành
 * vi nào trong repo với tới được**.
 *
 * **Vì sao là test đọc mã nguồn.** `AndroidNetworkMonitor` là adapter quanh `ConnectivityManager`;
 * dự án không dùng Robolectric (LLM.md §11) nên `:data:test` không dựng được `Context` thật, và một
 * fake `ConnectivityManager` chỉ chứng minh fake hoạt động — KDoc của chính lớp đó đã nói vậy, và
 * kết luận đó ĐÚNG. Nhưng hệ quả là cả file nằm ngoài tầm mọi test: đã đo bằng mutation ở review
 * phase-07 — năm đột biến dưới đây, mỗi cái hỏng đúng một yêu cầu của phase, **đều để lại 331/331
 * XANH**. Cùng lý do và cùng khuôn với `MapBlockerIsNotADialogTest` (`:ui`) và
 * `RoutingAttributionContractTest` (`:ui`).
 *
 * Đây là giải pháp cho một hạn chế hạ tầng, không phải mẫu đáng nhân rộng: ngày nào có
 * Robolectric hoặc một `ConnectivityManager` bọc được, thay bằng ca hành vi thật rồi xoá file này.
 */
class AndroidNetworkMonitorContractTest {

    /**
     * FR-5 / Key Insight #3 — `registerDefaultNetworkCallback` KHÔNG bắn gì khi máy đã hoàn toàn
     * không có mạng từ trước. Đọc trạng thái NGAY và phát nó TRƯỚC khi đăng ký là nguồn `false`
     * duy nhất cho ca "mở app khi đã ở chế độ máy bay" — đúng lúc người dùng cần lớp phủ nhất.
     * Mutation đã đo: xoá dòng `trySend(...)` này ⇒ 331/331 vẫn xanh.
     */
    @Test
    fun `the current state is read and emitted before the callback is registered`() {
        val lines = monitorCode()
        val initialRead = lines.indexOfFirst { it.contains("trySend(") && it.contains("readVerifiedInternet()") }
        val register = lines.indexOfFirst { it.contains("registerDefaultNetworkCallback(") }

        assertTrue("thiếu `trySend(readVerifiedInternet())` — mở app lúc offline sẽ KHÔNG hiện lớp phủ (FR-5)", initialRead >= 0)
        assertTrue("thiếu `registerDefaultNetworkCallback(`", register >= 0)
        assertTrue("phải đọc trạng thái hiện tại TRƯỚC khi đăng ký callback (Key Insight #3)", initialRead < register)
    }

    /**
     * FR-6 / QA-SRM-37 — điều kiện D8 nguyên văn: thiếu `NET_CAPABILITY_INTERNET` **hoặc** thiếu
     * `NET_CAPABILITY_VALIDATED` là mất internet ⇒ phép kiểm phải là `&&` của hai cái đó. Đổi thành
     * `||`: wifi quán cà phê chưa qua captive portal tính là "có mạng", app im lặng, mọi request
     * routing timeout, người dùng nhìn một bản đồ trông như đang chạy. Mutation đã đo: 331/331 xanh.
     */
    @Test
    fun `verified internet requires BOTH capabilities, not either`() {
        val check = monitorCode().single { it.contains("hasCapability(") }

        assertTrue("phải kiểm NET_CAPABILITY_INTERNET: $check", check.contains("NET_CAPABILITY_INTERNET"))
        assertTrue("phải kiểm NET_CAPABILITY_VALIDATED: $check", check.contains("NET_CAPABILITY_VALIDATED"))
        assertTrue("phải là `&&`, không phải `||` — đây là điều kiện captive portal: $check", check.contains("&&"))
        assertTrue("`||` ở đây là hỏng QA-SRM-37: $check", !check.contains("||"))
    }

    /**
     * Step 3c — `onAvailable` bắn TRƯỚC khi hệ thống kiểm chứng xong. Override nó và `trySend(true)`
     * làm lớp phủ đóng ~1 giây rồi mở lại khi nối một wifi captive portal — kiểu nháy khó chịu nhất,
     * và đúng ca QA-SRM-37. `onCapabilitiesChanged` luôn bắn ngay sau nên không mất sự kiện nào.
     */
    @Test
    fun `onAvailable is never overridden`() {
        val overrides = monitorCode().filter { it.contains("override fun onAvailable") }

        assertTrue("KHÔNG được override onAvailable (Step 3c): $overrides", overrides.isEmpty())
    }

    /**
     * NFR-6/S11 — `onCapabilitiesChanged` bắn cả khi băng thông ước lượng đổi. Không
     * `distinctUntilChanged()` thì log `network_state` spam hàng chục dòng mỗi phút và QA-SRM-13/37
     * không đếm được gì.
     */
    @Test
    fun `emissions are de-duplicated before they are logged`() {
        val lines = monitorCode()
        val distinct = lines.indexOfFirst { it.contains(".distinctUntilChanged()") }
        val log = lines.indexOfFirst { it.contains("network_state hasInternet=") }

        assertTrue("thiếu .distinctUntilChanged() — log network_state sẽ spam (NFR-6)", distinct >= 0)
        assertTrue("thiếu dòng log network_state", log >= 0)
        assertTrue("phải lọc trùng TRƯỚC khi log, nếu không mỗi lần đổi băng thông là một dòng", distinct < log)
    }

    /**
     * NFR-3 — `callbackFlow` trần là flow LẠNH: mỗi collector một `registerDefaultNetworkCallback`.
     * `MapViewModel` không phải chỉ có một instance (`LLM.md` §13 Open #23 — điều hướng tab dựng
     * ViewModel mới mỗi lần), nên đo thật được **5 callback cùng đăng ký sau 3 lần chuyển tab**. Hệ
     * thống chặn ~100 callback mỗi uid rồi ném `TooManyRequestsException`. `shareIn` là thứ chặn cả
     * điều đó lẫn "một dòng log mỗi collector" — và mutation đã đo: gỡ nó ra, 331/331 vẫn xanh.
     */
    @Test
    fun `the flow is shared so one callback serves every collector`() {
        val lines = monitorCode()
        // Nối lại thành một chuỗi: đây là khẳng định về THAM SỐ của lời gọi, không phải về cách
        // xuống dòng. Bản đầu của ca này so khớp trong PHẠM VI MỘT DÒNG và đỏ giả ngay khi
        // `shareIn(...)` được tách nhiều dòng để nhét chú thích vào — đúng loại đỏ giả không bảo
        // vệ gì (khác `MapBlockerIsNotADialogTest`, nơi đỏ giả là cái giá chấp nhận được để ghim
        // thứ tự lớp).
        val code = lines.joinToString(" ")

        assertTrue("thiếu .shareIn(...) — mỗi collector sẽ đăng ký một NetworkCallback riêng (NFR-3)", code.contains(".shareIn("))
        assertTrue("cần replay = 1 để collector mới nhận ngay trạng thái hiện tại", code.contains("replay = 1"))
        assertTrue(
            "cần replayExpirationMillis = 0 — mặc định Long.MAX_VALUE giữ bộ đệm replay sau khi " +
                "upstream dừng, nên mở lại app lúc mạng ĐÃ về vẫn nháy một lớp phủ không đóng được " +
                "từ giá trị false cũ (review phase-07)",
            code.contains("replayExpirationMillis = 0"),
        )
        assertTrue(
            "phải huỷ đăng ký khi flow đóng (NFR-3)",
            lines.any { it.contains("awaitClose") } && lines.any { it.contains("unregisterNetworkCallback(") },
        )
    }

    /** Bỏ dòng trắng và chú thích — hợp đồng nằm ở MÃ, không ở văn xuôi (cùng mẫu
     * `MapBlockerIsNotADialogTest.codeLinesOf()`). */
    private fun monitorCode(): List<String> =
        File(findDataSrcMainJava(), "com/example/pion/family/tracker/demo/data/network/AndroidNetworkMonitor.kt")
            .readLines()
            .map { it.trim() }
            .filterNot { it.isEmpty() || it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }

    /** Gradle chạy test với working dir là thư mục module (`data/`) — cùng mẫu
     * `InternetBlockerBoundaryTest.findDataSrcMain()`. */
    private fun findDataSrcMainJava(): File {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir = File(workingDir)
        repeat(5) {
            val candidate = File(dir, "src/main/java")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate data/src/main/java from working dir $workingDir")
    }
}
