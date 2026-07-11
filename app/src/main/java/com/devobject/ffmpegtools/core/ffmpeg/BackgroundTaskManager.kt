package com.devobject.ffmpegtools.core.ffmpeg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class TaskStatus { RUNNING, COMPLETED, FAILED, CANCELLED }

data class BackgroundTask(
    val id: String,
    val title: String,
    val status: TaskStatus = TaskStatus.RUNNING,
    val progress: FfmpegProgress? = null,
    val progressPercent: Int? = null,
    val outputPath: String? = null,
    val message: String? = null,
    val logs: List<String> = emptyList(),
    val startTime: Long = System.currentTimeMillis()
)

class BackgroundTaskManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<BackgroundTask>>(emptyList())
    val tasks: StateFlow<List<BackgroundTask>> = _tasks.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val contexts = ConcurrentHashMap<String, TaskContext>()

    fun startTask(title: String, block: suspend (TaskContext) -> File): String {
        val id = UUID.randomUUID().toString()
        _tasks.update { it + BackgroundTask(id = id, title = title) }
        val ctx = TaskContext(id, title)
        contexts[id] = ctx
        val job = scope.launch {
            try {
                val output = block(ctx)
                updateTask(id) {
                    it.copy(status = TaskStatus.COMPLETED, outputPath = output.absolutePath, message = "完成")
                }
            } catch (_: CancellationException) {
                updateTask(id) { it.copy(status = TaskStatus.CANCELLED, message = "已取消") }
            } catch (e: Throwable) {
                updateTask(id) { it.copy(status = TaskStatus.FAILED, message = e.message ?: "失败") }
            }
        }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id)
            contexts.remove(id)
        }
        return id
    }

    fun cancelTask(id: String) {
        contexts[id]?.cancel()
        jobs[id]?.cancel()
    }

    fun clearCompleted() {
        _tasks.update { it.filter { task -> task.status == TaskStatus.RUNNING } }
    }

    private fun updateTask(id: String, transform: (BackgroundTask) -> BackgroundTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    inner class TaskContext(
        val id: String,
        val title: String
    ) {
        private val cancellation = MutableCancellationToken()
        val cancellationToken: CancellationToken = cancellation
        val isActive: Boolean get() = scope.isActive && !cancellation.isCancelled

        fun cancel() {
            cancellation.isCancelled = true
        }

        fun appendLog(line: String) {
            updateTask(id) { it.copy(logs = it.logs + line) }
        }

        fun updateProgress(progress: FfmpegProgress, totalDurationMs: Long?) {
            val percent = if (totalDurationMs != null && totalDurationMs > 0 && progress.outTimeMs != null) {
                ((progress.outTimeMs * 100) / totalDurationMs).toInt().coerceIn(0, 100)
            } else null
            updateTask(id) { it.copy(progress = progress, progressPercent = percent) }
        }
    }

    private class MutableCancellationToken : CancellationToken {
        private val cancelled = AtomicBoolean(false)
        override var isCancelled: Boolean
            get() = cancelled.get()
            set(value) = cancelled.set(value)
    }
}
