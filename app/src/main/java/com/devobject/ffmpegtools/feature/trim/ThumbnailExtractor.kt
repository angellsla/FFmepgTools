package com.devobject.ffmpegtools.feature.trim

import android.graphics.BitmapFactory
import com.devobject.ffmpegtools.core.ffmpeg.FfmpegExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ThumbnailExtractor(private val executor: FfmpegExecutor) {

    suspend fun extract(input: File, durationMs: Long, count: Int = 20, width: Int = 120): List<ThumbnailFrame> =
        withContext(Dispatchers.IO) {
            if (durationMs <= 0 || count <= 0) return@withContext emptyList()
            val dir = File(input.parentFile, "thumbs-${System.currentTimeMillis()}").apply { mkdirs() }
            val pattern = File(dir, "thumb_%04d.jpg").absolutePath
            val durationSec = durationMs / 1000f
            val fps = count / durationSec.coerceAtLeast(1f)

            val command = listOf(
                "-hide_banner", "-y", "-i", input.absolutePath,
                "-vf", "fps=$fps,scale=$width:-1",
                "-vsync", "vfr",
                "-q:v", "2",
                pattern
            )

            val result = executor.runFfmpeg(
                arguments = command,
                onStdout = {},
                onStderr = {}
            )
            if (!result.isSuccess) return@withContext emptyList()

            dir.listFiles { file -> file.extension.equals("jpg", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.mapIndexedNotNull { index, file ->
                    val timeMs = ((index * 1000) / fps).toLong()
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@mapIndexedNotNull null
                    ThumbnailFrame(timeMs = timeMs.coerceIn(0, durationMs), bitmap = bitmap)
                }
                ?: emptyList()
        }
}
