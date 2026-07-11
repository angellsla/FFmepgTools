package com.devobject.ffmpegtools.core.ffmpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.measureTimeMillis

class ProcessCommandExecutor : CommandExecutor {
    override suspend fun execute(
        command: List<String>,
        environment: Map<String, String>,
        workingDirectory: File?,
        cancellationToken: CancellationToken,
        onStdout: suspend (String) -> Unit,
        onStderr: suspend (String) -> Unit,
        onProgress: suspend (FfmpegProgress) -> Unit,
        timeoutMs: Long
    ): CommandResult = withContext(Dispatchers.IO) {
        require(command.isNotEmpty()) { "命令不能为空" }
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode = -1
        val duration = measureTimeMillis {
            val process = ProcessBuilder(command)
                .apply {
                    if (workingDirectory != null) directory(workingDirectory)
                    environment().putAll(environment)
                }
                .start()

            val parser = FfmpegProgressParser()
            exitCode = coroutineScope {
                val stdoutJob = async {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            stdout.appendLine(line)
                            onStdout(line)
                            parser.consume(line)?.let { onProgress(it) }
                        }
                    }
                }
                val stderrJob = async {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            stderr.appendLine(line)
                            onStderr(line)
                        }
                    }
                }
                val monitorJob = async {
                    while (process.isAlive && isActive && !cancellationToken.isCancelled) {
                        delay(200)
                    }
                    if (cancellationToken.isCancelled || !isActive) {
                        process.destroyForcibly()
                        stdoutJob.cancel()
                        stderrJob.cancel()
                        throw CancellationException("任务已取消")
                    }
                }
                try {
                    var finished = false
                    var code = -1
                    val startAt = System.currentTimeMillis()
                    while (!finished) {
                        if (process.waitFor(500, TimeUnit.MILLISECONDS)) {
                            finished = true
                            code = process.exitValue()
                        }
                        if (cancellationToken.isCancelled || !isActive) {
                            process.destroyForcibly()
                            stdoutJob.cancel()
                            stderrJob.cancel()
                            throw CancellationException("任务已取消")
                        }
                        if (timeoutMs > 0L && System.currentTimeMillis() - startAt > timeoutMs) {
                            process.destroyForcibly()
                            stdoutJob.cancel()
                            stderrJob.cancel()
                            stderr.appendLine("命令执行超时")
                            code = -1
                            finished = true
                        }
                    }
                    monitorJob.cancel()
                    stdoutJob.await()
                    stderrJob.await()
                    code
                } catch (e: CancellationException) {
                    process.destroyForcibly()
                    throw e
                }
            }
        }
        CommandResult(exitCode, stdout.toString(), stderr.toString(), duration)
    }
}
