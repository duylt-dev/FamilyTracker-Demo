package com.example.pion.family.tracker.demo.ui.feature.navigation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens

/**
 * `anchor` của [DestinationPin]: mũi nhọn ở ĐÁY bitmap mới là toạ độ thật, không phải tâm hình
 * tròn. Mặc định của `MarkerComposable` cũng là `(0.5f, 1f)` — khai tường minh ở đây vì cái đúng
 * đắn của nó phụ thuộc vào hình vẽ, không phải vào giá trị mặc định của thư viện.
 */
internal val DESTINATION_PIN_ANCHOR = Offset(0.5f, 1f)

/** `anchor` của [NavigationDot]: toạ độ thật là TÂM hình tròn. */
internal val SELF_DOT_ANCHOR = Offset(0.5f, 0.5f)

/**
 * Chấm tròn phẳng — self, tức điểm XUẤT PHÁT. Cùng hình với chấm xanh ở màn Bản đồ một cách có chủ
 * ý: "tôi" phải trông giống nhau ở cả hai màn. Toạ độ thật nằm ở TÂM hình tròn, nên marker dùng nó
 * khai `anchor = SELF_DOT_ANCHOR` — mặc định giữa-đáy của `MarkerComposable` sẽ đẩy cả chấm lên
 * trên toạ độ thật đúng một nửa đường kính.
 */
@Composable
internal fun NavigationDot(color: Color, size: Dp, borderWidth: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape)
            .border(borderWidth, MaterialTheme.colorScheme.surface, CircleShape),
    )
}

/**
 * **feedback #4 — marker ĐÍCH, cố ý khác hình với mọi chấm tròn khác trong app.**
 *
 * Trước đây self và đích là hai chấm tròn chỉ khác nhau ở màu và vài dp đường kính; trên một màn
 * hình mà cả hai đầu tuyến đều quan trọng, người dùng phải đoán đầu nào là đích. Giọt nước (tròn +
 * đuôi nhọn chỉ xuống) là hình quy ước của "đích đến" trên mọi ứng dụng bản đồ, và nó còn nói thêm
 * một điều chấm tròn không nói được: toạ độ thật nằm ở MŨI NHỌN, không phải ở tâm — xem
 * [DESTINATION_PIN_ANCHOR].
 *
 * Màu lấy từ `Member.colorArgb` (PRD §5.2) nên vẫn nhận ra được đây là Minh hay Lan; viền
 * `colorScheme.surface` để nổi trên mọi nền tile, cùng luật `MemberDot`/`SelfDot`.
 */
@Composable
internal fun DestinationPin(color: Color) {
    Box(
        modifier = Modifier.size(
            width = Dimens.DestinationPinSize,
            height = Dimens.DestinationPinSize + Dimens.DestinationPinTailHeight,
        ),
    ) {
        PinTail(color = color, modifier = Modifier.matchParentSize())
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(Dimens.DestinationPinSize)
                .background(color, CircleShape)
                .border(Dimens.DestinationPinBorderWidth, MaterialTheme.colorScheme.surface, CircleShape),
        )
    }
}

/**
 * Đuôi nhọn nối từ mép dưới hình tròn xuống đúng toạ độ. Vẽ TRƯỚC hình tròn (thứ tự trong [Box] là
 * thứ tự chồng lớp) để mép trên tam giác bị hình tròn che, cho ra một giọt nước liền chứ không phải
 * một hình tròn dán lên một tam giác.
 */
@Composable
private fun PinTail(color: Color, modifier: Modifier = Modifier) {
    val borderColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val halfWidth = Dimens.DestinationPinSize.toPx() / 4f
        val shoulderY = Dimens.DestinationPinSize.toPx() * TAIL_SHOULDER_FRACTION
        val path = Path().apply {
            moveTo(centerX - halfWidth, shoulderY)
            lineTo(centerX + halfWidth, shoulderY)
            lineTo(centerX, size.height)
            close()
        }
        drawPath(path, color = borderColor, style = Stroke(width = Dimens.DestinationPinBorderWidth.toPx() * 2f))
        drawPath(path, color = color)
    }
}

/** Vai tam giác đặt ở 70% chiều cao hình tròn — đủ sâu để hình tròn che hết cạnh trên, không sâu
 * tới mức đuôi trông như mọc từ giữa chấm. */
private const val TAIL_SHOULDER_FRACTION = 0.7f
