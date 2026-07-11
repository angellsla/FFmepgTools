package com.devobject.ffmpegtools.core.tasks

import com.devobject.ffmpegtools.core.ffmpeg.FfmpegProgress
import java.io.File
import java.util.UUID

data class FfmpegTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val state: TaskState = TaskState.Pending,
    val jobDirectory: File? = null,
    val progress: FfmpegProgress? = null,
    val logs: List<String> = emptyList(),
    val output: File? = null,
    val error: String? = null
)

enum class TaskState {
    Pending,
    PreparingInput,
    Running,
    WritingOutput,
    Completed,
    Failed,
    Cancelled
}

class TranscodeTaskManager {
    fun create(title: String, jobDirectory: File): FfmpegTask = FfmpegTask(
        title = title,
        jobDirectory = jobDirectory,
        state = TaskState.Pending
    )
}
