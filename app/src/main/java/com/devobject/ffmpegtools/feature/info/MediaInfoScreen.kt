package com.devobject.ffmpegtools.feature.info

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.ffmpeg.MediaInfo
import com.devobject.ffmpegtools.core.ffmpeg.MediaStreamInfo
import com.devobject.ffmpegtools.feature.common.AppBody
import com.devobject.ffmpegtools.feature.common.AppButton
import com.devobject.ffmpegtools.feature.common.AppCircularProgressIndicator
import com.devobject.ffmpegtools.feature.common.SectionCard
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MediaInfoScreen(
    uiState: AppUiState,
    viewModel: MainViewModel
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.probeMediaInfo(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.consumePendingImportedUri()?.let { viewModel.probeMediaInfo(it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(title = "") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppBody("选择要分析的音视频文件")
                    AppButton(
                        onClick = { launcher.launch("video/*,audio/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AppBody("选择文件")
                    }
                }
            }
        }

        if (uiState.isRunning && uiState.probeResult == null) {
            item {
                SectionCard(title = "") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppCircularProgressIndicator()
                        AppBody("正在分析...")
                    }
                }
            }
        }

        uiState.probeError?.let { error ->
            item {
                Card(
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.errorContainer,
                        contentColor = MiuixTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppBody(
                        text = error,
                        color = MiuixTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        uiState.probeResult?.let { info ->
            item { FormatInfoCard(info) }
            if (info.streams.isNotEmpty()) {
                item {
                    AppBody(
                        "流信息",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            info.streams.forEach { stream ->
                item { StreamInfoCard(stream) }
            }
            item {
                SectionCard(title = "原始 JSON") {
                    AppBody(
                        text = info.rawJson,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatInfoCard(info: MediaInfo) {
    SectionCard(title = "格式信息") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            info.formatName?.let { InfoRow("格式", it) }
            info.formatLongName?.let { InfoRow("完整格式", it) }
            info.durationMs?.let { InfoRow("时长", formatDuration(it)) }
            info.bitRate?.let { InfoRow("总码率", "${it / 1000} kbps") }
            info.size?.let { InfoRow("大小", formatFileSize(it)) }
        }
    }
}

@Composable
private fun StreamInfoCard(stream: MediaStreamInfo) {
    val typeLabel = when (stream.codecType) {
        "video" -> "视频流 #${stream.index}"
        "audio" -> "音频流 #${stream.index}"
        "subtitle" -> "字幕流 #${stream.index}"
        else -> "流 #${stream.index}"
    }
    SectionCard(title = typeLabel) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InfoRow("编码", stream.codecLongName ?: stream.codecName)
            if (stream.codecType == "video") {
                if (stream.width != null && stream.height != null) {
                    InfoRow("分辨率", "${stream.width}x${stream.height}")
                }
                stream.displayAspectRatio?.let { InfoRow("宽高比", it) }
                stream.pixelFormat?.let { InfoRow("像素格式", it) }
                stream.frameRate?.let { InfoRow("帧率", "%.2f fps".format(it)) }
            }
            if (stream.codecType == "audio") {
                stream.sampleRate?.let { InfoRow("采样率", "$it Hz") }
                stream.channels?.let { InfoRow("声道数", "$it") }
                stream.channelLayout?.let { InfoRow("声道布局", it) }
            }
            stream.language?.let { InfoRow("语言", it) }
            stream.title?.let { InfoRow("标题", it) }
            stream.bitRate?.let { InfoRow("码率", "${it / 1000} kbps") }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AppBody(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant
        )
        AppBody(text = value)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return "%.2f %s".format(size, units[unitIndex])
}
