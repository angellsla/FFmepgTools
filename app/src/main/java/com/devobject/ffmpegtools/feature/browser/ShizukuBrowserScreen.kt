package com.devobject.ffmpegtools.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.devobject.ffmpegtools.app.AppDestination
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.files.ShellFileEntry
import com.devobject.ffmpegtools.feature.common.AppBody
import com.devobject.ffmpegtools.feature.common.AppButton
import com.devobject.ffmpegtools.feature.common.AppCircularProgressIndicator
import com.devobject.ffmpegtools.feature.common.AppClickableCard
import com.devobject.ffmpegtools.feature.common.AppIcon
import com.devobject.ffmpegtools.feature.common.AppIconButton
import com.devobject.ffmpegtools.feature.common.AppOutlinedButton
import com.devobject.ffmpegtools.feature.common.AppTextButton
import com.devobject.ffmpegtools.feature.common.SectionCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun ShizukuBrowserScreen(
    uiState: AppUiState,
    viewModel: MainViewModel,
    navController: NavController
) {
    LaunchedEffect(uiState.browserPath) {
        if (uiState.browserEntries.isEmpty() && !uiState.isBrowserLoading && uiState.browserError == null) {
            viewModel.loadBrowserPath(uiState.browserPath)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatusCard(uiState, viewModel)
        }

        item {
            PathBreadcrumb(
                path = uiState.browserPath,
                onSegmentClick = { viewModel.loadBrowserPath(it) },
                onRefresh = { viewModel.loadBrowserPath(uiState.browserPath) },
                onUp = { viewModel.openBrowserParent() }
            )
        }

        if (uiState.isBrowserLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    AppCircularProgressIndicator()
                    AppBody("正在读取目录...")
                }
            }
        }

        uiState.browserError?.let { error ->
            item {
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.errorContainer,
                        contentColor = MiuixTheme.colorScheme.onErrorContainer
                    )
                ) {
                    AppBody(
                        text = error,
                        color = MiuixTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (!uiState.isBrowserLoading && uiState.browserEntries.isEmpty() && uiState.browserError == null) {
            item {
                SectionCard(title = "") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppIcon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = outlineColor()
                        )
                        AppBody("目录为空", color = outlineColor())
                    }
                }
            }
        }

        items(uiState.browserEntries, key = { it.path }) { entry ->
            BrowserEntryCard(entry = entry, onClick = { viewModel.openBrowserEntry(entry) })
        }
    }

    uiState.browserImportedFile?.let { file ->
        ImportActionDialog(
            fileName = file.name,
            onDismiss = { viewModel.dismissImportedFile() },
            onSelect = { destination ->
                viewModel.prepareImportedFileForNavigation(file)
                navController.navigate(destination.route) {
                    popUpTo(AppDestination.Browser.route) { inclusive = false }
                }
            }
        )
    }
}

@Composable
private fun ImportActionDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onSelect: (AppDestination) -> Unit
) {
    val options = listOf(
        AppDestination.Video to "视频转码",
        AppDestination.Audio to "音频转码",
        AppDestination.Extract to "提取音频",
        AppDestination.Mux to "音视频合并",
        AppDestination.Info to "查看媒体信息"
    )

    OverlayDialog(
        show = true,
        title = "选择对该文件的操作",
        summary = "已导入：$fileName",
        onDismissRequest = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (destination, label) ->
                    AppOutlinedButton(
                        onClick = { onSelect(destination) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppBody(label)
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

@Composable
private fun StatusCard(uiState: AppUiState, viewModel: MainViewModel) {
    SectionCard(title = "") {
        AppBody("当前 Shizuku 状态：${uiState.shizukuState.label}")
        AppBody("服务版本：${uiState.shizukuVersion}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(onClick = { viewModel.loadBrowserPath(uiState.browserPath) }) { AppBody("刷新") }
            AppOutlinedButton(onClick = { viewModel.requestShizukuPermission() }) { AppBody("请求授权") }
        }
    }
}

@Composable
private fun PathBreadcrumb(
    path: String,
    onSegmentClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onUp: () -> Unit
) {
    val segments = path.trim('/').split('/').filter { it.isNotBlank() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppIconButton(onClick = onUp, enabled = path != "/") {
                AppIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上级目录")
            }
            AppIconButton(onClick = onRefresh) {
                AppIcon(Icons.Default.Refresh, contentDescription = "刷新")
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "/",
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSegmentClick("/") }
                )
                var accumulated = ""
                segments.forEachIndexed { index, segment ->
                    accumulated += "/$segment"
                    val target = accumulated
                    Text(
                        text = segment,
                        style = MiuixTheme.textStyles.title3,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onSegmentClick(target) }
                    )
                    if (index < segments.lastIndex) {
                        Text(
                            "/",
                            color = MiuixTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserEntryCard(entry: ShellFileEntry, onClick: () -> Unit) {
    AppClickableCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIcon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.secondary
                }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    entry.name,
                    style = MiuixTheme.textStyles.title3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AppBody(
                    entry.path,
                    color = outlineColor(),
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (!entry.isDirectory) {
                    AppBody(
                        "${entry.formattedSize} · ${entry.formattedDate}",
                        color = outlineColor()
                    )
                }
            }
        }
    }
}

@Composable
private fun outlineColor() = MiuixTheme.colorScheme.onSurfaceContainerVariant
