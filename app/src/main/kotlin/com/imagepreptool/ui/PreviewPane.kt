package com.imagepreptool.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import com.imagepreptool.model.CropRect
import com.imagepreptool.model.EditOptions
import com.imagepreptool.model.ImageSize
import com.imagepreptool.model.OutputFormat
import com.imagepreptool.model.PenStroke
import com.imagepreptool.presentation.ImageItem
import com.imagepreptool.presentation.PreviewState
import com.imagepreptool.resources.Res
import com.imagepreptool.resources.ic_arrow_forward
import com.imagepreptool.resources.ic_broken_image
import com.imagepreptool.resources.ic_brush
import com.imagepreptool.resources.ic_chevron_left
import com.imagepreptool.resources.ic_chevron_right
import com.imagepreptool.resources.ic_crop
import com.imagepreptool.resources.ic_rotate_left
import com.imagepreptool.resources.ic_rotate_right
import com.imagepreptool.ui.components.Pill
import com.imagepreptool.ui.components.SlantedToggle
import com.imagepreptool.ui.theme.AppTheme
import com.imagepreptool.ui.theme.MonoNumberStyle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private enum class PreviewMode(val label: String) {
    Processed("書き出し後"),
    Original("元画像"),
    ;

    val toggled: PreviewMode get() = if (this == Processed) Original else Processed
}

private enum class PreviewEditor { None, Crop, Pen }

@Composable
fun PreviewPane(
    preview: PreviewState,
    item: ImageItem?,
    index: Int,
    total: Int,
    options: EditOptions,
    onMove: (Int) -> Unit,
    onCropChange: (File, CropRect?) -> Unit,
    onRotateClockwise: (File) -> Unit,
    onRotateCounterClockwise: (File) -> Unit,
    onStrokesChange: (File, List<PenStroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoom = remember(preview.file) { PreviewZoomState(initialScale = PreviewZoomState.MIN_SCALE, initialOffset = Offset.Zero) }
    PreviewPaneContent(
        preview = preview,
        item = item,
        index = index,
        total = total,
        options = options,
        zoom = zoom,
        onMove = onMove,
        onCropChange = { crop -> preview.file?.let { onCropChange(it, crop) } },
        onRotateClockwise = { preview.file?.let(onRotateClockwise) },
        onRotateCounterClockwise = { preview.file?.let(onRotateCounterClockwise) },
        onStrokesChange = { strokes -> preview.file?.let { onStrokesChange(it, strokes) } },
        modifier = modifier,
    )
}

@Composable
private fun PreviewPaneContent(
    preview: PreviewState,
    item: ImageItem?,
    index: Int,
    total: Int,
    options: EditOptions,
    zoom: PreviewZoomState,
    onMove: (Int) -> Unit,
    onCropChange: (CropRect?) -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateCounterClockwise: () -> Unit,
    onStrokesChange: (List<PenStroke>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = AppTheme.extended
    var mode by rememberSaveable { mutableStateOf(PreviewMode.Processed) }
    var openEditor by rememberSaveable { mutableStateOf(PreviewEditor.None) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Column(modifier = modifier.fillMaxSize().background(ext.canvas)) {
        // ヘッダー
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item != null) {
                    Text(
                        item.file.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(
                        "${index + 1} / $total",
                        style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                SlantedToggle(
                    firstLabel = PreviewMode.Processed.label,
                    secondLabel = PreviewMode.Original.label,
                    isSecondSelected = mode == PreviewMode.Original,
                    onToggle = { mode = mode.toggled },
                    modifier = Modifier.width(188.dp),
                )
            }
        }

        // 画像
        // 切り抜きやペンは書き出し後の設定なので、書き出し後の表示でだけ編集する
        val activeEditor = if (mode == PreviewMode.Processed) openEditor else PreviewEditor.None
        val isEditing = activeEditor != PreviewEditor.None
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .hoverable(interaction)
                // 編集中のドラッグは範囲の指定や線を描くのに使う
                .then(if (isEditing) Modifier else Modifier.previewZoomGestures(zoom)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap: ImageBitmap? = when {
                isEditing -> preview.painted ?: preview.original
                mode == PreviewMode.Processed -> preview.processed ?: preview.original
                else -> preview.original
            }
            val originalSize = preview.originalSize
            when {
                preview.error != null -> ErrorContent(preview.error)
                bitmap != null && activeEditor == PreviewEditor.Crop && originalSize != null -> CropEditor(
                    bitmap = bitmap,
                    imageSize = originalSize,
                    crop = preview.crop,
                    onCropChange = onCropChange,
                    onDone = { openEditor = PreviewEditor.None },
                )
                // 元に戻すの履歴は画像ごとなので、画像を切り替えたら作り直す
                bitmap != null && activeEditor == PreviewEditor.Pen -> key(preview.file) {
                    PenEditor(
                        bitmap = bitmap,
                        strokes = preview.strokes,
                        onStrokesChange = onStrokesChange,
                        onDone = { openEditor = PreviewEditor.None },
                    )
                }
                bitmap != null -> FittedImage(bitmap, zoom)
                preview.file != null -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = ext.canvasContent)
                else -> Text("画像を選択するとプレビューが表示されます", color = ext.canvasContent)
            }

            if (!isEditing) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZoomResetButton(zoom = zoom)
                    LoadingBadge(visible = preview.loading && bitmap != null)
                }
                if (mode == PreviewMode.Processed && bitmap != null) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RotateButton(
                            iconResource = Res.drawable.ic_rotate_left,
                            contentDescription = "左に回転",
                            onClick = onRotateCounterClockwise,
                        )
                        RotateButton(
                            iconResource = Res.drawable.ic_rotate_right,
                            contentDescription = "右に回転",
                            onClick = onRotateClockwise,
                        )
                        PenButton(
                            hasStrokes = preview.strokes.isNotEmpty(),
                            onClick = { openEditor = PreviewEditor.Pen },
                        )
                        CropButton(
                            isCropped = preview.crop != null,
                            onClick = { openEditor = PreviewEditor.Crop },
                        )
                    }
                }
            }

            if (total > 1) {
                NavButton(
                    visible = hovered && index > 0,
                    onClick = { onMove(-1) },
                    icon = { Icon(painterResource(Res.drawable.ic_chevron_left), contentDescription = "前の画像") },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                )
                NavButton(
                    visible = hovered && index < total - 1,
                    onClick = { onMove(1) },
                    icon = { Icon(painterResource(Res.drawable.ic_chevron_right), contentDescription = "次の画像") },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                )
            }
        }

        // 情報バー
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            val original = preview.originalSize
            val output = preview.outputSize
            if (original != null) {
                Pill(original.toString())
                if (output != null) {
                    Icon(painterResource(Res.drawable.ic_arrow_forward), null, tint = ext.canvasContent, modifier = Modifier.size(16.dp))
                    Pill(
                        output.toString(),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            preview.outputFormat?.let { format ->
                Pill(if (format.lossy) "${format.label} · 品質 ${options.quality}" else format.label)
            }
            preview.outputByteSize?.let { byteSize ->
                Pill("約 ${formatByteSize(byteSize)}")
            }
        }
    }
}

/**
 * 余白を残して画面いっぱいに収まる大きさで表示する。
 * 書き出しサイズが小さくても表示サイズは変えず、引き伸ばしで劣化具合を確認できるようにする
 */
@Composable
private fun FittedImage(bitmap: ImageBitmap, zoom: PreviewZoomState) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offset.x
                translationY = zoom.offset.y
            }
            .padding(horizontal = 56.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fitScale = minOf(constraints.maxWidth.toFloat() / bitmap.width, constraints.maxHeight.toFloat() / bitmap.height)
        val density = LocalDensity.current
        val isMagnified = fitScale * zoom.scale > 1f
        Image(
            bitmap = bitmap,
            contentDescription = "プレビュー",
            contentScale = ContentScale.FillBounds,
            // 拡大表示ではピクセルをぼかさずに見せ、劣化をそのまま確認できるようにする
            filterQuality = if (isMagnified) FilterQuality.None else DrawScope.DefaultFilterQuality,
            modifier = Modifier
                .size(with(density) { (bitmap.width * fitScale).toDp() }, with(density) { (bitmap.height * fitScale).toDp() })
                .shadow(18.dp, clip = false),
        )
    }
}

@Stable
private class PreviewZoomState(initialScale: Float, initialOffset: Offset) {
    var scale by mutableFloatStateOf(initialScale)
        private set
    var offset by mutableStateOf(initialOffset)
        private set

    val isTransformed: Boolean get() = scale != MIN_SCALE || offset != Offset.Zero

    /** [focus] の位置にある画像上の点を動かさずに拡大縮小する。座標はビューポート中心基準 */
    fun zoomAt(focus: Offset, factor: Float) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (newScale == MIN_SCALE) {
            reset()
            return
        }
        offset = focus - (focus - offset) * (newScale / scale)
        scale = newScale
    }

    fun pan(delta: Offset) {
        if (scale == MIN_SCALE) return
        offset += delta
    }

    fun reset() {
        scale = MIN_SCALE
        offset = Offset.Zero
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 32f
        const val WHEEL_ZOOM_STEP = 1.15f
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.previewZoomGestures(zoom: PreviewZoomState): Modifier = this
    .onPointerEvent(PointerEventType.Scroll) { event ->
        val change = event.changes.first()
        val center = Offset(size.width / 2f, size.height / 2f)
        val factor = PreviewZoomState.WHEEL_ZOOM_STEP.pow(-change.scrollDelta.y)
        zoom.zoomAt(change.position - center, factor)
        change.consume()
    }
    .pointerInput(zoom) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            zoom.pan(dragAmount)
        }
    }
    .pointerInput(zoom) {
        detectTapGestures(onDoubleTap = { zoom.reset() })
    }

@Composable
private fun CropButton(isCropped: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isCropped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
    ) {
        Icon(painterResource(Res.drawable.ic_crop), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (isCropped) "切り抜き中" else "切り抜き", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PenButton(hasStrokes: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (hasStrokes) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
    ) {
        Icon(painterResource(Res.drawable.ic_brush), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (hasStrokes) "ペン描画中" else "ペン", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RotateButton(iconResource: DrawableResource, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        ),
    ) {
        Icon(painterResource(iconResource), contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ZoomResetButton(zoom: PreviewZoomState, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = zoom.isTransformed, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FilledTonalButton(
            onClick = { zoom.reset() },
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ),
        ) {
            Text("${(zoom.scale * 100).roundToInt()}% · 元に戻す", style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle))
        }
    }
}

private fun formatByteSize(byteSize: Long): String = when {
    byteSize < 1024 -> "$byteSize B"
    byteSize < 1024 * 1024 -> "${(byteSize + 512) / 1024} KB"
    else -> String.format(Locale.ROOT, "%.1f MB", byteSize / (1024.0 * 1024.0))
}

@Composable
private fun LoadingBadge(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
            CircularProgressIndicator(Modifier.padding(6.dp).size(16.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun NavButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ),
        ) { icon() }
    }
}

@Composable
private fun ErrorContent(message: String) {
    val ext = AppTheme.extended
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(painterResource(Res.drawable.ic_broken_image), null, tint = ext.canvasContent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("この画像は読み込めません", style = MaterialTheme.typography.titleSmall, color = ext.canvasContent)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = ext.canvasContent)
    }
}

@Preview
@Composable
private fun PreviewPaneFittedPreview() {
    PreviewPaneForPreview(zoom = PreviewZoomState(initialScale = PreviewZoomState.MIN_SCALE, initialOffset = Offset.Zero))
}

@Preview
@Composable
private fun PreviewPaneZoomedPreview() {
    PreviewPaneForPreview(zoom = PreviewZoomState(initialScale = 4f, initialOffset = Offset(120f, -60f)))
}

@Composable
private fun PreviewPaneForPreview(zoom: PreviewZoomState) {
    val processed = remember { checkerboardBitmap(width = 48, height = 32, cellSize = 4) }
    val original = remember { checkerboardBitmap(width = 480, height = 320, cellSize = 40) }
    AppTheme(darkTheme = false) {
        Box(Modifier.size(width = 720.dp, height = 480.dp)) {
            PreviewPaneContent(
                preview = PreviewState(
                    file = File("sample.jpg"),
                    original = original,
                    painted = null,
                    processed = processed,
                    originalSize = ImageSize(original.width, original.height),
                    outputSize = ImageSize(processed.width, processed.height),
                    outputFormat = OutputFormat.Jpeg,
                    outputByteSize = 1_234,
                    captionFields = mapOf(),
                    crop = null,
                    strokes = listOf(),
                    loading = false,
                    error = null,
                ),
                item = ImageItem(File("sample.jpg")),
                index = 0,
                total = 3,
                options = EditOptions(),
                zoom = zoom,
                onMove = {},
                onCropChange = {},
                onRotateClockwise = {},
                onRotateCounterClockwise = {},
                onStrokesChange = {},
            )
        }
    }
}

/** ピクセルの粗さが分かるよう、市松模様に斜めのグラデーションを重ねた画像 */
private fun checkerboardBitmap(width: Int, height: Int, cellSize: Int): ImageBitmap {
    val bitmap = ImageBitmap(width, height)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    for (y in 0 until height step cellSize) {
        for (x in 0 until width step cellSize) {
            val isDark = (x / cellSize + y / cellSize) % 2 == 0
            val shade = (x + y).toFloat() / (width + height)
            paint.color = if (isDark) Color(0.2f, 0.3f + shade * 0.5f, 0.6f) else Color(0.95f, 0.85f - shade * 0.4f, 0.5f)
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + cellSize).toFloat(), (y + cellSize).toFloat(), paint)
        }
    }
    return bitmap
}
