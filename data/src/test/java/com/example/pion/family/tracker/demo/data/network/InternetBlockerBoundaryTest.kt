package com.example.pion.family.tracker.demo.data.network

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * phase-07 (US-47, D8, Key Insight #1) — khoá ranh giới sống còn: *mất internet* ≠ *lỗi nhà cung
 * cấp*. `ConnectivityManager` quyết định lớp phủ; mã lỗi HTTP quyết định chọn tầng nguồn tuyến
 * (phase 04). Nếu một 401 lỡ tay bật được lớp phủ "mất mạng", điều kiện tắt (có internet) đã đúng
 * sẵn từ đầu ⇒ lớp phủ KHÔNG BAO GIỜ tự tắt được — chế độ hỏng tệ nhất D8 có thể sinh ra
 * (QA-SRM-40).
 *
 * Quét mã nguồn hai chiều, cùng mẫu `RealGpsNoSnapArchitectureTest`/`CoroutineSafetyArchitectureTest`:
 * - `data/routing/MemberRouteSource.kt` + `data/routing/OnDevicePolylineCache.kt` (phía chọn tầng
 *   tuyến) không được nhắc tới bất kỳ khái niệm nào của phía mạng;
 * - `data/network/AndroidNetworkMonitor.kt` (phía quyết định lớp phủ) không được nhắc tới bất kỳ
 *   khái niệm nào của phía routing/mã lỗi HTTP.
 *
 * **Test này tồn tại để LẦN SAU đỏ, không phải để mô tả hiện trạng** — cùng tinh thần KDoc của
 * `RealGpsNoSnapArchitectureTest`. Không có test riêng cho hành vi thật của `AndroidNetworkMonitor`
 * ở `:data`: nó là adapter thuần quanh API Android, dự án không dùng Robolectric (LLM.md §11), và
 * một fake `ConnectivityManager` chỉ chứng minh được rằng fake hoạt động — hành vi thật được
 * nghiệm thu trên máy thật (phase-07 Step 14/15).
 */
class InternetBlockerBoundaryTest {

    private val networkConcepts = listOf(
        "ConnectivityManager",
        "NetworkMonitor",
        "NetworkCapabilities",
        "hasInternet",
    )

    private val routingConcepts = listOf(
        "RoutingProvider",
        "MemberRouteSource",
        "AppError",
        "401",
        "429",
    )

    /**
     * Quét **cả thư mục `data/routing/`**, không phải một danh sách file tường minh (review
     * phase-07). Danh sách tường minh có đúng lỗi mà `RealGpsNoSnapArchitectureTest` đã dính ở
     * phase-02: thêm một file mới vào thư mục thì nó nằm NGOÀI tầm quét, im lặng, và phạm vi guard
     * hẹp lại mà không ai thấy. Ở đó phải chữa bằng một ca thứ hai đối chiếu danh sách với đĩa vì
     * thư mục có chứa file được miễn trừ hợp lệ (code mô phỏng BẮT BUỘC bám đường). Ở đây **không
     * có ngoại lệ nào**: không file nào trong `data/routing/` có lý do chính đáng để biết về trạng
     * thái mạng của thiết bị, nên quét cả thư mục vừa mạnh hơn vừa không cần ai bảo trì danh sách.
     */
    @Test
    fun `nothing in the routing package ever references the network-state concepts`() {
        val dataSrcMain = findDataSrcMain()
        val routingDir = File(dataSrcMain, "com/example/pion/family/tracker/demo/data/routing")
        check(routingDir.isDirectory) { "Không tìm thấy data/routing/ từ $dataSrcMain" }

        val files = routingDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        check(files.isNotEmpty()) { "data/routing/ rỗng — phép quét ranh giới D8 đang không kiểm gì cả" }

        val violations = files.flatMap { file -> violationsIn(file, dataSrcMain, networkConcepts) }

        assertTrue(
            "Ranh giới D8 bị đọc chéo — tầng nguồn tuyến KHÔNG được biết gì về NetworkMonitor " +
                "(Key Insight #1). Tìm thấy:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    @Test
    fun `AndroidNetworkMonitor never references routing or HTTP error concepts`() {
        val dataSrcMain = findDataSrcMain()
        val file = File(dataSrcMain, "com/example/pion/family/tracker/demo/data/network/AndroidNetworkMonitor.kt")

        val violations = violationsIn(file, dataSrcMain, routingConcepts)

        assertTrue(
            "Ranh giới D8 bị đọc chéo — lớp phủ mất mạng KHÔNG được biết gì về RoutingProvider/mã " +
                "lỗi HTTP (Key Insight #1). Một 401 lỡ bật lớp phủ này thì lớp phủ không bao giờ tự " +
                "tắt được. Tìm thấy:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }

    private fun violationsIn(file: File, sourceRoot: File, forbidden: List<String>): List<String> {
        check(file.isFile) { "File khoá ranh giới D8 không tồn tại: ${file.relativeTo(sourceRoot)}" }
        return file.readLines().mapIndexedNotNull { index, rawLine ->
            val hit = forbidden.firstOrNull { rawLine.contains(it) } ?: return@mapIndexedNotNull null
            "${file.relativeTo(sourceRoot)}:${index + 1} contains `$hit`"
        }
    }

    /** Gradle's test working dir is the module dir (`data/`) — cùng mẫu
     * `RealGpsNoSnapArchitectureTest.findDataLocationDir()`. */
    private fun findDataSrcMain(): File {
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
