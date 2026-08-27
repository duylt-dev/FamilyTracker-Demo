package com.example.pion.family.tracker.demo.ui.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = ZoneEnterGreen,
    tertiary = Pink80,
)

/** PRD §5.2 — `Primary #1B6EF3`, nền/bề mặt theo đúng bảng ("Surface / Background: #FFFFFF /
 * #F5F6F8"). `secondary`/`tertiary` mượn 2 màu vai trò khác trong cùng bảng thay vì để tím mặc
 * định của template — không có vai trò secondary/tertiary riêng nào được PRD định nghĩa. */
private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = ZoneEnterGreen,
    tertiary = ZoneExitRed,
    background = BackgroundGray,
    surface = SurfaceWhite,
)

@Composable
fun FamilyTrackerDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // PRD §5.1: "Material 3, dynamic color TẮT (để màu zone không bị đổi theo wallpaper máy
    // demo)". phase-06 sửa LLM.md §13 (trước là Open #5) — mặc định cũ `true` làm nút/Switch lấy
    // màu theo wallpaper thay vì bảng màu PRD §5.2 trên mọi máy Android 12+.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
