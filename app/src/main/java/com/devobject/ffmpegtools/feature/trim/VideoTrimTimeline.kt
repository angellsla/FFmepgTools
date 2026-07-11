package com.devobject.ffmpegtools.feature.trim

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.devobject.ffmpegtools.ui.theme.LocalUiDimensions
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private enum class DragTarget { Playhead, Start, End, Range, Pan, None }

private data class TimelineColors(
    val background: Color,
    val border: Color,
    val buttonHover: Color,
    val gridLineMinor: Color,
    val gridLineMajor: Color,
    val gridNumber: Color,
    val trackBackground: Color,
    val selectionFill: Color,
    val selectionBorder: Color,
    val unselectedOverlay: Color,
    val playhead: Color,
    val handle: Color,
    val handleTint: Color
)

@Composable
private fun rememberTimelineColors(): TimelineColors {
    val scheme = MiuixTheme.colorScheme
    return remember(scheme) {
        TimelineColors(
            background = scheme.surfaceContainer,
            border = scheme.surfaceVariant,
            buttonHover = scheme.surfaceVariant,
            gridLineMinor = scheme.onSurface.copy(alpha = 0.25f),
            gridLineMajor = scheme.onSurface.copy(alpha = 0.45f),
            gridNumber = scheme.onSurfaceContainerVariant,
            trackBackground = scheme.surface,
            selectionFill = scheme.primary.copy(alpha = 0.2f),
            selectionBorder = scheme.primary,
            unselectedOverlay = Color.Black.copy(alpha = 0.45f),
            playhead = scheme.error,
            handle = scheme.surface,
            handleTint = scheme.primary
        )
    }
}

@Composable
fun VideoTrimTimeline(
    trimState: TrimState,
    onPositionChange: (Long) -> Unit,
    onRangeChange: (Long, Long) -> Unit,
    onViewportChange: (Float, Float) -> Unit,
    onZoomAroundPivot: (Float, Float) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomFit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiDims = LocalUiDimensions.current
    val density = LocalDensity.current
    val colors = rememberTimelineColors()
    val handleThresholdPx = with(density) { 40.dp.toPx() }
    val handleWidthPx = with(density) { 5.dp.toPx() }
    val handleTouchWidthPx = with(density) { 32.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragTarget by remember { mutableStateOf(DragTarget.None) }

    val currentTrimState by rememberUpdatedState(trimState)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    val currentOnViewportChange by rememberUpdatedState(onViewportChange)
    val currentOnZoomAroundPivot by rememberUpdatedState(onZoomAroundPivot)

    fun timeToX(timeMs: Long): Float = (timeMs - currentTrimState.viewportStartMs) * currentTrimState.pixelsPerMs
    fun xToTime(x: Float): Float = currentTrimState.viewportStartMs + x / currentTrimState.pixelsPerMs
    fun determineTarget(x: Float): DragTarget {
        val playheadX = timeToX(currentTrimState.currentPositionMs)
        val startX = timeToX(currentTrimState.startMs)
        val endX = timeToX(currentTrimState.endMs)
        return when {
            abs(x - playheadX) < handleThresholdPx -> DragTarget.Playhead
            abs(x - startX) < handleTouchWidthPx -> DragTarget.Start
            abs(x - endX) < handleTouchWidthPx -> DragTarget.End
            x in min(startX, endX)..max(startX, endX) -> DragTarget.Range
            else -> DragTarget.Pan
        }
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background)
    ) {
        TimelineToolbar(
            trimState = trimState,
            colors = colors,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onZoomFit = onZoomFit
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LeftOperationStrip(
                colors = colors,
                modifier = Modifier.fillMaxHeight()
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { widthPx = it.width.toFloat() }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTrimGestures(
                                onDragStart = { offset -> dragTarget = determineTarget(offset.x) },
                                onDragEnd = { dragTarget = DragTarget.None },
                                onDragCancel = { dragTarget = DragTarget.None },
                                onDrag = { change, _ ->
                                    val x = change.position.x
                                    when (dragTarget) {
                                        DragTarget.Playhead -> currentOnPositionChange(xToTime(x).toLong())
                                        DragTarget.Start -> currentOnRangeChange(xToTime(x).toLong(), currentTrimState.endMs)
                                        DragTarget.End -> currentOnRangeChange(currentTrimState.startMs, xToTime(x).toLong())
                                        DragTarget.Range -> {
                                            val dx = change.positionChange().x
                                            val dt = (-dx / currentTrimState.pixelsPerMs).toLong()
                                            val range = currentTrimState.endMs - currentTrimState.startMs
                                            val newStart = (currentTrimState.startMs + dt).coerceIn(0, max(0, currentTrimState.durationMs - range))
                                            currentOnRangeChange(newStart, newStart + range)
                                        }
                                        DragTarget.Pan -> {
                                            val dx = change.positionChange().x
                                            val dt = dx / currentTrimState.pixelsPerMs
                                            currentOnViewportChange(currentTrimState.viewportStartMs - dt, currentTrimState.pixelsPerMs)
                                        }
                                        else -> {}
                                    }
                                    change.consume()
                                },
                                onPinch = { centroidX, zoomFactor ->
                                    currentOnZoomAroundPivot(centroidX, zoomFactor)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val x = offset.x
                                val target = determineTarget(x)
                                when (target) {
                                    DragTarget.Start -> currentOnRangeChange(xToTime(x).toLong(), currentTrimState.endMs)
                                    DragTarget.End -> currentOnRangeChange(currentTrimState.startMs, xToTime(x).toLong())
                                    else -> currentOnPositionChange(xToTime(x).toLong())
                                }
                            }
                        }
                ) {
                    val height = size.height
                    drawVideoTrackBackground(colors, trimState, widthPx, height)
                    drawThumbnails(trimState, widthPx, height)
                    drawGrid(colors, trimState, widthPx, height, density.density)
                    drawSelection(colors, trimState, widthPx, height)
                    drawHandles(colors, trimState, height, handleWidthPx)
                    drawPlayhead(colors, trimState, height)
                }
            }
        }
    }
}

@Composable
private fun TimelineToolbar(
    trimState: TrimState,
    colors: TimelineColors,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomFit: () -> Unit
) {
    val uiDims = LocalUiDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(uiDims.toolbarHeight)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = null,
                modifier = Modifier.size(uiDims.smallIconSize),
                tint = colors.gridNumber
            )
            Text(
                text = "裁剪时间轴",
                style = MiuixTheme.textStyles.body2,
                color = colors.gridNumber
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTimelineTime(trimState.currentPositionMs)} / ${formatTimelineTime(trimState.durationMs)}",
                style = MiuixTheme.textStyles.body2,
                color = colors.gridNumber
            )
            Spacer(modifier = Modifier.width(8.dp))
            ToolbarIconButton(
                onClick = onZoomOut,
                enabled = trimState.durationMs > 0,
                colors = colors,
                content = { Icon(Icons.Default.Remove, contentDescription = "缩小", tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(uiDims.smallIconSize)) }
            )
            ToolbarIconButton(
                onClick = onZoomFit,
                enabled = trimState.durationMs > 0,
                colors = colors,
                content = { Icon(Icons.Default.AspectRatio, contentDescription = "适配", tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(uiDims.smallIconSize)) }
            )
            ToolbarIconButton(
                onClick = onZoomIn,
                enabled = trimState.durationMs > 0,
                colors = colors,
                content = { Icon(Icons.Default.Add, contentDescription = "放大", tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(uiDims.smallIconSize)) }
            )
        }
    }
}

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    colors: TimelineColors,
    content: @Composable () -> Unit
) {
    val uiDims = LocalUiDimensions.current
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(uiDims.iconSize)
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) colors.buttonHover else Color.Transparent),
        content = content
    )
}

@Composable
private fun LeftOperationStrip(
    colors: TimelineColors,
    modifier: Modifier = Modifier
) {
    val uiDims = LocalUiDimensions.current
    Box(
        modifier = modifier
            .width(uiDims.iconSize)
            .fillMaxHeight()
            .background(colors.border),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(0.6f)
                .background(colors.handleTint)
        )
    }
}

private suspend fun PointerInputScope.detectTrimGestures(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onPinch: (centroidX: Float, zoomFactor: Float) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onDragStart(down.position)

        var isPinching = false
        var isHorizontalDrag = false
        var previousPinchPositions: List<Offset>? = null

        do {
            val event = awaitPointerEvent()
            val changes = event.changes
            val pressed = changes.filter { it.pressed }

            if (pressed.size >= 2) {
                isPinching = true
                val positions = pressed.map { it.position }
                val currentSpan = positions.pinchSpan()
                val centroid = positions.centroid()
                previousPinchPositions?.let { prev ->
                    val prevSpan = prev.pinchSpan()
                    if (prevSpan > 0f) {
                        val zoomChange = currentSpan / prevSpan
                        if (zoomChange != 1f) {
                            onPinch(centroid.x, zoomChange)
                        }
                    }
                }
                previousPinchPositions = positions
                changes.forEach { it.consume() }
            } else if (!isPinching) {
                val change = pressed.firstOrNull()
                if (change != null) {
                    val delta = change.positionChange()
                    if (isHorizontalDrag || abs(delta.x) > abs(delta.y)) {
                        isHorizontalDrag = true
                        onDrag(change, delta)
                        change.consume()
                    }
                }
            }
        } while (changes.any { it.pressed })

        onDragEnd()
    }
}

private fun List<Offset>.pinchSpan(): Float {
    if (size < 2) return 0f
    val a = this[0]
    val b = this[1]
    return sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
}

private fun List<Offset>.centroid(): Offset {
    if (isEmpty()) return Offset.Zero
    val sum = reduce { acc, offset -> acc + offset }
    return Offset(sum.x / size, sum.y / size)
}

private fun DrawScope.drawVideoTrackBackground(colors: TimelineColors, trimState: TrimState, widthPx: Float, heightPx: Float) {
    drawRect(colors.background)
    val trackTop = heightPx * 0.22f
    val trackHeight = heightPx * 0.5f
    val startX = max(0f, -trimState.viewportStartMs * trimState.pixelsPerMs)
    val endX = min(widthPx, (trimState.durationMs - trimState.viewportStartMs) * trimState.pixelsPerMs)
    if (endX > startX) {
        drawRect(colors.trackBackground, topLeft = Offset(startX, trackTop), size = Size(endX - startX, trackHeight))
    }
}

private fun DrawScope.drawThumbnails(trimState: TrimState, widthPx: Float, heightPx: Float) {
    val trackTop = heightPx * 0.22f
    val trackHeight = heightPx * 0.5f
    val thumbHeight = trackHeight * 0.92f
    val top = trackTop + (trackHeight - thumbHeight) / 2f
    trimState.thumbnails.forEach { frame ->
        val x = (frame.timeMs - trimState.viewportStartMs) * trimState.pixelsPerMs
        val bitmapWidth = frame.bitmap.width.toFloat()
        val bitmapHeight = frame.bitmap.height.toFloat()
        val width = bitmapWidth * (thumbHeight / bitmapHeight)
        if (x + width < 0 || x > widthPx) return@forEach
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawBitmap(
                frame.bitmap,
                null,
                android.graphics.RectF(x, top, x + width, top + thumbHeight),
                null
            )
        }
    }
}

private fun DrawScope.drawGrid(colors: TimelineColors, trimState: TrimState, widthPx: Float, heightPx: Float, density: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.gridNumber.toArgb()
        textSize = 10 * density
        textAlign = Paint.Align.LEFT
    }
    val lineY = heightPx * 0.18f
    val viewportEnd = trimState.viewportEndMs(widthPx)
    val spanMs = viewportEnd - trimState.viewportStartMs
    val (majorStepMs, minorDivisions) = chooseGridSteps(spanMs)
    val minorStepMs = majorStepMs / minorDivisions

    var t = (trimState.viewportStartMs / minorStepMs).toInt() * minorStepMs
    while (t <= viewportEnd) {
        val x = (t - trimState.viewportStartMs) * trimState.pixelsPerMs
        if (x in 0f..widthPx) {
            val isMajor = (t.roundToInt() % majorStepMs.roundToInt()) == 0
            val lineHeight = if (isMajor) 12f else 6f
            val lineColor = if (isMajor) colors.gridLineMajor else colors.gridLineMinor
            drawLine(lineColor, Offset(x, lineY), Offset(x, lineY + lineHeight), strokeWidth = 1.5f)
            if (isMajor) {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(formatTimelineTime(t.toLong()), x + 3f, lineY - 3f, paint)
                }
            }
        }
        t += minorStepMs
    }
}

private fun chooseGridSteps(spanMs: Float): Pair<Float, Int> {
    val spanSec = spanMs / 1000f
    return when {
        spanSec <= 2f -> Pair(500f, 5)
        spanSec <= 5f -> Pair(1000f, 5)
        spanSec <= 10f -> Pair(2000f, 4)
        spanSec <= 20f -> Pair(5000f, 5)
        spanSec <= 60f -> Pair(10000f, 5)
        spanSec <= 120f -> Pair(30000f, 5)
        spanSec <= 300f -> Pair(60000f, 4)
        spanSec <= 600f -> Pair(120000f, 4)
        else -> Pair(300000f, 5)
    }
}

private fun formatTimelineTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun DrawScope.drawSelection(colors: TimelineColors, trimState: TrimState, widthPx: Float, heightPx: Float) {
    val startX = max(0f, (trimState.startMs - trimState.viewportStartMs) * trimState.pixelsPerMs)
    val endX = min(widthPx, (trimState.endMs - trimState.viewportStartMs) * trimState.pixelsPerMs)
    if (endX <= startX) return

    val selectionCenter = colors.selectionFill.copy(alpha = 0.3f)
    val selectedBrush = Brush.verticalGradient(
        listOf(colors.selectionFill, selectionCenter, colors.selectionFill),
        startY = 0f,
        endY = heightPx
    )
    drawRect(selectedBrush, topLeft = Offset(startX, 0f), size = Size(endX - startX, heightPx))
    drawLine(colors.selectionBorder, Offset(startX, 0f), Offset(startX, heightPx), strokeWidth = 2f)
    drawLine(colors.selectionBorder, Offset(endX, 0f), Offset(endX, heightPx), strokeWidth = 2f)

    if (startX > 0f) {
        drawRect(colors.unselectedOverlay, topLeft = Offset(0f, 0f), size = Size(startX, heightPx))
    }
    if (endX < widthPx) {
        drawRect(colors.unselectedOverlay, topLeft = Offset(endX, 0f), size = Size(widthPx - endX, heightPx))
    }
}

private fun DrawScope.drawPlayhead(colors: TimelineColors, trimState: TrimState, heightPx: Float) {
    val x = (trimState.currentPositionMs - trimState.viewportStartMs) * trimState.pixelsPerMs
    if (x < 0f || x > size.width) return
    drawLine(colors.playhead, Offset(x, 0f), Offset(x, heightPx), strokeWidth = 2.5f)
    drawCircle(colors.playhead, radius = 5f, center = Offset(x, 0f))
}

private fun DrawScope.drawHandles(colors: TimelineColors, trimState: TrimState, heightPx: Float, handleWidthPx: Float) {
    val startX = (trimState.startMs - trimState.viewportStartMs) * trimState.pixelsPerMs
    val endX = (trimState.endMs - trimState.viewportStartMs) * trimState.pixelsPerMs
    val handleHeight = heightPx * 0.55f
    val top = (heightPx - handleHeight) / 2f

    drawHandle(colors, Offset(startX, top), handleHeight, handleWidthPx, true)
    drawHandle(colors, Offset(endX, top), handleHeight, handleWidthPx, false)
}

private fun DrawScope.drawHandle(colors: TimelineColors, centerTop: Offset, height: Float, width: Float, pointLeft: Boolean) {
    val barHalf = width / 2f
    drawRoundRect(
        color = colors.handle,
        topLeft = Offset(centerTop.x - barHalf, centerTop.y),
        size = Size(width, height),
        cornerRadius = CornerRadius(width, width)
    )
    val triangleY = centerTop.y + height + 8f
    drawTriangle(Offset(centerTop.x, triangleY), 10f, pointLeft, colors.handleTint)
}

private fun DrawScope.drawTriangle(center: Offset, size: Float, pointLeft: Boolean, color: Color) {
    val path = Path().apply {
        if (pointLeft) {
            moveTo(center.x - size / 2, center.y - size / 2)
            lineTo(center.x + size / 2, center.y)
            lineTo(center.x - size / 2, center.y + size / 2)
        } else {
            moveTo(center.x + size / 2, center.y - size / 2)
            lineTo(center.x - size / 2, center.y)
            lineTo(center.x + size / 2, center.y + size / 2)
        }
        close()
    }
    drawPath(path, color)
}
