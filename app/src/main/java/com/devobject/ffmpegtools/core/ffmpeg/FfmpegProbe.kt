package com.devobject.ffmpegtools.core.ffmpeg

import org.json.JSONObject
import java.io.File

class FfmpegProbe(private val executor: FfmpegExecutor) {
    suspend fun inspect(input: File): MediaInfo {
        val result = executor.runFfprobe(
            arguments = listOf("-v", "error", "-show_format", "-show_streams", "-print_format", "json"),
            input = input
        )
        val json = result.stdout.ifBlank { result.stderr }
        return parseMediaInfo(json)
    }

    private fun parseMediaInfo(json: String): MediaInfo = runCatching {
        val root = JSONObject(json)
        val format = root.optJSONObject("format")
        val streamsArray = root.optJSONArray("streams")

        val streams = mutableListOf<MediaStreamInfo>()
        if (streamsArray != null) {
            for (i in 0 until streamsArray.length()) {
                val obj = streamsArray.getJSONObject(i)
                streams.add(parseStream(obj))
            }
        }

        MediaInfo(
            durationMs = format?.optString("duration")?.toDoubleOrNull()?.times(1000)?.toLong(),
            formatName = format?.optString("format_name").takeIf { it?.isNotBlank() == true },
            formatLongName = format?.optString("format_long_name").takeIf { it?.isNotBlank() == true },
            bitRate = format?.optString("bit_rate")?.toLongOrNull(),
            size = format?.optString("size")?.toLongOrNull(),
            streams = streams,
            rawJson = json
        )
    }.getOrElse { MediaInfo(rawJson = json) }

    private fun parseStream(obj: JSONObject): MediaStreamInfo {
        val tags = obj.optJSONObject("tags")
        val fps = parseFrameRate(
            obj.optString("r_frame_rate"),
            obj.optString("avg_frame_rate")
        )
        return MediaStreamInfo(
            index = obj.optInt("index", -1),
            codecType = obj.optString("codec_type").ifBlank { "unknown" },
            codecName = obj.optString("codec_name").ifBlank { "unknown" },
            codecLongName = obj.optString("codec_long_name").takeIf { it.isNotBlank() },
            width = obj.optString("width").toIntOrNull(),
            height = obj.optString("height").toIntOrNull(),
            displayAspectRatio = obj.optString("display_aspect_ratio").takeIf { it.isNotBlank() },
            pixelFormat = obj.optString("pix_fmt").takeIf { it.isNotBlank() },
            frameRate = fps,
            bitRate = obj.optString("bit_rate").toLongOrNull(),
            sampleRate = obj.optString("sample_rate").toIntOrNull(),
            channels = obj.optString("channels").toIntOrNull(),
            channelLayout = obj.optString("channel_layout").takeIf { it.isNotBlank() },
            language = tags?.optString("language")?.takeIf { it.isNotBlank() },
            title = tags?.optString("title")?.takeIf { it.isNotBlank() }
        )
    }

    private fun parseFrameRate(vararg candidates: String): Double? {
        for (candidate in candidates) {
            val rate = candidate.toFractionOrNull() ?: continue
            if (rate > 0) return rate
        }
        return null
    }

    private fun String.toFractionOrNull(): Double? = runCatching {
        val parts = trim().split('/')
        if (parts.size == 2) {
            parts[0].toDouble() / parts[1].toDouble()
        } else {
            toDouble()
        }
    }.getOrNull()
}
