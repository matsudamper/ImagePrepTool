package com.imagepreptool.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.ImageSize
import com.imagepreptool.service.Cropper
import com.imagepreptool.ui.components.Pill
import com.imagepreptool.ui.components.SegmentedControl

internal enum class CropAspect(val label: String) {
    Free("自由"),
    Original("元の比率"),
    Square("1:1"),
    FourThree("4:3"),
    ThreeTwo("3:2"),
    SixteenNine("16:9"),
    ;

    /** 幅 / 高さ。自由なら null */
    fun ratio(imageSize: ImageSize, portrait: Boolean): Float? {
        val landscapeRatio = when (this) {
            Free -> return null
            Original -> imageSize.width.toFloat() / imageSize.height
            Square -> 1f
            FourThree -> 4f / 3f
            ThreeTwo -> 3f / 2f
            SixteenNine -> 16f / 9f
        }
        return if (portrait) 1f / landscapeRatio else landscapeRatio
    }
}

/**
 * プレビュー上で切り抜き範囲を指定する。
 * 枠の内側をドラッグで移動、辺や角で大きさを変え、枠の外からドラッグすると新しく範囲を取り直す
 */
@Composable
internal fun CropEditor(
    bitmap: ImageBitmap,
    imageSize: ImageSize,
    crop: CropRect?,
    onCropChange: (CropRect?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var aspect by rememberSaveable { mutableStateOf(CropAspect.Free) }
    var portrait by rememberSaveable { mutableStateOf(imageSize.height > imageSize.width) }
    val ratio = aspect.ratio(imageSize, portrait)
    val currentCrop = crop ?: CropRect.Full

    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SegmentedControl(
                    options = CropAspect.entries,
                    selected = aspect,
                    onSelect = { selected ->
                        aspect = selected
                        val newRatio = selected.ratio(imageSize, portrait)
                        if (newRatio != null) onCropChange(CropGeometry.fitRatio(currentCrop, newRatio, imageSize))
                    },
                    label = { it.label },
                    modifier = Modifier.width(420.dp),
                )
                TextButton(
                    onClick = {
                        val swapped = !portrait
                        portrait = swapped
                        aspect.ratio(imageSize, swapped)?.let { onCropChange(CropGeometry.fitRatio(CropRect.Full, it, imageSize)) }
                    },
                    enabled = aspect != CropAspect.Free && aspect != CropAspect.Square,
                ) { Text(if (portrait) "縦長" else "横長") }
                TextButton(onClick = { onCropChange(null) }, enabled = crop != null) { Text("リセット") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Pill("${Cropper.croppedSize(imageSize, crop)} px")
                }
                Button(onClick = onDone, shape = MaterialTheme.shapes.small) { Text("完了") }
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
                    contentDescription = "切り抜き前の画像",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize().shadow(18.dp, clip = false),
                )
                CropOverlay(
                    crop = currentCrop,
                    displaySize = displaySize,
                    ratio = ratio,
                    onCropChange = onCropChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private enum class CropHandle(val cursor: Int) {
    Move(Cursor.MOVE_CURSOR),
    Left(Cursor.W_RESIZE_CURSOR),
    Top(Cursor.N_RESIZE_CURSOR),
    Right(Cursor.E_RESIZE_CURSOR),
    Bottom(Cursor.S_RESIZE_CURSOR),
    TopLeft(Cursor.NW_RESIZE_CURSOR),
    TopRight(Cursor.NE_RESIZE_CURSOR),
    BottomLeft(Cursor.SW_RESIZE_CURSOR),
    BottomRight(Cursor.SE_RESIZE_CURSOR),
    New(Cursor.CROSSHAIR_CURSOR),
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CropOverlay(
    crop: CropRect,
    displaySize: Size,
    ratio: Float?,
    onCropChange: (CropRect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handleTolerance = with(density) { 10.dp.toPx() }
    val minSide = with(density) { 24.dp.toPx() }
    val bounds = Rect(Offset.Zero, displaySize)
    // ドラッグ中は手元だけで動かし、離したときに確定する（毎回プレビューを作り直さない）
    val rectState = remember(crop, displaySize) { mutableStateOf(crop.toDisplayRect(displaySize)) }
    var hoveredHandle by remember { mutableStateOf(CropHandle.New) }
    val latestRatio by rememberUpdatedState(ratio)
    val latestOnCropChange by rememberUpdatedState(onCropChange)

    Canvas(
        modifier = modifier
            .pointerHoverIcon(PointerIcon(Cursor(hoveredHandle.cursor)))
            .onPointerEvent(PointerEventType.Move) { event ->
                hoveredHandle = CropGeometry.hitTest(rectState.value, event.changes.first().position, handleTolerance)
            }
            .pointerInput(rectState, displaySize) {
                var handle = CropHandle.New
                var startRect = Rect.Zero
                var startPosition = Offset.Zero
                var dragTotal = Offset.Zero
                detectDragGestures(
                    onDragStart = { position ->
                        handle = CropGeometry.hitTest(rectState.value, position, handleTolerance)
                        startRect = rectState.value
                        startPosition = position
                        dragTotal = Offset.Zero
                    },
                    onDragEnd = { latestOnCropChange(rectState.value.toCropRect(displaySize)) },
                    onDragCancel = { rectState.value = startRect },
                ) { change, dragAmount ->
                    change.consume()
                    dragTotal += dragAmount
                    val next = CropGeometry.drag(handle, startRect, startPosition, dragTotal, bounds, latestRatio)
                    if (handle == CropHandle.Move || (next.width >= minSide && next.height >= minSide)) rectState.value = next
                }
            },
    ) {
        val rect = rectState.value
        // 4 枚の矩形に分けて塗ると境目に細い線が出るため、範囲の外側だけを 1 回で塗る
        clipRect(rect.left, rect.top, rect.right, rect.bottom, ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.55f))
        }

        val guide = Color.White.copy(alpha = 0.45f)
        for (i in 1..2) {
            val x = rect.left + rect.width * i / 3
            val y = rect.top + rect.height * i / 3
            drawLine(guide, Offset(x, rect.top), Offset(x, rect.bottom), strokeWidth = 1f)
            drawLine(guide, Offset(rect.left, y), Offset(rect.right, y), strokeWidth = 1f)
        }
        drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(width = 1.5.dp.toPx()))

        val handleLength = min(16.dp.toPx(), min(rect.width, rect.height) / 2)
        val handleWidth = 3.dp.toPx()
        listOf(
            rect.topLeft to Offset(1f, 1f),
            rect.topRight to Offset(-1f, 1f),
            rect.bottomLeft to Offset(1f, -1f),
            rect.bottomRight to Offset(-1f, -1f),
        ).forEach { (corner, direction) ->
            drawLine(Color.White, corner, corner + Offset(handleLength * direction.x, 0f), strokeWidth = handleWidth)
            drawLine(Color.White, corner, corner + Offset(0f, handleLength * direction.y), strokeWidth = handleWidth)
        }
    }
}

private fun CropRect.toDisplayRect(displaySize: Size): Rect =
    Rect(left * displaySize.width, top * displaySize.height, right * displaySize.width, bottom * displaySize.height)

private fun Rect.toCropRect(displaySize: Size): CropRect = CropRect(
    left = (left / displaySize.width).coerceIn(0f, 1f),
    top = (top / displaySize.height).coerceIn(0f, 1f),
    right = (right / displaySize.width).coerceIn(0f, 1f),
    bottom = (bottom / displaySize.height).coerceIn(0f, 1f),
)

private object CropGeometry {

    fun hitTest(rect: Rect, position: Offset, tolerance: Float): CropHandle {
        val nearLeft = abs(position.x - rect.left) <= tolerance
        val nearRight = abs(position.x - rect.right) <= tolerance
        val nearTop = abs(position.y - rect.top) <= tolerance
        val nearBottom = abs(position.y - rect.bottom) <= tolerance
        val withinX = position.x in (rect.left - tolerance)..(rect.right + tolerance)
        val withinY = position.y in (rect.top - tolerance)..(rect.bottom + tolerance)
        return when {
            nearTop && nearLeft -> CropHandle.TopLeft
            nearTop && nearRight -> CropHandle.TopRight
            nearBottom && nearLeft -> CropHandle.BottomLeft
            nearBottom && nearRight -> CropHandle.BottomRight
            nearLeft && withinY -> CropHandle.Left
            nearRight && withinY -> CropHandle.Right
            nearTop && withinX -> CropHandle.Top
            nearBottom && withinX -> CropHandle.Bottom
            rect.contains(position) -> CropHandle.Move
            else -> CropHandle.New
        }
    }

    /** ドラッグ開始時の [start] から [dragTotal] だけ動かした後の範囲。[ratio] は幅 / 高さで、指定があれば保つ */
    fun drag(handle: CropHandle, start: Rect, startPosition: Offset, dragTotal: Offset, bounds: Rect, ratio: Float?): Rect {
        val pointer = clampTo(startPosition + dragTotal, bounds)
        return when (handle) {
            CropHandle.Move -> {
                val dx = dragTotal.x.coerceIn(bounds.left - start.left, bounds.right - start.right)
                val dy = dragTotal.y.coerceIn(bounds.top - start.top, bounds.bottom - start.bottom)
                start.translate(dx, dy)
            }
            CropHandle.TopLeft -> fromCorner(start.bottomRight, pointer, bounds, ratio)
            CropHandle.TopRight -> fromCorner(start.bottomLeft, pointer, bounds, ratio)
            CropHandle.BottomLeft -> fromCorner(start.topRight, pointer, bounds, ratio)
            CropHandle.BottomRight -> fromCorner(start.topLeft, pointer, bounds, ratio)
            CropHandle.New -> fromCorner(clampTo(startPosition, bounds), pointer, bounds, ratio)
            CropHandle.Left -> fromHorizontalEdge(start, anchorX = start.right, pointerX = pointer.x, bounds, ratio)
            CropHandle.Right -> fromHorizontalEdge(start, anchorX = start.left, pointerX = pointer.x, bounds, ratio)
            CropHandle.Top -> fromVerticalEdge(start, anchorY = start.bottom, pointerY = pointer.y, bounds, ratio)
            CropHandle.Bottom -> fromVerticalEdge(start, anchorY = start.top, pointerY = pointer.y, bounds, ratio)
        }
    }

    /** [crop] の中心を保ったまま、その内側に収まる最大の [ratio] の範囲にする */
    fun fitRatio(crop: CropRect, ratio: Float, imageSize: ImageSize): CropRect {
        val pixelWidth = crop.width * imageSize.width
        val pixelHeight = crop.height * imageSize.height
        val width = min(pixelWidth, pixelHeight * ratio)
        val height = width / ratio
        val centerX = (crop.left + crop.right) / 2
        val centerY = (crop.top + crop.bottom) / 2
        val halfWidth = width / imageSize.width / 2
        val halfHeight = height / imageSize.height / 2
        return CropRect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
    }

    private fun fromCorner(anchor: Offset, pointer: Offset, bounds: Rect, ratio: Float?): Rect {
        val signX = if (pointer.x >= anchor.x) 1f else -1f
        val signY = if (pointer.y >= anchor.y) 1f else -1f
        val freeSize = Size(abs(pointer.x - anchor.x), abs(pointer.y - anchor.y))
        val (width, height) = if (ratio == null) {
            freeSize.width to freeSize.height
        } else {
            val availableWidth = if (signX > 0) bounds.right - anchor.x else anchor.x - bounds.left
            val availableHeight = if (signY > 0) bounds.bottom - anchor.y else anchor.y - bounds.top
            // 縦横どちらか大きく動かした方に合わせ、はみ出す分は縮める
            val width = min(max(freeSize.width, freeSize.height * ratio), min(availableWidth, availableHeight * ratio))
            width to width / ratio
        }
        val endX = anchor.x + width * signX
        val endY = anchor.y + height * signY
        return Rect(min(anchor.x, endX), min(anchor.y, endY), max(anchor.x, endX), max(anchor.y, endY))
    }

    private fun fromHorizontalEdge(start: Rect, anchorX: Float, pointerX: Float, bounds: Rect, ratio: Float?): Rect {
        val span = edgeSpan(anchorX, pointerX, start.center.y, bounds.top, bounds.bottom, start.height, ratio)
        return Rect(span.start, span.crossStart, span.end, span.crossEnd)
    }

    private fun fromVerticalEdge(start: Rect, anchorY: Float, pointerY: Float, bounds: Rect, ratio: Float?): Rect {
        val span = edgeSpan(anchorY, pointerY, start.center.x, bounds.left, bounds.right, start.width, ratio?.let { 1f / it })
        return Rect(span.crossStart, span.start, span.crossEnd, span.end)
    }

    private class EdgeSpan(val start: Float, val end: Float, val crossStart: Float, val crossEnd: Float)

    /**
     * 1 辺だけを動かしたときの範囲。交差方向は [crossCenter] を中心に、[ratio]（動かす方向の長さ / 交差方向の長さ）があれば保つ
     */
    private fun edgeSpan(
        anchor: Float,
        pointer: Float,
        crossCenter: Float,
        crossMin: Float,
        crossMax: Float,
        crossLength: Float,
        ratio: Float?,
    ): EdgeSpan {
        val maxCross = 2 * min(crossCenter - crossMin, crossMax - crossCenter)
        val length = if (ratio == null) abs(pointer - anchor) else min(abs(pointer - anchor), maxCross * ratio)
        val start = if (pointer >= anchor) anchor else anchor - length
        val halfCross = if (ratio == null) crossLength / 2 else length / ratio / 2
        return EdgeSpan(start, start + length, crossCenter - halfCross, crossCenter + halfCross)
    }

    private fun clampTo(offset: Offset, bounds: Rect): Offset =
        Offset(offset.x.coerceIn(bounds.left, bounds.right), offset.y.coerceIn(bounds.top, bounds.bottom))
}
