package com.devobject.ffmpegtools.feature.trim

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.devobject.ffmpegtools.ui.theme.LocalUiDimensions
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.ffmpeg.AudioCodec
import com.devobject.ffmpegtools.core.ffmpeg.OutputContainer
import com.devobject.ffmpegtools.core.ffmpeg.VideoCodec
import com.devobject.ffmpegtools.feature.common.AppBody
import com.devobject.ffmpegtools.feature.common.AppButton
import com.devobject.ffmpegtools.feature.common.AppCheckboxRow
import com.devobject.ffmpegtools.feature.common.AppIcon
import com.devobject.ffmpegtools.feature.common.AppIconButton
import com.devobject.ffmpegtools.feature.common.AppOutlinedTextField
import com.devobject.ffmpegtools.feature.common.EnumDropdown
import com.devobject.ffmpegtools.feature.common.FilePickerRow
import com.devobject.ffmpegtools.feature.common.LogPanel
import com.devobject.ffmpegtools.feature.common.OutputPickerRow
import com.devobject.ffmpegtools.feature.common.SectionCard
import com.devobject.ffmpegtools.feature.common.TaskProgressPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(UnstableApi::class)
@Composable
fun VideoTrimScreen(uiState: AppUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiDims = LocalUiDimensions.current
    val trimState = uiState.trimState
    val player = trimState.player
    val videoAspectRatio = trimState.videoAspectRatio() ?: (16f / 9f)

    var outputUri by remember { mutableStateOf<Uri?>(null) }
    var outputName by remember { mutableStateOf("trimmed") }
    var outputContainer by remember { mutableStateOf(OutputContainer.Mp4) }
    var videoCodec by remember { mutableStateOf(VideoCodec.Libx264) }
    var audioCodec by remember { mutableStateOf(AudioCodec.Aac) }
    var copyMode by remember { mutableStateOf(false) }
    var timelineWidth by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose { viewModel.releaseTrimPlayer() }
    }

    LaunchedEffect(Unit) {
        viewModel.consumePendingImportedUri()?.let { viewModel.loadTrimVideo(it) }
    }

    LaunchedEffect(trimState.videoUri, trimState.player) {
        val uri = trimState.videoUri
        if (uri != null && trimState.player == null && !trimState.isLoadingThumbnails) {
            viewModel.loadTrimVideo(uri)
        }
    }

    LaunchedEffect(timelineWidth) {
        if (timelineWidth > 0f && trimState.durationMs > 0 && trimState.pixelsPerMs == 0f) {
            viewModel.zoomFit(timelineWidth)
        }
    }

    LaunchedEffect(player) {
        player ?: return@LaunchedEffect
        while (isActive) {
            delay(100)
            if (player.isPlaying) {
                viewModel.updateTrimPosition(player.currentPosition)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(uiDims.sectionPadding),
        verticalArrangement = Arrangement.spacedBy(uiDims.cardSpacing)
    ) {
        item {
            SectionCard(title = "输入视频") {
                FilePickerRow(
                    label = "选择视频",
                    uri = trimState.videoUri,
                    onPicked = { viewModel.loadTrimVideo(it) },
                    mimeType = "video/*"
                )
            }
        }

        item {
            SectionCard(title = "预览") {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val maxW = maxWidth
                    val maxH = uiDims.previewMaxHeight
                    val minH = uiDims.previewMinHeight

                    var boxW = maxW
                    var boxH = (maxW / videoAspectRatio).coerceAtMost(maxH)
                    if (boxH < minH) {
                        boxH = minH
                        boxW = (minH * videoAspectRatio).coerceAtMost(maxW)
                    }

                    Box(
                        modifier = Modifier
                            .width(boxW)
                            .height(boxH)
                    ) {
                        if (player != null) {
                            AndroidView(
                                factory = {
                                    PlayerView(context).apply {
                                        this.player = player
                                        useController = true
                                        controllerShowTimeoutMs = 2500
                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                update = { it.player = player },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                TrimBody(
                                    text = if (trimState.isLoadingThumbnails) "正在加载视频..." else "请先选择视频",
                                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconButton(
                        onClick = { player?.play() },
                        enabled = player != null
                    ) { AppIcon(Icons.Default.PlayArrow, "播放") }
                    AppIconButton(
                        onClick = { player?.pause() },
                        enabled = player != null
                    ) { AppIcon(Icons.Default.Pause, "暂停") }
                    TrimBody(
                        text = "${formatDuration(trimState.currentPositionMs)} / ${formatDuration(trimState.durationMs)}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            SectionCard(title = "时间轴") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(uiDims.timelineHeight)
                        .onSizeChanged { timelineWidth = it.width.toFloat() }
                ) {
                    if (trimState.durationMs > 0) {
                        VideoTrimTimeline(
                            trimState = trimState,
                            onPositionChange = { viewModel.updateTrimPosition(it) },
                            onRangeChange = { start, end -> viewModel.updateTrimRange(start, end) },
                            onViewportChange = { start, scale ->
                                viewModel.updateTrimViewport(start, scale, timelineWidth)
                            },
                            onZoomAroundPivot = { pivotX, factor ->
                                viewModel.zoomAroundPivot(pivotX, factor, timelineWidth)
                            },
                            onZoomIn = { viewModel.zoomIn(timelineWidth) },
                            onZoomOut = { viewModel.zoomOut(timelineWidth) },
                            onZoomFit = { viewModel.zoomFit(timelineWidth) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TrimBody(
                                text = "选择视频后显示时间轴",
                                color = MiuixTheme.colorScheme.onSurfaceContainerVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "精确定位") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeInputField(
                        label = "入点",
                        timeMs = trimState.startMs,
                        onTimeChange = {
                            viewModel.updateTrimRange(it, trimState.endMs)
                            viewModel.updateTrimPosition(it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TimeInputField(
                        label = "出点",
                        timeMs = trimState.endMs,
                        onTimeChange = { viewModel.updateTrimRange(trimState.startMs, it) },
                        modifier = Modifier.weight(1f)
                    )
                }
                TimeInputField(
                    label = "当前位置",
                    timeMs = trimState.currentPositionMs,
                    onTimeChange = { viewModel.updateTrimPosition(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            SectionCard(title = "输出设置") {
                OutputPickerRow(
                    label = "输出位置",
                    uri = outputUri,
                    fileName = "$outputName.${outputContainer.extension}",
                    mimeType = outputContainer.mimeType,
                    onPicked = { outputUri = it }
                )
                AppOutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = "输出文件名（不含扩展名）",
                    modifier = Modifier.fillMaxWidth()
                )
                EnumDropdown(
                    label = "输出格式",
                    value = outputContainer,
                    values = OutputContainer.videoContainers(),
                    display = { it.label }
                ) { outputContainer = it }
                if (!copyMode) {
                    val codecs = VideoCodec.entries.filter { codec ->
                        codec.ffmpegName == "copy" || codec.ffmpegName == "mpeg4" ||
                            uiState.capabilities.encoders.isEmpty() ||
                            uiState.capabilities.hasEncoder(codec.ffmpegName.orEmpty())
                    }
                    EnumDropdown(
                        label = "视频编码",
                        value = videoCodec,
                        values = codecs.ifEmpty { VideoCodec.entries },
                        display = { it.label }
                    ) { videoCodec = it }
                    EnumDropdown(
                        label = "音频编码",
                        value = audioCodec,
                        values = AudioCodec.entries.filter { codec ->
                            codec.ffmpegName == "copy" ||
                                uiState.capabilities.encoders.isEmpty() ||
                                uiState.capabilities.hasEncoder(codec.ffmpegName.orEmpty())
                        }.ifEmpty { AudioCodec.entries },
                        display = { it.label }
                    ) { audioCodec = it }
                }
                AppCheckboxRow(
                    checked = copyMode,
                    onCheckedChange = { copyMode = it },
                    label = "快速裁剪（复制流，可能不精确）"
                )
            }
        }

        item {
            AppButton(
                onClick = { viewModel.runVideoTrim(outputUri, outputName, outputContainer, videoCodec, audioCodec, copyMode) },
                enabled = trimState.videoUri != null && trimState.endMs > trimState.startMs && !uiState.isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIcon(Icons.Default.ContentCut, null)
                Text("开始裁剪")
            }
        }

        item { TaskProgressPanel(uiState, onCancel = { viewModel.cancelCurrentTask() }) }
        item { LogPanel(uiState) }
    }
}

@Composable
private fun TrimBody(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onSurface
) {
    Text(text = text, modifier = modifier, style = MiuixTheme.textStyles.body1, color = color)
}

@Composable
private fun TimeInputField(
    label: String,
    timeMs: Long,
    onTimeChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(timeMs) { mutableStateOf(formatTimeForInput(timeMs)) }
    var isError by remember { mutableStateOf(false) }

    AppOutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            parseTimeInput(it)?.let { ms ->
                isError = false
                onTimeChange(ms)
            } ?: run { isError = true }
        },
        label = label,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val millis = ms % 1000
    return if (hours > 0) {
        String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    } else {
        String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }
}

private fun formatTimeForInput(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val millis = ms % 1000
    return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
}

private fun parseTimeInput(input: String): Long? {
    val regex = Regex("""^(?:(\d+):)?(?:(\d{1,2}):)?(\d{1,2})(?:\.(\d{1,3}))?$""")
    val match = regex.matchEntire(input.trim()) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutesGroup = match.groupValues[2].toLongOrNull() ?: 0L
    val secondsGroup = match.groupValues[3].toLongOrNull() ?: return null
    val millisRaw = match.groupValues[4].padEnd(3, '0').toLongOrNull() ?: 0L

    val totalMinutes: Long
    val totalSeconds: Long
    if (match.groupValues[2].isEmpty() && match.groupValues[1].isNotEmpty()) {
        totalMinutes = hours
        totalSeconds = minutesGroup
    } else {
        totalMinutes = minutesGroup + hours * 60
        totalSeconds = secondsGroup
    }
    return totalMinutes * 60_000 + totalSeconds * 1000 + millisRaw
}
