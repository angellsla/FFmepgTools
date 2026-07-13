package com.devobject.ffmpegtools.app

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devobject.ffmpegtools.feature.audio.AudioTranscodeScreen
import com.devobject.ffmpegtools.feature.browser.ShizukuBrowserScreen
import com.devobject.ffmpegtools.feature.extract.ExtractAudioScreen
import com.devobject.ffmpegtools.feature.gif.GifConvertScreen
import com.devobject.ffmpegtools.feature.home.HomeScreen
import com.devobject.ffmpegtools.feature.info.MediaInfoScreen
import com.devobject.ffmpegtools.feature.merge.MultiAudioMergeScreen
import com.devobject.ffmpegtools.feature.merge.MultiVideoMergeScreen
import com.devobject.ffmpegtools.feature.mux.MuxScreen
import com.devobject.ffmpegtools.feature.settings.SettingsScreen
import com.devobject.ffmpegtools.feature.tasks.BackgroundTasksScreen
import com.devobject.ffmpegtools.feature.trim.VideoTrimScreen
import com.devobject.ffmpegtools.feature.video.VideoTranscodeScreen
import com.devobject.ffmpegtools.ui.theme.LocalUiDimensions
import com.devobject.ffmpegtools.ui.theme.rememberUiDimensions
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun FfmpegToolsApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val uiState by viewModel.state.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val activeCount = tasks.count { it.status == com.devobject.ffmpegtools.core.ffmpeg.TaskStatus.RUNNING }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val currentDestination = AppDestination.entries.find { it.route == currentRoute }
    val showBackButton = currentDestination != AppDestination.Home

    val systemDark = isSystemInDarkTheme()
    val isDark = uiState.themeMode.isDark || (uiState.themeMode.isSystem && systemDark)
    val appNavHost: @Composable (Modifier) -> Unit = { modifier ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = modifier.background(
                if (isDark) MiuixTheme.colorScheme.background
                else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            enterTransition = {
                slideInHorizontally { it } + fadeIn()
            },
            exitTransition = {
                slideOutHorizontally { -it } + fadeOut()
            },
            popEnterTransition = {
                slideInHorizontally { -it } + fadeIn()
            },
            popExitTransition = {
                slideOutHorizontally { it } + fadeOut()
            }
        ) {
            composable(AppDestination.Home.route) { HomeScreen(uiState, viewModel, navController) }
            composable(AppDestination.Video.route) { VideoTranscodeScreen(uiState, viewModel) }
            composable(AppDestination.Audio.route) { AudioTranscodeScreen(uiState, viewModel) }
            composable(AppDestination.Extract.route) { ExtractAudioScreen(uiState, viewModel) }
            composable(AppDestination.Mux.route) { MuxScreen(uiState, viewModel) }
            composable(AppDestination.Gif.route) { GifConvertScreen(uiState, viewModel) }
            composable(AppDestination.MultiVideoMerge.route) { MultiVideoMergeScreen(uiState, viewModel) }
            composable(AppDestination.MultiAudioMerge.route) { MultiAudioMergeScreen(uiState, viewModel) }
            composable(AppDestination.Info.route) { MediaInfoScreen(uiState, viewModel) }
            composable(AppDestination.Browser.route) { ShizukuBrowserScreen(uiState, viewModel, navController) }
            composable(AppDestination.Trim.route) { VideoTrimScreen(uiState, viewModel) }
            composable(AppDestination.Settings.route) { SettingsScreen(uiState, viewModel) }
            composable(AppDestination.Tasks.route) { BackgroundTasksScreen(tasks, viewModel) }
        }
    }

    val navigationEventDispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
    val uiDimensions = rememberUiDimensions()
    val density = LocalDensity.current
    val scaledDensity = remember(uiDimensions, density) {
        Density(density.density * uiDimensions.densityScale, density.fontScale * uiDimensions.densityScale)
    }

    CompositionLocalProvider(
        LocalUiDimensions provides uiDimensions,
        LocalDensity provides scaledDensity,
        LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner
    ) {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = currentDestination?.label ?: "",
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        TaskIcon(activeCount, currentRoute, navController)
                        SettingsIcon(currentRoute, navController)
                    }
                )
            }
        ) { padding ->
            appNavHost(Modifier.padding(padding))
        }
    }

    if (uiState.showExternalActionDialog) {
        ExternalActionDialog(
            uri = uiState.externalSharedUri,
            fileName = uiState.externalSharedUri?.lastPathSegment ?: "未知文件",
            onDismiss = { viewModel.dismissExternalActionDialog() },
            onSelect = { destination ->
                viewModel.prepareExternalFileForNavigation(destination)
                navController.navigate(destination.route) {
                    popUpTo(AppDestination.Home.route) { inclusive = false }
                }
            }
        )
    }
}

@Composable
private fun RowScope.TaskIcon(
    activeCount: Int,
    currentRoute: String?,
    navController: NavController
) {
    IconButton(onClick = {
        if (currentRoute != AppDestination.Tasks.route) {
            navController.navigate(AppDestination.Tasks.route)
        }
    }) {
        Box {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = "后台任务",
                modifier = Modifier.size(28.dp)
            )
            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .background(MiuixTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (activeCount > 99) "99+" else activeCount.toString(),
                        color = MiuixTheme.colorScheme.onError,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.SettingsIcon(
    currentRoute: String?,
    navController: NavController
) {
    IconButton(onClick = {
        if (currentRoute != AppDestination.Settings.route) {
            navController.navigate(AppDestination.Settings.route)
        }
    }) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "设置",
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ExternalActionDialog(
    uri: Uri?,
    fileName: String,
    onDismiss: () -> Unit,
    onSelect: (AppDestination) -> Unit
) {
    val context = LocalContext.current
    val mimeType = remember(uri) {
        uri?.let { runCatching { context.contentResolver.getType(it) }.getOrNull() } ?: ""
    }
    val isVideo = mimeType.startsWith("video/")
    val isAudio = mimeType.startsWith("audio/")

    val options = buildList {
        if (isVideo) {
            add(AppDestination.Video to "视频转码")
            add(AppDestination.Trim to "视频裁剪")
            add(AppDestination.Gif to "视频转 GIF")
            add(AppDestination.Extract to "提取音频")
            add(AppDestination.Mux to "音视频合并")
        } else if (isAudio) {
            add(AppDestination.Audio to "音频转码")
            add(AppDestination.Extract to "提取音频")
        }
        add(AppDestination.Info to "查看媒体信息")
    }

    OverlayDialog(
        show = true,
        title = "选择对该文件的操作",
        summary = "已接收：$fileName",
        onDismissRequest = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (destination, label) ->
                    Button(
                        onClick = { onSelect(destination) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label)
                    }
                }
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

sealed class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : AppDestination("home", "", Icons.Default.Home)
    data object Video : AppDestination("video", "视频", Icons.Default.VideoFile)
    data object Audio : AppDestination("audio", "音频", Icons.Default.AudioFile)
    data object Extract : AppDestination("extract", "提取", Icons.Default.MusicNote)
    data object Mux : AppDestination("mux", "合并", Icons.Default.VideoFile)
    data object Gif : AppDestination("gif", "视频转 GIF", Icons.Default.Animation)
    data object MultiVideoMerge : AppDestination("multi_video_merge", "多视频合并", Icons.Default.VideoFile)
    data object MultiAudioMerge : AppDestination("multi_audio_merge", "多音频合并", Icons.Default.AudioFile)
    data object Info : AppDestination("info", "媒体信息", Icons.Default.Info)
    data object Browser : AppDestination("browser", "浏览", Icons.Default.Folder)
    data object Trim : AppDestination("trim", "视频裁剪", Icons.Default.ContentCut)
    data object Settings : AppDestination("settings", "设置", Icons.Default.Settings)
    data object Tasks : AppDestination("tasks", "后台任务", Icons.AutoMirrored.Filled.List)

    companion object {
        val entries: List<AppDestination> = listOf(
            Home, Video, Audio, Extract, Mux, Gif, MultiVideoMerge, MultiAudioMerge, Info, Browser, Trim, Settings, Tasks
        )
    }
}
