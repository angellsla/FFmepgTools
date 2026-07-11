package com.devobject.ffmpegtools.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devobject.ffmpegtools.app.MainViewModel
import com.devobject.ffmpegtools.core.ffmpeg.BackgroundTask
import com.devobject.ffmpegtools.core.ffmpeg.TaskStatus
import com.devobject.ffmpegtools.feature.common.AppBody
import com.devobject.ffmpegtools.feature.common.AppLinearProgressIndicator
import com.devobject.ffmpegtools.feature.common.AppOutlinedButton
import com.devobject.ffmpegtools.feature.common.SectionCard
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun BackgroundTasksScreen(
    tasks: List<BackgroundTask>,
    viewModel: MainViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (tasks.isEmpty()) {
            item {
                AppBody(
                    "暂无后台任务",
                    color = outlineColor()
                )
            }
        }

        items(tasks.sortedByDescending { it.startTime }, key = { it.id }) { task ->
            TaskCard(task = task, onCancel = { viewModel.cancelTask(task.id) })
        }

        item {
            val hasCompleted = tasks.any { it.status != TaskStatus.RUNNING }
            AppOutlinedButton(
                onClick = { viewModel.clearCompletedTasks() },
                enabled = hasCompleted
            ) {
                AppBody("清除已完成任务")
            }
        }
    }
}

@Composable
private fun TaskCard(task: BackgroundTask, onCancel: () -> Unit) {
    SectionCard(title = "") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBody(task.title)
            StatusLabel(task.status)
        }

        AppBody(
            "开始时间：${timeFormatter.format(Date(task.startTime))}",
            color = secondaryTextColor()
        )

        if (task.status == TaskStatus.RUNNING) {
            AppLinearProgressIndicator(
                progress = (task.progressPercent ?: 0) / 100f,
                modifier = Modifier.fillMaxWidth()
            )
            AppBody(
                "${task.progressPercent ?: 0}%  速度：${task.progress?.speed ?: "未知"}"
            )
        }

        task.outputPath?.let { path ->
            AppBody(
                "输出：$path",
                color = secondaryTextColor()
            )
        }

        task.message?.let { message ->
            AppBody(
                message,
                color = secondaryTextColor()
            )
        }

        if (task.status == TaskStatus.RUNNING) {
            AppOutlinedButton(onClick = onCancel) {
                AppBody("取消")
            }
        }
    }
}

@Composable
private fun StatusLabel(status: TaskStatus) {
    val (text, color) = when (status) {
        TaskStatus.RUNNING -> "运行中" to MiuixTheme.colorScheme.primary
        TaskStatus.COMPLETED -> "已完成" to MiuixTheme.colorScheme.secondary
        TaskStatus.FAILED -> "失败" to MiuixTheme.colorScheme.error
        TaskStatus.CANCELLED -> "已取消" to MiuixTheme.colorScheme.outline
    }
    AppBody(
        text = text,
        color = color
    )
}

@Composable
private fun secondaryTextColor() = MiuixTheme.colorScheme.onSurfaceContainerVariant

@Composable
private fun outlineColor() = MiuixTheme.colorScheme.onSurfaceContainerVariant
