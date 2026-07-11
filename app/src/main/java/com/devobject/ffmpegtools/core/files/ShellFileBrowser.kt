package com.devobject.ffmpegtools.core.files

import com.devobject.ffmpegtools.core.ffmpeg.CommandExecutor
import com.devobject.ffmpegtools.core.shizuku.ShizukuProcessHelper
import com.devobject.ffmpegtools.core.shizuku.shellQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShellFileBrowser(private val executor: CommandExecutor) {
    suspend fun list(path: String): ShellDirectoryResult = withContext(Dispatchers.IO) {
        val script = """
            DIR=${shellQuote(path)}
            [ -d "${'$'}DIR" ] || { echo "NOT_DIR" >&2; exit 2; }
            cd "${'$'}DIR" 2>/dev/null || { echo "ACCESS_DENIED" >&2; exit 3; }
            for name in * .[^.]*; do
                [ -e "${'$'}name" ] || continue
                case "${'$'}name" in proc|sys|dev|selinux|.proc|.sys|.dev|.selinux) continue ;; esac
                stat -c '%F|%s|%Y|%n' "${'$'}name" 2>/dev/null
            done
        """.trimIndent()
        val result = executor.execute(listOf("sh", "-c", script), timeoutMs = 8000L)
        if (!result.isSuccess) return@withContext ShellDirectoryResult(path, emptyList(), result.stderr.ifBlank { "无法读取目录 (exit=${result.exitCode})" })
        val dirPrefix = path.trimEnd('/') + "/"
        val entries = result.stdout.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 4)
                if (parts.size < 4) null else {
                    val name = parts[3].removePrefix("./")
                    ShellFileEntry(
                        name = name,
                        path = dirPrefix + name,
                        isDirectory = parts[0].trim() == "directory",
                        size = parts[1].trim().toLongOrNull() ?: 0L,
                        modifiedEpochSeconds = parts[2].trim().toLongOrNull() ?: 0L
                    )
                }
            }
            .sortedWith(compareByDescending<ShellFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
        ShellDirectoryResult(path, entries)
    }

    suspend fun copyRemoteFileToLocal(remotePath: String, targetDir: File): File = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val name = remotePath.substringAfterLast('/').sanitizeFileName().ifBlank { "input" }
        val output = File(targetDir, name)
        val process = ShizukuProcessHelper.newProcess(
            arrayOf("sh", "-c", "cat ${shellQuote(remotePath)}"),
            null,
            null
        )
        output.outputStream().use { fileOut ->
            process.inputStream.use { input -> input.copyTo(fileOut) }
        }
        val stderr = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        require(code == 0) { stderr.ifBlank { "Shizuku 读取文件失败：$remotePath" } }
        output
    }

    /**
     * 将本地文件复制到远程目录（如 /sdcard/Download/FFmpegtools）。
     * 返回最终远程路径；若目标文件已存在则自动追加序号。
     */
    suspend fun copyLocalFileToRemote(localFile: File, remoteDir: String): String = withContext(Dispatchers.IO) {
        require(localFile.exists()) { "本地文件不存在：${localFile.absolutePath}" }
        val dir = remoteDir.trimEnd('/')
        val mkdirResult = executor.execute(listOf("mkdir", "-p", dir), timeoutMs = 8000L)
        if (!mkdirResult.isSuccess) {
            throw IllegalStateException("无法创建目标目录：$dir\n${mkdirResult.stderr}")
        }
        val baseName = localFile.name.sanitizeFileName().ifBlank { "output" }
        val targetPath = generateUniqueRemotePath(dir, baseName)
        val copyResult = executor.execute(listOf("cp", localFile.absolutePath, targetPath), timeoutMs = 0L)
        if (!copyResult.isSuccess) {
            throw IllegalStateException("复制到默认目录失败：$targetPath\n${copyResult.stderr}")
        }
        targetPath
    }

    private suspend fun generateUniqueRemotePath(dir: String, name: String): String {
        val candidate = "$dir/$name"
        val checkResult = executor.execute(listOf("test", "-e", candidate), timeoutMs = 5000L)
        if (!checkResult.isSuccess) return candidate
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        val extSuffix = if (ext.isNotBlank()) ".$ext" else ""
        var index = 1
        while (true) {
            val next = "$dir/$base-$index$extSuffix"
            val result = executor.execute(listOf("test", "-e", next), timeoutMs = 5000L)
            if (!result.isSuccess) return next
            index++
        }
    }
}

data class ShellFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedEpochSeconds: Long
) {
    val formattedSize: String = formatFileSize(size)
    val formattedDate: String = formatEpochSeconds(modifiedEpochSeconds)
}

private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatEpochSeconds(epochSeconds: Long): String = runCatching {
    dateFormatter.format(Date(epochSeconds * 1000L))
}.getOrDefault("")

data class ShellDirectoryResult(
    val path: String,
    val entries: List<ShellFileEntry>,
    val error: String? = null
)

fun formatFileSize(bytes: Long): String = when {
    bytes < 0L -> "未知"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
