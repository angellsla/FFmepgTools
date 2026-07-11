package com.devobject.ffmpegtools.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.runtime.CompositionLocalProvider
import com.devobject.ffmpegtools.app.AppDestination
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.shizuku.ShizukuState
import com.devobject.ffmpegtools.feature.common.AppBody
import com.devobject.ffmpegtools.feature.common.AppClickableCard
import com.devobject.ffmpegtools.feature.common.AppIcon
import com.devobject.ffmpegtools.feature.common.StatusTag
import com.devobject.ffmpegtools.ui.theme.LocalUiDimensions
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private data class HomeItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val mediaItems = listOf(
    HomeItem("视频转码", Icons.Filled.VideoFile, AppDestination.Video.route),
    HomeItem("音频转码", Icons.Filled.AudioFile, AppDestination.Audio.route),
    HomeItem("提取音频", Icons.Filled.MusicNote, AppDestination.Extract.route),
    HomeItem("视频裁剪", Icons.Filled.ContentCut, AppDestination.Trim.route)
)

private val mergeItems = listOf(
    HomeItem("音视频合并", Icons.AutoMirrored.Filled.MergeType, AppDestination.Mux.route),
    HomeItem("多视频合并", Icons.Filled.VideoFile, AppDestination.MultiVideoMerge.route),
    HomeItem("多音频合并", Icons.Filled.AudioFile, AppDestination.MultiAudioMerge.route),
    HomeItem("视频转 GIF", Icons.Filled.Animation, AppDestination.Gif.route)
)

private val toolItems = listOf(
    HomeItem("媒体信息", Icons.Filled.Info, AppDestination.Info.route),
    HomeItem("Shizuku 文件浏览", Icons.Filled.Folder, AppDestination.Browser.route)
)

@Composable
fun HomeScreen(
    uiState: AppUiState,
    viewModel: MainViewModel,
    navController: NavController
) {
    val uiDims = LocalUiDimensions.current
    val density = LocalDensity.current
    val isDark = uiState.themeMode.isDark || (uiState.themeMode.isSystem && isSystemInDarkTheme())
    val isAmoled = uiState.themeMode.isAmoled
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * uiDims.homeFontScale
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isDark) MiuixTheme.colorScheme.background
                    else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(uiDims.pagePadding),
                verticalArrangement = Arrangement.spacedBy(uiDims.groupSpacing)
            ) {
            item {
                StatusCardGrid(uiState, isDark)
            }

            item {
                HomeGroup(title = "媒体处理", isDark = isDark, isAmoled = isAmoled) {
                    mediaItems.forEach { item ->
                        HomeEntryCard(item, isDark, isAmoled) { navController.navigate(item.route) }
                    }
                }
            }

            item {
                HomeGroup(title = "合并 / 转换", isDark = isDark, isAmoled = isAmoled) {
                    mergeItems.forEach { item ->
                        HomeEntryCard(item, isDark, isAmoled) { navController.navigate(item.route) }
                    }
                }
            }

            item {
                HomeGroup(title = "工具", isDark = isDark, isAmoled = isAmoled) {
                    toolItems.forEach { item ->
                        HomeEntryCard(item, isDark, isAmoled) { navController.navigate(item.route) }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StatusCardGrid(uiState: AppUiState, isDark: Boolean) {
    val isReady = !uiState.isInitializing && uiState.statusMessage.contains("就绪", ignoreCase = true)

    val statusBackground = if (isReady) {
        if (isDark) Color(0xFF234830) else Color(0xFFDFFAE4)
    } else {
        MiuixTheme.colorScheme.primaryContainer
    }
    val statusContent = if (isReady) {
        if (isDark) Color(0xFF36D167) else Color(0xFF1A8E3D)
    } else {
        MiuixTheme.colorScheme.onPrimaryContainer
    }
    val statusLabel = if (isReady) "就绪" else "检测中"
    val statusMessage = if (isReady) "FFmpeg 已就绪" else uiState.statusMessage

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.defaultColors(color = statusBackground),
            pressFeedbackType = PressFeedbackType.Tilt
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(38.dp, 45.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Icon(
                        modifier = Modifier.size(170.dp),
                        imageVector = Icons.Default.CheckCircle,
                        tint = statusContent.copy(alpha = 0.25f),
                        contentDescription = null
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = statusLabel,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusContent
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = statusMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusContent.copy(alpha = 0.85f),
                        maxLines = 2
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val (shizukuColor, shizukuBg) = shizukuTagColors(uiState.shizukuState, isDark)
            SmallStatusCard(
                label = "Shizuku",
                value = uiState.shizukuState.label,
                icon = Icons.Default.Security,
                modifier = Modifier.weight(1f),
                tagBackground = shizukuBg,
                tagContent = shizukuColor
            )
            Spacer(Modifier.height(12.dp))
            SmallStatusCard(
                label = "版本",
                value = uiState.appVersion,
                icon = Icons.Default.SettingsSuggest,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SmallStatusCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tagBackground: Color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
    tagContent: Color = MiuixTheme.colorScheme.primary
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        insideMargin = PaddingValues(14.dp),
        pressFeedbackType = PressFeedbackType.Tilt
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Spacer(Modifier.height(8.dp))
            StatusTag(
                label = value,
                backgroundColor = tagBackground,
                contentColor = tagContent
            )
        }
    }
}

@Composable
private fun shizukuTagColors(state: ShizukuState, isDark: Boolean): Pair<Color, Color> {
    return when (state) {
        ShizukuState.AUTHORIZED -> {
            val bg = if (isDark) Color(0xFF234830) else Color(0xFFDFFAE4)
            val content = if (isDark) Color(0xFF36D167) else Color(0xFF1A8E3D)
            content to bg
        }
        ShizukuState.UNAUTHORIZED -> {
            val bg = if (isDark) Color(0xFF4D3A00) else Color(0xFFFFF4D6)
            val content = if (isDark) Color(0xFFFFD54F) else Color(0xFF8C6A00)
            content to bg
        }
        else -> {
            val bg = MiuixTheme.colorScheme.surfaceVariant
            val content = MiuixTheme.colorScheme.onSurfaceVariantSummary
            content to bg
        }
    }
}

@Composable
private fun HomeGroup(
    title: String,
    isDark: Boolean,
    isAmoled: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardColor = when {
        isAmoled -> Color(0xFF0D0D0D)
        isDark -> MiuixTheme.colorScheme.surfaceContainer
        else -> MiuixTheme.colorScheme.surface
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(color = cardColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 3.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
    }
}

@Composable
private fun HomeEntryCard(item: HomeItem, isDark: Boolean, isAmoled: Boolean, onClick: () -> Unit) {
    val cardColor = when {
        isAmoled -> Color(0xFF0D0D0D)
        isDark -> MiuixTheme.colorScheme.surfaceContainer
        else -> MiuixTheme.colorScheme.surface
    }
    AppClickableCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = cardColor,
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppIcon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MiuixTheme.colorScheme.onSurface
            )
            AppBody(
                text = item.label,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}
