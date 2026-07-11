package com.devobject.ffmpegtools.feature.common

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devobject.ffmpegtools.app.AppUiState
import com.devobject.ffmpegtools.ui.theme.LocalUiDimensions
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FilePickerRow(label: String, uri: Uri?, onPicked: (Uri) -> Unit, mimeType: String = "*/*") {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        picked?.let(onPicked)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(14.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surface,
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { launcher.launch(arrayOf(mimeType)) }) {
                    Text("选择")
                }
                Icon(Icons.Default.FileOpen, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceContainerVariant)
                Text(
                    text = uri?.toString() ?: "未选择",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (uri == null) MiuixTheme.colorScheme.onSurfaceContainerVariant else MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun OutputPickerRow(label: String, uri: Uri?, fileName: String, mimeType: String, onPicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { picked ->
        picked?.let(onPicked)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceContainerVariant)
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(14.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surface,
                contentColor = MiuixTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { launcher.launch(fileName) },
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("选择输出")
                }
                Icon(Icons.Default.Save, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceContainerVariant)
                Text(
                    text = uri?.toString() ?: "未选择则保留在缓存目录",
                    modifier = Modifier.weight(1f),
                    style = MiuixTheme.textStyles.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (uri == null) MiuixTheme.colorScheme.onSurfaceContainerVariant else MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun <T> EnumDropdown(label: String, value: T, values: List<T>, display: (T) -> String, onSelected: (T) -> Unit) {
    val selectedIndex = values.indexOf(value).coerceAtLeast(0)
    val uiDims = LocalUiDimensions.current
    val itemHeight = 56.dp * uiDims.densityScale
    OverlayDropdownPreference(
        title = label,
        summary = display(value),
        items = values.map(display),
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { onSelected(values[it]) },
        modifier = Modifier.fillMaxWidth(),
        maxHeight = itemHeight * 4
    )
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val uiDims = LocalUiDimensions.current
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(uiDims.sectionPadding),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface,
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(uiDims.cardSpacing)) {
            if (title.isNotBlank()) {
                Text(title, style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurface)
            }
            content()
        }
    }
}

@Composable
fun LogPanel(uiState: AppUiState) {
    val uiDims = LocalUiDimensions.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(uiDims.sectionPadding)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(uiDims.bodySpacing)) {
            Text("任务日志", style = MiuixTheme.textStyles.title2)
            Text(
                text = uiState.logs.takeLast(80).joinToString("\n").ifBlank { "暂无日志" },
                style = MiuixTheme.textStyles.body2,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = uiDims.textFieldHeight, max = uiDims.previewMaxHeight)
                    .verticalScroll(rememberScrollState()),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant
            )
        }
    }
}

@Composable
fun TaskProgressPanel(
    uiState: AppUiState,
    onCancel: () -> Unit
) {
    val progress = uiState.lastProgress
    if (uiState.isRunning || progress != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${uiState.currentTask ?: "任务"} 运行中", style = MiuixTheme.textStyles.title2)
                val percent = uiState.progressPercent
                if (percent != null) {
                    LinearProgressIndicator(progress = percent / 100f, modifier = Modifier.fillMaxWidth())
                    Text("$percent%", style = MiuixTheme.textStyles.body1)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                progress?.let {
                    Text("速度：${it.speed ?: "未知"}  输出时间：${it.outTimeMs ?: 0} ms", style = MiuixTheme.textStyles.body1)
                }
                if (uiState.isRunning) {
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("取消任务")
                    }
                }
            }
        }
    }
}

@Composable
fun AppHeadline(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = MiuixTheme.textStyles.title2, modifier = modifier)
}

@Composable
fun AppBody(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    Text(
        text = text,
        modifier = modifier,
        style = MiuixTheme.textStyles.body1,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
        content = content
    )
}

@Composable
fun AppOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(),
        content = content
    )
}

@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable
fun AppIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tint = tint)
}

@Composable
fun AppOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardOptions = keyboardOptions
    )
}

@Composable
fun AppClickableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.defaultColors(
        color = MiuixTheme.colorScheme.surface,
        contentColor = MiuixTheme.colorScheme.onSurface
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        insideMargin = PaddingValues(12.dp),
        colors = colors,
        content = content
    )
}

@Composable
fun AppCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val uiDims = LocalUiDimensions.current
    val checkboxScale = if (uiDims.densityScale <= 0.8f) 0.6f else 1f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = { onCheckedChange(!checked) },
            modifier = Modifier.scale(checkboxScale)
        )
        Text(text = label, style = MiuixTheme.textStyles.body1)
    }
}

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
}

@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps
    )
}

@Composable
fun AppLinearProgressIndicator(
    progress: Float?,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(progress = progress, modifier = modifier)
}

@Composable
fun AppCircularProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    CircularProgressIndicator(progress = progress, modifier = modifier)
}

@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}

@Composable
fun SettingsIcon(
    imageVector: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    val color = MiuixTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(36.dp)
            .background(color.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = color
        )
    }
}

@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    summary: String? = null,
    modifier: Modifier = Modifier,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            startAction?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            endAction?.invoke()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null
) {
    SettingsRow(
        title = title,
        summary = summary,
        modifier = modifier,
        startAction = startAction,
        endAction = endAction
    )
}

@Composable
fun SettingsAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    startAction: @Composable (() -> Unit)? = null,
    endAction: @Composable (() -> Unit)? = null
) {
    val chevron: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant
        )
    }
    SettingsRow(
        title = title,
        summary = summary,
        modifier = modifier,
        startAction = startAction,
        endAction = endAction ?: chevron,
        onClick = onClick
    )
}
