package com.devobject.ffmpegtools.core.files

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class TempFileManager(private val context: Context) {
    fun createJobDirectory(): File = File(context.cacheDir, "ffmpeg-jobs/${UUID.randomUUID()}").apply {
        File(this, "input").mkdirs()
        File(this, "output").mkdirs()
        File(this, "logs").mkdirs()
    }

    fun inputDirectory(jobDir: File): File = File(jobDir, "input").apply { mkdirs() }
    fun outputDirectory(jobDir: File): File = File(jobDir, "output").apply { mkdirs() }

    suspend fun cleanAllJobs() = withContext(Dispatchers.IO) {
        File(context.cacheDir, "ffmpeg-jobs").deleteRecursively()
    }
}

class SafFileResolver(private val context: Context) {
    suspend fun copyUriToFile(uri: Uri, targetDir: File, fallbackName: String = "input"): File = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val displayName = queryDisplayName(uri) ?: fallbackName
        val target = uniqueFile(targetDir, displayName.sanitizeFileName())
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开输入文件：$uri" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    }

    suspend fun writeFileToUri(source: File, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "无法打开输出位置：$uri" }
            source.inputStream().use { input -> input.copyTo(output) }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

}

class FileAccessManager(
    private val tempFileManager: TempFileManager,
    private val safFileResolver: SafFileResolver
) {
    fun createJobDirectory(): File = tempFileManager.createJobDirectory()
    fun outputFile(jobDir: File, fileName: String): File = File(tempFileManager.outputDirectory(jobDir), fileName.sanitizeFileName())
    suspend fun importUri(uri: Uri, jobDir: File, fallbackName: String): File =
        safFileResolver.copyUriToFile(uri, tempFileManager.inputDirectory(jobDir), fallbackName)
    suspend fun exportToUri(source: File, outputUri: Uri) = safFileResolver.writeFileToUri(source, outputUri)
    suspend fun cleanTemporaryFiles() = tempFileManager.cleanAllJobs()

    /**
     * 不依赖 Shizuku，直接通过标准文件 API 将文件复制到
     * /sdcard/Download/[dirName] 目录。在 Android 9（targetSdk=28）上可直接写入；
     * Android 10+ 受存储分区限制可能失败，此时应再尝试 Shizuku。
     */
    suspend fun copyToDefaultDownloadDir(source: File, dirName: String = "FFmpegtools"): String? = withContext(Dispatchers.IO) {
        runCatching {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, dirName).apply { mkdirs() }
            val target = uniqueFile(targetDir, source.name.sanitizeFileName().ifBlank { "output" })
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.absolutePath
        }.getOrNull()
    }
}

fun String.sanitizeFileName(): String = replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }

fun uniqueFile(dir: File, name: String): File {
    var candidate = File(dir, name)
    if (!candidate.exists()) return candidate
    val base = candidate.nameWithoutExtension
    val extension = candidate.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    var index = 1
    while (candidate.exists()) {
        candidate = File(dir, "$base-$index$extension")
        index++
    }
    return candidate
}
