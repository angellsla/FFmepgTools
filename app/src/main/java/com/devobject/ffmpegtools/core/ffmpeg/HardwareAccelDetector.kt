package com.devobject.ffmpegtools.core.ffmpeg

import android.media.MediaCodecList
import android.media.MediaFormat

class HardwareAccelDetector(private val executor: FfmpegExecutor) {
    suspend fun detect(): FfmpegCapabilities {
        val version = executor.runFfmpeg(listOf("-hide_banner", "-version"))
        val probeVersion = executor.runFfprobe(listOf("-hide_banner", "-version"))
        val hwaccels = executor.runFfmpeg(listOf("-hide_banner", "-hwaccels"))
        val encoders = executor.runFfmpeg(listOf("-hide_banner", "-encoders"))
        val decoders = executor.runFfmpeg(listOf("-hide_banner", "-decoders"))
        return FfmpegCapabilities(
            ffmpegVersion = parseVersion(version.stdout.ifBlank { version.stderr }),
            ffprobeVersion = parseVersion(probeVersion.stdout.ifBlank { probeVersion.stderr }),
            hwaccels = parseHwaccels(hwaccels.stdout.ifBlank { hwaccels.stderr }),
            encoders = parseCodecs(encoders.stdout.ifBlank { encoders.stderr }),
            decoders = parseCodecs(decoders.stdout.ifBlank { decoders.stderr }),
            androidHardwareEncoders = androidCodecs(encoder = true),
            androidHardwareDecoders = androidCodecs(encoder = false)
        )
    }

    private fun parseVersion(text: String): String =
        text.lineSequence().firstOrNull { it.contains("ffmpeg version", ignoreCase = true) || it.contains("ffprobe version", ignoreCase = true) }
            ?: text.lineSequence().firstOrNull().orEmpty()

    private fun parseHwaccels(text: String): Set<String> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.contains("Hardware acceleration", ignoreCase = true) }
        .filterNot { it.startsWith("ffmpeg", ignoreCase = true) || it.startsWith("configuration", ignoreCase = true) }
        .toSet()

    private fun parseCodecs(text: String): Set<String> = text.lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2 && parts[0].length >= 6 && parts[0].any { it == 'V' || it == 'A' }) parts[1] else null
        }
        .toSet()

    private fun androidCodecs(encoder: Boolean): Set<String> {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        return list
            .filter { it.isEncoder == encoder }
            .flatMap { info ->
                info.supportedTypes.mapNotNull { type ->
                    when (type.lowercase()) {
                        MediaFormat.MIMETYPE_VIDEO_AVC -> "h264"
                        MediaFormat.MIMETYPE_VIDEO_HEVC -> "hevc"
                        "video/mp4v-es" -> "mpeg4"
                        else -> null
                    }
                }
            }
            .toSet()
    }
}
