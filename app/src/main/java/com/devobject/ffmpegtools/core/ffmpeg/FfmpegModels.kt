package com.devobject.ffmpegtools.core.ffmpeg

import java.io.File

interface CancellationToken {
    val isCancelled: Boolean
}

fun cancellationToken(): CancellationToken = object : CancellationToken {
    override val isCancelled: Boolean = false
}

interface CommandExecutor {
    suspend fun execute(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
        workingDirectory: File? = null,
        cancellationToken: CancellationToken = cancellationToken(),
        onStdout: suspend (String) -> Unit = {},
        onStderr: suspend (String) -> Unit = {},
        onProgress: suspend (FfmpegProgress) -> Unit = {},
        timeoutMs: Long = 0L
    ): CommandResult
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
) {
    val isSuccess: Boolean = exitCode == 0
}

data class FfmpegRuntime(
    val ffmpegPath: File,
    val ffprobePath: File,
    val libraryDir: File
)

data class FfmpegProgress(
    val frame: Long? = null,
    val fps: Double? = null,
    val bitrate: String? = null,
    val totalSize: Long? = null,
    val outTimeMs: Long? = null,
    val speed: String? = null,
    val progress: String? = null
)

data class FfmpegCapabilities(
    val ffmpegVersion: String = "Unknown",
    val ffprobeVersion: String = "Unknown",
    val hwaccels: Set<String> = emptySet(),
    val encoders: Set<String> = emptySet(),
    val decoders: Set<String> = emptySet(),
    val androidHardwareEncoders: Set<String> = emptySet(),
    val androidHardwareDecoders: Set<String> = emptySet()
) {
    val hasMediaCodec: Boolean = "mediacodec" in hwaccels
    fun hasEncoder(name: String): Boolean = name in encoders
    fun hasDecoder(name: String): Boolean = name in decoders
}

data class MediaInfo(
    val durationMs: Long? = null,
    val formatName: String? = null,
    val formatLongName: String? = null,
    val bitRate: Long? = null,
    val size: Long? = null,
    val streams: List<MediaStreamInfo> = emptyList(),
    val rawJson: String = ""
)

data class MediaStreamInfo(
    val index: Int,
    val codecType: String,
    val codecName: String,
    val codecLongName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val displayAspectRatio: String? = null,
    val pixelFormat: String? = null,
    val frameRate: Double? = null,
    val bitRate: Long? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val channelLayout: String? = null,
    val language: String? = null,
    val title: String? = null
)

enum class VideoCodec(val label: String, val ffmpegName: String?, val pixelFormat: String? = null) {
    Copy("复制视频流", "copy"),
    H264MediaCodec("H.264 硬件编码", "h264_mediacodec"),
    HevcMediaCodec("HEVC 硬件编码", "hevc_mediacodec"),
    Mpeg4MediaCodec("MPEG4 硬件编码", "mpeg4_mediacodec"),
    Mpeg4Software("MPEG4 软件编码", "mpeg4"),
    Libx264("H.264 (libx264)", "libx264"),
    Libx264_10("H.264 10bit (libx264)", "libx264", "yuv420p10le"),
    Libx264_12("H.264 12bit (libx264)", "libx264", "yuv420p12le"),
    Libx265("HEVC (libx265)", "libx265"),
    Libx265_10("HEVC 10bit (libx265)", "libx265", "yuv420p10le"),
    Libx265_12("HEVC 12bit (libx265)", "libx265", "yuv420p12le"),
    Libkvazaar("HEVC (libkvazaar)", "libkvazaar"),
    LibvpxVp8("VP8 (libvpx)", "libvpx"),
    LibvpxVp9("VP9 (libvpx-vp9)", "libvpx-vp9"),
    LibaomAv1("AV1 (libaom)", "libaom-av1"),
    LibsvtAv1("AV1 (libsvtav1)", "libsvtav1")
}

enum class AudioCodec(val label: String, val ffmpegName: String?) {
    Copy("复制音频流", "copy"),
    Aac("AAC", "aac"),
    Mp3("MP3", "libmp3lame"),
    Opus("Opus", "libopus"),
    LibfdkAac("AAC (libfdk_aac)", "libfdk_aac")
}

data class GifConfig(
    val fps: Int = 15,
    val width: Int = 480,
    val colors: Int = 128,
    val loop: Int = 0
) {
    init {
        require(fps in 1..60) { "fps 必须在 1-60 之间" }
        require(width in 80..1920) { "宽度必须在 80-1920 之间" }
        require(colors in 2..256) { "颜色数必须在 2-256 之间" }
    }
}

enum class ConcatMode(val label: String) {
    Demuxer("快速合并（要求格式相同）"),
    Filter("强制转码合并（兼容不同格式）")
}

enum class OutputContainer(
    val label: String,
    val extension: String,
    val mimeType: String,
    val category: Category
) {
    Mp4("MP4", "mp4", "video/mp4", Category.Video),
    Mkv("MKV", "mkv", "video/x-matroska", Category.Video),
    WebM("WebM", "webm", "video/webm", Category.Video),
    Mov("MOV", "mov", "video/quicktime", Category.Video),
    Avi("AVI", "avi", "video/x-msvideo", Category.Video),
    Flv("FLV", "flv", "video/x-flv", Category.Video),
    Ts("TS", "ts", "video/mp2t", Category.Video),
    Gif("GIF", "gif", "image/gif", Category.Video),

    Mp3("MP3", "mp3", "audio/mpeg", Category.Audio),
    M4a("M4A", "m4a", "audio/mp4", Category.Audio),
    Aac("AAC", "aac", "audio/aac", Category.Audio),
    Ogg("OGG", "ogg", "audio/ogg", Category.Audio),
    Opus("Opus", "opus", "audio/opus", Category.Audio),
    Flac("FLAC", "flac", "audio/flac", Category.Audio),
    Wav("WAV", "wav", "audio/wav", Category.Audio);

    enum class Category { Video, Audio }

    companion object {
        fun videoContainers(): List<OutputContainer> = entries.filter { it.category == Category.Video }
        fun audioContainers(): List<OutputContainer> = entries.filter { it.category == Category.Audio }
    }
}
