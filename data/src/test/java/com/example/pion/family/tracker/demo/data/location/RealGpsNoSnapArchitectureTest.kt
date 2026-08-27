package com.example.pion.family.tracker.demo.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * QA-SRM-21 (phase-01, D4/US-44 — `decisions.md` §C3) — khoá lời hứa "không bao giờ nắn vị trí THẬT
 * về đường". Cùng tinh thần với `CoroutineSafetyArchitectureTest` (`ui/src/test/.../core/mvi/`):
 * đọc mã nguồn và khẳng định không tệp nào trên đường đi của một điểm GPS thật tham chiếu tới một
 * khái niệm nắn/khớp đường nào.
 *
 * **Phạm vi quét là một DANH SÁCH TƯỜNG MINH, không phải cả thư mục — sửa ở phase-02 review
 * (LLM.md §13 Fixed #25).** Bản đầu quét cả `data/location/` và KDoc khẳng định
 * `MemberMovementSimulator.kt` "nằm ngoài thư mục này". Sai sự thật: nó nằm NGAY TRONG đó, cùng
 * `SimulatedLocationSource.kt` — đều là code MÔ PHỎNG, thứ **bắt buộc** phải bám đường từ phase-02
 * (`LLM.md` §8.1). Guard xanh cho tới lúc đó chỉ vì chưa file mô phỏng nào cần nhắc tên một lớp bám
 * đường. Ngay khi phase-02 review viết `PolylineFollower` vào KDoc của `MemberMovementSimulator`,
 * guard đỏ — và cách sửa rẻ nhất lúc đó sẽ là **nới [forbidden]**, tức là giết guard cho cả đường
 * GPS thật, đúng thứ nó sinh ra để bảo vệ.
 *
 * **Test này tồn tại để LẦN SAU đỏ, không phải để mô tả hiện trạng.** Đã xác nhận nó thật sự đỏ
 * bằng mutation (`reports/dev-phase-01-report.md` S6), không phải một assert luôn xanh.
 *
 * Khác `CoroutineSafetyArchitectureTest`, ở đây KHÔNG bỏ qua dòng comment — một cái tên nắn đường
 * nằm trong TODO/gợi ý cũng là tín hiệu đáng bắt.
 */
class RealGpsNoSnapArchitectureTest {

    private val forbidden = listOf(
        "RoutingProvider",
        "Directions",
        "PolylineFollower",
        "PolylineDecoder",
        "snapTo",
        "mapMatch",
    )

    /** Đường đi của MỘT điểm GPS thật, từ lúc hệ điều hành phát ra tới lúc nó được vẽ/ghi.
     * Thêm file mới vào đường đó thì thêm tên vào đây — [everyFileIsClassified] ép làm việc đó. */
    private val realGpsPath = listOf(
        "FusedLocationSource.kt",   // nguồn: Play Services phát điểm thật
        "LocationPointProcessor.kt", // lọc -> ghi Room, và publish cho chấm xanh
        "LiveSelfLocation.kt",       // phase-01: cổng hiển thị vị trí thật CHƯA lọc
        "LocationTrackingService.kt", // nối dây nguồn -> processor
    )

    /** KHÔNG thuộc đường GPS thật, và được miễn có LÝ DO, không phải vì quên. */
    private val notRealGpsPath = mapOf(
        // Chuyển động MÔ PHỎNG của Minh/Lan (§8.1). Bám đường là YÊU CẦU của nó từ phase-02
        // (US-41), nên quét nó bằng danh sách trên là quét ngược lại chính đặc tả.
        "MemberMovementSimulator.kt" to "mô phỏng thành viên — bám đường là yêu cầu, không phải vi phạm",
        // Tách khỏi MemberMovementSimulator ở phase-04 review "VIỆC 4" (giữ file đó dưới 200 dòng) —
        // cùng lý do miễn: quyết định cổng tuyến nào (path/wander) cho chuyển động MÔ PHỎNG, không
        // chạm điểm GPS thật nào.
        "MemberRoutePathResolver.kt" to "quyết định nguồn tuyến cho chuyển động mô phỏng — không phải điểm GPS thật",
        // Nguồn vị trí GIẢ cho F5 (US-33), đi chung đường ống với nguồn thật nhưng dữ liệu là dựng.
        "SimulatedLocationSource.kt" to "nguồn mô phỏng của F5 — không phải điểm GPS thật",
        // Không đụng toạ độ: chỉ dựng Notification cho foreground service.
        "TrackingNotification.kt" to "chỉ dựng thông báo, không nằm trên đường dữ liệu vị trí",
    )

    @Test
    fun `no file on the real GPS path references any road-snapping concept`() {
        val locationDir = findDataLocationDir()

        val violations = realGpsPath
            .map { File(locationDir, it) }
            .flatMap { file -> violationsIn(file, locationDir) }

        assertTrue(
            "Nắn vị trí thật về đường bị cấm (US-44, QA-SRM-21) — tìm thấy tham chiếu cấm:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    /**
     * Giữ cho phạm vi quét không bao giờ **âm thầm hẹp lại**: đổi tên/xoá một file GPS thật, hoặc
     * thêm một file mới vào `data/location/` mà không phân loại nó, đều làm ca này đỏ. Không có ca
     * này thì danh sách tường minh ở trên là một cách rất gọn để vô hiệu hoá guard mà không ai thấy.
     */
    @Test
    fun `every file under data location is classified as real-GPS or explicitly exempt`() {
        val locationDir = findDataLocationDir()
        val onDisk = locationDir.listFiles { file -> file.isFile && file.extension == "kt" }
            .orEmpty()
            .map { it.name }
            .toSortedSet()

        val classified = (realGpsPath + notRealGpsPath.keys).toSortedSet()

        assertEquals(
            "Mỗi file .kt trong data/location/ phải nằm trong `realGpsPath` (được quét) HOẶC " +
                "`notRealGpsPath` (miễn, kèm lý do). File mới chưa phân loại là một lỗ trong guard.",
            classified,
            onDisk,
        )
    }

    private fun violationsIn(file: File, sourceRoot: File): List<String> {
        check(file.isFile) { "File trên đường GPS thật không tồn tại: ${file.relativeTo(sourceRoot)}" }
        return file.readLines().mapIndexedNotNull { index, rawLine ->
            val hit = forbidden.firstOrNull { rawLine.contains(it) } ?: return@mapIndexedNotNull null
            "${file.relativeTo(sourceRoot)}:${index + 1} contains `$hit`"
        }
    }

    /** Gradle's test working dir is the module dir (`data/`) — cùng mẫu
     * `CoroutineSafetyArchitectureTest.findUiSrcMain()`. */
    private fun findDataLocationDir(): File {
        val workingDir = System.getProperty("user.dir") ?: "."
        var dir = File(workingDir)
        repeat(5) {
            val candidate = File(dir, "src/main/java/com/example/pion/family/tracker/demo/data/location")
            if (candidate.exists()) return candidate
            dir = dir.parentFile ?: return@repeat
        }
        error("Could not locate data/location from working dir $workingDir")
    }
}
