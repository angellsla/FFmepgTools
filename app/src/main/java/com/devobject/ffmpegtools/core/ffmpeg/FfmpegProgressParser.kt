package com.devobject.ffmpegtools.core.ffmpeg

class FfmpegProgressParser {
    private val values = linkedMapOf<String, String>()

    fun consume(line: String): FfmpegProgress? {
        val index = line.indexOf('=')
        if (index <= 0) return null
        val key = line.substring(0, index).trim()
        val value = line.substring(index + 1).trim()
        values[key] = value
        if (key != "progress") return null
        return FfmpegProgress(
            frame = values["frame"]?.toLongOrNull(),
            fps = values["fps"]?.toDoubleOrNull(),
            bitrate = values["bitrate"],
            totalSize = values["total_size"]?.toLongOrNull(),
            outTimeMs = values["out_time_ms"]?.toLongOrNull()
                ?: values["out_time_us"]?.toLongOrNull()?.div(1000),
            speed = values["speed"],
            progress = value
        )
    }
}
