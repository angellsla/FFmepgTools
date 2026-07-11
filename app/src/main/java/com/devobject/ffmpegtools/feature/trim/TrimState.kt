package com.devobject.ffmpegtools.feature.trim

import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.Player

data class TrimState(
    val videoUri: Uri? = null,
    val inputFile: java.io.File? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val durationMs: Long = 0,
    val currentPositionMs: Long = 0,
    val startMs: Long = 0,
    val endMs: Long = 0,
    val viewportStartMs: Float = 0f,
    val pixelsPerMs: Float = 0f,
    val thumbnails: List<ThumbnailFrame> = emptyList(),
    val isLoadingThumbnails: Boolean = false,
    val player: Player? = null
)

fun TrimState.videoAspectRatio(): Float? {
    val w = videoWidth ?: return null
    val h = videoHeight ?: return null
    return if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
}

data class ThumbnailFrame(
    val timeMs: Long,
    val bitmap: Bitmap
)

fun TrimState.viewportEndMs(widthPx: Float): Float = viewportStartMs + (widthPx / pixelsPerMs.coerceAtLeast(0.0001f))

fun TrimState.coerceRange(): TrimState {
    val newStart = startMs.coerceIn(0, durationMs)
    val newEnd = endMs.coerceIn(newStart, durationMs)
    return copy(startMs = newStart, endMs = newEnd)
}

fun TrimState.fitPixelsPerMs(widthPx: Float): Float {
    if (durationMs <= 0 || widthPx <= 0) return 0.1f
    return (widthPx * 0.85f / durationMs).coerceAtLeast(minPixelsPerMs(widthPx))
}

fun TrimState.minPixelsPerMs(widthPx: Float): Float {
    if (durationMs <= 0 || widthPx <= 0) return 0.01f
    return (widthPx / 3f / durationMs).coerceAtLeast(0.001f)
}

fun TrimState.maxPixelsPerMs(): Float = 2f
