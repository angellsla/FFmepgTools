package com.devobject.ffmpegtools.core.ffmpeg

import java.io.File

class FfmpegCommandBuilder {
    fun videoTranscode(
        input: File,
        output: File,
        videoCodec: VideoCodec,
        audioCodec: AudioCodec,
        videoBitrate: String = "4000k",
        audioBitrate: String = "128k",
        width: Int? = null,
        height: Int? = null,
        fps: Int? = null
    ): List<String> = commonPrefix() + buildList {
        add("-i")
        add(input.absolutePath)
        videoCodec.ffmpegName?.let {
            add("-c:v")
            add(it)
            if (it != "copy") {
                add("-b:v")
                add(videoBitrate)
                videoCodec.pixelFormat?.let { fmt ->
                    add("-pix_fmt")
                    add(fmt)
                }
            }
        }
        if (width != null && height != null) {
            add("-vf")
            add("scale=$width:$height")
        }
        if (fps != null) {
            add("-r")
            add(fps.toString())
        }
        audioCodec.ffmpegName?.let {
            add("-c:a")
            add(it)
            if (it != "copy") {
                add("-b:a")
                add(audioBitrate)
            }
        }
        if (output.extension.equals("mp4", ignoreCase = true)) {
            add("-movflags")
            add("+faststart")
        }
        add(output.absolutePath)
    }

    fun audioTranscode(
        input: File,
        output: File,
        audioCodec: AudioCodec,
        audioBitrate: String = "192k",
        sampleRate: Int? = null,
        channels: Int? = null
    ): List<String> = commonPrefix() + buildList {
        add("-i")
        add(input.absolutePath)
        add("-vn")
        add("-c:a")
        add(audioCodec.ffmpegName ?: "aac")
        if (audioCodec != AudioCodec.Copy) {
            add("-b:a")
            add(audioBitrate)
        }
        sampleRate?.let {
            add("-ar")
            add(it.toString())
        }
        channels?.let {
            add("-ac")
            add(it.toString())
        }
        add(output.absolutePath)
    }

    fun extractAudio(
        input: File,
        output: File,
        audioCodec: AudioCodec,
        audioBitrate: String = "192k"
    ): List<String> = commonPrefix() + buildList {
        add("-i")
        add(input.absolutePath)
        add("-vn")
        add("-c:a")
        add(audioCodec.ffmpegName ?: "copy")
        if (audioCodec != AudioCodec.Copy) {
            add("-b:a")
            add(audioBitrate)
        }
        add(output.absolutePath)
    }

    fun muxAudioVideo(
        video: File,
        audio: File,
        output: File,
        audioCodec: AudioCodec,
        audioBitrate: String = "192k",
        shortest: Boolean = true,
        keepOriginalAudio: Boolean = false
    ): List<String> = commonPrefix() + buildList {
        add("-i")
        add(video.absolutePath)
        add("-i")
        add(audio.absolutePath)
        add("-map")
        add("0:v:0")
        if (keepOriginalAudio) {
            add("-map")
            add("0:a?")
        }
        add("-map")
        add("1:a:0")
        add("-c:v")
        add("copy")
        add("-c:a")
        add(audioCodec.ffmpegName ?: "aac")
        if (audioCodec != AudioCodec.Copy) {
            add("-b:a")
            add(audioBitrate)
        }
        if (shortest) add("-shortest")
        if (output.extension.equals("mp4", ignoreCase = true)) {
            add("-movflags")
            add("+faststart")
        }
        add(output.absolutePath)
    }

    fun gifConvert(
        input: File,
        output: File,
        config: GifConfig
    ): List<String> = commonPrefix() + buildList {
        add("-i")
        add(input.absolutePath)
        val filter = "fps=${config.fps},scale=${config.width}:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=${config.colors}[p];[s1][p]paletteuse=dither=bayer"
        add("-vf")
        add(filter)
        add("-loop")
        add(config.loop.toString())
        add(output.absolutePath)
    }

    fun concatMedia(
        inputs: List<File>,
        output: File,
        mode: ConcatMode,
        outputContainer: OutputContainer
    ): List<String> = commonPrefix() + buildList {
        require(inputs.size >= 2) { "合并至少需要 2 个文件" }
        when (mode) {
            ConcatMode.Demuxer -> {
                val listFile = File(output.parentFile, "concat_list_${System.currentTimeMillis()}.txt")
                listFile.writeText(inputs.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" })
                add("-f")
                add("concat")
                add("-safe")
                add("0")
                add("-i")
                add(listFile.absolutePath)
                add("-c")
                add("copy")
            }
            ConcatMode.Filter -> {
                inputs.forEach {
                    add("-i")
                    add(it.absolutePath)
                }
                val n = inputs.size
                val isVideo = outputContainer.category == OutputContainer.Category.Video
                val filter = if (isVideo) {
                    buildString {
                        repeat(n) { append("[$it:v][$it:a]") }
                        append("concat=n=$n:v=1:a=1[outv][outa]")
                    }
                } else {
                    buildString {
                        repeat(n) { append("[$it:a]") }
                        append("concat=n=$n:v=0:a=1[outa]")
                    }
                }
                add("-filter_complex")
                add(filter)
                if (isVideo) {
                    add("-map")
                    add("[outv]")
                    add("-map")
                    add("[outa]")
                    add("-c:v")
                    add("libx264")
                    add("-c:a")
                    add("aac")
                } else {
                    add("-map")
                    add("[outa]")
                    add("-c:a")
                    add("aac")
                }
            }
        }
        if (output.extension.equals("mp4", ignoreCase = true)) {
            add("-movflags")
            add("+faststart")
        }
        add(output.absolutePath)
    }

    fun trimVideo(
        input: File,
        output: File,
        startMs: Long,
        endMs: Long,
        videoCodec: VideoCodec = VideoCodec.Libx264,
        audioCodec: AudioCodec = AudioCodec.Aac,
        copyMode: Boolean = false
    ): List<String> = commonPrefix() + buildList {
        require(startMs >= 0) { "入点不能为负数" }
        require(endMs > startMs) { "出点必须大于入点" }
        add("-ss")
        add(formatTime(startMs))
        add("-i")
        add(input.absolutePath)
        add("-to")
        add(formatTime(endMs))
        if (copyMode) {
            add("-c")
            add("copy")
        } else {
            add("-c:v")
            add(videoCodec.ffmpegName ?: "libx264")
            videoCodec.pixelFormat?.let {
                add("-pix_fmt")
                add(it)
            }
            add("-c:a")
            add(audioCodec.ffmpegName ?: "aac")
        }
        if (output.extension.equals("mp4", ignoreCase = true)) {
            add("-movflags")
            add("+faststart")
        }
        add(output.absolutePath)
    }

    private fun commonPrefix(): List<String> = listOf("-hide_banner", "-y", "-progress", "pipe:1", "-nostats")

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }
}
