package com.example.pion.family.tracker.demo.ui.feature.zone.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pion.family.tracker.demo.ui.designsystem.theme.Dimens
import com.example.pion.family.tracker.demo.ui.designsystem.theme.ZoneColorPalette

private val SWATCH_SIZE = 32.dp
private val SWATCH_BORDER_WIDTH = 3.dp

/** US-20 — 6 màu định sẵn PRD §5.2, chấm đang chọn có viền (PRD §5.6 layout). */
@Composable
internal fun ColorPicker(
    selectedColorArgb: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSm),
    ) {
        ZoneColorPalette.COLORS.forEach { colorArgb ->
            val isSelected = colorArgb == selectedColorArgb
            val border = if (isSelected) {
                Modifier.border(BorderStroke(SWATCH_BORDER_WIDTH, MaterialTheme.colorScheme.onSurface), CircleShape)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .clip(CircleShape)
                    .background(Color(colorArgb), CircleShape)
                    .then(border)
                    .clickable { onColorSelected(colorArgb) },
            )
        }
    }
}
