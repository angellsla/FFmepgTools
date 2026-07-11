package com.devobject.ffmpegtools.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.ui.AppThemeMode
import com.devobject.ffmpegtools.feature.common.EnumDropdown
import com.devobject.ffmpegtools.feature.common.SettingsAction
import com.devobject.ffmpegtools.feature.common.SettingsGroup
import com.devobject.ffmpegtools.feature.common.SettingsIcon
import com.devobject.ffmpegtools.feature.common.SettingsItem
import com.devobject.ffmpegtools.ui.theme.LocalUiDimensions

@Composable
fun SettingsScreen(uiState: AppUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiDims = LocalUiDimensions.current
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * uiDims.settingsFontScale
        )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsGroup(title = "主题") {
                    EnumDropdown(
                        label = "主题模式",
                        value = uiState.themeMode,
                        values = AppThemeMode.entries,
                        display = { it.label }
                    ) { viewModel.setThemeMode(it) }
                }
            }

            item {
                SettingsGroup(title = "应用信息") {
                    SettingsItem(
                        title = "版本",
                        summary = uiState.appVersion,
                        startAction = { SettingsIcon(Icons.Default.Info, contentDescription = null) }
                    )
                    SettingsItem(
                        title = "targetSdkVersion",
                        summary = "28",
                        startAction = { SettingsIcon(Icons.Default.Info, contentDescription = null) }
                    )
                    SettingsItem(
                        title = "运行状态",
                        summary = uiState.statusMessage,
                        startAction = { SettingsIcon(Icons.Default.Info, contentDescription = null) }
                    )
                }
            }

            item {
                SettingsGroup(title = "FFmpeg") {
                    SettingsItem(
                        title = "运行路径",
                        summary = uiState.runtimePath,
                        startAction = { SettingsIcon(Icons.Default.VideoFile, contentDescription = null) }
                    )
                    SettingsItem(
                        title = "FFmpeg 版本",
                        summary = uiState.capabilities.ffmpegVersion,
                        startAction = { SettingsIcon(Icons.Default.VideoFile, contentDescription = null) }
                    )
                    SettingsItem(
                        title = "FFprobe 版本",
                        summary = uiState.capabilities.ffprobeVersion,
                        startAction = { SettingsIcon(Icons.Default.VideoFile, contentDescription = null) }
                    )
                    SettingsItem(
                        title = "硬件加速",
                        summary = uiState.capabilities.hwaccels.ifEmpty { setOf("未检测到") }.joinToString(),
                        startAction = { SettingsIcon(Icons.Default.VideoFile, contentDescription = null) }
                    )
                    SettingsItem(
                        title = "MediaCodec",
                        summary = if (uiState.capabilities.hasMediaCodec) "可用" else "未检测到",
                        startAction = { SettingsIcon(Icons.Default.VideoFile, contentDescription = null) }
                    )
                    SettingsItem(
                        title = "硬件编码器",
                        summary = uiState.capabilities.androidHardwareEncoders.ifEmpty { setOf("未检测到") }.joinToString(),
                        startAction = { SettingsIcon(Icons.Default.VideoFile, contentDescription = null) }
                    )
                }
            }

            item {
                SettingsGroup(title = "Shizuku") {
                    SettingsItem(
                        title = "授权状态",
                        summary = uiState.shizukuState.label,
                        startAction = { SettingsIcon(Icons.Default.Security, contentDescription = null) }
                    )
                    SettingsItem(
                        title = "服务版本",
                        summary = uiState.shizukuVersion,
                        startAction = { SettingsIcon(Icons.Default.Security, contentDescription = null) }
                    )
                    SettingsAction(
                        title = "请求授权 / 刷新",
                        onClick = { viewModel.requestShizukuPermission() },
                        startAction = { SettingsIcon(Icons.Default.Security, contentDescription = null) }
                    )
                }
            }

            item {
                SettingsGroup(title = "缓存") {
                    SettingsItem(
                        title = "说明",
                        summary = "任务输入输出会先写入 App cache，再按需写回 SAF 输出位置。",
                        startAction = { SettingsIcon(Icons.Default.CleaningServices, contentDescription = null) }
                    )
                    SettingsAction(
                        title = "在文件管理器中查看缓存",
                        summary = "通过系统 SAF 浏览应用缓存目录（只读）。",
                        onClick = { openCacheDocumentsProvider(context) },
                        startAction = { SettingsIcon(Icons.Default.FolderOpen, contentDescription = null) }
                    )
                    SettingsAction(
                        title = "清理临时文件",
                        onClick = { viewModel.cleanTemporaryFiles() },
                        startAction = { SettingsIcon(Icons.Default.CleaningServices, contentDescription = null) }
                    )
                }
            }
        }
    }
}

private fun openCacheDocumentsProvider(context: Context) {
    val authority = "${context.packageName}.cache.documents"
    val rootUri = DocumentsContract.buildRootUri(authority, "cache-root")
    val intent = Intent(Intent.ACTION_VIEW).setData(rootUri)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "未找到支持 SAF 的文件管理器", Toast.LENGTH_SHORT).show()
    }
}
