package com.example.pion.family.tracker.demo.ui.feature.map.component

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.ui.R

/**
 * Nút mở màn Dẫn đường cho thành viên đang chọn — `internal`, chỉ màn Bản đồ dùng.
 *
 * **Góc PHẢI TRÊN, không còn `BottomCenter` (feedback #1).** Card `TrackingToggle` ("Theo dõi gia
 * đình") neo ở `BottomEnd` và rộng hơn nửa màn hình, nên `BottomCenter` nằm gọn dưới nó: chọn Minh
 * hoặc Lan là hai nút chồng lên nhau. Đưa lên nửa trên là chỗ duy nhất trong khung bản đồ còn
 * trống mà không đụng logo/credit Google ở góc dưới-trái (Key Insight #2 của Routing plan phase-05
 * — áp dụng cho MỌI thứ nổi trong khung bản đồ, che nó là vi phạm ToS).
 *
 * **Không còn là `BoxScope` extension.** Vị trí nay do người gọi quyết bằng [modifier]: nút sống
 * trong CÙNG một `Column` với các `PermissionBanner` (`MapScreen.kt`) chứ không phải một
 * `align()` rời — banner là `fillMaxWidth()`, nên hai `align()` độc lập ở `TopStart`/`TopEnd` tái
 * tạo đúng kiểu chồng lấn vừa sửa, chỉ ở đầu kia màn hình.
 *
 * **Cố ý nằm DƯỚI scrim của [NoInternetOverlay]** (smooth-road plan phase-07): màn Dẫn đường thật
 * sự cần internet để lấy tuyến, nên chặn nó lúc mất mạng là đúng. Khác `TrackingToggle`, thứ nổi
 * LÊN TRÊN scrim vì theo dõi chạy được hoàn toàn ngoại tuyến (§13 Fixed #33). Thứ tự trong `Box`
 * của `MapScreen` là thứ quyết định điều đó — đừng đổi khi không đọc cả hai KDoc.
 *
 * Tách khỏi `MapScreen.kt` ở smooth-road plan phase-06 khi file đó chạm 204 dòng, vượt trần 200
 * (`LLM.md` §5, §13 Open #16). Khối này không mang bất biến nào của màn nên tách rẻ nhất.
 */
@Composable
internal fun NavigateToMemberButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier) {
        Text(stringResource(R.string.map_navigate_action))
    }
}
