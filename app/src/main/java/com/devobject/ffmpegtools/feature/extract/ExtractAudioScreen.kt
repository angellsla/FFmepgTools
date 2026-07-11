package com.devobject.ffmpegtools.feature.extract

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
fun ExtractAudioScreen(uiState: AppUiState, viewModel: MainViewModel) {
    var input by remember { mutableStateOf<Uri?>(null) }
    var output by remember { mutableStateOf<Uri?>(null) }
    var outputName by remember { mutableStateOf("extracted") }
    var outputContainer by remember { mutableStateOf(OutputContainer.M4a) }
    var audioCodec by remember { mutableStateOf(AudioCodec.Copy) }
    var audioBitrate by remember { mutableStateOf("192k") }
    val codecs = listOf(AudioCodec.Copy, AudioCodec.Aac, AudioCodec.Mp3, AudioCodec.Opus).filter { codec ->
        codec == AudioCodec.Copy || uiState.capabilities.encoders.isEmpty() || uiState.capabilities.hasEncoder(codec.ffmpegName.orEmpty())
    }

    LaunchedEffect(Unit) {
        viewModel.consumePendingImportedUri()?.let { input = it }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "输入 / 输出") {
                FilePickerRow("输入视频", input, { input = it }, "video/*")
                OutputPickerRow("输出位置", output, "$outputName.${outputContainer.extension}", outputContainer.mimeType, { output = it })
                EnumDropdown("输出格式", outputContainer, OutputContainer.audioContainers(), { it.label }) { outputContainer = it }
                AppOutlinedTextField(outputName, { outputName = it }, label = "输出文件名（不含扩展名）", modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            SectionCard(title = "音频设置") {
                EnumDropdown("提取方式", audioCodec, codecs.ifEmpty { listOf(AudioCodec.Copy, AudioCodec.Aac) }, { it.label }) { audioCodec = it }
                AppOutlinedTextField(audioBitrate, { audioBitrate = it }, label = "音频码率（转码时使用）")
            }
        }

        item {
            AppButton(
                enabled = input != null && !uiState.isRunning,
                onClick = { viewModel.runExtractAudio(input!!, output, outputName, outputContainer, audioCodec, audioBitrate) }
            ) {
                AppIcon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null)
                AppBody(text = "开始提取")
            }
        }

        item { TaskProgressPanel(uiState, onCancel = { viewModel.cancelCurrentTask() }) }
        item { LogPanel(uiState) }
    }
}
