package com.devobject.ffmpegtools.feature.video

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.ffmpeg.AudioCodec
import com.devobject.ffmpegtools.core.ffmpeg.OutputContainer
import com.devobject.ffmpegtools.core.ffmpeg.VideoCodec
import com.devobject.ffmpegtools.feature.common.AppBody
import com.devobject.ffmpegtools.feature.common.AppButton
import com.devobject.ffmpegtools.feature.common.AppIcon
import com.devobject.ffmpegtools.feature.common.AppOutlinedTextField
import com.devobject.ffmpegtools.feature.common.EnumDropdown
import com.devobject.ffmpegtools.feature.common.FilePickerRow
import com.devobject.ffmpegtools.feature.common.LogPanel
import com.devobject.ffmpegtools.feature.common.OutputPickerRow
import com.devobject.ffmpegtools.feature.common.SectionCard
import com.devobject.ffmpegtools.feature.common.TaskProgressPanel

@Composable
fun VideoTranscodeScreen(uiState: AppUiState, viewModel: MainViewModel) {
    var input by remember { mutableStateOf<Uri?>(null) }
    var output by remember { mutableStateOf<Uri?>(null) }
    var outputName by remember { mutableStateOf("output") }
    var outputContainer by remember { mutableStateOf(OutputContainer.Mp4) }
    var videoCodec by remember { mutableStateOf(VideoCodec.Libx264) }
    var audioCodec by remember { mutableStateOf(AudioCodec.Aac) }
    var videoBitrate by remember { mutableStateOf("4000k") }
    var audioBitrate by remember { mutableStateOf("128k") }

    LaunchedEffect(Unit) {
        viewModel.consumePendingImportedUri()?.let { input = it }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "输入 / 输出") {
                FilePickerRow("输入视频", input, { input = it }, "video/*")
                OutputPickerRow("输出位置", output, "$outputName.${outputContainer.extension}", outputContainer.mimeType, { output = it })
                EnumDropdown("输出格式", outputContainer, OutputContainer.videoContainers(), { it.label }) { outputContainer = it }
                AppOutlinedTextField(outputName, { outputName = it }, label = "输出文件名（不含扩展名）", modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            SectionCard(title = "编码设置") {
                val codecs = VideoCodec.entries.filter { codec ->
                    codec.ffmpegName == "copy" || codec.ffmpegName == "mpeg4" || uiState.capabilities.encoders.isEmpty() || uiState.capabilities.hasEncoder(codec.ffmpegName.orEmpty())
                }
                EnumDropdown("视频编码", videoCodec, codecs.ifEmpty { VideoCodec.entries }, { it.label }) { videoCodec = it }
                EnumDropdown("音频编码", audioCodec, AudioCodec.entries.filter { codec ->
                    codec.ffmpegName == "copy" || uiState.capabilities.encoders.isEmpty() || uiState.capabilities.hasEncoder(codec.ffmpegName.orEmpty())
                }.ifEmpty { AudioCodec.entries }, { it.label }) { audioCodec = it }
                AppOutlinedTextField(videoBitrate, { videoBitrate = it }, label = "视频码率")
                AppOutlinedTextField(audioBitrate, { audioBitrate = it }, label = "音频码率")
            }
        }

        item {
            AppButton(
                enabled = input != null && !uiState.isRunning,
                onClick = { viewModel.runVideoTranscode(input!!, output, outputName, outputContainer, videoCodec, audioCodec, videoBitrate, audioBitrate) }
            ) {
                AppIcon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null)
                AppBody(text = "开始转码")
            }
        }

        item { TaskProgressPanel(uiState, onCancel = { viewModel.cancelCurrentTask() }) }
        item { LogPanel(uiState) }
    }
}
