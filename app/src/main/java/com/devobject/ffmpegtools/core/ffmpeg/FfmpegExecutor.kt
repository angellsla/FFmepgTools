package com.devobject.ffmpegtools.core.ffmpeg

import android.content.Context
import java.io.File

class FfmpegExecutor(
    private val context: Context,
    private val runtime: FfmpegRuntime,
    private val executor: CommandExecutor = ProcessCommandExecutor()
) {
    private val defaultEnvironment: Map<String, String>
        get() = mapOf(
            "LD_LIBRARY_PATH" to runtime.libraryDir.absolutePath,
            "TMPDIR" to context.cacheDir.absolutePath
        )

    suspend fun runFfmpeg(
        arguments: List<String>,
        cancellationToken: CancellationToken = cancellationToken(),
        onStdout: suspend (String) -> Unit = {},
        onStderr: suspend (String) -> Unit = {},
        onProgress: suspend (FfmpegProgress) -> Unit = {}
    ): CommandResult = executor.execute(
        command = listOf(runtime.ffmpegPath.absolutePath) + arguments,
        environment = defaultEnvironment,
        workingDirectory = runtime.libraryDir,
        cancellationToken = cancellationToken,
        onStdout = onStdout,
        onStderr = onStderr,
        onProgress = onProgress
    )

    suspend fun runFfprobe(
        arguments: List<String>,
        input: File? = null
    ): CommandResult = executor.execute(
        command = listOf(runtime.ffprobePath.absolutePath) + arguments + listOfNotNull(input?.absolutePath),
        environment = defaultEnvironment,
        workingDirectory = runtime.libraryDir
    )
}
