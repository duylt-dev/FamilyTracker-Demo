package com.example.pion.family.tracker.demo.ui.feature.history.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pion.family.tracker.demo.domain.tracking.TrackingConstants
import com.example.pion.family.tracker.demo.ui.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/**
 * US-27 — date picker giới hạn ĐÚNG [TrackingConstants.HISTORY_RETENTION_DAYS] ngày gần nhất (7,
 * cùng số với hạn lưu trữ `PurgeOldHistoryUseCase` — phase file Key Insight #6: cho chọn ngày thứ
 * 8 là hứa một empty state thực ra do dữ liệu ĐÃ BỊ XOÁ, không phải chưa từng có).
 *
 * Danh sách đúng N ngày trong một `DropdownMenu` — không dùng Material3 `DatePickerDialog` (dù nó
 * có `SelectableDates` để chặn ngày ngoài khoảng): việc đó cần dựng `DatePickerState`, quy đổi
 * `LocalDate` <-> epoch millis UTC, và một dialog xác nhận/huỷ, chỉ để cuối cùng hiển thị đúng 7
 * lựa chọn cố định — không cần thiết so với liệt kê thẳng (YAGNI).
 */
@Composable
internal fun DayPickerBar(selectedDay: LocalDate, onDaySelected: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val selectableDays = remember(today) {
        (0 until TrackingConstants.HISTORY_RETENTION_DAYS).map { today.minusDays(it.toLong()) }
    }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }) {
            Text(DAY_FORMATTER.format(selectedDay))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            selectableDays.forEach { day ->
                val label = if (day == today) {
                    stringResource(R.string.history_day_today, DAY_FORMATTER.format(day))
                } else {
                    DAY_FORMATTER.format(day)
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onDaySelected(day)
                    },
                )
            }
        }
    }
}
