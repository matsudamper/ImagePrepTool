package com.imagepreptool.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imagepreptool.model.EditOptions
import com.imagepreptool.presentation.ImageItem
import com.imagepreptool.presentation.PreviewState
import com.imagepreptool.ui.components.Pill
import com.imagepreptool.ui.components.SegmentedControl
import com.imagepreptool.ui.theme.AppTheme
import com.imagepreptool.ui.theme.MonoNumberStyle

private enum class PreviewMode(val label: String) { Processed("書き出し後"), Original("元画像") }

@Composable
fun PreviewPane(
    preview: PreviewState,
    item: ImageItem?,
    index: Int,
    total: Int,
    options: EditOptions,
    onToggleInclusion: () -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = AppTheme.extended
    var mode by rememberSaveable { mutableStateOf(PreviewMode.Processed) }
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
                    Checkbox(checked = item.included, onCheckedChange = { onToggleInclusion() })
                    Column(Modifier.weight(1f)) {
                        Text(item.file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (item.included) "書き出しに含める" else "書き出しから外しています",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${index + 1} / $total",
                        style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                SegmentedControl(
                    options = PreviewMode.entries,
                    selected = mode,
                    onSelect = { mode = it },
                    label = { it.label },
                    modifier = Modifier.width(188.dp),
                )
            }
        }

        // 画像
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().hoverable(interaction),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap: ImageBitmap? = when (mode) {
                PreviewMode.Processed -> preview.processed ?: preview.original
                PreviewMode.Original -> preview.original
            }
            when {
                preview.error != null -> ErrorContent(preview.error)
                bitmap != null -> FittedImage(bitmap)
                preview.file != null -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = ext.canvasContent)
                else -> Text("画像を選択するとプレビューが表示されます", color = ext.canvasContent)
            }

            LoadingBadge(
                visible = preview.loading && bitmap != null,
                modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            )

            if (total > 1) {
                NavButton(
                    visible = hovered && index > 0,
                    onClick = { onMove(-1) },
                    icon = { Icon(Icons.Rounded.ChevronLeft, contentDescription = "前の画像") },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                )
                NavButton(
                    visible = hovered && index < total - 1,
                    onClick = { onMove(1) },
                    icon = { Icon(Icons.Rounded.ChevronRight, contentDescription = "次の画像") },
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = ext.canvasContent, modifier = Modifier.size(16.dp))
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
        }
    }
}

/** 余白を残して画面に収まる大きさで表示する（拡大はしない） */
@Composable
private fun FittedImage(bitmap: ImageBitmap) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val naturalWidth = with(density) { bitmap.width.toDp() }
        val naturalHeight = with(density) { bitmap.height.toDp() }
        val scale = minOf(1f, maxWidth / naturalWidth, maxHeight / naturalHeight)
        Image(
            bitmap = bitmap,
            contentDescription = "プレビュー",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(naturalWidth * scale, naturalHeight * scale)
                .shadow(18.dp, clip = false),
        )
    }
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
        Icon(Icons.Rounded.BrokenImage, null, tint = ext.canvasContent, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("この画像は読み込めません", style = MaterialTheme.typography.titleSmall, color = ext.canvasContent)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = ext.canvasContent)
    }
}
