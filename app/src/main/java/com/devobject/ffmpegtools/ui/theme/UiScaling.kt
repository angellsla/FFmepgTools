package com.devobject.ffmpegtools.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局 DPI 自适应尺寸集合。
 *
 * 根据屏幕 densityDpi 自动选择 Phone（高密度小屏）或 Tablet（低密度大屏）两套尺寸，
 * 使 UI 在不同设备上保持相对一致的视觉比例与可操作性。
 */
data class UiDimensions(
    val pagePadding: Dp,
    val groupSpacing: Dp,
    val sectionPadding: Dp,
    val cardSpacing: Dp,
    val bodySpacing: Dp,
    val iconSize: Dp,
    val toolbarHeight: Dp,
    val timelineHeight: Dp,
    val previewMinHeight: Dp,
    val previewMaxHeight: Dp,
    val textFieldHeight: Dp,
    val smallIconSize: Dp,
    val densityScale: Float,
    val homeFontScale: Float,
    val settingsFontScale: Float
)

/**
 * 高密度手机预设（例如 480dpi 手机）。
 */
val PhoneDimensions = UiDimensions(
    pagePadding = 12.dp,
    groupSpacing = 12.dp,
    sectionPadding = 9.6.dp,
    cardSpacing = 8.4.dp,
    bodySpacing = 6.dp,
    iconSize = 21.6.dp,
    toolbarHeight = 24.dp,
    timelineHeight = 78.dp,
    previewMinHeight = 108.dp,
    previewMaxHeight = 192.dp,
    textFieldHeight = 72.dp,
    smallIconSize = 10.8.dp,
    densityScale = 0.8f,
    homeFontScale = 2.0f,
    settingsFontScale = 1.5f
)

/**
 * 低密度大屏预设（例如 280dpi 平板）。
 */
val TabletDimensions = UiDimensions(
    pagePadding = 16.dp,
    groupSpacing = 16.dp,
    sectionPadding = 20.dp,
    cardSpacing = 18.dp,
    bodySpacing = 12.dp,
    iconSize = 44.dp,
    toolbarHeight = 48.dp,
    timelineHeight = 160.dp,
    previewMinHeight = 220.dp,
    previewMaxHeight = 380.dp,
    textFieldHeight = 150.dp,
    smallIconSize = 22.dp,
    densityScale = 1.0f,
    homeFontScale = 1.0f,
    settingsFontScale = 1.0f
)

/**
 * 根据当前屏幕 densityDpi 选择合适的尺寸预设。
 *
 * - densityDpi >= 400：使用 PhoneDimensions（紧凑）。
 * - densityDpi < 400：使用 TabletDimensions（舒展）。
 */
@Composable
fun rememberUiDimensions(): UiDimensions {
    val densityDpi = LocalContext.current.resources.configuration.densityDpi
    return if (densityDpi != Configuration.DENSITY_DPI_UNDEFINED && densityDpi >= 400) {
        PhoneDimensions
    } else {
        TabletDimensions
    }
}

/**
 * 全局 CompositionLocal，供所有 Composable 读取当前 DPI 尺寸。
 */
val LocalUiDimensions = staticCompositionLocalOf { PhoneDimensions }
