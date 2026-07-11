package com.devobject.ffmpegtools.feature.merge

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.ffmpeg.ConcatMode
import com.devobject.ffmpegtools.core.ffmpeg.OutputContainer
import com.devobject.ffmpegtools.feature.common.AppBody
import com.devobject.ffmpegtools.feature.common.AppButton
import com.devobject.ffmpegtools.feature.common.AppIcon
import com.devobject.ffmpegtools.feature.common.AppOutlinedTextField
import com.devobject.ffmpegtools.feature.common.EnumDropdown
import com.devobject.ffmpegtools.feature.common.LogPanel
import com.devobject.ffmpegtools.feature.common.MultiFilePicker
import com.devobject.ffmpegtools.feature.common.OutputPickerRow
import com.devobject.ffmpegtools.feature.common.SectionCard
import com.devobject.ffmpegtools.feature.common.TaskProgressPanel

@Composable
fun MultiAudioMergeScreen(uiState: AppUiState, viewModel: MainViewModel) {
    var inputUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var output by remember { mutableStateOf<Uri?>(null) }
    var outputName by remember { mutableStateOf("merged") }
    var outputContainer by remember { mutableStateOf(OutputContainer.Mp3) }
    var mode by remember { mutableStateOf(ConcatMode.Demuxer) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionCard(title = "输入文件") {
                MultiFilePicker(
                    label = "选择音频（至少 2 个）",
                    uris = inputUris,
                    onUrisChange = { inputUris = it },
                    mimeType = "audio/*"
                )
            }
        }

        item {
            SectionCard(title = "输出 / 模式") {
                OutputPickerRow("输出位置", output, "$outputName.${outputContainer.extension}", outputContainer.mimeType, { output = it })
                AppOutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = "输出文件名（不含扩展名）",
                    modifier = Modifier.fillMaxWidth()
                )
                EnumDropdown("输出格式", outputContainer, OutputContainer.audioContainers(), { it.label }) { outputContainer = it }
                EnumDropdown("合并模式", mode, ConcatMode.entries, { it.label }) { mode = it }
                AppBody(
                    text = "快速合并要求所有音频编码参数完全相同；不同格式请选择强制转码。"
                )
            }
        }

        item {
            AppButton(
                enabled = inputUris.size >= 2 && !uiState.isRunning,
                onClick = { viewModel.runMultiAudioMerge(inputUris, output, outputName, outputContainer, mode) }
            ) {
                AppIcon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null)
                AppBody("开始合并")
            }
        }

        item { TaskProgressPanel(uiState, onCancel = { viewModel.cancelCurrentTask() }) }
        item { LogPanel(uiState) }
    }
}
