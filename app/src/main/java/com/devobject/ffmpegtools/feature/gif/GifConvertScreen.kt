package com.devobject.ffmpegtools.feature.gif

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.ffmpeg.GifConfig
import com.devobject.ffmpegtools.feature.common.AppBody
import com.devobject.ffmpegtools.feature.common.AppButton
import com.devobject.ffmpegtools.feature.common.AppIcon
import com.devobject.ffmpegtools.feature.common.AppOutlinedTextField
import com.devobject.ffmpegtools.feature.common.AppSlider
import com.devobject.ffmpegtools.feature.common.AppSwitch
import com.devobject.ffmpegtools.feature.common.FilePickerRow
import com.devobject.ffmpegtools.feature.common.LogPanel
import com.devobject.ffmpegtools.feature.common.OutputPickerRow
import com.devobject.ffmpegtools.feature.common.SectionCard
import com.devobject.ffmpegtools.feature.common.TaskProgressPanel

@Composable
fun GifConvertScreen(uiState: AppUiState, viewModel: MainViewModel) {
    var input by remember { mutableStateOf<Uri?>(null) }
    var output by remember { mutableStateOf<Uri?>(null) }
    var outputName by remember { mutableStateOf("output") }
    var fps by remember { mutableIntStateOf(15) }
    var width by remember { mutableIntStateOf(480) }
    var colors by remember { mutableIntStateOf(128) }
    var loop by remember { mutableStateOf(true) }

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
                OutputPickerRow("输出位置", output, "$outputName.gif", "image/gif", { output = it })
                AppOutlinedTextField(outputName, { outputName = it }, label = "输出文件名（不含扩展名）", modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            SectionCard(title = "GIF 参数") {
                AppBody(text = "帧率 (FPS)：$fps")
                AppSlider(
                    value = fps.toFloat(),
                    onValueChange = { fps = it.toInt() },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth()
                )

                AppOutlinedTextField(
                    value = width.toString(),
                    onValueChange = { width = it.toIntOrNull()?.coerceIn(80, 1920) ?: width },
                    label = "输出宽度（高度按比例）",
                    modifier = Modifier.fillMaxWidth()
                )

                AppBody(text = "调色板颜色数：$colors")
                AppSlider(
                    value = colors.toFloat(),
                    onValueChange = { colors = it.toInt() },
                    valueRange = 2f..256f,
                    steps = 253,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppBody(text = "循环播放")
                    AppSwitch(checked = loop, onCheckedChange = { loop = it })
                }
            }
        }

        item {
            AppButton(
                enabled = input != null && !uiState.isRunning,
                onClick = {
                    val config = GifConfig(fps = fps, width = width, colors = colors, loop = if (loop) 0 else -1)
                    viewModel.runGifConvert(input!!, output, outputName, config)
                }
            ) {
                AppIcon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null)
                AppBody(text = "开始转换")
            }
        }

        item { TaskProgressPanel(uiState, onCancel = { viewModel.cancelCurrentTask() }) }
        item { LogPanel(uiState) }
    }
}
