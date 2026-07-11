package com.devobject.ffmpegtools.app

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.devobject.ffmpegtools.BuildConfig
import com.devobject.ffmpegtools.core.ffmpeg.AudioCodec
import com.devobject.ffmpegtools.core.ffmpeg.BackgroundTask
import com.devobject.ffmpegtools.core.ffmpeg.BackgroundTaskManager
import com.devobject.ffmpegtools.core.ffmpeg.ConcatMode
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegCapabilities
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegCommandBuilder
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegExecutor
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegInstaller
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegProgress
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegProbe
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegRuntime
import com.devobject.ffmpegtools.core.ffmpeg.GifConfig
import com.devobject.ffmpegtools.core.ffmpeg.MediaInfo
import com.devobject.ffmpegtools.core.ffmpeg.HardwareAccelDetector
import com.devobject.ffmpegtools.core.ffmpeg.OutputContainer
import com.devobject.ffmpegtools.core.ffmpeg.TaskStatus
import com.devobject.ffmpegtools.core.ffmpeg.VideoCodec
import com.devobject.ffmpegtools.core.ui.AppThemeMode
import com.devobject.ffmpegtools.core.files.FileAccessManager
import com.devobject.ffmpegtools.core.files.SafFileResolver
import com.devobject.ffmpegtools.core.files.ShellDirectoryResult
import com.devobject.ffmpegtools.core.files.ShellFileBrowser
import com.devobject.ffmpegtools.core.files.ShellFileEntry
import com.devobject.ffmpegtools.core.files.TempFileManager
import com.devobject.ffmpegtools.core.shizuku.ShizukuManager
import com.devobject.ffmpegtools.core.shizuku.ShizukuShellExecutor
import com.devobject.ffmpegtools.feature.trim.ThumbnailExtractor
import com.devobject.ffmpegtools.feature.trim.TrimState
import com.devobject.ffmpegtools.feature.trim.coerceRange
import com.devobject.ffmpegtools.feature.trim.fitPixelsPerMs
import com.devobject.ffmpegtools.feature.trim.maxPixelsPerMs
import com.devobject.ffmpegtools.feature.trim.minPixelsPerMs
import com.devobject.ffmpegtools.feature.trim.viewportEndMs
import com.devobject.ffmpegtools.core.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private typealias TaskContextWithJob = BackgroundTaskManager.TaskContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val installer = FfmpegInstaller(context)
    private val commandBuilder = FfmpegCommandBuilder()
    private val fileAccessManager = FileAccessManager(TempFileManager(context), SafFileResolver(context))
    private val shizukuManager = ShizukuManager()
    private val shellFileBrowser = ShellFileBrowser(ShizukuShellExecutor())
    private val taskManager = BackgroundTaskManager()

    private var runtime: FfmpegRuntime? = null
    private var ffmpegExecutor: FfmpegExecutor? = null

    private val _state = MutableStateFlow(AppUiState(appVersion = BuildConfig.VERSION_NAME))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    val tasks: StateFlow<List<BackgroundTask>> = taskManager.tasks

    init {
        initialize()
    }

    fun initialize() {
        viewModelScope.launch {
            _state.update { it.copy(isInitializing = true, statusMessage = "正在安装 FFmpeg...") }
            Log.d("FfmpegTools", "initialize start")
            runCatching {
                val installed = installer.install()
                runtime = installed
                Log.d("FfmpegTools", "FFmpeg installed at ${installed.ffmpegPath}, libs=${installed.libraryDir}")
                val executor = FfmpegExecutor(context, installed)
                ffmpegExecutor = executor
                _state.update {
                    it.copy(
                        runtimePath = installed.ffmpegPath.absolutePath,
                        statusMessage = "正在检测 FFmpeg 能力..."
                    )
                }
                val capabilities = runCatching { HardwareAccelDetector(executor).detect() }
                    .getOrElse {
                        Log.e("FfmpegTools", "detect failed", it)
                        FfmpegCapabilities(ffmpegVersion = it.message ?: "能力检测失败")
                    }
                Log.d("FfmpegTools", "capabilities encoders=${capabilities.encoders.size}, decoders=${capabilities.decoders.size}")
                _state.update {
                    it.copy(
                        isInitializing = false,
                        capabilities = capabilities,
                        shizukuState = shizukuManager.currentState(),
                        shizukuVersion = shizukuManager.versionName(),
                        statusMessage = "FFmpeg 已就绪"
                    )
                }
            }.onFailure { error ->
                Log.e("FfmpegTools", "initialize failed", error)
                _state.update { it.copy(isInitializing = false, statusMessage = error.message ?: "初始化失败") }
            }
        }
    }

    fun refreshShizuku() {
        _state.update {
            it.copy(
                shizukuState = shizukuManager.currentState(),
                shizukuVersion = shizukuManager.versionName()
            )
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
        refreshShizuku()
    }

    fun setThemeMode(mode: AppThemeMode) {
        _state.update { it.copy(themeMode = mode) }
    }

    fun backgroundCurrentTask() {
        _state.update {
            it.copy(
                isRunning = false,
                currentTaskId = null,
                currentTask = null,
                lastProgress = null,
                progressPercent = null,
                totalDurationMs = null,
                logs = emptyList(),
                statusMessage = "任务已转入后台运行"
            )
        }
    }

    fun cancelCurrentTask() {
        val id = state.value.currentTaskId ?: return
        taskManager.cancelTask(id)
        _state.update {
            it.copy(
                isRunning = false,
                currentTaskId = null,
                currentTask = null,
                lastProgress = null,
                progressPercent = null,
                totalDurationMs = null,
                logs = emptyList()
            )
        }
    }

    fun cancelTask(id: String) = taskManager.cancelTask(id)

    fun clearCompletedTasks() = taskManager.clearCompleted()

    fun loadBrowserPath(path: String = state.value.browserPath) {
        viewModelScope.launch {
            refreshShizuku()
            Log.d("FfmpegTools", "loadBrowserPath: $path")
            _state.update { it.copy(isBrowserLoading = true, browserError = null, browserPath = path) }
            val result = withContext(Dispatchers.IO) {
                runCatching { shellFileBrowser.list(path) }
                    .getOrElse { ShellDirectoryResult(path, emptyList(), it.message ?: "读取目录失败") }
            }
            Log.d("FfmpegTools", "list success entries=${result.entries.size}, error=${result.error}")
            _state.update {
                it.copy(
                    isBrowserLoading = false,
                    browserPath = result.path,
                    browserEntries = result.entries,
                    browserError = result.error
                )
            }
        }
    }

    fun openBrowserParent() {
        val current = state.value.browserPath.trimEnd('/')
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "/").ifBlank { "/" }
        loadBrowserPath(parent)
    }

    fun openBrowserEntry(entry: ShellFileEntry) {
        if (entry.isDirectory) {
            loadBrowserPath(entry.path)
        } else {
            importBrowserFile(entry.path, entry.name)
        }
    }

    fun importBrowserFile(remotePath: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBrowserLoading = true, browserError = null) }
            runCatching {
                val targetDir = File(context.cacheDir, "shizuku-imports").apply { mkdirs() }
                shellFileBrowser.copyRemoteFileToLocal(remotePath, targetDir)
            }.onSuccess { file ->
                _state.update {
                    it.copy(
                        isBrowserLoading = false,
                        browserImportedFile = file,
                        statusMessage = "已导入：${file.absolutePath}"
                    )
                }
                appendLog("Shizuku 导入成功：${file.absolutePath}")
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isBrowserLoading = false,
                        browserError = error.message ?: "导入失败"
                    )
                }
                appendLog("Shizuku 导入失败：${error.message}")
            }
        }
    }

    fun prepareImportedFileForNavigation(file: File) {
        _state.update { it.copy(pendingImportedUri = Uri.fromFile(file), browserImportedFile = null) }
    }

    fun consumePendingImportedUri(): Uri? {
        val uri = state.value.pendingImportedUri
        _state.update { it.copy(pendingImportedUri = null) }
        return uri
    }

    fun dismissImportedFile() {
        _state.update { it.copy(browserImportedFile = null) }
    }

    fun probeMediaInfo(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isRunning = true, probeResult = null, probeError = null, statusMessage = "正在分析媒体信息...") }
            runCatching {
                val executor = ffmpegExecutor ?: error("FFmpeg 尚未初始化")
                val jobDir = withContext(Dispatchers.IO) { fileAccessManager.createJobDirectory() }
                val input = fileAccessManager.importUri(uri, jobDir, "input-probe")
                val info = FfmpegProbe(executor).inspect(input)
                _state.update {
                    it.copy(
                        isRunning = false,
                        probeResult = info,
                        statusMessage = "媒体信息分析完成"
                    )
                }
            }.onFailure { error ->
                Log.e("FfmpegTools", "probe failed", error)
                _state.update {
                    it.copy(
                        isRunning = false,
                        probeError = error.message ?: "分析失败",
                        statusMessage = "媒体信息分析失败"
                    )
                }
            }
        }
    }

    fun clearProbeResult() {
        _state.update { it.copy(probeResult = null, probeError = null) }
    }

    fun handleExternalSharedUri(uri: Uri) {
        _state.update { it.copy(externalSharedUri = uri, showExternalActionDialog = true) }
    }

    fun dismissExternalActionDialog() {
        _state.update { it.copy(showExternalActionDialog = false, externalSharedUri = null) }
    }

    fun prepareExternalFileForNavigation(destination: AppDestination) {
        val uri = state.value.externalSharedUri ?: return
        _state.update {
            it.copy(
                pendingImportedUri = uri,
                externalSharedUri = null,
                showExternalActionDialog = false
            )
        }
    }

    fun cleanTemporaryFiles() {
        viewModelScope.launch {
            fileAccessManager.cleanTemporaryFiles()
            File(context.cacheDir, "trim-preview").deleteRecursively()
            appendLog("已清理临时文件")
        }
    }

    @OptIn(UnstableApi::class)
    fun loadTrimVideo(uri: Uri) {
        releaseTrimPlayer()
        viewModelScope.launch {
            _state.update { it.copy(trimState = TrimState(videoUri = uri, isLoadingThumbnails = true)) }
            runCatching {
                val executor = ffmpegExecutor ?: error("FFmpeg 尚未初始化")
                val previewDir = File(context.cacheDir, "trim-preview").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val input = fileAccessManager.importUri(uri, previewDir, "trim-input")
                val info = FfmpegProbe(executor).inspect(input)
                val duration = info.durationMs ?: 0L
                val videoStream = info.streams.firstOrNull { it.codecType == "video" }
                val videoWidth = videoStream?.width
                val videoHeight = videoStream?.height

                val player = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                }

                _state.update {
                    val initialTrim = TrimState(
                        videoUri = uri,
                        inputFile = input,
                        videoWidth = videoWidth,
                        videoHeight = videoHeight,
                        durationMs = duration,
                        endMs = duration,
                        player = player,
                        pixelsPerMs = 0f
                    )
                    it.copy(trimState = initialTrim)
                }

                val thumbnails = ThumbnailExtractor(executor).extract(input, duration, count = 20)
                _state.update { current ->
                    current.copy(trimState = current.trimState.copy(thumbnails = thumbnails, isLoadingThumbnails = false))
                }
            }.onFailure { error ->
                Log.e("FfmpegTools", "load trim video failed", error)
                _state.update { it.copy(statusMessage = error.message ?: "加载视频失败", trimState = TrimState()) }
            }
        }
    }

    fun updateTrimPosition(positionMs: Long) {
        val trim = _state.value.trimState
        val coerced = positionMs.coerceIn(0, trim.durationMs)
        trim.player?.let { player ->
            if (kotlin.math.abs(player.currentPosition - coerced) > 100) {
                player.seekTo(coerced)
            }
        }
        _state.update { it.copy(trimState = trim.copy(currentPositionMs = coerced)) }
    }

    fun updateTrimRange(startMs: Long, endMs: Long) {
        val trim = _state.value.trimState
        val newStart = startMs.coerceIn(0, trim.durationMs)
        val newEnd = endMs.coerceIn(newStart, trim.durationMs)
        _state.update { it.copy(trimState = trim.copy(startMs = newStart, endMs = newEnd)) }
    }

    fun updateTrimViewport(viewportStartMs: Float, pixelsPerMs: Float, widthPx: Float) {
        val trim = _state.value.trimState
        val minScale = trim.minPixelsPerMs(widthPx)
        val maxScale = trim.maxPixelsPerMs()
        val newScale = pixelsPerMs.coerceIn(minScale, maxScale)
        val visibleMs = widthPx / newScale
        val maxStart = (trim.durationMs - visibleMs).coerceAtLeast(0f)
        val newStart = viewportStartMs.coerceIn(0f, maxStart)
        _state.update { it.copy(trimState = trim.copy(viewportStartMs = newStart, pixelsPerMs = newScale)) }
    }

    fun zoomAroundPivot(pivotX: Float, scaleFactor: Float, widthPx: Float) {
        val trim = _state.value.trimState
        val minScale = trim.minPixelsPerMs(widthPx)
        val maxScale = trim.maxPixelsPerMs()
        val newScale = (trim.pixelsPerMs * scaleFactor).coerceIn(minScale, maxScale)
        if (newScale == trim.pixelsPerMs) return
        val pivotTimeMs = trim.viewportStartMs + pivotX / trim.pixelsPerMs
        val newStart = pivotTimeMs - pivotX / newScale
        updateTrimViewport(newStart, newScale, widthPx)
    }

    fun zoomIn(widthPx: Float) {
        zoomAroundPivot(widthPx / 2f, 2f, widthPx)
    }

    fun zoomOut(widthPx: Float) {
        zoomAroundPivot(widthPx / 2f, 0.5f, widthPx)
    }

    fun zoomFit(widthPx: Float) {
        val trim = _state.value.trimState
        val fitScale = trim.fitPixelsPerMs(widthPx)
        updateTrimViewport(0f, fitScale, widthPx)
    }

    fun releaseTrimPlayer() {
        _state.value.trimState.player?.let { player ->
            player.release()
            _state.update {
                it.copy(
                    trimState = it.trimState.copy(
                        player = null,
                        thumbnails = emptyList(),
                        isLoadingThumbnails = false
                    )
                )
            }
        }
    }

    fun runVideoTrim(
        outputUri: Uri?,
        outputName: String,
        outputContainer: OutputContainer,
        videoCodec: VideoCodec,
        audioCodec: AudioCodec,
        copyMode: Boolean
    ) {
        val trim = _state.value.trimState
        val inputFile = trim.inputFile
        val videoUri = trim.videoUri
        val startMs = trim.startMs
        val endMs = trim.endMs
        if (videoUri == null || inputFile == null) {
            _state.update { it.copy(statusMessage = "未选择视频") }
            return
        }
        if (endMs <= startMs) {
            _state.update { it.copy(statusMessage = "裁剪范围无效") }
            return
        }
        runJob("视频裁剪") { ctx, jobDir ->
            val duration = endMs - startMs
            val fullName = "${outputName.ifBlank { "trimmed" }}.${outputContainer.extension}"
            val output = fileAccessManager.outputFile(jobDir, fullName)
            ctx.executeFfmpeg(
                commandBuilder.trimVideo(inputFile, output, startMs, endMs, videoCodec, audioCodec, copyMode),
                duration
            )
            exportOutput(output, outputUri)
            output
        }
    }

    fun runVideoTranscode(
        inputUri: Uri,
        outputUri: Uri?,
        outputName: String,
        outputContainer: OutputContainer,
        videoCodec: VideoCodec,
        audioCodec: AudioCodec,
        videoBitrate: String,
        audioBitrate: String
    ) = runJob("视频转码") { ctx, jobDir ->
        val input = fileAccessManager.importUri(inputUri, jobDir, "input-video")
        val duration = ctx.probeDuration(input)
        val fullName = "${outputName.ifBlank { "output" }}.${outputContainer.extension}"
        val output = fileAccessManager.outputFile(jobDir, fullName)
        val command = commandBuilder.videoTranscode(input, output, videoCodec, audioCodec, videoBitrate, audioBitrate)
        ctx.executeFfmpeg(command, duration)
        exportOutput(output, outputUri)
        output
    }

    fun runAudioTranscode(
        inputUri: Uri,
        outputUri: Uri?,
        outputName: String,
        outputContainer: OutputContainer,
        audioCodec: AudioCodec,
        audioBitrate: String
    ) = runJob("音频转码") { ctx, jobDir ->
        val input = fileAccessManager.importUri(inputUri, jobDir, "input-audio")
        val duration = ctx.probeDuration(input)
        val fullName = "${outputName.ifBlank { "output" }}.${outputContainer.extension}"
        val output = fileAccessManager.outputFile(jobDir, fullName)
        ctx.executeFfmpeg(commandBuilder.audioTranscode(input, output, audioCodec, audioBitrate), duration)
        exportOutput(output, outputUri)
        output
    }

    fun runExtractAudio(
        inputUri: Uri,
        outputUri: Uri?,
        outputName: String,
        outputContainer: OutputContainer,
        audioCodec: AudioCodec,
        audioBitrate: String
    ) = runJob("提取音频") { ctx, jobDir ->
        val input = fileAccessManager.importUri(inputUri, jobDir, "input-video")
        val duration = ctx.probeDuration(input)
        val fullName = "${outputName.ifBlank { "audio" }}.${outputContainer.extension}"
        val output = fileAccessManager.outputFile(jobDir, fullName)
        ctx.executeFfmpeg(commandBuilder.extractAudio(input, output, audioCodec, audioBitrate), duration)
        exportOutput(output, outputUri)
        output
    }

    fun runMux(
        videoUri: Uri,
        audioUri: Uri,
        outputUri: Uri?,
        outputName: String,
        outputContainer: OutputContainer,
        audioCodec: AudioCodec,
        audioBitrate: String,
        shortest: Boolean,
        keepOriginalAudio: Boolean
    ) = runJob("音视频合并") { ctx, jobDir ->
        val video = fileAccessManager.importUri(videoUri, jobDir, "video-input")
        val audio = fileAccessManager.importUri(audioUri, jobDir, "audio-input")
        val duration = ctx.probeDuration(video)
        val fullName = "${outputName.ifBlank { "muxed" }}.${outputContainer.extension}"
        val output = fileAccessManager.outputFile(jobDir, fullName)
        ctx.executeFfmpeg(
            commandBuilder.muxAudioVideo(video, audio, output, audioCodec, audioBitrate, shortest, keepOriginalAudio),
            duration
        )
        exportOutput(output, outputUri)
        output
    }

    fun runGifConvert(
        inputUri: Uri,
        outputUri: Uri?,
        outputName: String,
        config: GifConfig
    ) = runJob("视频转 GIF") { ctx, jobDir ->
        val input = fileAccessManager.importUri(inputUri, jobDir, "input-video")
        val duration = ctx.probeDuration(input)
        val output = fileAccessManager.outputFile(jobDir, "${outputName.ifBlank { "output" }}.gif")
        ctx.executeFfmpeg(commandBuilder.gifConvert(input, output, config), duration)
        exportOutput(output, outputUri)
        output
    }

    fun runMultiVideoMerge(
        inputUris: List<Uri>,
        outputUri: Uri?,
        outputName: String,
        outputContainer: OutputContainer,
        mode: ConcatMode
    ) = runJob("多视频合并") { ctx, jobDir ->
        require(inputUris.size >= 2) { "至少需要选择 2 个视频" }
        val inputs = inputUris.mapIndexed { index, uri ->
            fileAccessManager.importUri(uri, jobDir, "input-$index")
        }
        val fullName = "${outputName.ifBlank { "merged" }}.${outputContainer.extension}"
        val output = fileAccessManager.outputFile(jobDir, fullName)
        val duration = inputs.mapNotNull { ctx.probeDuration(it) }.sum().takeIf { it > 0 }
        ctx.executeFfmpeg(commandBuilder.concatMedia(inputs, output, mode, outputContainer), duration)
        exportOutput(output, outputUri)
        output
    }

    fun runMultiAudioMerge(
        inputUris: List<Uri>,
        outputUri: Uri?,
        outputName: String,
        outputContainer: OutputContainer,
        mode: ConcatMode
    ) = runJob("多音频合并") { ctx, jobDir ->
        require(inputUris.size >= 2) { "至少需要选择 2 个音频" }
        val inputs = inputUris.mapIndexed { index, uri ->
            fileAccessManager.importUri(uri, jobDir, "input-$index")
        }
        val fullName = "${outputName.ifBlank { "merged" }}.${outputContainer.extension}"
        val output = fileAccessManager.outputFile(jobDir, fullName)
        val duration = inputs.mapNotNull { ctx.probeDuration(it) }.sum().takeIf { it > 0 }
        ctx.executeFfmpeg(commandBuilder.concatMedia(inputs, output, mode, outputContainer), duration)
        exportOutput(output, outputUri)
        output
    }

    private suspend fun exportOutput(output: File, outputUri: Uri?) {
        if (outputUri != null) {
            fileAccessManager.exportToUri(output, outputUri)
            return
        }
        // 优先尝试标准文件 API（不依赖 Shizuku），适用于 Android 9 及旧存储模式。
        val standardPath = fileAccessManager.copyToDefaultDownloadDir(output)
        if (standardPath != null) {
            appendLog("已保存到默认目录：$standardPath")
            return
        }
        // 标准 API 失败时，回退到 Shizuku。
        if (shizukuManager.currentState() == ShizukuState.AUTHORIZED) {
            val path = shellFileBrowser.copyLocalFileToRemote(output, DEFAULT_OUTPUT_DIR)
            appendLog("已保存到默认目录：$path")
        } else {
            appendLog("无法保存到默认目录，文件保留在：${output.absolutePath}")
        }
    }

    private fun runJob(title: String, block: suspend (TaskContextWithJob, File) -> File): String {
        val executor = ffmpegExecutor
        if (executor == null) {
            _state.update { it.copy(statusMessage = "FFmpeg 尚未初始化") }
            return ""
        }
        val id = taskManager.startTask(title) { ctx ->
            val jobDir = withContext(Dispatchers.IO) { fileAccessManager.createJobDirectory() }
            ctx.log("任务目录：${jobDir.absolutePath}")
            val output = block(ctx, jobDir)
            ctx.log("完成：${output.absolutePath}")
            output
        }
        _state.update {
            it.copy(
                isRunning = true,
                currentTaskId = id,
                currentTask = title,
                lastProgress = null,
                progressPercent = null,
                totalDurationMs = null,
                logs = emptyList(),
                statusMessage = "$title 运行中"
            )
        }
        viewModelScope.launch {
            taskManager.tasks
                .map { list -> list.find { it.id == id } }
                .filterNotNull()
                .first { it.status != TaskStatus.RUNNING }
            val task = taskManager.tasks.value.find { it.id == id } ?: return@launch
            if (state.value.currentTaskId == id) {
                val record = TaskRecord(
                    title = task.title,
                    state = when (task.status) {
                        TaskStatus.COMPLETED -> "完成"
                        TaskStatus.CANCELLED -> "已取消"
                        TaskStatus.FAILED -> "失败"
                        else -> "未知"
                    },
                    outputPath = task.outputPath,
                    message = task.message
                )
                _state.update {
                    it.copy(
                        isRunning = false,
                        currentTaskId = null,
                        currentTask = null,
                        statusMessage = record.message ?: "${task.title} ${record.state}"
                    )
                }
                addTaskRecord(record)
            }
        }
        return id
    }

    private suspend fun TaskContextWithJob.probeDuration(input: File): Long? {
        val executor = ffmpegExecutor ?: return null
        val info = runCatching { FfmpegProbe(executor).inspect(input) }.getOrNull()
        return info?.durationMs
    }

    private suspend fun TaskContextWithJob.executeFfmpeg(arguments: List<String>, totalDurationMs: Long?) {
        log("命令：ffmpeg ${arguments.joinToString(" ")}")
        val result = requireNotNull(ffmpegExecutor).runFfmpeg(
            arguments = arguments,
            cancellationToken = cancellationToken,
            onStdout = { line -> log(line) },
            onStderr = { line -> log(line) },
            onProgress = { progress -> reportProgress(progress, totalDurationMs) }
        )
        if (!result.isSuccess) error(result.stderr.ifBlank { "FFmpeg exitCode=${result.exitCode}" })
    }

    private fun TaskContextWithJob.log(line: String) {
        appendLog(line)
        if (state.value.currentTaskId == id) {
            this@MainViewModel.appendLog(line)
        }
    }

    private fun TaskContextWithJob.reportProgress(progress: FfmpegProgress, totalDurationMs: Long?) {
        updateProgress(progress, totalDurationMs)
        if (state.value.currentTaskId == id) {
            this@MainViewModel.updateProgress(progress, totalDurationMs)
        }
    }

    private fun updateProgress(progress: FfmpegProgress, totalDurationMs: Long?) {
        val percent = if (totalDurationMs != null && totalDurationMs > 0 && progress.outTimeMs != null) {
            ((progress.outTimeMs * 100) / totalDurationMs).toInt().coerceIn(0, 100)
        } else null
        _state.update { it.copy(lastProgress = progress, progressPercent = percent) }
    }

    private fun addTaskRecord(record: TaskRecord) {
        _state.update { it.copy(tasks = listOf(record) + it.tasks.take(19)) }
    }

    private fun appendLog(line: String) {
        Log.d("FfmpegTools", line)
        _state.update { current ->
            val next = (current.logs + line).takeLast(300)
            current.copy(logs = next)
        }
    }

    companion object {
        private const val DEFAULT_OUTPUT_DIR = "/sdcard/Download/FFmpegtools"
    }

}

data class AppUiState(
    val isInitializing: Boolean = false,
    val isRunning: Boolean = false,
    val currentTaskId: String? = null,
    val currentTask: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val statusMessage: String = "等待初始化",
    val runtimePath: String = "未安装",
    val appVersion: String = "0.1.0",
    val capabilities: FfmpegCapabilities = FfmpegCapabilities(),
    val lastProgress: FfmpegProgress? = null,
    val progressPercent: Int? = null,
    val totalDurationMs: Long? = null,
    val logs: List<String> = emptyList(),
    val tasks: List<TaskRecord> = emptyList(),
    val shizukuState: ShizukuState = ShizukuState.SERVICE_NOT_RUNNING,
    val shizukuVersion: String = "未知",
    val browserPath: String = "/sdcard",
    val browserEntries: List<ShellFileEntry> = emptyList(),
    val browserError: String? = null,
    val isBrowserLoading: Boolean = false,
    val browserImportedFile: java.io.File? = null,
    val pendingImportedUri: Uri? = null,
    val probeResult: MediaInfo? = null,
    val probeError: String? = null,
    val externalSharedUri: Uri? = null,
    val showExternalActionDialog: Boolean = false,
    val trimState: TrimState = TrimState()
)

data class TaskRecord(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val state: String,
    val outputPath: String? = null,
    val message: String? = null
)
