package com.imagepreptool.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import java.util.Locale
import kotlin.math.min
import com.imagepreptool.model.PenKind
import com.imagepreptool.model.PenPoint
import com.imagepreptool.model.PenStroke
import com.imagepreptool.model.PenTool
import com.imagepreptool.ui.components.SegmentedControl
import com.imagepreptool.ui.theme.MonoNumberStyle

private val PenColors = listOf(
    0xFFFFFFFF.toInt(),
    0xFF000000.toInt(),
    0xFFE53935.toInt(),
    0xFFFDD835.toInt(),
    0xFF43A047.toInt(),
    0xFF1E88E5.toInt(),
)

/**
 * プレビュー上にペンで線を描く。ぼかしを選ぶと、なぞった部分だけをぼかす。
 * Ctrl+Z で元に戻し、Ctrl+Y / Ctrl+Shift+Z でやり直す
 */
@Composable
internal fun PenEditor(
    bitmap: ImageBitmap,
    strokes: List<PenStroke>,
    tool: PenTool,
    onStrokesChange: (List<PenStroke>) -> Unit,
    onToolChange: (PenTool) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val history = remember { PenHistory() }
    val focusRequester = remember { FocusRequester() }
    val change = { next: List<PenStroke> ->
        history.record(strokes)
        onStrokesChange(next)
    }
    val undo = { history.undo(strokes)?.let(onStrokesChange) }
    val redo = { history.redo(strokes)?.let(onStrokesChange) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                when (event.toHistoryShortcut()) {
                    HistoryShortcut.Undo -> undo()
                    HistoryShortcut.Redo -> redo()
                    null -> return@onPreviewKeyEvent false
                }
                true
            },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SegmentedControl(
                        options = PenKind.entries,
                        selected = tool.kind,
                        onSelect = { onToolChange(tool.copy(kind = it)) },
                        label = { it.label },
                        modifier = Modifier.width(150.dp),
                    )
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { undo() }, enabled = history.canUndo(strokes)) { Text("元に戻す") }
                    TextButton(onClick = { redo() }, enabled = history.canRedo) { Text("やり直す") }
                    TextButton(onClick = { change(listOf()) }, enabled = strokes.isNotEmpty()) { Text("すべて消す") }
                    Button(onClick = onDone, shape = MaterialTheme.shapes.small) { Text("完了") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PenSlider(
                        label = "太さ",
                        value = tool.widthPercent,
                        valueRange = PenStroke.MIN_WIDTH_PERCENT..PenStroke.MAX_WIDTH_PERCENT,
                        onValueChange = { onToolChange(tool.copy(widthPercent = it)) },
                    )
                    when (tool.kind) {
                        PenKind.Draw -> ColorSwatches(selected = tool.color, onSelect = { onToolChange(tool.copy(color = it)) })
                        PenKind.Blur -> PenSlider(
                            label = "強さ",
                            value = tool.blurPercent,
                            valueRange = PenStroke.MIN_BLUR_PERCENT..PenStroke.MAX_BLUR_PERCENT,
                            onValueChange = { onToolChange(tool.copy(blurPercent = it)) },
                        )
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 56.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            val fitScale = minOf(constraints.maxWidth.toFloat() / bitmap.width, constraints.maxHeight.toFloat() / bitmap.height)
            val displaySize = Size(bitmap.width * fitScale, bitmap.height * fitScale)
            val density = LocalDensity.current
            Box(Modifier.size(with(density) { displaySize.width.toDp() }, with(density) { displaySize.height.toDp() })) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "線を描く画像",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize().shadow(18.dp, clip = false),
                )
                PenOverlay(
                    bitmap = bitmap,
                    displaySize = displaySize,
                    newStroke = { points -> PenStroke(tool.kind, points, tool.widthPercent, tool.color, tool.blurPercent) },
                    onStrokeAdd = { stroke -> change(strokes + stroke) },
                    // ボタンなどにフォーカスが移った後も、描き始めれば Ctrl+Z が効くようにする
                    onPress = { focusRequester.requestFocus() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PenOverlay(
    bitmap: ImageBitmap,
    displaySize: Size,
    newStroke: (List<PenPoint>) -> PenStroke,
    onStrokeAdd: (PenStroke) -> Unit,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestNewStroke by rememberUpdatedState(newStroke)
    val latestOnStrokeAdd by rememberUpdatedState(onStrokeAdd)
    var drawingPoints by remember { mutableStateOf(listOf<Offset>()) }
    // 確定した線は、描き込んだ画像が届くまで手元で表示しておく（届くまでの間に線が消えて見えないようにする）
    var committedPoints by remember(bitmap) { mutableStateOf(listOf<List<Offset>>()) }
    var hoverPosition by remember { mutableStateOf<Offset?>(null) }
    val minPointDistance = with(LocalDensity.current) { 1.dp.toPx() }

    fun commit(points: List<Offset>) {
        if (points.isEmpty()) return
        committedPoints = committedPoints + listOf(points)
        latestOnStrokeAdd(latestNewStroke(points.map { PenPoint(it.x / displaySize.width, it.y / displaySize.height) }))
    }

    Canvas(
        modifier = modifier
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR)))
            .onPointerEvent(PointerEventType.Move) { event -> hoverPosition = event.changes.first().position }
            .onPointerEvent(PointerEventType.Exit) { hoverPosition = null }
            .onPointerEvent(PointerEventType.Press) { onPress() }
            .pointerInput(displaySize) {
                detectTapGestures(onTap = { position -> commit(listOf(position)) })
            }
            .pointerInput(displaySize) {
                detectDragGestures(
                    onDragStart = { position -> drawingPoints = listOf(position) },
                    onDragEnd = {
                        commit(drawingPoints)
                        drawingPoints = listOf()
                    },
                    onDragCancel = { drawingPoints = listOf() },
                ) { change, _ ->
                    change.consume()
                    hoverPosition = change.position
                    val last = drawingPoints.lastOrNull()
                    if (last == null || (change.position - last).getDistance() >= minPointDistance) {
                        drawingPoints = drawingPoints + change.position
                    }
                }
            },
    ) {
        val stroke = latestNewStroke(listOf())
        val strokeWidth = (min(size.width, size.height) * stroke.widthPercent / 100f).coerceAtLeast(1f)
        val strokeColor = when (stroke.kind) {
            PenKind.Draw -> Color(stroke.color)
            PenKind.Blur -> Color.White.copy(alpha = 0.45f)
        }
        (committedPoints + listOf(drawingPoints)).forEach { points -> drawStrokePath(points, strokeColor, strokeWidth) }
        hoverPosition?.let { position ->
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = strokeWidth / 2, center = position, style = Stroke(width = 2f))
            drawCircle(Color.White, radius = strokeWidth / 2, center = position, style = Stroke(width = 1f))
        }
    }
}

private enum class HistoryShortcut { Undo, Redo }

private fun KeyEvent.toHistoryShortcut(): HistoryShortcut? {
    if (type != KeyEventType.KeyDown || !(isCtrlPressed || isMetaPressed)) return null
    return when (key) {
        Key.Z -> if (isShiftPressed) HistoryShortcut.Redo else HistoryShortcut.Undo
        Key.Y -> HistoryShortcut.Redo
        else -> null
    }
}

/**
 * 編集画面を開いている間の元に戻す / やり直すの履歴。
 * 開く前に描いた線は履歴に無いため、元に戻すと 1 本ずつ消す
 */
@Stable
private class PenHistory {
    private var undoSnapshots by mutableStateOf(listOf<List<PenStroke>>())
    private var redoSnapshots by mutableStateOf(listOf<List<PenStroke>>())

    val canRedo: Boolean get() = redoSnapshots.isNotEmpty()

    fun canUndo(current: List<PenStroke>): Boolean = undoSnapshots.isNotEmpty() || current.isNotEmpty()

    /** [current] から変更する直前に呼ぶ */
    fun record(current: List<PenStroke>) {
        undoSnapshots = undoSnapshots + listOf(current)
        redoSnapshots = listOf()
    }

    /** 戻した後の線。戻せなければ null */
    fun undo(current: List<PenStroke>): List<PenStroke>? {
        if (!canUndo(current)) return null
        val previous = undoSnapshots.lastOrNull() ?: current.dropLast(1)
        undoSnapshots = undoSnapshots.dropLast(1)
        redoSnapshots = redoSnapshots + listOf(current)
        return previous
    }

    /** やり直した後の線。やり直せなければ null */
    fun redo(current: List<PenStroke>): List<PenStroke>? {
        val next = redoSnapshots.lastOrNull() ?: return null
        redoSnapshots = redoSnapshots.dropLast(1)
        undoSnapshots = undoSnapshots + listOf(current)
        return next
    }
}

private fun DrawScope.drawStrokePath(points: List<Offset>, color: Color, strokeWidth: Float) {
    val first = points.firstOrNull() ?: return
    if (points.size == 1) {
        drawCircle(color, radius = strokeWidth / 2, center = first)
        return
    }
    val path = Path().apply {
        moveTo(first.x, first.y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun PenSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, modifier = Modifier.width(120.dp).height(28.dp))
        Text(
            String.format(Locale.ROOT, "%.1f%%", value),
            style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        PenColors.forEach { swatch ->
            val isSelected = swatch == selected
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .padding(if (isSelected) 4.dp else 1.dp)
                    .clip(CircleShape)
                    .background(Color(swatch))
                    .clickable { onSelect(swatch) },
            )
        }
    }
}
