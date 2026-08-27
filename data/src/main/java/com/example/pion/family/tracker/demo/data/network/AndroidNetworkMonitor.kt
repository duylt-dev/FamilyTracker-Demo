package com.example.pion.family.tracker.demo.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.pion.family.tracker.demo.data.util.FtdLog
import com.example.pion.family.tracker.demo.domain.repository.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

/**
 * phase-07 (US-47, D8) — implementation THẬT của [NetworkMonitor], package MỚI cố ý KHÔNG nằm
 * cạnh `data/routing/`: ranh giới sống còn của D8 là *mất internet* ≠ *lỗi nhà cung cấp*
 * (Key Insight #1). Cây package là chỗ rẻ nhất để ranh giới đó nhìn thấy được —
 * `InternetBlockerBoundaryTest` khoá nó bằng cách quét mã nguồn cả hai phía.
 *
 * Không test riêng cho lớp này ở `:data` — nó là adapter thuần quanh API Android, dự án không
 * dùng Robolectric (LLM.md §11), và một fake `ConnectivityManager` chỉ chứng minh được rằng fake
 * hoạt động. Hành vi thật được nghiệm thu trên máy thật (phase-07 Step 14/15).
 */
class AndroidNetworkMonitor(private val context: Context) : NetworkMonitor {

    /**
     * Scope sống bằng vòng đời tiến trình — lớp này là `single` của Koin, không có ai "đóng" nó.
     * Chỉ dùng để [shareIn] giữ ĐÚNG MỘT đăng ký callback; không chạy việc gì khác.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * **Đo thật trên emulator 2026-08-26 — vì sao phải [shareIn], không phải flow lạnh trần.**
     *
     * `callbackFlow` là flow LẠNH: mỗi collector dựng một upstream riêng, tức một
     * `registerDefaultNetworkCallback` riêng và một bộ đếm [distinctUntilChanged] riêng. Mà
     * `MapViewModel` KHÔNG phải chỉ có một instance: `FamilyTrackerNavHost` điều hướng tới
     * `MapRoute` bằng `navigate()` trần (không `launchSingleTop`, không `popUpTo`), nên mỗi lần
     * bấm tab "Bản đồ" là một entry mới, một ViewModel mới, một collector mới — ViewModel cũ vẫn
     * sống trong back stack. Đo được: 3 lần chuyển tab ⇒ **5 dòng `network_state` cho MỘT lần đổi
     * trạng thái**, tức 5 callback đang đăng ký cùng lúc.
     *
     * Hai thứ hỏng theo, cả hai đều là yêu cầu của phase này:
     * - **NFR-3** — callback tích luỹ. Hệ thống giới hạn ~100 callback mỗi uid rồi ném
     *   `TooManyRequestsException`: app chết giữa buổi demo sau khoảng 100 lần chuyển tab.
     * - **NFR-6/S11** — "đúng một dòng mỗi lần ĐỔI trạng thái" thành ra "một dòng mỗi collector",
     *   nên số dòng log phụ thuộc số màn đang sống, và QA-SRM-13/37 đếm log ra số vô nghĩa.
     *
     * [shareIn] chặn cả hai tại gốc: N collector dùng chung một upstream ⇒ đúng một đăng ký, đúng
     * một dòng log. `replay = 1` để collector mới nhận ngay trạng thái hiện tại mà không cần đọc
     * lại. `stopTimeoutMillis` khác 0 để một lần chuyển tab (collector cuối rời đi rồi collector
     * mới vào ngay sau đó) KHÔNG dựng lại upstream — dựng lại là một dòng log thừa và một lần đăng
     * ký/huỷ đăng ký thừa.
     *
     * Lỗi điều hướng nói trên vẫn còn (`LLM.md` §13 Open #23) — nó nhân bản cả năm nguồn còn lại
     * của `MapViewModel`. Sửa ở đây chỉ làm cổng này ĐÚNG bất kể có bao nhiêu màn quan sát, không
     * phải là bản vá cho lỗi kia.
     */
    private val shared: Flow<Boolean> = networkStateFlow()
        // onCapabilitiesChanged bắn cả khi băng thông ước lượng đổi — không lọc thì log
        // network_state spam hàng chục dòng mỗi phút (NFR-6), và QA-SRM-13/37 không đếm được gì.
        .distinctUntilChanged()
        .onEach { hasInternet -> FtdLog.d(TAG, "network_state hasInternet=$hasInternet") }
        .shareIn(
            scope,
            // `replayExpirationMillis = 0` KHÔNG phải mặc định — mặc định là `Long.MAX_VALUE`, tức
            // bộ đệm replay SỐNG SÓT sau khi upstream dừng (review phase-07). Chiều hỏng: app ở nền
            // quá `stopTimeoutMillis` lúc đang mất mạng ⇒ cache giữ `false`; mở lại khi mạng ĐÃ về
            // thì collector mới nhận `false` cũ TRƯỚC khi `readVerifiedInternet()` kịp chạy ⇒ một
            // lớp phủ KHÔNG ĐÓNG ĐƯỢC nháy lên trên một máy có mạng bình thường. Đó đúng là thứ mà
            // quyết định "`hasInternet` mặc định `true`" (MapContract) được chọn để tránh; để mặc
            // định ở đây là mở lại cửa đó bằng đường khác.
            SharingStarted.WhileSubscribed(
                stopTimeoutMillis = SHARE_STOP_TIMEOUT_MS,
                replayExpirationMillis = 0,
            ),
            replay = 1,
        )

    override fun observeHasInternet(): Flow<Boolean> = shared

    private fun networkStateFlow(): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java) ?: return@callbackFlow

        // Key Insight #3 — registerDefaultNetworkCallback KHÔNG bắn gì khi máy đã hoàn toàn không
        // có mạng từ trước (không onAvailable, không onLost, không có gì để mất). Đọc trạng thái
        // NGAY, trước khi đăng ký, là cách DUY NHẤT bắt được ca "mở app khi đã ở chế độ máy bay"
        // (FR-5, Step 14d).
        trySend(connectivityManager.readVerifiedInternet())

        val callback = object : ConnectivityManager.NetworkCallback() {
            // KHÔNG override onAvailable (Step 3c). Nó bắn TRƯỚC khi hệ thống kiểm chứng internet
            // xong, nên nối một wifi captive portal sẽ đóng lớp phủ trong ~1 giây rồi mở lại ngay
            // (QA-SRM-37) — kiểu nháy khó chịu nhất. onCapabilitiesChanged luôn bắn ngay sau
            // onAvailable nên không có sự kiện nào bị mất vì bỏ qua onAvailable.
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(networkCapabilities.isVerified())
            }

            // onUnavailable KHÔNG override: nó chỉ bắn cho requestNetwork() có timeout, không bao
            // giờ bắn cho registerDefaultNetworkCallback (không có gì để chờ ở đây).
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    /** Nguồn `false` DUY NHẤT khi mở app lúc đang ở chế độ máy bay (Key Insight #3). */
    private fun ConnectivityManager.readVerifiedInternet(): Boolean =
        getNetworkCapabilities(activeNetwork)?.isVerified() == true

    /** Đúng nguyên văn điều kiện D8 — `&&`, không phải `||`. Wifi đầy vạch nhưng chưa qua captive
     * portal có đủ `NET_CAPABILITY_INTERNET` nhưng chưa `NET_CAPABILITY_VALIDATED` (QA-SRM-37). */
    private fun NetworkCapabilities.isVerified(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    private companion object {
        const val TAG = "FTD_EVENT"

        /** Đủ dài để một lần chuyển tab (collector cuối rời đi, collector mới vào ngay sau) không
         * dựng lại upstream — dựng lại là một lần đăng ký/huỷ đăng ký thừa và một dòng log thừa.
         * Đủ ngắn để app ở nền lâu thì callback được thả ra. */
        const val SHARE_STOP_TIMEOUT_MS = 5_000L
    }
}
