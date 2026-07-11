package com.devobject.ffmpegtools.core.ffmpeg

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FfmpegInstaller(private val context: Context) {
    suspend fun install(): FfmpegRuntime = withContext(Dispatchers.IO) {
        require(Build.SUPPORTED_ABIS.contains(ABI)) {
            "当前设备 ABI 不支持。此应用内置 FFmpeg 仅支持 $ABI，当前设备：${Build.SUPPORTED_ABIS.joinToString()}"
        }
        val dir = File(context.filesDir, "ffmpeg-bin/$ABI").apply { mkdirs() }
        val ffmpeg = copyAsset("ffmpeg/$ABI/ffmpeg", File(dir, "ffmpeg"), executable = true)
        val ffprobe = copyAsset("ffmpeg/$ABI/ffprobe", File(dir, "ffprobe"), executable = true)
        copyAsset("ffmpeg/$ABI/libc++_shared.so", File(dir, "libc++_shared.so"), executable = false)
        FfmpegRuntime(ffmpegPath = ffmpeg, ffprobePath = ffprobe, libraryDir = dir)
    }

    private fun copyAsset(assetPath: String, target: File, executable: Boolean): File {
        context.assets.open(assetPath).use { input ->
            val assetSize = input.available().toLong()
            if (target.exists() && target.length() == assetSize) {
                if (executable) target.setExecutable(true, false)
                return target
            }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        if (executable) target.setExecutable(true, false)
        return target
    }

    companion object {
        const val ABI = "arm64-v8a"
    }
}
