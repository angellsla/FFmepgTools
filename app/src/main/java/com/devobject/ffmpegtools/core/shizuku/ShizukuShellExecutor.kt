package com.devobject.ffmpegtools.core.shizuku

import com.devobject.ffmpegtools.core.ffmpeg.CancellationToken
import com.devobject.ffmpegtools.core.ffmpeg.CommandExecutor
import com.devobject.ffmpegtools.core.ffmpeg.CommandResult
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegProgress
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegProgressParser
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

class ShizukuShellExecutor : CommandExecutor {
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
        val env = environment.map { "${it.key}=${it.value}" }.toTypedArray()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode = -1
        val duration = measureTimeMillis {
            val process = try {
                ShizukuProcessHelper.newProcess(
                    command.toTypedArray(),
                    env,
                    workingDirectory?.absolutePath
                )
            } catch (e: Exception) {
                stderr.appendLine("Shizuku 启动进程失败：${e.message}")
                return@withContext CommandResult(-1, stdout.toString(), stderr.toString(), 0L)
            }
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
                    while (process.isAliveSafe() && isActive && !cancellationToken.isCancelled) {
                        delay(200)
                    }
                    if (cancellationToken.isCancelled || !isActive) {
                        process.destroyForciblySilently()
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
                        if (process.waitForSafe(500, TimeUnit.MILLISECONDS)) {
                            finished = true
                            code = process.exitValueSafe()
                        }
                        if (cancellationToken.isCancelled || !isActive) {
                            process.destroyForciblySilently()
                            stdoutJob.cancel()
                            stderrJob.cancel()
                            throw CancellationException("任务已取消")
                        }
                        if (timeoutMs > 0L && System.currentTimeMillis() - startAt > timeoutMs) {
                            process.destroyForciblySilently()
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

fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

private fun Process.isAliveSafe(): Boolean = try {
    isAlive
} catch (e: IllegalArgumentException) {
    true
} catch (e: Exception) {
    false
}

private fun Process.exitValueSafe(): Int = try {
    exitValue()
} catch (e: IllegalArgumentException) {
    -1
} catch (e: Exception) {
    -1
}

private fun Process.waitForSafe(timeout: Long, unit: TimeUnit): Boolean = try {
    waitFor(timeout, unit)
} catch (e: IllegalArgumentException) {
    false
} catch (e: Exception) {
    false
}

private fun Process.destroyForciblySilently(): Process? = try {
    destroyForcibly()
} catch (e: Exception) {
    null
}
