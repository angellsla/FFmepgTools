package com.devobject.ffmpegtools.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.devobject.ffmpegtools.core.ui.AppThemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 应用统一主题入口。
 *
 * 基于 miuix 0.9.2 的 ThemeController：
 * - 支持 SYSTEM / LIGHT / DARK / MONET_SYSTEM / MONET_LIGHT / MONET_DARK / DARK_AMOLED 模式切换。
 * - Monet 模式在 Android 12+ 使用系统壁纸动态取色，低版本回退到小米蓝 seed。
 * - AMOLED 模式使用纯黑背景。
 */
@Composable
fun FfmpegToolsTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = themeMode.isDark || (themeMode.isSystem && systemDarkTheme)

    val keyColor = when {
        themeMode.isAmoled -> Color(0xFF3482FF)
        themeMode.isMonet -> null
        else -> Color(0xFF3482FF)
    }

    val controller = remember(themeMode, darkTheme) {
        if (themeMode.isAmoled) {
            ThemeController(
                AppThemeMode.DARK.toColorSchemeMode(),
                lightColors = lightColorScheme(),
                darkColors = darkColorScheme().copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color.Black,
                    surfaceContainer = Color.Black,
                    surfaceContainerHigh = Color.Black,
                    surfaceContainerHighest = Color.Black
                ),
                isDark = true
            )
        } else {
            val baseDark = darkColorScheme()
            val unifiedSurface = baseDark.surfaceContainer
            ThemeController(
                themeMode.toColorSchemeMode(),
                lightColors = lightColorScheme(),
                darkColors = baseDark.copy(
                    surface = unifiedSurface,
                    surfaceVariant = unifiedSurface,
                    surfaceContainerHigh = unifiedSurface,
                    surfaceContainerHighest = unifiedSurface
                ),
                keyColor = keyColor
            )
        }
    }

    MiuixTheme(controller = controller) {
        LaunchedEffect(darkTheme) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        content()
    }
}
